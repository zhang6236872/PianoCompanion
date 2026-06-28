package com.pianocompanion.analytics

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 难度等级枚举。由 [DifficultyResult.totalScore] 映射而来。
 *
 * - [BEGINNER] 入门 (⭐)：单声部、慢速、窄音域、无装饰
 * - [EASY] 初级 (⭐⭐)：简单旋律、少量跳跃
 * - [INTERMEDIATE] 中级 (⭐⭐⭐)：较密音符、双声部或少量和弦
 * - [ADVANCED] 高级 (⭐⭐⭐⭐)：快速跑动、复杂节奏、多和弦
 * - [EXPERT] 专家 (⭐⭐⭐⭐⭐)：极高音密度、大跨音域、密集半音与装饰音
 */
enum class DifficultyLevel(val stars: String, val label: String, val maxScore: Int) {
    BEGINNER("⭐", "入门", 20),
    EASY("⭐⭐", "初级", 40),
    INTERMEDIATE("⭐⭐⭐", "中级", 60),
    ADVANCED("⭐⭐⭐⭐", "高级", 80),
    EXPERT("⭐⭐⭐⭐⭐", "专家", 100);

    companion object {
        /** 将 0-100 的难度分值映射为等级。 */
        fun fromScore(score: Int): DifficultyLevel {
            val s = score.coerceIn(0, 100)
            return when {
                s <= BEGINNER.maxScore -> BEGINNER
                s <= EASY.maxScore -> EASY
                s <= INTERMEDIATE.maxScore -> INTERMEDIATE
                s <= ADVANCED.maxScore -> ADVANCED
                else -> EXPERT
            }
        }
    }
}

/**
 * 单项难度因子的评估结果。
 *
 * @param key 因子标识符（用于 UI 引用与测试断言）
 * @param name 人类可读名称（中文）
 * @param rawValue 原始测量值（各因子物理含义不同，仅供展示）
 * @param score 该因子的 0-100 归一化分值
 * @param weight 该因子在加权总分中的权重 (0-1)
 * @param weightedScore [score] * [weight]，即该因子对总分的实际贡献
 */
data class DifficultyFactor(
    val key: String,
    val name: String,
    val rawValue: Double,
    val score: Int,
    val weight: Double
) {
    /** 该因子对总分的实际贡献。 */
    val weightedScore: Double get() = score * weight
}

/**
 * 完整的难度评估结果。
 *
 * @param totalScore 0-100 加权总分
 * @param level 由总分映射的难度等级
 * @param factors 各项难度因子明细（透明可解释）
 * @param summary 一句话摘要，适合在 UI 卡片上展示
 */
data class DifficultyResult(
    val totalScore: Int,
    val level: DifficultyLevel,
    val factors: List<DifficultyFactor>,
    val summary: String
) {
    /** 按对总分贡献度排序的因子（降序），用于「主要难点」展示。 */
    val dominantFactors: List<DifficultyFactor>
        get() = factors.sortedByDescending { it.weightedScore }

    /** 按 key 查找因子。 */
    fun factor(key: String): DifficultyFactor? = factors.firstOrNull { it.key == key }
}

/**
 * 乐谱难度评估引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 用多维音乐复杂度分析替代原先 `LibraryScreen` 中仅凭 `notes.size` 的粗糙启发式。
 * 从以下维度综合评估一首 [Score] 的演奏难度，输出 0-100 加权总分与等级：
 *
 * | 因子 | 权重 | 含义 |
 * |------|------|------|
 * | noteDensity | 0.20 | 音符密度（每秒音符数），越高越难 |
 * | rhythmicComplexity | 0.18 | 节奏复杂度（短音符比例 + 连音 + 时值多样性） |
 * | polyphony | 0.15 | 复音/和弦密度（同时发声的音符） |
 * | tempo | 0.12 | 速度（BPM），越快越难 |
 * | pitchRange | 0.10 | 音域跨度（最高音-最低音的半音数） |
 * | leaps | 0.10 | 旋律跳跃（平均音程 + 大跳比例） |
 * | chromaticism | 0.08 | 半音化程度（升降号比例） |
 * | ornamentation | 0.04 | 装饰音比例（倚音/颤音/琶音/滑音） |
 * | handIndependence | 0.02 | 双手独立性（使用多个谱表） |
 * | length | 0.01 | 曲目长度（耐力/记忆负担） |
 *
 * 设计要点：
 * - 各因子独立归一化到 0-100，加权求和得到总分，**透明可解释**（可在 UI 展示「主要难点」）
 * - 权重反映钢琴学习的实际难度构成：音密度与节奏是主导，装饰音/长度是次要
 * - 空乐谱（无音符）安全返回 0 分 / 入门级，不抛异常
 */
object DifficultyEstimator {

    /** 视为同时发声（和弦）的起始时间容差（毫秒）。 */
    private const val ONSET_TOLERANCE_MS = 15L

    /** 视为「短音符」（增加节奏难度）的时长阈值系数（相对四分音符）。 */
    private const val SHORT_NOTE_FRACTION = 0.6

    /** 视为「大跳」（增加手部移位难度）的最小音程（半音数）。 */
    private const val LARGE_LEAP_SEMITONES = 7

    /**
     * 评估一首乐谱的难度。
     *
     * @param score 待评估乐谱
     * @return 难度评估结果
     */
    fun estimate(score: Score): DifficultyResult {
        val notes = score.notes
        if (notes.isEmpty()) {
            return DifficultyResult(
                totalScore = 0,
                level = DifficultyLevel.BEGINNER,
                factors = emptyList(),
                summary = "空乐谱"
            )
        }

        val tempo = score.tempo.coerceAtLeast(1)
        val quarterMs = 60_000.0 / tempo
        val nonGrace = notes.filterNot { it.isGraceNote }

        val noteDensity = computeNoteDensity(notes)
        val rhythmic = computeRhythmicComplexity(nonGrace.ifEmpty { notes }, quarterMs)
        val polyphony = computePolyphony(notes)
        val tempoScore = computeTempo(tempo)
        val range = computePitchRange(notes)
        val leaps = computeLeaps(notes)
        val chromatic = computeChromaticism(notes)
        val ornament = computeOrnamentation(notes)
        val hand = computeHandIndependence(notes)
        val length = computeLength(notes)

        val factors = listOf(
            DifficultyFactor("noteDensity", "音符密度", noteDensity, norm(noteDensity, 1.0, 9.0), 0.20),
            DifficultyFactor("rhythmicComplexity", "节奏复杂度", rhythmic.combined, rhythmic.score, 0.18),
            DifficultyFactor("polyphony", "复音密度", polyphony.ratio, polyphony.score, 0.15),
            DifficultyFactor("tempo", "速度", tempo.toDouble(), tempoScore, 0.12),
            DifficultyFactor("pitchRange", "音域跨度", range.semitones.toDouble(), range.score, 0.10),
            DifficultyFactor("leaps", "旋律跳跃", leaps.avgInterval, leaps.score, 0.10),
            DifficultyFactor("chromaticism", "半音化", chromatic.fraction, chromatic.score, 0.08),
            DifficultyFactor("ornamentation", "装饰音", ornament.fraction, ornament.score, 0.04),
            DifficultyFactor("handIndependence", "双手独立", hand.staffCount.toDouble(), hand.score, 0.02),
            DifficultyFactor("length", "曲目长度", length.seconds, length.score, 0.01)
        )

        val rawTotal = factors.sumOf { it.weightedScore }
        val total = rawTotal.roundToInt().coerceIn(0, 100)
        val level = DifficultyLevel.fromScore(total)

        return DifficultyResult(
            totalScore = total,
            level = level,
            factors = factors,
            summary = buildSummary(level, total, factors)
        )
    }

    // ------------------------------------------------------------------
    // 各因子的原始测量
    // ------------------------------------------------------------------

    /** 每秒音符数（按时间跨度）。 */
    private fun computeNoteDensity(notes: List<ScoreNote>): Double {
        if (notes.size < 2) return notes.size.toDouble()
        val first = notes.minOf { it.startTime }
        val last = notes.maxOf { it.startTime }
        val spanSec = ((last - first).coerceAtLeast(1L)) / 1000.0
        return notes.size / spanSec
    }

    private data class RhythmicResult(val combined: Double, val score: Int)

    /** 节奏复杂度：短音符比例 + 连音 + 时值多样性。 */
    private fun computeRhythmicComplexity(notes: List<ScoreNote>, quarterMs: Double): RhythmicResult {
        if (notes.isEmpty()) return RhythmicResult(0.0, 0)
        val shortThreshold = quarterMs * SHORT_NOTE_FRACTION
        val shortCount = notes.count { it.duration in 1 until shortThreshold.toLong() }
        val shortFraction = shortCount.toDouble() / notes.size

        val tupletCount = notes.count { it.tuplet > 0 }
        val tupletBonus = (tupletCount.toDouble() / notes.size * 2.0).coerceAtMost(1.0) * 30.0

        // 时值多样性：将每个音符时长量化到 quarter 的比例桶，统计不同桶数。
        val buckets = notes.map { durationBucket(it.duration, quarterMs) }.toSet().size
        val variety = (buckets.coerceAtMost(8)) / 8.0 * 20.0

        val combined = shortFraction * 70.0 + tupletBonus + variety
        val score = combined.roundToInt().coerceIn(0, 100)
        return RhythmicResult(combined, score)
    }

    /** 将时长量化到相对四分音符的桶编号（用于统计时值多样性）。 */
    private fun durationBucket(durationMs: Long, quarterMs: Double): Int {
        if (durationMs <= 0 || quarterMs <= 0) return 0
        val ratio = durationMs / quarterMs
        return when {
            ratio < 0.3 -> 1   // 三十二分及更短
            ratio < 0.6 -> 2   // 十六分
            ratio < 1.1 -> 3   // 四分
            ratio < 2.1 -> 4   // 二分
            else -> 5          // 全音符及更长
        }
    }

    private data class PolyphonyResult(val ratio: Double, val score: Int)

    /** 复音/和弦密度：同时发声音符的比例 + 最大同时音数。 */
    private fun computePolyphony(notes: List<ScoreNote>): PolyphonyResult {
        if (notes.isEmpty()) return PolyphonyResult(0.0, 0)
        val byOnset = groupByOnset(notes)
        val chordNotes = byOnset.values.sumOf { group -> if (group.size >= 2) group.size else 0 }
        val ratio = chordNotes.toDouble() / notes.size
        val maxPolyphony = byOnset.values.maxOf { it.size }
        val score = (ratio * 100.0 * 0.6 + (maxPolyphony - 1) * 15.0).roundToInt().coerceIn(0, 100)
        return PolyphonyResult(ratio, score)
    }

    /** 速度：BPM 60→0，200→100（线性）。 */
    private fun computeTempo(tempo: Int): Int =
        ((tempo - 60.0) / 140.0 * 100.0).roundToInt().coerceIn(0, 100)

    private data class RangeResult(val semitones: Int, val score: Int)

    /** 音域跨度：半音数 0→0，48(四个八度)→100。 */
    private fun computePitchRange(notes: List<ScoreNote>): RangeResult {
        val pitches = notes.map { it.midiNumber }
        val semitones = (pitches.max() - pitches.min()).coerceAtLeast(0)
        val score = (semitones / 48.0 * 100.0).roundToInt().coerceIn(0, 100)
        return RangeResult(semitones, score)
    }

    private data class LeapResult(val avgInterval: Double, val score: Int)

    /** 旋律跳跃：平均音程 + 大跳比例。 */
    private fun computeLeaps(notes: List<ScoreNote>): LeapResult {
        // 按谱表分组、时间排序后计算连续音程。
        val ordered = notes.filterNot { it.isGraceNote }.sortedBy { it.startTime }
        if (ordered.size < 2) return LeapResult(0.0, 0)
        val intervals = ordered.zipWithNext { a, b -> abs(b.midiNumber - a.midiNumber) }
        if (intervals.isEmpty()) return LeapResult(0.0, 0)
        val avg = intervals.average()
        val largeLeapRatio = intervals.count { it >= LARGE_LEAP_SEMITONES }.toDouble() / intervals.size
        val avgScore = (avg / 8.0 * 50.0).coerceAtMost(50.0)
        val largeScore = (largeLeapRatio * 100.0 * 0.5).coerceAtMost(50.0)
        val score = (avgScore + largeScore).roundToInt().coerceIn(0, 100)
        return LeapResult(avg, score)
    }

    private data class ChromaticResult(val fraction: Double, val score: Int)

    /** 半音化程度：带升降号（非 NATURAL/NONE）的音符比例。 */
    private fun computeChromaticism(notes: List<ScoreNote>): ChromaticResult {
        if (notes.isEmpty()) return ChromaticResult(0.0, 0)
        val accCount = notes.count {
            it.accidental == com.pianocompanion.data.model.Accidental.SHARP ||
            it.accidental == com.pianocompanion.data.model.Accidental.FLAT ||
            it.accidental == com.pianocompanion.data.model.Accidental.DOUBLE_SHARP ||
            it.accidental == com.pianocompanion.data.model.Accidental.DOUBLE_FLAT
        }
        val fraction = accCount.toDouble() / notes.size
        // 半音化对难度影响显著，放大 2 倍
        val score = (fraction * 100.0 * 2.0).roundToInt().coerceIn(0, 100)
        return ChromaticResult(fraction, score)
    }

    private data class OrnamentResult(val fraction: Double, val score: Int)

    /** 装饰音比例：倚音/颤音(震音)/琶音/滑音。 */
    private fun computeOrnamentation(notes: List<ScoreNote>): OrnamentResult {
        if (notes.isEmpty()) return OrnamentResult(0.0, 0)
        val ornamentCount = notes.count {
            it.isGraceNote || it.tremoloSlashCount > 0 || it.isArpeggiated || it.isGlissando
        }
        val fraction = ornamentCount.toDouble() / notes.size
        val score = (fraction * 100.0 * 2.0).roundToInt().coerceIn(0, 100)
        return OrnamentResult(fraction, score)
    }

    private data class HandResult(val staffCount: Int, val score: Int)

    /** 双手独立性：使用的不同谱表数量 + 是否同时有高低音谱表。 */
    private fun computeHandIndependence(notes: List<ScoreNote>): HandResult {
        val staves = notes.map { it.staff }.filter { it != Staff.BOTH }.toSet()
        val staffCount = staves.size
        val hasTrebleBass = Staff.TREBLE in staves && Staff.BASS in staves
        val score = when {
            staffCount <= 1 -> 0
            hasTrebleBass -> 80
            else -> 50
        }
        return HandResult(staffCount, score)
    }

    private data class LengthResult(val seconds: Double, val score: Int)

    /** 曲目长度：耐力/记忆负担。0s→0，600s(10min)→100。 */
    private fun computeLength(notes: List<ScoreNote>): LengthResult {
        val lastEnd = notes.maxOfOrNull { it.endTime } ?: 0L
        val seconds = (lastEnd / 1000.0)
        val score = (seconds / 600.0 * 100.0).roundToInt().coerceIn(0, 100)
        return LengthResult(seconds, score)
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 把原始值从 [lo, hi] 线性归一化到 0-100（钳制）。 */
    private fun norm(value: Double, lo: Double, hi: Double): Int {
        val v = ((value - lo) / (hi - lo) * 100.0).roundToInt()
        return v.coerceIn(0, 100)
    }

    /** 按起始时间（容差 [ONSET_TOLERANCE_MS]）分组，返回「桶起始时间 → 该桶音符列表」。 */
    private fun groupByOnset(notes: List<ScoreNote>): Map<Long, List<ScoreNote>> {
        val sorted = notes.sortedBy { it.startTime }
        val groups = LinkedHashMap<Long, MutableList<ScoreNote>>()
        for (note in sorted) {
            val bucket = groups.keys.firstOrNull { abs(it - note.startTime) <= ONSET_TOLERANCE_MS }
            if (bucket != null) {
                groups[bucket]!!.add(note)
            } else {
                groups[note.startTime] = mutableListOf(note)
            }
        }
        return groups
    }

    /** 构建一句话摘要。 */
    private fun buildSummary(level: DifficultyLevel, total: Int, factors: List<DifficultyFactor>): String {
        val top = factors.maxByOrNull { it.weightedScore }
        val main = when (top?.key) {
            "noteDensity" -> "音符密集"
            "rhythmicComplexity" -> "节奏复杂"
            "polyphony" -> "多声部和弦"
            "tempo" -> "速度较快"
            "pitchRange" -> "音域宽广"
            "leaps" -> "大跳较多"
            "chromaticism" -> "半音丰富"
            "ornamentation" -> "装饰音多"
            "handIndependence" -> "双手协调"
            "length" -> "篇幅较长"
            else -> ""
        }
        val qualifier = if (main.isNotEmpty()) "（主要难点：$main）" else ""
        return "${level.label} · ${total}分$qualifier"
    }
}
