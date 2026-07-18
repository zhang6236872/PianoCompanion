package com.pianocompanion.voicecounttraining

/**
 * 声部数量听辨训练（Voice Count / Note Count Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **声部数量听辨**：用户听到一段同时发声的和弦（block chord），判断其中**有几个音（声部）**
 *   在同时鸣响。这是听觉训练中的一项基础能力——**和声密度感知（harmonic density perception）**：
 *   即"这一刻有几个音在响？"。
 *
 * 与已有训练模块的区别：
 * - [com.pianocompanion.chordtraining]（和弦品质）：判断和弦的**类型**（大三/小三/属七…）——答案是和弦种类。
 * - [com.pianocompanion.intervaltraining]（音程听辨）：判断**两个音之间**的具体音程——答案是一个具体音程。
 * - [com.pianocompanion.harmonicintervaltraining]（和声音程）：两个音的具体音程。
 * - **本模块**：忽略音高与和弦种类，只判断**同时鸣响的音有多少个**（1 / 2 / 3 / 4 / 5 / 6）。
 *   答案是数量。这是辨识和弦种类之前的前置感知能力。
 *
 * 训练流程：
 * 1. 播放一段由若干音同时鸣响构成的 block chord（所有音同时起、同时落）。
 * 2. 用户聆听后，判断其中有几个音。
 * 3. 从「1 个音 … N 个音」选项中选出正确答案。
 *
 * 难度通过两个维度叠加：
 * - **声部数范围**：初级 1-3（单音/音程/三和弦），中级 1-4（+七和弦），高级 1-6（+九和弦与密集音簇）
 * - **音符间距**：初级宽间距（每个音清晰可辨），中级中等间距，高级密集间距（音簇相互融合，最难辨识）
 */

/**
 * 音符间距（和声密度感知难度）。
 *
 * 间距越窄，相邻音越容易融合成一个音色，越难数清声部数。
 *
 * @param displayName 中文显示名
 * @param intervalPool 向上构造声部时，相邻音之间的半音间距候选池（构造时随机取其一）
 */
enum class NoteSpacing(
    val displayName: String,
    val intervalPool: List<Int>
) {
    /** 宽间距：相邻音至少纯四度（P4=5）以上，每个音清晰分离，最易辨识。 */
    WIDE(
        displayName = "宽间距",
        intervalPool = listOf(5, 7, 12)
    ),

    /** 中等间距：相邻音大二度到大三度，需专注分辨。 */
    MEDIUM(
        displayName = "中等间距",
        intervalPool = listOf(2, 3, 4)
    ),

    /** 密集间距：相邻音小二度到小三度，可形成音簇，相互融合，最难辨识。 */
    CLOSE(
        displayName = "密集间距",
        intervalPool = listOf(1, 2, 3)
    )
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param voiceCountRange 本难度可能出现的声部数范围（每题从中随机一个）
 * @param spacing 本难度的音符间距（密度感知难度）
 */
enum class VoiceCountDifficulty(
    val displayName: String,
    val description: String,
    val voiceCountRange: IntRange,
    val spacing: NoteSpacing
) {
    BEGINNER(
        displayName = "初级",
        description = "1-3 个音 · 宽间距 · 每个音清晰可辨",
        voiceCountRange = 1..3,
        spacing = NoteSpacing.WIDE
    ),

    INTERMEDIATE(
        displayName = "中级",
        description = "1-4 个音 · 中等间距 · 需专注分辨",
        voiceCountRange = 1..4,
        spacing = NoteSpacing.MEDIUM
    ),

    ADVANCED(
        displayName = "高级",
        description = "1-6 个音 · 密集间距 · 音簇融合最难数清",
        voiceCountRange = 1..6,
        spacing = NoteSpacing.CLOSE
    );

    companion object {
        val ALL: List<VoiceCountDifficulty> = entries.toList()
    }
}

/**
 * 声部数量听辨训练题目。
 *
 * @param difficulty 难度
 * @param voiceCount 正确答案：同时鸣响的音的数量
 * @param rootMidi 根音 MIDI 音高
 * @param voicing 实际播放的所有音的 MIDI 音高列表（长度 = voiceCount，已平移到合法音域）
 * @param spacing 音符间距
 * @param durationMs 和弦持续时长（毫秒）
 * @param answerChoices 所有选项文本（含正确答案，按"1 个音 … N 个音"顺序）
 * @param correctAnswer 正确答案文本
 */
data class VoiceCountQuestion(
    val difficulty: VoiceCountDifficulty,
    val voiceCount: Int,
    val rootMidi: Int,
    val voicing: List<Int>,
    val spacing: NoteSpacing,
    val durationMs: Long,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 选项文本（如「3 个音」）。 */
    val countLabel: String get() = countLabelText(voiceCount)

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${voiceCount}个音 · ${spacing.displayName} · 根音 MIDI $rootMidi"

    init {
        require(voiceCount in 1..8) { "声部数 $voiceCount 超出范围 [1, 8]" }
        require(voiceCount in difficulty.voiceCountRange) {
            "声部数 $voiceCount 不在 ${difficulty.displayName} 的范围 ${difficulty.voiceCountRange} 中"
        }
        require(voicing.size == voiceCount) {
            "voicing 长度 ${voicing.size} 不等于声部数 $voiceCount"
        }
        require(voicing.all { it in MIN_MIDI..MAX_MIDI }) {
            "voicing 含超出音域 [$MIN_MIDI, $MAX_MIDI] 的 MIDI 值: $voicing"
        }
        require(voicing.size == voicing.toSet().size) { "voicing 含重复音: $voicing" }
        require(durationMs > 0) { "持续时长必须为正数" }
        require(answerChoices.isNotEmpty()) { "选项不能为空" }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
    }

    companion object {
        const val MIN_MIDI = 36  // C2
        const val MAX_MIDI = 96  // C7

        /** 声部数 → 选项文本（带音乐术语提示）。 */
        fun countLabelText(count: Int): String = when (count) {
            1 -> "1 个音（单音）"
            2 -> "2 个音（音程）"
            3 -> "3 个音（三和弦）"
            4 -> "4 个音（七和弦）"
            5 -> "5 个音（九和弦）"
            6 -> "6 个音（密集音簇）"
            else -> "$count 个音"
        }
    }
}

/**
 * 一次答题结果。
 */
data class VoiceCountAnswerRecord(
    val question: VoiceCountQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
