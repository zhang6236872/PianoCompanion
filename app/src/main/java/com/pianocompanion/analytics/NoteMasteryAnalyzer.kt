package com.pianocompanion.analytics

import com.pianocompanion.data.model.MatchStatus
import com.pianocompanion.data.model.SessionRecord
import com.pianocompanion.util.MusicUtils

// ════════════════════════════════════════════════════════════════
//  数据模型
// ════════════════════════════════════════════════════════════════

/**
 * 音域（register）分类。
 *
 * 钢琴键盘按中央 C(C4=MIDI 60) 划分三个音区，不同音区的识读与手指控制难度不同：
 * - [LOW] 低音区：MIDI < 60（中央 C 以下），左手低音谱表常见
 * - [MID] 中音区：60 ≤ MIDI ≤ 72（C4–C5），钢琴中央区，初学最熟悉
 * - [HIGH] 高音区：MIDI > 72（C5 以上），右手高音谱表常见
 */
enum class NoteRegister(val label: String) {
    LOW("低音区"),
    MID("中音区"),
    HIGH("高音区");

    companion object {
        /** 根据 MIDI 音高判定所属音区。 */
        fun forMidi(midi: Int): NoteRegister = when {
            midi < 60 -> LOW
            midi <= 72 -> MID
            else -> HIGH
        }
    }
}

/**
 * 黑键（升号/降号）判定。钢琴上 5 个黑键音级为 C#/D#/F#/G#/A#（pitch class 1/3/6/8/10）。
 */
fun isBlackKeyPitchClass(pitchClass: Int): Boolean =
    pitchClass in setOf(1, 3, 6, 8, 10)

/**
 * 单个音级（pitch class，0=C … 11=B）的错误统计。
 *
 * @param pitchClass 音级序号 0–11
 * @param name 音级名（"C"/"C#"/…）
 * @param isAccidental 是否为黑键（升降号）音级
 * @param errorCount 该音级在所有会话中的累计错误数
 * @param errorTypeCounts 按错误类型细分计数
 * @param totalAnalyzedErrors 分析所基于的总错误数（用于计算 [errorRate]）
 */
data class PitchClassStat(
    val pitchClass: Int,
    val name: String,
    val isAccidental: Boolean,
    val errorCount: Int,
    val errorTypeCounts: Map<MatchStatus, Int>,
    val totalAnalyzedErrors: Int
) {
    /** 该音级的错误占比（errorCount / totalAnalyzedErrors），范围 [0,1]。 */
    val errorRate: Float
        get() = if (totalAnalyzedErrors > 0) errorCount.toFloat() / totalAnalyzedErrors else 0f

    /** 该音级最常见的错误类型。 */
    val dominantErrorType: MatchStatus
        get() = errorTypeCounts.maxByOrNull { it.value }?.key ?: MatchStatus.WRONG_PITCH
}

/**
 * 黑键 vs 白键的错误分布。
 *
 * @param whiteKeyCount 白键音级（C/D/E/F/G/A/B）累计错误数
 * @param blackKeyCount 黑键音级（C#/D#/F#/G#/A#）累计错误数
 * @param totalAnalyzedErrors 总错误数
 */
data class KeyTypeBreakdown(
    val whiteKeyCount: Int,
    val blackKeyCount: Int,
    val totalAnalyzedErrors: Int
) {
    /** 白键错误占比（whiteKeyCount / total），范围 [0,1]。 */
    val whiteKeyRate: Float
        get() = if (totalAnalyzedErrors > 0) whiteKeyCount.toFloat() / totalAnalyzedErrors else 0f

    /** 黑键错误占比（blackKeyCount / total），范围 [0,1]。 */
    val blackKeyRate: Float
        get() = if (totalAnalyzedErrors > 0) blackKeyCount.toFloat() / totalAnalyzedErrors else 0f

    /**
     * 黑键相对白键的错误倍率（已按音级数量归一化）。
     *
     * 黑键有 5 个音级、白键有 7 个音级，直接比较绝对错误数会因音级数量不同而失真。
     * 本指标计算「平均每个黑键音级的错误数 ÷ 平均每个白键音级的错误数」：
     * - 比值 > 1：黑键错误率**高于**白键（用户更不擅长黑键/升降号）
     * - 比值 = 1：黑白键错误率持平
     * - 比值 < 1：白键错误率更高
     *
     * 白键零错误时返回 [Float.POSITIVE_INFINITY]（黑键有错但白键完全没错）。
     */
    val blackToWhiteRatio: Float
        get() {
            val whitePerClass = whiteKeyCount.toFloat() / WHITE_KEY_CLASS_COUNT
            val blackPerClass = blackKeyCount.toFloat() / BLACK_KEY_CLASS_COUNT
            if (whitePerClass == 0f) {
                return if (blackKeyCount > 0) Float.POSITIVE_INFINITY else 0f
            }
            return blackPerClass / whitePerClass
        }

    companion object {
        /** 白键音级数（C/D/E/F/G/A/B = 7）。 */
        const val WHITE_KEY_CLASS_COUNT = 7
        /** 黑键音级数（C#/D#/F#/G#/A# = 5）。 */
        const val BLACK_KEY_CLASS_COUNT = 5
    }
}

/**
 * 各音域的错误分布。
 *
 * @param lowCount 低音区错误数
 * @param midCount 中音区错误数
 * @param highCount 高音区错误数
 * @param totalAnalyzedErrors 总错误数
 */
data class RegisterBreakdown(
    val lowCount: Int,
    val midCount: Int,
    val highCount: Int,
    val totalAnalyzedErrors: Int
) {
    /** 错误最多的音区（平局取更低音区）。无错误时为 [NoteRegister.MID]。 */
    val dominantRegister: NoteRegister
        get() = when {
            totalAnalyzedErrors == 0 -> NoteRegister.MID
            lowCount >= midCount && lowCount >= highCount -> NoteRegister.LOW
            highCount >= midCount -> NoteRegister.HIGH
            else -> NoteRegister.MID
        }

    /** 指定音区的错误占比。 */
    fun rateFor(register: NoteRegister): Float {
        if (totalAnalyzedErrors == 0) return 0f
        val count = when (register) {
            NoteRegister.LOW -> lowCount
            NoteRegister.MID -> midCount
            NoteRegister.HIGH -> highCount
        }
        return count.toFloat() / totalAnalyzedErrors
    }
}

/**
 * 单个具体音符（含八度，如 "F#4"）的错误统计。
 *
 * @param noteName 音符名（如 "F#4"）
 * @param midi MIDI 音高
 * @param errorCount 累计错误数
 */
data class NoteStat(
    val noteName: String,
    val midi: Int,
    val errorCount: Int
)

/**
 * 音高混淆（pitch confusion）——错误地弹成了另一个音。
 *
 * 来自 WRONG_PITCH 错误的 expectedNote→detectedNote 配对。反映用户最容易
 * 混淆的两个音（例如 C#↔D、F#↔G），是音准训练的高价值洞察。
 *
 * @param expectedNote 期望音符名
 * @param detectedNote 实际弹奏音符名
 * @param count 此混淆出现的次数
 * @param semitoneDistance 两音的半音距离（|midi 差|），1=相邻半音
 */
data class NoteConfusion(
    val expectedNote: String,
    val detectedNote: String,
    val count: Int,
    val semitoneDistance: Int
)

/**
 * 音符掌握度分析完整报告。
 *
 * @param totalSessions 分析所基于的总会话数
 * @param totalAnalyzedErrors 成功解析出音高的错误总数（不含无法解析的占位符错误）
 * @param totalRawErrors 所有错误原始总数（含无法解析的）
 * @param pitchClassStats 12 个音级的错误统计，按错误数降序排列
 * @param keyTypeStats 黑键/白键错误分布
 * @param registerStats 各音域错误分布
 * @param weakestNotes 最易出错的具体音符（含八度），按错误数降序，最多 [NoteMasteryOptions.maxWeakestNotes] 个
 * @param topConfusions 最常见的音高混淆，按次数降序，最多 [NoteMasteryOptions.maxConfusions] 个
 * @param summary 人类可读的中文摘要（供 UI / 报告展示）
 */
data class NoteMasteryReport(
    val totalSessions: Int,
    val totalAnalyzedErrors: Int,
    val totalRawErrors: Int,
    val pitchClassStats: List<PitchClassStat>,
    val keyTypeStats: KeyTypeBreakdown,
    val registerStats: RegisterBreakdown,
    val weakestNotes: List<NoteStat>,
    val topConfusions: List<NoteConfusion>,
    val summary: String
) {
    /** 是否有足够的错误数据支撑分析。 */
    val hasData: Boolean get() = totalAnalyzedErrors > 0

    /** 最易出错的音级（错误数最高的那个）。 */
    val weakestPitchClass: PitchClassStat?
        get() = pitchClassStats.firstOrNull { it.errorCount > 0 }
}

/**
 * 音符掌握度分析参数。
 *
 * @param maxWeakestNotes 最易出错音符列表的最大长度（默认 5）
 * @param maxConfusions 音高混淆列表的最大长度（默认 5）
 */
data class NoteMasteryOptions(
    val maxWeakestNotes: Int = 5,
    val maxConfusions: Int = 5
)

// ════════════════════════════════════════════════════════════════
//  分析引擎
// ════════════════════════════════════════════════════════════════

/**
 * 音符掌握度分析引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 聚合多次练习会话（[SessionRecord]）中的错误记录（[com.pianocompanion.data.model.ErrorPosition]），
 * 从**音高维度**（而非 WeakSpotAnalyzer 的小节维度）分析用户的掌握情况：
 *
 * 1. **音级错误分布**：12 个音级（C/C#/…/B）各自出错频率，找出最薄弱的音级
 * 2. **黑键 vs 白键**：钢琴初学者常在黑键（升降号）上出错。本引擎按音级数量归一化后
 *    计算 [KeyTypeBreakdown.blackToWhiteRatio]，直接回答「我是不是更不擅长黑键」
 * 3. **音域分布**：低/中/高音区的错误分布，反映换把位/高低音谱表的熟练度
 * 4. **具体音符排行**：精确到八度（如 "F#4"）的最易出错音符
 * 5. **音高混淆**：WRONG_PITCH 错误的 expected→detected 配对，揭示最易混淆的音对
 *
 * 与 [WeakSpotAnalyzer]（回答「**哪里**出错」——小节维度）互补，本引擎回答
 * 「**什么音**出错」——音高维度，两者结合给出立体诊断。
 *
 * 音高解析说明：对每个错误，根据错误类型选择「分析对象」：
 * - EXTRA_NOTE（多弹）→ 用 detectedNote（多弹的那个音）
 * - 其他（WRONG_PITCH/MISSING_NOTE/RHYTHM_ERROR）→ 用 expectedNote（应弹的音）
 * 无法解析的占位符（如 "—"、"(未弹)"）计入 totalRawErrors 但不计入 totalAnalyzedErrors。
 *
 * 典型用法：
 * ```kotlin
 * val sessions = statsRepository.getAllSessions()
 * val report = NoteMasteryAnalyzer.analyze(sessions)
 * if (report.hasData) {
 *     println(report.summary)
 *     report.weakestPitchClass // 最薄弱音级
 *     report.keyTypeStats.blackToWhiteRatio // 黑键相对白键的倍率
 * }
 * ```
 */
object NoteMasteryAnalyzer {

    /** 12 个音级名（C=0 … B=11），与 [MusicUtils] 一致。 */
    private val PITCH_CLASS_NAMES =
        arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * 分析给定会话列表，生成音符掌握度报告。
     *
     * 本引擎不区分会话属于哪首乐谱——音高掌握度是**跨乐谱的技能维度**（例如「总是弹错
     * 升 F」在任何乐谱中都可能发生）。调用方若需乐谱相关分析可先过滤会话。
     *
     * @param sessions 练习会话列表（通常为全部历史会话）
     * @param options 分析参数
     */
    fun analyze(
        sessions: List<SessionRecord>,
        options: NoteMasteryOptions = NoteMasteryOptions()
    ): NoteMasteryReport {
        val totalSessions = sessions.size

        // 边界：无会话
        if (totalSessions == 0) {
            return emptyReport(0, "暂无练习数据，无法分析音符掌握度。")
        }

        // --- 1. 遍历所有错误，解析音高 ---
        val errorTypeByClass = Array<MutableMap<MatchStatus, Int>>(12) { mutableMapOf() }
        val totalCountByClass = IntArray(12)
        val noteCountByName = mutableMapOf<String, IntArray>() // noteName -> [midi, count]
        var lowCount = 0
        var midCount = 0
        var highCount = 0
        var whiteCount = 0
        var blackCount = 0
        var analyzedErrors = 0
        var rawErrors = 0

        // 音高混淆聚合：key = "expectedMidi->detectedMidi"
        val confusionCounts = mutableMapOf<Pair<Int, Int>, Int>()

        for (session in sessions) {
            for (err in session.errorPositions) {
                rawErrors++
                val type = err.errorType

                // 确定分析对象：EXTRA_NOTE 用 detectedNote，其余用 expectedNote
                val subjectNote = if (type == MatchStatus.EXTRA_NOTE) {
                    err.detectedNote
                } else {
                    err.expectedNote
                }
                val subjectMidi = MusicUtils.noteNameToMidi(subjectNote)
                if (subjectMidi < 0) continue // 无法解析的占位符，跳过

                analyzedErrors++

                // 音级聚合
                val pc = subjectMidi % 12
                if (pc < 0) continue // 防御性：负数取模保护
                totalCountByClass[pc]++
                errorTypeByClass[pc].merge(type, 1) { a, b -> a + b }

                // 黑白键
                if (isBlackKeyPitchClass(pc)) blackCount++ else whiteCount++

                // 音域
                when (NoteRegister.forMidi(subjectMidi)) {
                    NoteRegister.LOW -> lowCount++
                    NoteRegister.MID -> midCount++
                    NoteRegister.HIGH -> highCount++
                }

                // 具体音符
                val noteName = MusicUtils.midiToNoteName(subjectMidi)
                val entry = noteCountByName.getOrPut(noteName) { intArrayOf(subjectMidi, 0) }
                entry[1]++

                // 音高混淆（仅 WRONG_PITCH 且两者都可解析）
                if (type == MatchStatus.WRONG_PITCH) {
                    val detectedMidi = MusicUtils.noteNameToMidi(err.detectedNote)
                    if (detectedMidi >= 0 && detectedMidi != subjectMidi) {
                        val key = subjectMidi to detectedMidi
                        confusionCounts[key] = (confusionCounts[key] ?: 0) + 1
                    }
                }
            }
        }

        // 边界：有会话但无任何可解析错误
        if (analyzedErrors == 0) {
            val msg = if (rawErrors == 0) {
                "未发现错误记录，音准表现优异！继续保持。"
            } else {
                "虽有 $rawErrors 条错误记录，但无法解析具体音高，跳过音高维度分析。"
            }
            return emptyReport(totalSessions, msg, rawErrors)
        }

        // --- 2. 构建音级统计 ---
        val pitchClassStats = (0..11).map { pc ->
            PitchClassStat(
                pitchClass = pc,
                name = PITCH_CLASS_NAMES[pc],
                isAccidental = isBlackKeyPitchClass(pc),
                errorCount = totalCountByClass[pc],
                errorTypeCounts = errorTypeByClass[pc].toMap(),
                totalAnalyzedErrors = analyzedErrors
            )
        }.sortedWith(
            compareByDescending<PitchClassStat> { it.errorCount }
                .thenBy { it.pitchClass }
        )

        // --- 3. 构建黑/白键 & 音域分布 ---
        val keyTypeStats = KeyTypeBreakdown(
            whiteKeyCount = whiteCount,
            blackKeyCount = blackCount,
            totalAnalyzedErrors = analyzedErrors
        )
        val registerStats = RegisterBreakdown(
            lowCount = lowCount,
            midCount = midCount,
            highCount = highCount,
            totalAnalyzedErrors = analyzedErrors
        )

        // --- 4. 构建具体音符排行 ---
        val weakestNotes = noteCountByName.entries
            .map { (name, arr) -> NoteStat(noteName = name, midi = arr[0], errorCount = arr[1]) }
            .sortedWith(
                compareByDescending<NoteStat> { it.errorCount }
                    .thenBy { it.midi }
            )
            .take(options.maxWeakestNotes)

        // --- 5. 构建音高混淆排行 ---
        val topConfusions = confusionCounts.entries
            .map { (pair, count) ->
                val (expected, detected) = pair
                NoteConfusion(
                    expectedNote = MusicUtils.midiToNoteName(expected),
                    detectedNote = MusicUtils.midiToNoteName(detected),
                    count = count,
                    semitoneDistance = kotlin.math.abs(expected - detected)
                )
            }
            .sortedWith(
                compareByDescending<NoteConfusion> { it.count }
                    .thenBy { it.semitoneDistance }
                    .thenBy { it.expectedNote }
            )
            .take(options.maxConfusions)

        // --- 6. 生成摘要 ---
        val summary = buildSummary(
            analyzedErrors, rawErrors, totalSessions,
            pitchClassStats, keyTypeStats, registerStats,
            weakestNotes, topConfusions
        )

        return NoteMasteryReport(
            totalSessions = totalSessions,
            totalAnalyzedErrors = analyzedErrors,
            totalRawErrors = rawErrors,
            pitchClassStats = pitchClassStats,
            keyTypeStats = keyTypeStats,
            registerStats = registerStats,
            weakestNotes = weakestNotes,
            topConfusions = topConfusions,
            summary = summary
        )
    }

    /**
     * 生成人类可读的中文摘要。
     *
     * 核心洞察优先级：
     * 1. 黑键相对白键的错误倍率（>1.5 才提及，否则不算显著）
     * 2. 最薄弱的音级
     * 3. 最易出错的具体音符
     * 4. 最常见的音高混淆（如有）
     */
    private fun buildSummary(
        analyzedErrors: Int,
        rawErrors: Int,
        totalSessions: Int,
        pitchClassStats: List<PitchClassStat>,
        keyTypeStats: KeyTypeBreakdown,
        registerStats: RegisterBreakdown,
        weakestNotes: List<NoteStat>,
        topConfusions: List<NoteConfusion>
    ): String {
        val sb = StringBuilder()
        sb.append("基于 $totalSessions 次练习、$analyzedErrors 条错误分析，")

        // 黑键 vs 白键洞察
        val ratio = keyTypeStats.blackToWhiteRatio
        if (ratio.isFinite() && ratio > BLACK_KEY_NOTABLE_THRESHOLD && keyTypeStats.blackKeyCount > 0) {
            sb.append("黑键（升降号）错误率约为白键的 ${formatRatio(ratio)} 倍，")
            sb.append("建议加强黑键音阶（如 ${weakestBlackKeyNames(pitchClassStats)}）练习。")
        } else {
            val weakest = pitchClassStats.firstOrNull { it.errorCount > 0 }
            if (weakest != null) {
                sb.append("最易出错的音级是 ${weakest.name}（${weakest.errorCount} 次，占" +
                        "${formatPercent(weakest.errorRate)}）。")
            } else {
                sb.append("未发现明显音准弱项。")
            }
        }

        // 具体音符
        val topNote = weakestNotes.firstOrNull()
        if (topNote != null && topNote.errorCount > 0) {
            sb.append("最频繁出错的单音为 ${topNote.noteName}（${topNote.errorCount} 次）。")
        }

        // 音高混淆
        val topConfusion = topConfusions.firstOrNull()
        if (topConfusion != null) {
            sb.append("最易混淆的音对：${topConfusion.expectedNote}→${topConfusion.detectedNote}" +
                    "（${topConfusion.count} 次）。")
        }

        return sb.toString().trimEnd('，', '。') + "。"
    }

    /** 找出错误数最高的黑键音级名（最多 3 个），用于摘要建议。 */
    private fun weakestBlackKeyNames(pitchClassStats: List<PitchClassStat>): String {
        return pitchClassStats
            .filter { it.isAccidental && it.errorCount > 0 }
            .take(3)
            .joinToString("/") { it.name }
            .ifEmpty { "黑键" }
    }

    /** 格式化倍率：≤10 保留 1 位小数，>10 取整。 */
    private fun formatRatio(ratio: Float): String =
        if (ratio <= 10f) String.format("%.1f", ratio) else ratio.toInt().toString()

    /** 格式化百分比（0–100 整数）。 */
    private fun formatPercent(rate: Float): String =
        (rate * 100).toInt().toString() + "%"

    /** 黑键倍率达到此阈值才在摘要中作为显著洞察提及。 */
    private val BLACK_KEY_NOTABLE_THRESHOLD = 1.5f

    private fun emptyReport(
        totalSessions: Int,
        summary: String,
        rawErrors: Int = 0
    ): NoteMasteryReport = NoteMasteryReport(
        totalSessions = totalSessions,
        totalAnalyzedErrors = 0,
        totalRawErrors = rawErrors,
        pitchClassStats = emptyList(),
        keyTypeStats = KeyTypeBreakdown(0, 0, 0),
        registerStats = RegisterBreakdown(0, 0, 0, 0),
        weakestNotes = emptyList(),
        topConfusions = emptyList(),
        summary = summary
    )
}
