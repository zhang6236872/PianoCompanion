package com.pianocompanion.generator

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.KeySignature
import com.pianocompanion.util.MusicUtils
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToLong

// ──────────────────────────────────────────────────────────────────────────────
//  Difficulty
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 视奏练习难度等级。
 *
 * 每个等级控制三个维度：
 * - [tessituraLow] / [tessituraHigh] 旋律活动的音域范围（以相对主音的音阶级数表示）
 * - 可用的节奏型库（详见 [SightReadingGenerator] 内部 rhythm-pattern 表）
 * - 旋律进行中允许的最大跳进幅度（音阶级数）
 *
 * @param label 中文显示标签
 * @param tessituraLow 最低音阶级（可为负数，表示低于主音八度）
 * @param tessituraHigh 最高音阶级
 * @param maxLeap 允许的最大旋律跳进幅度（音阶级数）
 */
enum class SightReadingDifficulty(
    val label: String,
    val tessituraLow: Int,
    val tessituraHigh: Int,
    val maxLeap: Int
) {
    /** 初级：四分/二分/全音符，纯级进运动，一个八度内。 */
    BEGINNER("初级", 0, 7, 2),

    /** 入门：加入八分音符对，允许小三度跳进，约 1.3 个八度。 */
    ELEMENTARY("入门", -1, 8, 3),

    /** 中级：加入附点节奏与切分，允许四五度跳进，约 1.7 个八度。 */
    INTERMEDIATE("中级", -2, 9, 5),

    /** 高级：加入十六分音符与八度大跳，约 2 个八度。 */
    ADVANCED("高级", -3, 10, 7);

    /** 该难度旋律活动的半音跨度（近似值，用于 UI 展示）。 */
    val rangeSemitones: Int
        get() = tessituraHigh - tessituraLow
}

// ──────────────────────────────────────────────────────────────────────────────
//  Options
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 视奏练习生成器配置。
 *
 * @param keySignature 调号（决定使用的音阶与主音音高）
 * @param difficulty 难度等级
 * @param measures 小节数（建议为 4 的倍数以形成完整乐句）
 * @param timeSignature 拍号字符串（"4/4"、"3/4"、"2/4"）
 * @param tempo BPM
 * @param staff 谱号（TREBLE 高音 / BASS 低音）
 * @param seed 随机种子（相同种子 → 相同乐谱，便于复现与测试）
 */
data class SightReadingOptions(
    val keySignature: KeySignature = KeySignature.C_MAJOR_A_MINOR,
    val difficulty: SightReadingDifficulty = SightReadingDifficulty.BEGINNER,
    val measures: Int = 8,
    val timeSignature: String = "4/4",
    val tempo: Int = 100,
    val staff: Staff = Staff.TREBLE,
    val seed: Long = System.currentTimeMillis()
)

// ──────────────────────────────────────────────────────────────────────────────
//  Internal helpers
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 节奏单元格：占据 [beats] 拍，包含 [noteCount] 个音符起音。
 * noteCount=0 表示休止。
 */
internal data class RhythmCell(val beats: Double, val noteCount: Int)

/** 节奏型 = 一组填满一小节的节奏单元格序列。 */
internal typealias RhythmPattern = List<RhythmCell>

// ──────────────────────────────────────────────────────────────────────────────
//  Generator
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 视奏练习生成器 (Sight-Reading Generator)
 *
 * 根据调号、难度等级、拍号等参数，使用确定性的伪随机算法生成**音乐性连贯**
 * 的单声部旋律乐谱，供用户进行视奏练习。
 *
 * ## 设计原则
 *
 * 1. **音乐理论正确**：基于大调音阶（W-W-H-W-W-W-H）生成，所有音符都属于
 *    指定调号的音阶内，音高映射严格遵守半音间距规则。
 * 2. **音乐性**：
 *    - 以级进运动为主（音阶相邻音），偶尔出现三度/五度/八度跳进
 *    - 大跳后倾向于反向填充（音乐的"回归倾向"）
 *    - 不超过连续 3 次同向运动（避免失控音阶）
 *    - 每 4 小节形成乐句，乐句末尾倾向解决到主音或属音
 * 3. **难度分层**：
 *    - BEGINNER：四分/二分/全音符 + 休止，纯级进
 *    - ELEMENTARY：加入八分音符对
 *    - INTERMEDIATE：加入附点节奏、切分
 *    - ADVANCED：加入十六分音符、八度大跳
 * 4. **确定性**：相同 [SightReadingOptions.seed] 产生相同乐谱（基于 java.util.Random）
 * 5. **纯 Kotlin**：无 Android 依赖，完全可单元测试
 *
 * 生成的 [Score] 可直接用于现有的五线谱渲染、实时跟音、练习统计等全部管线。
 */
class SightReadingGenerator {

    /** 大调音阶各音阶级距主音的半音偏移：0,2,4,5,7,9,11 (W-W-H-W-W-W-H)。 */
    private val majorScaleSemitones = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    /**
     * 生成一份视奏练习乐谱。
     */
    fun generate(options: SightReadingOptions): Score {
        require(options.measures in 1..64) { "小节数应在 1..64 范围内，实际: ${options.measures}" }
        require(options.tempo in 20..400) { "BPM 应在 20..400 范围内，实际: ${options.tempo}" }

        val rng = Random(options.seed)
        val beatsPerMeasure = beatsPerMeasure(options.timeSignature)
        val rhythmPatterns = rhythmPatternsFor(options.difficulty, beatsPerMeasure)
        val tonicMidi = tonicMidiNote(options.keySignature, options.staff)
        val quarterMs = 60_000L / options.tempo

        val notes = mutableListOf<ScoreNote>()
        var currentTime = 0L
        var currentDegree = 0  // 从主音开始

        // 旋律方向追踪（避免失控音阶、跳进后回归）
        var lastDirection = 0
        var consecutiveSameDirection = 0

        // 第一个音符是否已生成（强制从主音开始）
        var isFirstNote = true

        for (measureIdx in 0 until options.measures) {
            val pattern = rhythmPatterns[rng.nextInt(rhythmPatterns.size)]
            val isPhraseEnd = (measureIdx + 1) % 4 == 0 || measureIdx == options.measures - 1

            for ((cellIdx, cell) in pattern.withIndex()) {
                val isLastCellInMeasure = cellIdx == pattern.lastIndex

                if (cell.noteCount == 0) {
                    // 休止符：推进时间但不产生音符
                    currentTime += (cell.beats * quarterMs).roundToLong()
                    continue
                }

                val cellDurationMs = (cell.beats * quarterMs).roundToLong()
                val perNoteMs = if (cell.noteCount > 1) {
                    cellDurationMs / cell.noteCount
                } else {
                    cellDurationMs
                }

                for (n in 0 until cell.noteCount) {
                    val isLastNoteInMeasure = isLastCellInMeasure && n == cell.noteCount - 1
                    val requireCadence = isLastNoteInMeasure && isPhraseEnd
                    val isFinalNote = isLastNoteInMeasure && measureIdx == options.measures - 1

                    if (isFirstNote) {
                        // 第一个音强制为主音（degree 0），建立调性中心
                        currentDegree = 0
                        isFirstNote = false
                    } else {
                        val (newDegree, newDirection) = nextScaleDegree(
                            current = currentDegree,
                            difficulty = options.difficulty,
                            rng = rng,
                            cadence = requireCadence,
                            isFinalNote = isFinalNote,
                            lastDirection = lastDirection,
                            consecutiveSameDir = consecutiveSameDirection
                        )
                        currentDegree = newDegree

                        if (newDirection != 0) {
                            if (newDirection == lastDirection) {
                                consecutiveSameDirection++
                            } else {
                                consecutiveSameDirection = 1
                            }
                            lastDirection = newDirection
                        }
                    }

                    val midi = degreeToMidi(currentDegree, tonicMidi)
                    notes.add(
                        ScoreNote(
                            midiNumber = midi,
                            noteName = MusicUtils.midiToNoteName(midi),
                            startTime = currentTime,
                            duration = perNoteMs,
                            staff = options.staff,
                            measureIndex = measureIdx
                        )
                    )
                    currentTime += perNoteMs
                }
            }
        }

        return Score(
            id = "sight_reading_${options.seed}_${options.keySignature}_${options.difficulty}",
            title = buildTitle(options),
            composer = "Piano Companion",
            notes = notes,
            tempo = options.tempo,
            timeSignature = options.timeSignature,
            source = ScoreSource.GENERATED
        )
    }

    // ── 音乐理论辅助 ──────────────────────────────────────────────────────

    /**
     * 根据 [keySignature] 计算主音的 MIDI 音高类（0-11）。
     *
     * 升号调：每加一个升号升纯五度（+7 半音），tonic = (sharpCount × 7) mod 12。
     * 降号调：每加一个降号降纯五度（-7 半音），tonic = (−flatCount × 7) mod 12。
     */
    internal fun tonicPitchClass(key: KeySignature): Int {
        val raw = if (key.sharpCount > 0) {
            key.sharpCount * 7
        } else {
            -(key.flatCount * 7)
        }
        return Math.floorMod(raw, 12)
    }

    /**
     * 根据调号和谱号，计算主音在合适八度的 MIDI 音高。
     *
     * TREBLE 以 C4=60 为参考八度，BASS 以 C3=48 为参考八度。
     */
    internal fun tonicMidiNote(key: KeySignature, staff: Staff): Int {
        val baseOctaveMidi = if (staff == Staff.BASS) 48 else 60
        // baseOctaveMidi 的音高类总是 C(0)，所以直接加上主音偏移
        return baseOctaveMidi + tonicPitchClass(key)
    }

    /**
     * 将音阶级数转换为 MIDI 音高。
     *
     * @param degree 音阶级数（可为负数或 >6，表示跨八度）
     * @param tonicMidi 主音 MIDI 音高
     */
    internal fun degreeToMidi(degree: Int, tonicMidi: Int): Int {
        val octave = Math.floorDiv(degree, 7)
        val degreeInOctave = Math.floorMod(degree, 7)
        val semitones = majorScaleSemitones[degreeInOctave] + octave * 12
        return tonicMidi + semitones
    }

    /**
     * 生成下一个音阶级数，应用音乐性约束。
     *
     * @param cadence 是否为乐句结尾（需要解决到主音或属音）
     * @param isFinalNote 是否为整首练习的最后一个音（更强倾向主音）
     * @return (新音阶级数, 运动方向) — 方向 +1=上行, -1=下行, 0=重复音
     */
    private fun nextScaleDegree(
        current: Int,
        difficulty: SightReadingDifficulty,
        rng: Random,
        cadence: Boolean,
        isFinalNote: Boolean,
        lastDirection: Int,
        consecutiveSameDir: Int
    ): Pair<Int, Int> {
        // ── 乐句结尾：解决到主音(0)或属音(4) ──
        if (cadence) {
            // 最终音更倾向主音（80%），乐句结尾倾向主音（65%）
            val tonicBias = if (isFinalNote) 8 else 6

            // 在音域范围内找出所有主音（degree % 7 == 0）和属音（degree % 7 == 4）实例
            val tonicCandidates = (difficulty.tessituraLow..difficulty.tessituraHigh)
                .filter { Math.floorMod(it, 7) == 0 }
            val dominantCandidates = (difficulty.tessituraLow..difficulty.tessituraHigh)
                .filter { Math.floorMod(it, 7) == 4 }

            // 找出离 current 最近的、在 maxLeap 范围内的主音和属音
            val nearestTonic = tonicCandidates.minByOrNull { abs(it - current) }
            val nearestDominant = dominantCandidates.minByOrNull { abs(it - current) }

            val tonicReachable = nearestTonic != null && abs(nearestTonic - current) <= difficulty.maxLeap
            val dominantReachable = nearestDominant != null && abs(nearestDominant - current) <= difficulty.maxLeap

            // 选择目标：优先可达的，偏好主音
            val target = when {
                tonicReachable && dominantReachable ->
                    if (rng.nextInt(10) < tonicBias) nearestTonic!! else nearestDominant!!
                tonicReachable -> nearestTonic!!
                dominantReachable -> nearestDominant!!
                else -> {
                    // 两者都不可达时，朝更近的目标移动 maxLeap 步（保证不越音域）
                    val nearer = if (nearestTonic != null && (nearestDominant == null ||
                        abs(nearestTonic - current) <= abs(nearestDominant - current))
                    ) nearestTonic else nearestDominant!!
                    val dir = if (nearer > current) 1 else -1
                    val candidate = (current + dir * difficulty.maxLeap)
                        .coerceIn(difficulty.tessituraLow, difficulty.tessituraHigh)
                    val actualDir = if (candidate > current) 1 else if (candidate < current) -1 else 0
                    return Pair(candidate, actualDir)
                }
            }

            val dir = if (target > current) 1 else if (target < current) -1 else 0
            return Pair(target, dir)
        }

        // ── 正常旋律进行 ──
        val roll = rng.nextInt(100)
        val allowReverse = consecutiveSameDir >= 3 && lastDirection != 0

        // 如果已连续 3 次同向，强制反向
        val forcedDirection = if (allowReverse) -lastDirection else 0
        val direction = if (forcedDirection != 0) {
            forcedDirection
        } else {
            if (rng.nextBoolean()) 1 else -1
        }

        val leap = when {
            roll < 50 -> 1                                    // 级进 (50%)
            roll < 72 -> 2                                    // 三度 (22%)
            roll < 84 -> if (difficulty.maxLeap >= 4) 3 + rng.nextInt(2) else 1  // 四/五度 (12%)
            roll < 94 -> 0                                    // 重复音 (10%)
            else -> if (difficulty.maxLeap >= 7) difficulty.maxLeap else 2       // 大跳 (6%)
        }

        val candidate = current + direction * leap

        // ── 音域约束：超出时反射 ──
        val clamped = when {
            candidate > difficulty.tessituraHigh -> {
                current - leap  // 反射为下行
            }
            candidate < difficulty.tessituraLow -> {
                current + leap  // 反射为上行
            }
            else -> candidate
        }

        val actualDirection = when {
            clamped > current -> 1
            clamped < current -> -1
            else -> 0
        }

        return Pair(clamped, actualDirection)
    }

    // ── 节奏型库 ──────────────────────────────────────────────────────────

    /**
     * 解析拍号字符串，返回每小节拍数。
     * 支持 "4/4"(4)、"3/4"(3)、"2/4"(2)、"6/8"(2, 以附点四分为一拍)。
     * 不支持的拍号默认返回 4。
     */
    internal fun beatsPerMeasure(timeSignature: String): Double {
        return when (timeSignature) {
            "4/4" -> 4.0
            "3/4" -> 3.0
            "2/4" -> 2.0
            "6/8" -> 2.0  // 复合拍：2 个附点四分音符拍
            else -> 4.0
        }
    }

    /**
     * 根据难度和拍号返回可用的节奏型列表。
     * 每种节奏型是一组填满一小节的 [RhythmCell]。
     */
    @Suppress("KotlinConstantConditions")
    internal fun rhythmPatternsFor(
        difficulty: SightReadingDifficulty,
        beatsPerMeasure: Double
    ): List<RhythmPattern> {
        val b = beatsPerMeasure

        // ── BEGINNER 基础节奏型 ──
        val beginner = mutableListOf<RhythmPattern>().apply {
            if (b == 4.0) {
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q Q Q Q
                add(listOf(RhythmCell(2.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // H Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(2.0, 1)))  // Q Q H
                add(listOf(RhythmCell(2.0, 1), RhythmCell(2.0, 1)))                      // H H
                add(listOf(RhythmCell(4.0, 1)))                                           // W
                add(listOf(RhythmCell(1.0, 0), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // R Q Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 0)))  // Q Q Q R
                add(listOf(RhythmCell(2.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 0)))  // H Q R
            } else if (b == 3.0) {
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q Q Q
                add(listOf(RhythmCell(2.0, 1), RhythmCell(1.0, 1)))                      // H Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(2.0, 1)))                      // Q H
                add(listOf(RhythmCell(3.0, 1)))                                           // dotted-half
                add(listOf(RhythmCell(1.0, 0), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // R Q Q
            } else if (b == 2.0) {
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q Q
                add(listOf(RhythmCell(2.0, 1)))                       // H
                add(listOf(RhythmCell(1.0, 0), RhythmCell(1.0, 1)))  // R Q
            }
        }

        if (difficulty == SightReadingDifficulty.BEGINNER) return beginner

        // ── ELEMENTARY: 加入八分音符对 ──
        val elementary = beginner.toMutableList().apply {
            if (b == 4.0) {
                add(listOf(RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // E E Q Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q E E Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1)))  // Q Q E E Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1)))  // Q Q Q E E
                add(listOf(RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // EEEEQQ
            } else if (b == 3.0) {
                add(listOf(RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // E E Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1)))  // Q E E Q
            }
        }

        if (difficulty == SightReadingDifficulty.ELEMENTARY) return elementary

        // ── INTERMEDIATE: 加入附点节奏与切分 ──
        val intermediate = elementary.toMutableList().apply {
            if (b == 4.0) {
                add(listOf(RhythmCell(1.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q. E Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1)))  // Q Q. E Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.5, 1), RhythmCell(0.5, 1)))  // Q Q Q. E
                add(listOf(RhythmCell(0.5, 1), RhythmCell(1.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // E Q.(syncopated) Q Q
                add(listOf(RhythmCell(1.5, 1), RhythmCell(0.5, 1), RhythmCell(1.5, 1), RhythmCell(0.5, 1)))  // Q. E Q. E
            } else if (b == 3.0) {
                add(listOf(RhythmCell(1.5, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1)))  // Q. E Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(1.5, 1), RhythmCell(0.5, 1)))  // Q Q. E
            }
        }

        if (difficulty == SightReadingDifficulty.INTERMEDIATE) return intermediate

        // ── ADVANCED: 加入十六分音符 ──
        val advanced = intermediate.toMutableList().apply {
            if (b == 4.0) {
                add(listOf(RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // SSSS Q Q Q
                add(listOf(RhythmCell(1.0, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q SSSS Q Q
                add(listOf(RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.5, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // SS E Q Q Q
                add(listOf(RhythmCell(0.5, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.5, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(0.5, 1), RhythmCell(0.5, 1)))  // E SS E SS E E
                add(listOf(RhythmCell(1.5, 1), RhythmCell(0.25, 1), RhythmCell(0.25, 1), RhythmCell(1.0, 1), RhythmCell(1.0, 1)))  // Q. SS Q Q
            }
        }

        return advanced
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private fun buildTitle(options: SightReadingOptions): String {
        val staffLabel = if (options.staff == Staff.BASS) "低音" else "高音"
        return "视奏练习 · ${options.keySignature.label} · ${options.difficulty.label} · $staffLabel"
    }

    /**
     * 统计生成乐谱中的休止小节数（含休止符的小节）。
     * 用于测试和诊断。
     */
    internal fun countRestMeasures(score: Score): Int {
        val measuresWithRests = mutableSetOf<Int>()
        // 一个小节如果没有音符，说明全休止
        val allMeasures = score.notes.map { it.measureIndex }.toSet()
        val totalMeasures = (allMeasures.maxOrNull() ?: -1) + 1
        return totalMeasures - allMeasures.size
    }
}
