package com.pianocompanion.mixedpractice

import com.pianocompanion.chordreading.ChordReadingClef
import com.pianocompanion.chordreading.ChordReadingDifficulty
import com.pianocompanion.chordreading.ChordReadingEngine
import com.pianocompanion.chordreading.ChordReadingQuestion
import com.pianocompanion.interval.IntervalClef
import com.pianocompanion.interval.IntervalDifficulty
import com.pianocompanion.interval.IntervalEngine
import com.pianocompanion.interval.IntervalQuestion
import com.pianocompanion.keysig.KeySigClef
import com.pianocompanion.keysig.KeySigDifficulty
import com.pianocompanion.keysig.KeySigEngine
import com.pianocompanion.keysig.KeySigQuestion
import com.pianocompanion.notation.NoteReadingClef
import com.pianocompanion.notation.NoteReadingDifficulty
import com.pianocompanion.notation.NoteReadingEngine
import com.pianocompanion.notation.NoteReadingQuestion
import com.pianocompanion.rhythmreading.RhythmReadingDifficulty
import com.pianocompanion.rhythmreading.RhythmReadingEngine
import com.pianocompanion.rhythmreading.RhythmReadingQuestion
import kotlin.random.Random

/**
 * 综合练习出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 5 个视唱练耳子训练模块的出题引擎组合在一起，随机/轮转交错出题。
 * 每次调用 [generate] 返回一道来自随机子模块的 [MixedQuestion]。
 *
 * 题型轮转策略：
 * - 维护一个打乱的题型队列（[typeQueue]），每次从队首取一个题型出题。
 * - 队列耗尽后重新打乱，确保连续多轮中 5 种题型均匀出现、不连续重复同一题型。
 * - 这比纯随机更好地保证题型的覆盖率（避免连续多题都是同一类型）。
 *
 * 难度映射：[MixedDifficulty] 按序号映射到各子模块的同名难度。
 * 谱号：所有音高类题目统一使用高音谱号（[NoteReadingClef.TREBLE] 等），
 * 保持综合练习的视觉一致性。
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class MixedPracticeEngine(
    private val root: Random = Random.Default
) {
    private val noteEngine = NoteReadingEngine(root)
    private val intervalEngine = IntervalEngine(root)
    private val chordEngine = ChordReadingEngine(root)
    private val keySigEngine = KeySigEngine(root)
    private val rhythmEngine = RhythmReadingEngine(root)

    /** 题型轮转队列。 */
    private val typeQueue = ArrayDeque<MixedQuestionType>()

    /** 上一次出的题型（用于队列重排后避免连续重复）。 */
    private var lastType: MixedQuestionType? = null

    /**
     * 生成一道综合练习题目。
     *
     * @param difficulty 综合练习难度
     * @return 包装后的 [MixedQuestion]
     */
    fun generate(difficulty: MixedDifficulty): MixedQuestion {
        val type = nextType()
        return generateByType(type, difficulty)
    }

    /**
     * 按指定题型生成一道题目（用于测试/定向练习）。
     */
    fun generateByType(type: MixedQuestionType, difficulty: MixedDifficulty): MixedQuestion {
        return when (type) {
            MixedQuestionType.NOTE_READING -> MixedQuestion.Note(
                noteEngine.generate(
                    NoteReadingClef.TREBLE,
                    difficulty.toNoteReading()
                )
            )
            MixedQuestionType.INTERVAL -> MixedQuestion.Interval(
                intervalEngine.generate(
                    IntervalClef.TREBLE,
                    difficulty.toInterval()
                )
            )
            MixedQuestionType.CHORD_READING -> MixedQuestion.Chord(
                chordEngine.generate(
                    ChordReadingClef.TREBLE,
                    difficulty.toChordReading()
                )
            )
            MixedQuestionType.KEY_SIGNATURE -> MixedQuestion.KeySig(
                keySigEngine.generate(
                    KeySigClef.TREBLE,
                    difficulty.toKeySig()
                )
            )
            MixedQuestionType.RHYTHM_READING -> MixedQuestion.Rhythm(
                rhythmEngine.generate(difficulty.toRhythmReading())
            )
        }
    }

    /**
     * 从轮转队列取下一个题型。队列空时重新打乱填充。
     */
    private fun nextType(): MixedQuestionType {
        if (typeQueue.isEmpty()) {
            refillQueue()
        }
        val type = typeQueue.removeFirst()
        lastType = type
        return type
    }

    /**
     * 重新打乱填充题型队列。
     *
     * 策略：打乱所有题型顺序；如果队列首个题型与 [lastType] 相同（避免连续重复），
     * 尝试交换到后面。
     */
    private fun refillQueue() {
        val shuffled = MixedQuestionType.ALL.shuffled(root).toMutableList()
        // 避免连续重复同一题型
        if (shuffled.isNotEmpty() && shuffled.first() == lastType && shuffled.size > 1) {
            val first = shuffled.removeAt(0)
            shuffled.add(1, first)
        }
        typeQueue.clear()
        typeQueue.addAll(shuffled)
    }

    // ── 难度映射 ──────────────────────────────────────────

    private fun MixedDifficulty.toNoteReading(): NoteReadingDifficulty =
        NoteReadingDifficulty.ALL[ordinal.coerceIn(0, 2)]

    private fun MixedDifficulty.toInterval(): IntervalDifficulty =
        IntervalDifficulty.ALL[ordinal.coerceIn(0, 2)]

    private fun MixedDifficulty.toChordReading(): ChordReadingDifficulty =
        ChordReadingDifficulty.ALL[ordinal.coerceIn(0, 2)]

    private fun MixedDifficulty.toKeySig(): KeySigDifficulty =
        KeySigDifficulty.ALL[ordinal.coerceIn(0, 2)]

    private fun MixedDifficulty.toRhythmReading(): RhythmReadingDifficulty =
        RhythmReadingDifficulty.ALL[ordinal.coerceIn(0, 2)]

    companion object {
        /**
         * 创建带固定种子的引擎实例（用于测试确定性）。
         */
        fun withSeed(seed: Long): MixedPracticeEngine = MixedPracticeEngine(Random(seed))
    }
}
