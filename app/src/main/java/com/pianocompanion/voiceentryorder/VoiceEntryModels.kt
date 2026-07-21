package com.pianocompanion.voiceentryorder

/**
 * 声部进入顺序辨识训练（Voice Entrance Order Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **声部进入顺序（Voice Entrance Order）**：在复调织体（赋格、卡农、对位作品）中，
 *   各声部并非同时开始，而是先后依次进入。辨识「哪个声部先进入、哪个后进入」是
 *   跟踪复调音乐结构（尤其是赋格主题在各声部间的依次呈现）的核心听觉能力。
 *
 * 训练流程：
 * 1. 播放一段多个声部先后进入的短织体（每个声部在其音区演奏短小动机）
 * 2. 用户聆听各声部的进入顺序——先听到哪个音区、再听到哪个音区
 * 3. 从选项中选出正确的进入顺序（如「低声部 → 高声部」）
 *
 * 与既有模块的区分：
 * - [com.pianocompanion.voicecounttraining] 问「同时有多少个声部」（数量）
 * - [com.pianocompanion.polyphonicmotion] 问「两条声部之间的相对运动关系」（运动）
 * - 本模块问「**声部进入的时间先后顺序**」——维度完全独立
 */

/**
 * 声部音区（辨识目标——用户通过音区高低判断是哪个声部进入）。
 *
 * 三个音区相隔约一个八度，使耳朵能清晰区分「高/中/低」三条线条。
 * 每个声部演奏相同的 D 大调三和弦琶音动机（D-F-A），仅因音区不同而可辨识。
 *
 * @param displayName 中文名
 * @param englishName 英文名
 * @param emoji 视觉符号
 * @param baseMidi 动机起始 MIDI 音高
 * @param motif 动机的 MIDI 音高序列（琶音 D-F-A）
 */
enum class VoiceRegister(
    val displayName: String,
    val englishName: String,
    val emoji: String,
    val baseMidi: Int,
    val motif: List<Int>
) {
    SOPRANO(
        displayName = "高声部",
        englishName = "Soprano",
        emoji = "🎵",
        baseMidi = 74, // D5
        motif = listOf(74, 77, 81) // D5 F5 A5
    ),
    ALTO(
        displayName = "中声部",
        englishName = "Alto",
        emoji = "🎶",
        baseMidi = 62, // D4
        motif = listOf(62, 65, 69) // D4 F4 A4
    ),
    BASS(
        displayName = "低声部",
        englishName = "Bass",
        emoji = "🎸",
        baseMidi = 50, // D3
        motif = listOf(50, 53, 57) // D3 F3 A3
    );

    /** 完整标签（如「高声部（Soprano）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    companion object {
        /** 初级难度使用的音区：高声部 + 低声部（最大音区分离，最易区分）。 */
        val BEGINNER_REGISTERS: List<VoiceRegister> = listOf(SOPRANO, BASS)

        /** 中级 / 高级难度使用的音区：高声部 + 中声部 + 低声部（三者全部）。 */
        val TRIPLE_REGISTERS: List<VoiceRegister> = listOf(SOPRANO, ALTO, BASS)
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param voiceCount 声部数量
 * @param choiceCount 选项数量
 * @param entryGapMs 相邻声部进入的时间间隔（毫秒）——间隔越小越难辨识
 * @param noteDurationMs 每个音符的持续毫秒
 * @param notesPerVoice 每个声部演奏的音符数（动机重复以覆盖整个织体）
 * @param registers 该难度使用的音区集合
 */
enum class EntryDifficulty(
    val displayName: String,
    val description: String,
    val voiceCount: Int,
    val choiceCount: Int,
    val entryGapMs: Int,
    val noteDurationMs: Int,
    val notesPerVoice: Int,
    val registers: List<VoiceRegister>
) {
    BEGINNER(
        "初级",
        "2 声部 · 大间隔 · 2 选项",
        voiceCount = 2,
        choiceCount = 2,
        entryGapMs = 700,
        noteDurationMs = 420,
        notesPerVoice = 4,
        registers = VoiceRegister.BEGINNER_REGISTERS
    ),
    INTERMEDIATE(
        "中级",
        "3 声部 · 中间隔 · 3 选项",
        voiceCount = 3,
        choiceCount = 3,
        entryGapMs = 520,
        noteDurationMs = 360,
        notesPerVoice = 4,
        registers = VoiceRegister.TRIPLE_REGISTERS
    ),
    ADVANCED(
        "高级",
        "3 声部 · 紧密间隔 · 4 选项",
        voiceCount = 3,
        choiceCount = 4,
        entryGapMs = 360,
        noteDurationMs = 300,
        notesPerVoice = 4,
        registers = VoiceRegister.TRIPLE_REGISTERS
    );

    companion object {
        val ALL: List<EntryDifficulty> = entries.toList()
    }
}

/**
 * 将进入顺序转为显示标签。
 *
 * 如 [低声部, 高声部] → "低声部 → 高声部"。
 */
fun orderLabel(order: List<VoiceRegister>): String =
    order.joinToString(" → ") { it.displayName }

/**
 * 声部进入顺序辨识训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param entryOrder 正确的声部进入顺序（按进入先后排列）
 * @param answerChoices 所有选项标签（含正确答案，已打乱）
 * @param correctAnswer 正确答案标签
 */
data class EntryOrderQuestion(
    val difficulty: EntryDifficulty,
    val seed: Long,
    val entryOrder: List<VoiceRegister>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 声部数量。 */
    val voiceCount: Int get() = entryOrder.size

    /** 是否包含中声部（3 声部题目）。 */
    val hasAlto: Boolean get() = VoiceRegister.ALTO in entryOrder

    init {
        require(entryOrder.isNotEmpty()) { "进入顺序不能为空" }
        require(entryOrder.size == difficulty.voiceCount) {
            "声部数 (${entryOrder.size}) 与难度 ${difficulty.displayName} 的声部数 (${difficulty.voiceCount}) 不一致"
        }
        require(entryOrder.toSet() == difficulty.registers.toSet()) {
            "进入顺序的音区集合与难度配置不符"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(orderLabel(entryOrder) == correctAnswer) {
            "正确答案标签与进入顺序不一致"
        }
        require(answerChoices.distinct().size == answerChoices.size) {
            "选项存在重复"
        }
    }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "进入顺序：${orderLabel(entryOrder)}"

    /** 带 emoji 的顺序标签（用于可视化）。 */
    val emojiOrderLabel: String
        get() = entryOrder.joinToString(" → ") { it.emoji }
}

/**
 * 一次答题结果。
 */
data class EntryAnswerRecord(
    val question: EntryOrderQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
