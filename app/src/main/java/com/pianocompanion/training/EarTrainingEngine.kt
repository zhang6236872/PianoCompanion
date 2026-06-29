package com.pianocompanion.training

import kotlin.random.Random

/**
 * 听音训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ExerciseType]、[Difficulty] 和随机种子生成 [EarTrainingQuestion]。
 * 使用确定性随机数生成器（java.util.Random / kotlin.random.Random），相同种子产生相同题目，
 * 便于测试复现和回放。
 *
 * 设计要点：
 * - 根音在舒适的钢琴音域内随机选取（C4-B4 = MIDI 60-71），避免过高/过低难以辨别
 * - 选项数随难度递增，初级只给易区分的选项
 * - 干扰项从同类型的其他答案中随机选取，确保选项互不相同
 * - 播放模式（上行/柱式/下行）随机切换，增强泛化能力
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class EarTrainingEngine(
    private val root: Random = Random.Default
) {

    // 舒适的根音音域：C4(60) 到 B4(71)
    private val rootMinMidi = 60
    private val rootMaxMidi = 71

    /**
     * 生成一道题目。
     *
     * @param exerciseType 练习类型
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(
        exerciseType: ExerciseType,
        difficulty: Difficulty
    ): EarTrainingQuestion {
        val rootMidi = root.nextInt(rootMinMidi, rootMaxMidi + 1)
        val playMode = pickPlayMode()

        return when (exerciseType) {
            ExerciseType.INTERVAL -> generateInterval(rootMidi, difficulty, playMode)
            ExerciseType.CHORD -> generateChord(rootMidi, difficulty, playMode)
            ExerciseType.SCALE -> generateScale(rootMidi, difficulty, playMode)
        }
    }

    // ── 音程出题 ──────────────────────────────────────────

    private fun generateInterval(
        rootMidi: Int,
        difficulty: Difficulty,
        playMode: PlayMode
    ): EarTrainingQuestion {
        val pool = intervalPool(difficulty)
        val target = pool.random(root)
        val lower = rootMidi
        val upper = rootMidi + target.semitones

        // 音程播放时，BLOCK 模式仍有意义（同时弹两个音听和声效果）
        val midiNotes = when (playMode) {
            PlayMode.ASCENDING -> listOf(lower, upper)
            PlayMode.BLOCK -> listOf(lower, upper)
            PlayMode.DESCENDING -> listOf(upper, lower)
        }

        val choices = buildChoices(target.fullName, pool.map { it.fullName })

        return EarTrainingQuestion(
            exerciseType = ExerciseType.INTERVAL,
            playMode = playMode,
            midiNotes = midiNotes,
            correctAnswer = target.fullName,
            answerChoices = choices,
            displayInfo = "${target.abbreviation}（${target.fullName}）"
        )
    }

    // ── 和弦出题 ──────────────────────────────────────────

    private fun generateChord(
        rootMidi: Int,
        difficulty: Difficulty,
        playMode: PlayMode
    ): EarTrainingQuestion {
        val pool = chordPool(difficulty)
        val target = pool.random(root)
        val chordNotes = target.intervals.map { rootMidi + it }

        // 和弦：BLOCK 模式是默认的柱式和弦（最常见听辨方式）
        val midiNotes = when (playMode) {
            PlayMode.ASCENDING -> chordNotes.sorted()
            PlayMode.BLOCK -> chordNotes
            PlayMode.DESCENDING -> chordNotes.sortedDescending()
        }

        val choices = buildChoices(target.fullName, pool.map { it.fullName })

        return EarTrainingQuestion(
            exerciseType = ExerciseType.CHORD,
            playMode = playMode,
            midiNotes = midiNotes,
            correctAnswer = target.fullName,
            answerChoices = choices,
            displayInfo = target.fullName
        )
    }

    // ── 音阶出题 ──────────────────────────────────────────

    private fun generateScale(
        rootMidi: Int,
        difficulty: Difficulty,
        playMode: PlayMode
    ): EarTrainingQuestion {
        val pool = scalePool(difficulty)
        val target = pool.random(root)
        val scaleNotes = target.intervals.map { rootMidi + it }

        // 音阶通常为旋律播放（上行），BLOCK 模式较少使用但仍保留
        val midiNotes = when (playMode) {
            PlayMode.ASCENDING -> scaleNotes
            PlayMode.BLOCK -> scaleNotes
            PlayMode.DESCENDING -> scaleNotes.reversed()
        }

        val choices = buildChoices(target.fullName, pool.map { it.fullName })

        return EarTrainingQuestion(
            exerciseType = ExerciseType.SCALE,
            playMode = playMode,
            midiNotes = midiNotes,
            correctAnswer = target.fullName,
            answerChoices = choices,
            displayInfo = target.fullName
        )
    }

    // ── 难度池 ────────────────────────────────────────────

    /**
     * 各难度可用的音程池。
     * - 初级：最易区分的协和音程（三度、四度、五度）
     * - 中级：加入二度和八度
     * - 高级：全部 12 种音程
     */
    private fun intervalPool(difficulty: Difficulty): List<IntervalType> = when (difficulty) {
        Difficulty.BEGINNER -> listOf(
            IntervalType.MINOR_3RD, IntervalType.MAJOR_3RD,
            IntervalType.PERFECT_4TH, IntervalType.PERFECT_5TH
        )
        Difficulty.INTERMEDIATE -> listOf(
            IntervalType.MINOR_3RD, IntervalType.MAJOR_3RD,
            IntervalType.PERFECT_4TH, IntervalType.TRITONE,
            IntervalType.PERFECT_5TH, IntervalType.MINOR_2ND,
            IntervalType.MAJOR_2ND, IntervalType.PERFECT_OCTAVE
        )
        Difficulty.ADVANCED -> IntervalType.entries
    }

    /**
     * 各难度可用的和弦池。
     * - 初级：大小三和弦（最基本对比）
     * - 中级：加入减三、增三
     * - 高级：全部三和弦 + 七和弦
     */
    private fun chordPool(difficulty: Difficulty): List<ChordType> = when (difficulty) {
        Difficulty.BEGINNER -> listOf(ChordType.MAJOR, ChordType.MINOR)
        Difficulty.INTERMEDIATE -> ChordType.TRIADS
        Difficulty.ADVANCED -> ChordType.entries
    }

    /**
     * 各难度可用的音阶池。
     * - 初级：大调 vs 自然小调
     * - 中级：加入和声小调
     * - 高级：加入旋律小调、半音阶、全音阶
     */
    private fun scalePool(difficulty: Difficulty): List<ScaleType> = when (difficulty) {
        Difficulty.BEGINNER -> listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR)
        Difficulty.INTERMEDIATE -> listOf(
            ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR
        )
        Difficulty.ADVANCED -> listOf(
            ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR,
            ScaleType.MELODIC_MINOR, ScaleType.CHROMATIC, ScaleType.WHOLE_TONE
        )
    }

    // ── 工具方法 ──────────────────────────────────────────

    private fun pickPlayMode(): PlayMode {
        // 音阶倾向旋律模式，和弦倾向 BLOCK，音程混合
        return PlayMode.entries.random(root)
    }

    /**
     * 构建选项列表：正确答案 + 从池中随机选取的干扰项。
     * 保证所有选项唯一且打乱顺序。
     *
     * @param correct 正确答案
     * @param allLabels 该类型所有可能的标签（含正确答案）
     * @return 打乱后的选项列表（含正确答案）
     */
    private fun buildChoices(correct: String, allLabels: List<String>): List<String> {
        val distractors = allLabels.filter { it != correct }
        // 选项数：min(池大小, 4)，保证至少 2 个选项（1 正确 + 1 干扰）
        val numChoices = minOf(allLabels.size, 4).coerceAtLeast(minOf(allLabels.size, 2))
        val numDistractors = numChoices - 1
        val selectedDistractors = if (distractors.size <= numDistractors) {
            distractors
        } else {
            distractors.shuffled(root).take(numDistractors)
        }
        return (selectedDistractors + correct).shuffled(root)
    }

    companion object {
        /**
         * 创建带固定种子的引擎实例（用于测试确定性）。
         */
        fun withSeed(seed: Long): EarTrainingEngine = EarTrainingEngine(Random(seed))
    }
}
