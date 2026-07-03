package com.pianocompanion.chordreading

/**
 * 和弦识别训练（Chord Identification Trainer）数据模型。
 *
 * 本文件包含所有与和弦识别训练相关的音乐理论和数据模型，均为纯 Kotlin（无 Android 依赖），
 * 完全可单元测试。
 *
 * 核心概念：
 * - **和弦（Chord）**：三个或更多音高按三度关系叠置。最基础的是三和弦（根音 + 三音 + 五音）。
 * - **三和弦类型**：由根音到三音、根音到五音的半音数决定：
 *   - 大三和弦（Major）：大三度(4) + 纯五度(7)
 *   - 小三和弦（Minor）：小三度(3) + 纯五度(7)
 *   - 减三和弦（Diminished）：小三度(3) + 减五度(6)
 *   - 增三和弦（Augmented）：大三度(4) + 增五度(8)
 * - **七和弦类型**：在三和弦上再加一个七音，由根音到七音的半音数进一步细分：
 *   - 大七和弦（Maj7）：大三和弦 + 大七度(11)
 *   - 属七和弦（Dom7）：大三和弦 + 小七度(10)
 *   - 小七和弦（Min7）：小三和弦 + 小七度(10)
 *   - 半减七和弦（ø7）：减三和弦 + 小七度(10)
 *
 * 自然音（白键）三和弦（C 大调顺阶三和弦）：
 * ```
 * 大三: C-E-G, F-A-C, G-B-D        小三: D-F-A, E-G-B, A-C-E
 * 减三: B-D-F                       增三: （自然音中不存在）
 * ```
 *
 * 自然音七和弦（C 大调顺阶七和弦）：
 * ```
 * 大七(Cmaj7): C-E-G-B   属七(G7): G-B-D-F     小七(Dm7/Em7/Am7): D-F-A-C 等
 * 半减七(Bø7): B-D-F-A
 * ```
 */

/**
 * 谱号。
 * - [TREBLE] 高音谱号（G clef）：底线 = E4
 * - [BASS] 低音谱号（F clef）：底线 = G2
 */
enum class ChordReadingClef(val displayName: String) {
    TREBLE("高音谱号"),
    BASS("低音谱号");

    companion object {
        val ALL = listOf(TREBLE, BASS)
    }
}

/**
 * 难度等级。
 * - [BEGINNER] 初级：大三/小三三和弦（根音避开 B），高音/低音谱号
 * - [INTERMEDIATE] 中级：大三/小三/减三三和弦（含 B 减三），高低音谱号
 * - [ADVANCED] 高级：七和弦（大七/属七/小七/半减七），高低音谱号
 */
enum class ChordReadingDifficulty(val displayName: String) {
    BEGINNER("初级"),
    INTERMEDIATE("中级"),
    ADVANCED("高级");

    companion object {
        val ALL = listOf(BEGINNER, INTERMEDIATE, ADVANCED)
    }
}

/**
 * 和弦类型（统一枚举，涵盖三和弦与七和弦）。
 *
 * @param displayName 中文显示名称
 * @param isSeventh 是否为七和弦
 * @param category 所属分类：三和弦 [ChordCategory.TRIAD] 或七和弦 [ChordCategory.SEVENTH]
 */
enum class ChordType(
    val displayName: String,
    val isSeventh: Boolean,
    val category: ChordCategory
) {
    MAJOR("大三和弦", false, ChordCategory.TRIAD),
    MINOR("小三和弦", false, ChordCategory.TRIAD),
    DIMINISHED("减三和弦", false, ChordCategory.TRIAD),
    AUGMENTED("增三和弦", false, ChordCategory.TRIAD),
    MAJOR_SEVENTH("大七和弦", true, ChordCategory.SEVENTH),
    DOMINANT_SEVENTH("属七和弦", true, ChordCategory.SEVENTH),
    MINOR_SEVENTH("小七和弦", true, ChordCategory.SEVENTH),
    HALF_DIMINISHED_SEVENTH("半减七和弦", true, ChordCategory.SEVENTH);

    companion object {
        /** 所有三和弦类型。 */
        val TRIADS = listOf(MAJOR, MINOR, DIMINISHED, AUGMENTED)

        /** 所有七和弦类型。 */
        val SEVENTHS = listOf(MAJOR_SEVENTH, DOMINANT_SEVENTH, MINOR_SEVENTH, HALF_DIMINISHED_SEVENTH)
    }
}

/** 和弦分类。 */
enum class ChordCategory {
    TRIAD,
    SEVENTH
}

/**
 * 和弦识别训练题目。
 *
 * @param clef 谱号
 * @param difficulty 难度
 * @param noteStaffSteps 各音的谱表位置列表（底线 = 0），从低到高排列
 * @param noteMidis 各音的 MIDI 音符号列表，从低到高排列
 * @param noteNames 各音的音名字母（C/D/E/F/G/A/B）列表
 * @param rootLetterName 根音音名字母
 * @param chordType 正确的和弦类型
 * @param isSeventh 是否为七和弦
 * @param answerChoices 所有选项列表（含正确答案，已打乱）
 * @param correctAnswer 正确答案文本
 */
data class ChordReadingQuestion(
    val clef: ChordReadingClef,
    val difficulty: ChordReadingDifficulty,
    val noteStaffSteps: List<Int>,
    val noteMidis: List<Int>,
    val noteNames: List<String>,
    val rootLetterName: String,
    val chordType: ChordType,
    val isSeventh: Boolean,
    val answerChoices: List<String>,
    val correctAnswer: String
)

/**
 * 一次答题结果。
 *
 * @param question 题目
 * @param userAnswer 用户选择的答案
 * @param isCorrect 是否答对
 */
data class ChordReadingAnswerRecord(
    val question: ChordReadingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
