package com.pianocompanion.omr

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.BinaryDenoiser
import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.Deskewer
import com.pianocompanion.omr.image.NoteDuration
import com.pianocompanion.omr.image.Notehead
import com.pianocompanion.omr.image.NoteheadDetector
import com.pianocompanion.omr.image.PitchMapper
import com.pianocompanion.omr.image.Rest
import com.pianocompanion.omr.image.RestDetector
import com.pianocompanion.omr.image.RhythmAnalyzer
import com.pianocompanion.omr.image.SignatureDetector
import com.pianocompanion.omr.image.StaffLineDetector
import com.pianocompanion.omr.image.StaffLineRemover
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

/**
 * Pure-Kotlin OMR pipeline: a [BinaryImage] → [Score].
 *
 * Stages:
 *  1. Staff-line detection (horizontal projection → grouped systems).
 *  2. Staff-line removal (long, thin horizontal runs erased; glyphs preserved).
 *  3. Connected-component labeling of the cleaned image.
 *  4. Notehead selection (size/aspect filters scaled by staff spacing).
 *  5. Pitch mapping (notehead Y → MIDI via staff geometry).
 *  6. Horizontal sequencing (left→right; near-vertical stacks become chords).
 *
 * Being Android-free, the whole pipeline is verifiable in JVM unit tests with
 * synthetic scores.
 */
object OmrPipeline {

    data class Diagnostics(
        val systemCount: Int,
        val lineSpacing: Int,
        val noteheadCount: Int
    )

    data class Result(
        val score: Score,
        val warnings: List<String>,
        val diagnostics: Diagnostics
    ) {
        val isEmpty: Boolean get() = score.notes.isEmpty()
    }

    /**
     * @param binary pre-binarized score image.
     * @param title  title for the produced score.
     * @param tempo  BPM used to convert step index → millisecond timing.
     */
    fun recognize(
        binary: BinaryImage,
        title: String = "拍照识别的乐谱",
        tempo: Int = 120
    ): Result {
        // --- 0. Deskew (correct rotation so staff lines are horizontal) --------
        // Real photos are rarely perfectly level. Even a small tilt spreads staff
        // lines across many rows and breaks the horizontal-projection detector.
        val skewAngle = Deskewer.estimateSkewAngle(binary)
        val img = if (abs(skewAngle) >= 0.5) {
            Deskewer.rotate(binary, -skewAngle)
        } else {
            binary
        }
        val deskewApplied = abs(skewAngle) >= 0.5

        // --- 0.5. Denoise (remove isolated specks + fill holes in strokes) ----
        // 真实拍照 / 自适应二值化会引入椒盐噪声：孤立黑斑污染水平投影与连通块，
        // 实心笔画内的白孔降低填充率判定准确性。此处保守清理（不破坏 1px 谱线）。
        val (denoised, denoiseStats) = BinaryDenoiser.denoise(img)

        // --- 1. Staff detection -------------------------------------------------
        val systems = StaffLineDetector.detect(denoised)
        if (systems.isEmpty()) {
            return Result(
                score = emptyScore(title, tempo),
                warnings = listOf("未检测到五线谱，请拍摄清晰、端正的乐谱"),
                diagnostics = Diagnostics(0, 0, 0)
            )
        }

        val lineSpacing = systems.map { it.lineSpacing }.average().toInt().coerceAtLeast(1)
        val avgThickness = systems.flatMap { it.lines }.map { it.thickness }.average().toInt().coerceAtLeast(1)
        val maxLineThickness = (avgThickness + 2).coerceIn(2, 6)

        // --- 2. Staff removal ---------------------------------------------------
        val minLineRun = (denoised.width * 0.5).toInt().coerceAtLeast(lineSpacing * 4)
        val cleaned = StaffLineRemover.remove(denoised, minLineRun, maxLineThickness)

        // --- 3. Connected components -------------------------------------------
        val blobs = ConnectedComponents.label(cleaned, minPixels = 4)

        // --- 4. 每系统检测符头 -------------------------------------------------
        // 分两阶段：先用"紧凑主扫描"得到不含谱号/拍号等高大字形的干净符头，用于确定
        // 签名区右界（避免谱号曲线被符头恢复扫描误判为符头、进而污染签名区边界）；
        // 再用完整扫描（含符头+符干恢复、连梁组切分）得到最终符头，并排除签名区。
        data class Located(val nh: Notehead, val staff: Staff, val systemIdx: Int)
        val fullNoteheadsBySystem = ArrayList<List<Notehead>>(systems.size)
        val noteheadsForSignature = ArrayList<List<Notehead>>(systems.size)
        systems.forEach { system ->
            val band = lineSpacing * 3
            val top = system.topLine.center - band
            val bottom = system.bottomLine.center + band
            val compact = NoteheadDetector.detect(blobs, system.lineSpacing) // 仅紧凑主扫描
                .filter { it.centerY in top..bottom }
            // 传入 cleaned 图像，启用"符头+符干"融合块的二次恢复扫描与连梁组切分。
            val full = NoteheadDetector.detect(blobs, system.lineSpacing, cleaned)
                .filter { it.centerY in top..bottom }
            fullNoteheadsBySystem += full
            // 签名区右界优先用干净符头；若该系统全是带符干/连梁音符（紧凑扫描为空），
            // 则回退到完整扫描，保证边界不为空。
            noteheadsForSignature += if (compact.isNotEmpty()) compact else full
        }

        // --- 5. 签名识别：谱号 / 调号 / 拍号（替代旧的"按竖直位置推断谱表"）---
        val signatures = SignatureDetector.detect(cleaned, systems, blobs, noteheadsForSignature)
        val keysBySystem = signatures.perSystem.map { it.keySignature }
        val detectedTimeSig = signatures.timeSignature

        // 排除签名区内的符头：谱号曲线、拍号数字等高大字形会被符头恢复扫描误判为
        // 符头，它们位于第一个真实音符左侧的签名区内，按 signatureEndX 过滤掉。
        val noteheadsBySystem = fullNoteheadsBySystem.mapIndexed { idx, nhs ->
            val endX = signatures.perSystem.getOrElse(idx) { null }?.signatureEndX ?: 0
            if (endX <= 0) nhs else nhs.filter { it.centerX > endX }
        }

        // --- 6. 用识别到的谱号 + 调号映射音高 ----------------------------------
        val located = ArrayList<Located>()
        systems.forEachIndexed { sysIdx, system ->
            val staff = resolveStaff(signatures.perSystem[sysIdx].clef, systems, system, denoised)
            for (nh in noteheadsBySystem[sysIdx]) {
                located += Located(nh, staff, sysIdx)
            }
        }

        // --- 节奏分析：符干/横梁/符尾 → 真实时值（不再清一色四分音符）---------
        val rhythms = RhythmAnalyzer.analyze(cleaned, located.map { it.nh }, lineSpacing)

        // --- 7. 休止符检测 ---------------------------------------------------
        // 在尚未被判定为符头的连通块中，依据几何形状识别休止符
        // （全/二分/四分/八分/十六分/三十二分）。传入 cleaned 图像以启用
        // 旗钩层数计数，从而区分八分/十六分/三十二分休止符。
        val restsBySystem = ArrayList<List<Rest>>(systems.size)
        systems.forEachIndexed { sysIdx, system ->
            val staffLineYs = system.lines.map { it.center }
            val endX = signatures.perSystem.getOrElse(sysIdx) { null }?.signatureEndX ?: 0
            restsBySystem += RestDetector.detect(
                blobs, noteheadsBySystem[sysIdx], lineSpacing, staffLineYs, endX, cleaned
            )
        }
        val allRests = restsBySystem.flatten()

        // --- 8. Horizontal sequencing (left→right; same-column = chord; rests advance cursor) ---
        val xTolerance = (lineSpacing * 0.8).toInt().coerceAtLeast(2)
        val quarterMs = 60_000L / tempo.coerceAtLeast(1)
        // 拍号决定一个小节的时长；未识别到时默认 4/4。
        val quartersPerMeasure = detectedTimeSig?.quartersPerMeasure ?: 4.0
        val measureMs = (quarterMs * quartersPerMeasure).toLong().coerceAtLeast(1L)

        // 时间轴项：音符（noteIdx >= 0）或休止符（noteIdx < 0，restDuration 非 null）。
        data class TimelineItem(val x: Int, val noteIdx: Int, val restDuration: NoteDuration?)
        val timeline = ArrayList<TimelineItem>()
        for (idx in located.indices) {
            timeline += TimelineItem(located[idx].nh.centerX, idx, null)
        }
        for (rest in allRests) {
            timeline += TimelineItem(rest.centerX, -1, rest.duration)
        }
        timeline.sortBy { it.x }

        val notes = ArrayList<ScoreNote>()
        var cursor = 0L
        var i = 0
        while (i < timeline.size) {
            val item = timeline[i]
            if (item.noteIdx < 0) {
                // 休止符：推进时间轴但不产生音符。
                cursor += item.restDuration!!.toMillis(quarterMs)
                i++
                continue
            }
            // 音符：处理同列和弦（与旧逻辑一致）。
            val leadIdx = item.noteIdx
            val columnX = item.x
            // 同一列（和弦）共享起始时间与时值；取首成员的时值（含附点倍率）。
            val duration = rhythms[leadIdx].effectiveMillis(quarterMs)
            val startTime = cursor
            var j = i
            while (j < timeline.size && timeline[j].noteIdx >= 0 &&
                located[timeline[j].noteIdx].nh.centerX - columnX <= xTolerance
            ) {
                val ln = located[timeline[j].noteIdx]
                val system = systems[ln.systemIdx]
                val midi = PitchMapper.mapToMidi(ln.nh.centerY, system, ln.staff, keysBySystem[ln.systemIdx])
                if (midi in 21..108) {
                    notes += ScoreNote(
                        midiNumber = midi,
                        noteName = MusicUtils.midiToNoteName(midi),
                        startTime = startTime,
                        duration = duration,
                        staff = ln.staff,
                        measureIndex = (startTime / measureMs).toInt()
                    )
                }
                j++
            }
            cursor += duration
            i = j
        }

        val warnings = ArrayList<String>()
        if (deskewApplied) {
            warnings += "检测到图像倾斜约 ${skewAngle}°，已自动校正"
        }
        // 降噪提示：仅在清理掉可观噪声时告知用户（说明源照片可能有噪点）。
        if (denoiseStats.totalChanged > 0) {
            warnings += "检测到图像噪点（擦除 ${denoiseStats.pepperRemoved} 个杂散像素、" +
                "填充 ${denoiseStats.saltFilled} 个笔画孔洞），已自动降噪"
        }
        if (notes.isEmpty()) {
            warnings += "识别到五线谱但未找到音符（可能图片太小或音符不清晰）"
        }
        if (systems.size > 1) {
            warnings += "检测到 ${systems.size} 个谱表，已分别识别谱号后处理"
        }
        // 签名识别提示。
        warnings += signatures.warnings
        val detectedClefs = signatures.perSystem.map { it.clef }
        if (detectedClefs.any {
                it == SignatureDetector.ClefType.TREBLE ||
                    it == SignatureDetector.ClefType.BASS ||
                    it == SignatureDetector.ClefType.ALTO ||
                    it == SignatureDetector.ClefType.TENOR
            }
        ) {
            val names = detectedClefs.joinToString("、") {
                when (it) {
                    SignatureDetector.ClefType.TREBLE -> "高音谱号"
                    SignatureDetector.ClefType.BASS -> "低音谱号"
                    SignatureDetector.ClefType.ALTO -> "中音谱号"
                    SignatureDetector.ClefType.TENOR -> "次中音谱号"
                    else -> "未知"
                }
            }
            warnings += "谱号识别：$names"
        }
        if (detectedTimeSig != null) {
            warnings += "拍号识别：${detectedTimeSig.numerator}/${detectedTimeSig.denominator}"
        }
        // 节奏提示：根据是否检测到符干给出更准确的说明。
        val detectedStems = rhythms.count { it.hasStem }
        val durationTypes = rhythms.map { it.duration }.toSet()
        val dottedCount = rhythms.count { it.dotted }
        val flaggedCount = rhythms.count { it.flagCount > 0 }
        if (detectedStems > 0 || durationTypes.any { it != NoteDuration.QUARTER }) {
            val typeNames = durationTypes.joinToString("、") { it.label }
            val dotHint = if (dottedCount > 0) "，含 $dottedCount 个附点音符" else ""
            val flagHint = if (flaggedCount > 0) "，含 $flaggedCount 个带符尾音符" else ""
            warnings += "节奏已通过符干/横梁/符尾分析估算（$typeNames$dotHint$flagHint），复杂节奏需人工校对"
        } else {
            val dotHint = if (dottedCount > 0) "，含 $dottedCount 个附点音符" else ""
            warnings += "节奏为估算值（未检测到符干，每个音符按四分音符处理$dotHint），实际时值需人工校对"
        }
        // 休止符提示。
        if (allRests.isNotEmpty()) {
            val restSummary = allRests.groupBy { it.duration }
                .entries.joinToString("、") { (dur, list) ->
                    val label = dur.label.replace("音符", "休止符")
                    "${list.size} 个$label"
                }
            warnings += "检测到 ${allRests.size} 个休止符（$restSummary），已计入时间轴"
        }

        return Result(
            score = Score(
                id = "omr_${System.currentTimeMillis()}",
                title = title,
                composer = "OMR",
                notes = notes,
                tempo = tempo,
                timeSignature = detectedTimeSig?.toString() ?: "4/4",
                source = ScoreSource.OMR
            ),
            warnings = warnings,
            diagnostics = Diagnostics(systems.size, lineSpacing, notes.size)
        )
    }

    /**
     * 把识别到的谱号转为 [Staff]；识别失败(UNKNOWN)时回退到旧的竖直位置启发式，
     * 保证对没有绘制谱号的合成图仍保持原有行为。
     */
    private fun resolveStaff(
        clef: SignatureDetector.ClefType,
        systems: List<com.pianocompanion.omr.image.StaffSystem>,
        system: com.pianocompanion.omr.image.StaffSystem,
        binary: BinaryImage
    ): Staff = when (clef) {
        SignatureDetector.ClefType.TREBLE -> Staff.TREBLE
        SignatureDetector.ClefType.BASS -> Staff.BASS
        SignatureDetector.ClefType.ALTO -> Staff.ALTO
        SignatureDetector.ClefType.TENOR -> Staff.TENOR
        SignatureDetector.ClefType.UNKNOWN ->
            if (systems.size > 1 && system.centerY >= binary.height / 2) Staff.BASS else Staff.TREBLE
    }

    private fun emptyScore(title: String, tempo: Int): Score = Score(
        id = "omr_${System.currentTimeMillis()}",
        title = title,
        composer = "OMR",
        notes = emptyList(),
        tempo = tempo,
        source = ScoreSource.OMR
    )
}
