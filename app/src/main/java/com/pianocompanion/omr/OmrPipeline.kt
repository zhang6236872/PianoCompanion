package com.pianocompanion.omr

import com.pianocompanion.data.model.Articulation
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.ArticulationDetector
import com.pianocompanion.omr.image.BarlineDetector
import com.pianocompanion.omr.image.BarlineType
import com.pianocompanion.omr.image.BinaryDenoiser
import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.Deskewer
import com.pianocompanion.omr.image.KeystoneCorrector
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
import com.pianocompanion.omr.image.VoltaDetector
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

        // --- 0.7. Keystone (perspective / yaw) correction --------------------
        // deskew 只能消除均匀的平面内旋转；若拍摄时相机相对纸面有偏航(yaw)，
        // 谱线会朝消失点汇聚——谱表在图像左右两侧的高度不一致，破坏等间距分组与
        // Y→音高映射。此处按左右两侧谱带高度比检测并校正梯形畸变。
        val keystoneOutcome = KeystoneCorrector.correct(denoised)
        val warped = keystoneOutcome.image
        val keystoneApplied = keystoneOutcome.applied

        // --- 1. Staff detection -------------------------------------------------
        val systems = StaffLineDetector.detect(warped)
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
        val minLineRun = (warped.width * 0.5).toInt().coerceAtLeast(lineSpacing * 4)
        val cleaned = StaffLineRemover.remove(warped, minLineRun, maxLineThickness)

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
            val staff = resolveStaff(signatures.perSystem[sysIdx].clef, systems, system, warped)
            for (nh in noteheadsBySystem[sysIdx]) {
                located += Located(nh, staff, sysIdx)
            }
        }

        // --- 6.5. 小节线检测 -------------------------------------------------
        // 在含谱线的 warped 图上检测竖线（小节线需贯穿全谱高）。用已检测符头 X 排除符干。
        // 小节线位置用于精确计算 ScoreNote.measureIndex（替代旧的 startTime/measureMs 估算）。
        val barlinesBySystem = systems.mapIndexed { idx, system ->
            val endX = signatures.perSystem.getOrElse(idx) { null }?.signatureEndX ?: 0
            BarlineDetector.detect(warped, system, endX, noteheadsBySystem[idx].map { it.centerX })
        }
        // 虚线/段线（DASHED）是小节内细分，不计入 measureIndex；
        // 仅 SINGLE / DOUBLE / FINAL / REPEAT_START / REPEAT_END 是真正的小节边界。
        val totalBarlines = barlinesBySystem.sumOf { bars -> bars.count { it.type != BarlineType.DASHED } }

        // 每系统的累积小节偏移：系统 0 从 0 开始，系统 1 的偏移 = 系统 0 的小节线数，
        // 以此类推（多系统页面的乐谱从上到下、系统内从左到右流动）。
        val measureBaseBySystem = IntArray(systems.size)
        if (systems.size > 1) {
            var base = 0
            for (idx in systems.indices) {
                measureBaseBySystem[idx] = base
                base += barlinesBySystem[idx].count { it.type != BarlineType.DASHED }
            }
        }

        // --- 6.6. 反复跳房子(volta)检测 --------------------------------------
        // 跳房子是顶线上方的方括号，标记反复时第几遍走哪几个小节。搜索带限制在该系统
        // 顶线上方、且不低于上一个系统底线（多系统页面避免把上方谱表内容误判）。
        //
        // 注意：VoltaDetector 的搜索带本身就在顶线上方（1~2 个谱线间距），而签名区
        // （谱号/调号/拍号）位于谱线**之间**——两者在竖直方向天然分离，故**不传**
        // signatureEndX。若传入，谱号上方残留的跳房子序号数字可能被 SignatureDetector
        // 误判为拍号数字、推高 signatureEndX，进而切掉跳房子括号的左竖钩导致漏检。
        val voltasBySystem = systems.mapIndexed { idx, system ->
            val upperLimit = if (idx > 0) {
                systems[idx - 1].bottomLine.center + lineSpacing
            } else {
                0
            }
            VoltaDetector.detect(warped, system, upperLimit)
        }
        val allVoltas = voltasBySystem.flatten()

        // --- 节奏分析：符干/横梁/符尾 → 真实时值（不再清一色四分音符）---------
        val rhythms = RhythmAnalyzer.analyze(cleaned, located.map { it.nh }, lineSpacing)

        // --- 6.7. 演奏法标记(articulation)检测 -------------------------------
        // 演奏法标记是符头上方或下方（与符干相反一侧）的小标记：
        // - 断奏点(staccato •)：小实心圆点，指示短促断开
        // - 保持音(tenuto —)：短水平线，指示充分保持时值
        // - 重音(accent >)：小楔形/三角，指示强调
        // - 短断奏(staccatissimo ▼)：垂直小楔形，指示极短促断开
        // - 强音(marcato ^)：垂直V形标记，指示强烈强调
        // 使用节奏分析的符干方向确定搜索侧（避免符干干扰），在 cleaned 图像上检测。
        val articulations = ArticulationDetector.detectArticulations(
            cleaned, located.map { it.nh }, rhythms, lineSpacing
        )

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
        // 关键：多系统页面（真实乐谱一页通常有多行谱表，自上而下排列）必须按
        // (systemIdx, x) 排序——先处理上方系统的全部音符（左→右），再处理下方系统。
        // 若仅按 x 排序，下方系统最左侧的音符（小 x）会被插到上方系统最右侧音符（大 x）
        // 之前，完全打乱音乐顺序。
        val xTolerance = (lineSpacing * 0.8).toInt().coerceAtLeast(2)
        val quarterMs = 60_000L / tempo.coerceAtLeast(1)
        // 拍号决定一个小节的时长；未识别到时默认 4/4。
        val quartersPerMeasure = detectedTimeSig?.quartersPerMeasure ?: 4.0
        val measureMs = (quarterMs * quartersPerMeasure).toLong().coerceAtLeast(1L)

        // 时间轴项：音符（noteIdx >= 0）或休止符（noteIdx < 0，restDuration 非 null）。
        // systemIdx 确保多系统时按谱表从上到下、系统内从左到右排序。
        data class TimelineItem(val systemIdx: Int, val x: Int, val noteIdx: Int, val restDuration: NoteDuration?)
        val timeline = ArrayList<TimelineItem>()
        for (idx in located.indices) {
            timeline += TimelineItem(located[idx].systemIdx, located[idx].nh.centerX, idx, null)
        }
        // 休止符按所属系统索引配对（保留系统归属），而非使用丢失系统信息的 flattened 列表。
        restsBySystem.forEachIndexed { sysIdx, rests ->
            for (rest in rests) {
                timeline += TimelineItem(sysIdx, rest.centerX, -1, rest.duration)
            }
        }
        timeline.sortWith(compareBy({ it.systemIdx }, { it.x }))

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
            val leadSystemIdx = located[leadIdx].systemIdx
            val columnX = item.x
            // 同一列（和弦）共享起始时间与时值；取首成员的时值（含附点倍率）。
            // 和弦成员必须属于同一系统且 X 在容差范围内——跨系统的音符即使 X 较小
            // （多系统排序后下方系统的音符 X 小于上方系统末尾音符）也不能合并。
            val duration = rhythms[leadIdx].effectiveMillis(quarterMs)
            val startTime = cursor
            var j = i
            while (j < timeline.size && timeline[j].noteIdx >= 0 &&
                located[timeline[j].noteIdx].systemIdx == leadSystemIdx &&
                located[timeline[j].noteIdx].nh.centerX - columnX <= xTolerance
            ) {
                val ln = located[timeline[j].noteIdx]
                val system = systems[ln.systemIdx]
                val midi = PitchMapper.mapToMidi(ln.nh.centerY, system, ln.staff, keysBySystem[ln.systemIdx])
                if (midi in 21..108) {
                    // 小节线检测到时，用视觉小节线位置精确计算 measureIndex
                    // （该音符之前有多少条小节线 = 该音符所在小节序号）；
                    // 未检测到小节线时回退到旧的 startTime/measureMs 时间估算。
                    val measureIndex = if (totalBarlines > 0) {
                        measureBaseBySystem[ln.systemIdx] +
                            barlinesBySystem[ln.systemIdx]
                                .count { it.centerX < ln.nh.centerX && it.type != BarlineType.DASHED }
                    } else {
                        (startTime / measureMs).toInt()
                    }
                    notes += ScoreNote(
                        midiNumber = midi,
                        noteName = MusicUtils.midiToNoteName(midi),
                        startTime = startTime,
                        duration = duration,
                        staff = ln.staff,
                        measureIndex = measureIndex,
                        articulation = articulations[timeline[j].noteIdx] ?: Articulation.NONE
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
        // 透视变形提示：校正了梯形畸变时告知用户（说明拍摄角度有偏航）。
        if (keystoneApplied) {
            val diff = keystoneOutcome.ratio - 1.0
            val pct = "%.0f".format(kotlin.math.abs(diff) * 100)
            warnings += "检测到拍摄角度导致的透视变形（左右两侧谱表高度差约 $pct%），已自动校正梯形畸变"
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
        // 小节线提示：告知用户检测到的小节数，说明 measureIndex 基于视觉小节线。
        if (totalBarlines > 0) {
            val maxMeasure = (notes.maxOfOrNull { it.measureIndex } ?: 0) + 1
            val typeSummary = barlinesBySystem.flatten().groupBy { it.type }
                .entries.joinToString("、") { (type, list) ->
                    val name = when (type) {
                        BarlineType.SINGLE -> "单竖线"
                        BarlineType.DOUBLE -> "双竖线"
                        BarlineType.FINAL -> "终止线"
                        BarlineType.REPEAT_START -> "反复开始"
                        BarlineType.REPEAT_END -> "反复结束"
                        BarlineType.DASHED -> "虚线"
                    }
                    "${list.size} 条$name"
                }
            warnings += "检测到 $totalBarlines 条小节线（$typeSummary），乐谱约 $maxMeasure 个小节，已据此计算小节归属"
        }
        // 跳房子提示：告知用户检测到的反复结尾结构。
        if (allVoltas.isNotEmpty()) {
            val voltaSummary = allVoltas.joinToString("、") { "第${it.number}结尾" }
            warnings += "检测到 ${allVoltas.size} 个反复跳房子（$voltaSummary），已标注反复结构"
        }
        // 演奏法标记提示：告知用户检测到的断奏/保持音/重音标记。
        if (articulations.isNotEmpty()) {
            val parts = articulations.values.groupBy { it }
                .entries.joinToString("、") { (art, list) ->
                    val name = when (art) {
                        Articulation.STACCATO -> "断奏点(staccato)"
                        Articulation.TENUTO -> "保持音(tenuto)"
                        Articulation.ACCENT -> "重音(accent)"
                        Articulation.STACCATISSIMO -> "短断奏(staccatissimo)"
                        Articulation.MARCATO -> "强音(marcato)"
                        Articulation.NONE -> ""
                    }
                    "${list.size} 个$name"
                }
            warnings += "检测到 ${articulations.size} 个演奏法标记（$parts），已标注"
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
