package com.pianocompanion.omr

import com.pianocompanion.data.model.Articulation
import com.pianocompanion.data.model.Accidental
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.AccidentalDetector
import com.pianocompanion.omr.image.ArticulationDetector
import com.pianocompanion.omr.image.BarlineDetector
import com.pianocompanion.omr.image.BarlineType
import com.pianocompanion.omr.image.BinaryDenoiser
import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.Deskewer
import com.pianocompanion.omr.image.DynamicMarkingDetector
import com.pianocompanion.omr.image.FermataDetector
import com.pianocompanion.omr.image.FingeringDetector
import com.pianocompanion.omr.image.GraceNoteDetector
import com.pianocompanion.omr.image.HairpinDetector
import com.pianocompanion.omr.image.KeystoneCorrector
import com.pianocompanion.omr.image.NoteDuration
import com.pianocompanion.omr.image.Notehead
import com.pianocompanion.omr.image.NoteheadDetector
import com.pianocompanion.omr.image.OctavaDetector
import com.pianocompanion.omr.image.PitchMapper
import com.pianocompanion.omr.image.RepeatCountDetector
import com.pianocompanion.omr.image.Rest
import com.pianocompanion.omr.image.RestDetector
import com.pianocompanion.omr.image.RhythmAnalyzer
import com.pianocompanion.omr.image.SignatureDetector
import com.pianocompanion.omr.image.StaffLineDetector
import com.pianocompanion.omr.image.StaffLineRemover
import com.pianocompanion.omr.image.SlurDetector
import com.pianocompanion.omr.image.TempoMarkingDetector
import com.pianocompanion.omr.image.TieDetector
import com.pianocompanion.omr.image.TrillDetector
import com.pianocompanion.omr.image.TupletDetector
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

        // --- 3.5. Tempo marking detection --------------------------------------
        // 检测乐谱上方的速度记号（如 ♩=120），覆盖默认 tempo 值。
        // 此值决定所有音符的时长计算（quarterMs = 60000 / tempo）。
        // 若未检测到速度记号，回退到调用方传入的默认 tempo。
        val detectedTempo = TempoMarkingDetector.detect(cleaned, blobs, systems, lineSpacing)
        val effectiveTempo = detectedTempo?.bpm ?: tempo

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

        // --- 6.7. 反复次数标注(×N)检测 --------------------------------------
        // 当反复段需演奏超过两遍时，反复结束小节线(:‖)上方会标注 "×N"。
        // 在 cleaned 图像上检测乘号 "×" + 数字的组合（复用连通块 + 数字模板）。
        // 乘号是反复次数标注的标志特征，可据此与跳房子序号(纯数字)区分。
        // 不修改音符数据模型，仅产生提示信息。
        val repeatCounts = RepeatCountDetector.detect(
            cleaned, blobs, barlinesBySystem, systems, lineSpacing
        )

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

        // --- 6.8. 延音线(tie)检测 -------------------------------------------
        // 延音线是连接两个同音高音符的弧线，指示将两个音符的时值合并为一个持续音符。
        // 在 cleaned 图像上检测，使用列投影覆盖率法（弧线在每个列都有墨迹）。
        // 检测到的 tie 在步骤 8 的时间轴循环中合并：跳过第二个音符，将其时值加到
        // 第一个音符上——否则 score follower 会期待一个永远不会出现的第二个 onset。
        val ties = TieDetector.detect(
            cleaned, located.map { it.nh }, located.map { it.systemIdx }, lineSpacing
        )
        val tiedToNoteheads = ties.map { it.secondNoteIdx }.toSet()
        val tieFromMap = ties.associate { it.secondNoteIdx to it.firstNoteIdx }

        // --- 6.9. 连音(slur)检测 ---------------------------------------------
        // 连音是连接不同音高音符的弧线，指示 legato（连奏）奏法。与延音线(tie)
        // 不同，连音不改变音符时值或 onset——它纯粹是表情/奏法指示。
        // 在 cleaned 图像上检测，使用逐列插值搜索带跟随音高斜率（两个不同高度
        // 的符头之间的弧线会随高度变化倾斜）。检测到的 slur 段被合并为多音符组。
        val slurs = SlurDetector.detect(
            cleaned, located.map { it.nh }, located.map { it.systemIdx }, lineSpacing
        )

        // --- 6.10. 力度记号(dynamic marking)检测 ------------------------------
        // 力度记号(pp/p/mp/mf/f/ff/sfz/rf/cresc./decresc. 等)是谱表下方的文字标记，
        // 指示演奏音量。使用 5×7 点阵模板匹配识别 9 个字母(p/m/f/s/z/r/c/e/d)，
        // 组合成力度文本，缩写标记末尾句点被单独检测。
        // 不修改音符数据模型，仅产生提示信息。
        val dynamicMarkings = DynamicMarkingDetector.detect(
            cleaned, blobs, systems, lineSpacing
        )

        // --- 6.11. 渐强/渐弱符号(hairpin)检测 -------------------------------
        // Hairpin 是谱表下方的图形化渐强(`<`)/渐弱(`>`)标记——两条从窄端向宽端
        // 发散（渐强）或收敛（渐弱）的斜线。与文字力度记号互补：hairpin 表达
        // 连续的音量变化方向。逐列竖直跨度分析判定方向（左窄右宽=渐强，
        // 左宽右窄=渐弱），低填充率过滤排除实心形状。不修改音符数据模型，仅产生提示。
        val hairpins = HairpinDetector.detect(cleaned, blobs, systems, lineSpacing)

        // --- 6.12. 延音记号/停留号(fermata)检测 -------------------------------
        // Fermata 是符头上方（正立 `⌒`）或下方（倒立 `⌣`）的半圆弧+圆点符号，
        // 指示演奏者在该音符上停留比记谱时值更长的时间。
        // 通过圆顶形状验证（中心列顶部高于两侧边缘顶部）区分弧形与其他墨块。
        // 不修改音符数据模型，仅产生提示信息。
        val fermatas = FermataDetector.detect(
            cleaned, blobs, located.map { it.nh },
            located.map { it.systemIdx }, systems, lineSpacing
        )

        // --- 6.13. 装饰音(grace note)检测 -----------------------------------
        // 装饰音是出现在主音符正前方的小音符（短前倚音 acciaccatura 有斜杠 /
        // 长前倚音 appoggiatura 无斜杠），在钢琴音乐中极为常见。
        // 通过相对尺寸（面积 < 中位数 55%）+ 邻近性（紧邻更大音符右侧）识别。
        // 检测到的装饰音在步骤 8 的时间轴循环中标记 isGraceNote=true，并赋予
        // 极短时值且不推进时间游标——装饰音"偷取"主音符时间而非独立占拍。
        val graceNotes = GraceNoteDetector.detect(
            located.map { it.nh }, located.map { it.systemIdx }, cleaned, lineSpacing
        )
        val graceNoteheadIndices = graceNotes.map { it.noteheadIdx }.toSet()

        // --- 6.14. 颤音(trill)检测 ------------------------------------------
        // 颤音(trill)用符头上方的字母"tr"标记（有时后接波浪线），指示演奏者
        // 快速交替演奏本音与其上方相邻音。在巴洛克/古典钢琴音乐中极为常见。
        // 通过字母"t"+"r"的模板匹配识别（复用 DynamicMarkingDetector 的模板方法），
        // 搜索区域限定在谱表顶线上方 0.5~4.0 谱线间距内。不修改音符数据模型，仅产生提示。
        val trills = TrillDetector.detect(
            cleaned, blobs, located.map { it.nh },
            located.map { it.systemIdx }, systems, lineSpacing
        )

        // --- 6.15. 三连音/连音组(tuplet)检测 --------------------------------
        // 连音组(tuplet)是在正常应该容纳 M 个音符的时间段内挤入 N 个等时值音符的记法，
        // 最常见的是三连音(triplet, 3 in 2)。标注为谱表上方的数字（可能带方括号）。
        // 检测到的连音组成员的时值会按比例调整：三连音每个音符 ×2/3。
        // 复用 SignatureDetector.classifyDigit 做数字识别，确保与拍号数字模板一致。
        val tuplets = TupletDetector.detect(
            cleaned, blobs, located.map { it.nh },
            located.map { it.systemIdx }, systems, lineSpacing
        )
        // 构建 notehead 索引 → (tuplet数字, 缩放比例) 的映射，供时间轴循环使用。
        val tupletByNotehead = HashMap<Int, Pair<Int, Double>>()
        for (tuplet in tuplets) {
            val ratio = TupletDetector.tupletRatio(tuplet.number)
            for (nhIdx in tuplet.noteheadIndices) {
                tupletByNotehead[nhIdx] = tuplet.number to ratio
            }
        }

        // --- 6.16. 八度记号(8va/8vb/15ma/15mb)检测 -----------------------------
        // 八度记号是指示演奏者将一段音符移高或移低一个/两个八度的标记。
        // 由数字"8"或"15"加虚线组成，位置在谱表上方（移高）或下方（移低）。
        // 这是**音高正确性**的关键：若不识别 8va，被标记的音符会被读取为比实际
        // 演奏低一个八度——score follower 会完全匹配错误。
        // 检测到的八度移位会在步骤 8 的时间轴循环中应用到音符的 MIDI 音高上。
        val ottavaShifts = OctavaDetector.detect(cleaned, blobs, systems, lineSpacing)

        // --- 6.17. 临时记号(accidental)检测 ----------------------------------
        // 临时记号是写在音符前方的升降号（♯升号/♭降号/♮还原号），改变该音符的音高。
        // 与调号(key signature)不同，临时记号是逐音符的——升号只影响紧随其后的那个
        // 音符（及同小节内同音名的后续音符）。此前 OMR 管线完全忽略临时记号，
        // 导致所有含升降号的音符都被映射为错误的白键音高。
        //
        // 检测到的临时记号在步骤 8 的时间轴循环中应用到 MIDI 音高上，并实现
        // 小节内延续（measure carryover）：同一小节内同音名的后续音符继承该临时记号，
        // 直到小节结束或被新的临时记号覆盖。
        val accidentalDetection = AccidentalDetector.detect(
            cleaned, blobs, located.map { it.nh }, located.map { it.systemIdx },
            systems.mapIndexed { idx, _ ->
                signatures.perSystem.getOrElse(idx) { null }?.signatureEndX ?: 0
            },
            lineSpacing
        )
        val accidentalsByNotehead = accidentalDetection.byNotehead

        // --- 6.18. 指法数字(fingering)检测 -----------------------------------
        // 指法数字是写在音符上方或下方的小数字（1–5），指示演奏者用哪根手指弹奏
        // 该音符（1=拇指, 5=小指）。在钢琴教学乐谱和练习曲中极为常见。
        // 通过小型孤立数字识别（复用 SignatureDetector.classifyDigit），仅接受 1–5。
        // 不修改音高或时值，仅在 ScoreNote.fingering 字段中记录，供 UI 标注。
        val fingerings = FingeringDetector.detect(
            cleaned, blobs, located.map { it.nh },
            located.map { it.systemIdx }, systems, lineSpacing
        )
        val fingeringByNotehead = fingerings.associate { it.noteheadIdx to it.finger }

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
        val quarterMs = 60_000L / effectiveTempo.coerceAtLeast(1)
        // 拍号决定一个小节的时长；未识别到时默认 4/4。
        val quartersPerMeasure = detectedTimeSig?.quartersPerMeasure ?: 4.0
        val measureMs = (quarterMs * quartersPerMeasure).toLong().coerceAtLeast(1L)

        // 时间轴项：音符（noteIdx >= 0）或休止符（noteIdx < 0，restDuration 非 null）。
        // systemIdx 确保多系统时按谱表从上到下、系统内从左到右排序。
        data class TimelineItem(val systemIdx: Int, val x: Int, val noteIdx: Int, val restDuration: NoteDuration?)
        val timeline = ArrayList<TimelineItem>()
        // 升号/降号/还原号等临时记号形状有时会被 NoteheadDetector 误判为符头。
        // 用 AccidentalDetector 报告的临时记号连通块中心 X 坐标过滤掉这些伪符头。
        val accidentalCenters = accidentalDetection.accidentalBlobCenters
        val accidentalTolerance = (lineSpacing / 3).coerceAtLeast(2)
        for (idx in located.indices) {
            // 跳过被误判为符头的临时记号连通块
            val isFalseNotehead = accidentalCenters.any { bx ->
                kotlin.math.abs(located[idx].nh.centerX - bx) <= accidentalTolerance
            }
            if (isFalseNotehead) continue
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
        // 延音线(tie)合并：notehead 索引 → notes 列表中的位置。
        // 被 tie 连接的第二个音符不产生新的 ScoreNote，而是将其时值累加到第一个音符。
        val noteIdxToNotesPos = HashMap<Int, Int>()
        var cursor = 0L
        // 临时记号小节内延续（measure carryover）：同一小节内同音名的后续音符继承
        // 显式临时记号。进入新小节时清空，被新临时记号覆盖时更新。
        var carryMeasure = -1
        val carryAccidentals = HashMap<Int, Accidental>()  // letter(0-6) → Accidental
        var i = 0
        while (i < timeline.size) {
            val item = timeline[i]
            if (item.noteIdx < 0) {
                // 休止符：推进时间轴但不产生音符。
                cursor += item.restDuration!!.toMillis(quarterMs)
                i++
                continue
            }
            // 装饰音(grace note)：标记 isGraceNote=true，赋予极短时值，不推进时间游标。
            // 装饰音不独立占拍——它"偷取"主音符的时间，因此主音符与装饰音共享同一个
            // startTime（装饰音在主音符之前极短地演奏）。score follower 可据此特殊处理。
            if (item.noteIdx in graceNoteheadIndices) {
                val gnIdx = item.noteIdx
                val ln = located[gnIdx]
                val system = systems[ln.systemIdx]
                val key = keysBySystem[ln.systemIdx]
                val letter = PitchMapper.letterForPosition(ln.nh.centerY, system, ln.staff)
                val explicitAcc = accidentalsByNotehead[gnIdx]
                val effOffset = PitchMapper.effectiveOffset(letter, key, explicitAcc, carryAccidentals[letter])
                // 注意：使用 3 参数 mapToMidi（不含调号），调号修正已由 effectiveOffset 统一处理，
                // 避免调号偏移被重复应用（mapToMidi(...,key) + effectiveOffset 都返回 key offset）。
                val midi = PitchMapper.mapToMidi(ln.nh.centerY, system, ln.staff) + effOffset
                // 更新临时记号小节内延续状态
                if (explicitAcc != null) carryAccidentals[letter] = explicitAcc
                // 应用八度记号移位（8va/8vb/15ma/15mb）
                val octaveShift = OctavaDetector.semitoneShiftForNote(
                    ottavaShifts, ln.systemIdx, ln.nh.centerX
                )
                val shiftedMidi = (midi + octaveShift).coerceIn(21, 108)
                if (shiftedMidi in 21..108) {
                    val measureIndex = if (totalBarlines > 0) {
                        measureBaseBySystem[ln.systemIdx] +
                            barlinesBySystem[ln.systemIdx]
                                .count { it.centerX < ln.nh.centerX && it.type != BarlineType.DASHED }
                    } else {
                        (cursor / measureMs).toInt()
                    }
                    notes += ScoreNote(
                        midiNumber = shiftedMidi,
                        noteName = MusicUtils.midiToNoteName(shiftedMidi),
                        startTime = cursor,
                        duration = (quarterMs / 8).coerceAtLeast(1L),
                        staff = ln.staff,
                        measureIndex = measureIndex,
                        isGraceNote = true,
                        articulation = articulations[gnIdx] ?: Articulation.NONE,
                        accidental = explicitAcc ?: carryAccidentals[letter] ?: Accidental.NONE,
                        octaveShift = octaveShift,
                        fingering = fingeringByNotehead[gnIdx] ?: 0
                    )
                }
                // 不推进 cursor——装饰音与主音符共享起始时间。
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
            // 连音组(tuplet)成员的时值按比例缩减（三连音 ×2/3、五连音 ×4/5 等）。
            val baseDuration = rhythms[leadIdx].effectiveMillis(quarterMs)
            val leadTuplet = tupletByNotehead[leadIdx]
            val duration = if (leadTuplet != null) {
                (baseDuration * leadTuplet.second).toLong().coerceAtLeast(1L)
            } else {
                baseDuration
            }
            val startTime = cursor
            var j = i
            while (j < timeline.size && timeline[j].noteIdx >= 0 &&
                located[timeline[j].noteIdx].systemIdx == leadSystemIdx &&
                located[timeline[j].noteIdx].nh.centerX - columnX <= xTolerance
            ) {
                val curNoteIdx = timeline[j].noteIdx
                // 延音线(tie)合并：被 tie 的第二个音符跳过创建，将其时值加到第一个音符。
                // 支持延音线链（A→B→C）：每个被 tie 的 notehead 都映射到链首音符的位置。
                if (curNoteIdx in tiedToNoteheads) {
                    val fromNhIdx = tieFromMap[curNoteIdx]!!
                    val fromPos = noteIdxToNotesPos[fromNhIdx]
                    if (fromPos != null && fromPos >= 0) {
                        notes[fromPos] = notes[fromPos].copy(
                            duration = notes[fromPos].duration + duration
                        )
                    }
                    // 将此 notehead 映射到同一音符位置（支持延音线链 A→B→C）。
                    noteIdxToNotesPos[curNoteIdx] = fromPos ?: -1
                    j++
                    continue
                }
                val ln = located[curNoteIdx]
                val system = systems[ln.systemIdx]
                val key = keysBySystem[ln.systemIdx]
                // 先计算 measureIndex（小节序号），用于临时记号小节内延续状态的清空。
                val measureIndex = if (totalBarlines > 0) {
                    measureBaseBySystem[ln.systemIdx] +
                        barlinesBySystem[ln.systemIdx]
                            .count { it.centerX < ln.nh.centerX && it.type != BarlineType.DASHED }
                } else {
                    (startTime / measureMs).toInt()
                }
                // 进入新小节时清空临时记号延续状态
                if (measureIndex != carryMeasure) {
                    carryAccidentals.clear()
                    carryMeasure = measureIndex
                }
                // 临时记号处理：显式临时记号 > 小节内延续 > 调号。
                // 注意：使用 3 参数 mapToMidi（不含调号），调号修正已由 effectiveOffset 统一处理，
                // 避免调号偏移被重复应用（mapToMidi(...,key) + effectiveOffset 都返回 key offset）。
                val letter = PitchMapper.letterForPosition(ln.nh.centerY, system, ln.staff)
                val explicitAcc = accidentalsByNotehead[curNoteIdx]
                val effOffset = PitchMapper.effectiveOffset(letter, key, explicitAcc, carryAccidentals[letter])
                val midi = PitchMapper.mapToMidi(ln.nh.centerY, system, ln.staff) + effOffset
                if (explicitAcc != null) carryAccidentals[letter] = explicitAcc
                // 应用八度记号移位（8va/8vb/15ma/15mb）
                val octaveShift = OctavaDetector.semitoneShiftForNote(
                    ottavaShifts, ln.systemIdx, ln.nh.centerX
                )
                val shiftedMidi = (midi + octaveShift).coerceIn(21, 108)
                if (shiftedMidi in 21..108) {
                    notes += ScoreNote(
                        midiNumber = shiftedMidi,
                        noteName = MusicUtils.midiToNoteName(shiftedMidi),
                        startTime = startTime,
                        duration = duration,
                        staff = ln.staff,
                        measureIndex = measureIndex,
                        articulation = articulations[curNoteIdx] ?: Articulation.NONE,
                        tuplet = tupletByNotehead[curNoteIdx]?.first ?: 0,
                        accidental = explicitAcc ?: carryAccidentals[letter] ?: Accidental.NONE,
                        octaveShift = octaveShift,
                        fingering = fingeringByNotehead[curNoteIdx] ?: 0
                    )
                    noteIdxToNotesPos[curNoteIdx] = notes.size - 1
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
        // 临时记号提示：告知用户检测到多少临时记号（升号/降号/还原号），已修正音高。
        if (accidentalsByNotehead.isNotEmpty()) {
            val sharpCount = accidentalsByNotehead.values.count { it == Accidental.SHARP || it == Accidental.DOUBLE_SHARP }
            val flatCount = accidentalsByNotehead.values.count { it == Accidental.FLAT || it == Accidental.DOUBLE_FLAT }
            val naturalCount = accidentalsByNotehead.values.count { it == Accidental.NATURAL }
            val parts = mutableListOf<String>()
            if (sharpCount > 0) parts += "$sharpCount 个升号"
            if (flatCount > 0) parts += "$flatCount 个降号"
            if (naturalCount > 0) parts += "$naturalCount 个还原号"
            warnings += "检测到 ${accidentalsByNotehead.size} 个临时记号（${parts.joinToString("、")}），已修正音高并应用小节内延续规则"
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
        // 反复次数提示：告知用户检测到的 "×N" 多次反复标注。
        if (repeatCounts.isNotEmpty()) {
            val summary = repeatCounts.joinToString("、") { "${it.count}遍" }
            warnings += "检测到 ${repeatCounts.size} 个反复次数标注（$summary），已标注多次反复结构"
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
        // 延音线提示：告知用户检测到的延音线，说明已合并时值。
        if (ties.isNotEmpty()) {
            warnings += "检测到 ${ties.size} 个延音线(tie)，已合并相同音高音符的时值"
        }
        // 连音提示：告知用户检测到的连音(slur)，说明已标注连奏段。
        if (slurs.isNotEmpty()) {
            val noteCount = slurs.sumOf { it.lastNoteIdx - it.firstNoteIdx + 1 }
            warnings += "检测到 ${slurs.size} 个连音(slur)，覆盖 $noteCount 个音符，已标注连奏(legato)段"
        }
        // 力度记号提示：告知用户检测到的力度变化标记。
        if (dynamicMarkings.isNotEmpty()) {
            val summary = dynamicMarkings.joinToString("、") { it.text }
            warnings += "检测到 ${dynamicMarkings.size} 个力度记号（$summary），已标注音量变化"
        }
        // 渐强/渐弱符号提示：告知用户检测到的 hairpin，说明已标注渐变方向。
        if (hairpins.isNotEmpty()) {
            val crescCount = hairpins.count { it.type == HairpinDetector.HairpinType.CRESCENDO }
            val decrescCount = hairpins.count { it.type == HairpinDetector.HairpinType.DECRESCENDO }
            val parts = buildList {
                if (crescCount > 0) add("$crescCount 个渐强(crescendo)")
                if (decrescCount > 0) add("$decrescCount 个渐弱(decrescendo)")
            }
            warnings += "检测到 ${hairpins.size} 个渐强/渐弱符号（${parts.joinToString("、")}），已标注音量渐变方向"
        }
        // 延音记号(fermata)提示：告知用户检测到的停留号，说明已标注。
        if (fermatas.isNotEmpty()) {
            val normalCount = fermatas.count { f -> !f.inverted }
            val invertedCount = fermatas.count { f -> f.inverted }
            val parts = ArrayList<String>()
            if (normalCount > 0) parts.add("$normalCount 个正立fermata")
            if (invertedCount > 0) parts.add("$invertedCount 个倒立fermata")
            warnings += "检测到 ${fermatas.size} 个延音记号/停留号(fermata)（${parts.joinToString("、")}），已标注停留"
        }
        // 装饰音提示：告知用户检测到的装饰音，区分短前倚音/长前倚音。
        if (graceNotes.isNotEmpty()) {
            val acciaccaturaCount = graceNotes.count { it.hasSlash }
            val appoggiaturaCount = graceNotes.count { !it.hasSlash }
            val parts = ArrayList<String>()
            if (acciaccaturaCount > 0) parts.add("$acciaccaturaCount 个短前倚音(acciaccatura)")
            if (appoggiaturaCount > 0) parts.add("$appoggiaturaCount 个长前倚音(appoggiatura)")
            warnings += "检测到 ${graceNotes.size} 个装饰音(grace note)（${parts.joinToString("、")}），已标记为装饰音不占拍"
        }
        // 颤音(trill)提示：告知用户检测到的颤音标记，区分有无波浪线延长。
        if (trills.isNotEmpty()) {
            val withLine = trills.count { it.hasWavyLine }
            val withoutLine = trills.count { !it.hasWavyLine }
            val parts = ArrayList<String>()
            if (withLine > 0) parts.add("$withLine 个带波浪线")
            if (withoutLine > 0) parts.add("$withoutLine 个无波浪线")
            warnings += "检测到 ${trills.size} 个颤音标记(trill)（${parts.joinToString("、")}），已标注颤音"
        }
        // 三连音/连音组(tuplet)提示：告知用户检测到的连音组，说明已按比例调整时值。
        if (tuplets.isNotEmpty()) {
            val tupletSummary = tuplets.groupBy { it.number }
                .entries.joinToString("、") { (num, list) ->
                    val name = when (num) {
                        2 -> "二连音(duplet)"
                        3 -> "三连音(triplet)"
                        4 -> "四连音(quadruplet)"
                        5 -> "五连音(quintuplet)"
                        6 -> "六连音(sextuplet)"
                        7 -> "七连音(septuplet)"
                        else -> "${num}连音"
                    }
                    "$name ×${list.size}"
                }
            val totalNotes = tuplets.sumOf { it.noteheadIndices.size }
            warnings += "检测到 ${tuplets.size} 个连音组（$tupletSummary，共 $totalNotes 个音符），已按连音比例调整时值"
        }
        // 指法提示：告知用户检测到的指法数字，说明已标注到音符上。
        if (fingerings.isNotEmpty()) {
            val summary = fingerings.groupBy { it.finger }
                .entries.joinToString("、") { (finger, list) ->
                    "指${finger} ×${list.size}"
                }
            warnings += "检测到 ${fingerings.size} 个指法标注（$summary），已标注到对应音符"
        }

        return Result(
            score = Score(
                id = "omr_${System.currentTimeMillis()}",
                title = title,
                composer = "OMR",
                notes = notes,
                tempo = effectiveTempo,
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
