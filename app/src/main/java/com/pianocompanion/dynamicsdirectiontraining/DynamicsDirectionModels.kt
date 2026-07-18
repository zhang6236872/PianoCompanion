package com.pianocompanion.dynamicsdirectiontraining

/**
 * 力度变化方向辨识训练（Dynamics Direction / Crescendo Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **力度（Dynamics）**：音乐的响度（音量）。静态力度用力度记号表示（pp/p/mp/mf/f/ff，
 *   见 [com.pianocompanion.dynamicstraining]）。
 * - **力度变化方向（Dynamics Direction）**：力度随时间变化的「走势」，是音乐表情的核心
 *   维度之一。本训练专注于辨识这种「走势」，而非单一的静态响度级别。
 *
 * 5 种力度变化方向（辨识目标）：
 * - **渐强 Crescendo (<)**：音量逐渐变大 —— 推向高潮、积聚能量。
 * - **渐弱 Decrescendo (>)**：音量逐渐变小 —— 收束、远去、消散。
 * - **持平 Steady (n)**：音量保持不变 —— 无明显变化，作为参照。
 * - **渐强渐弱 Swell (⌣)**：先变大后变小 —— 形成山丘（messa di voce）。
 * - **渐弱渐强 Reverse Swell (⌢)**：先变小后变大 —— 形成山谷。
 *
 * 训练流程：
 * 1. 播放一段由若干音符组成的短句，音符的响度按某种「力度方向」走势变化
 * 2. 用户聆听后，判断这段音乐的力度是哪种走势
 * 3. 从选项中选出正确答案
 *
 * 音频设计要点：每个音符的增益（gain）按所选走势的轮廓（contour）缩放，
 * 旋律本身保持固定（避免音高变化成为干扰），使「力度变化」成为唯一显著特征。
 */

/**
 * 力度变化方向（辨识目标）。
 *
 * @param symbol 记号符号（如「<」「>」「⌣」）
 * @param displayName 中文名（如「渐强」）
 * @param englishName 意大利语/英文名（如「Crescendo」）
 * @param description 详细描述（答题后教学反馈）
 * @param hint 听辨提示
 */
enum class DynamicsDirection(
    val symbol: String,
    val displayName: String,
    val englishName: String,
    val description: String,
    val hint: String
) {
    CRESCENDO(
        symbol = "<",
        displayName = "渐强",
        englishName = "Crescendo",
        description = "渐强（Crescendo, <）：音量逐渐变大，像有人越走越近、或能量不断积聚。" +
            "常用于推向高潮、表达情绪高涨或制造期待感。",
        hint = "声音一点点变大，像有人越走越近，或情绪越来越激动"
    ),
    DECRESCENDO(
        symbol = ">",
        displayName = "渐弱",
        englishName = "Decrescendo",
        description = "渐弱（Decrescendo / Diminuendo, >）：音量逐渐变小，像渐渐远去、或缓缓消散。" +
            "常用于收束、结束、淡出或营造余韵。",
        hint = "声音一点点变小，像有人慢慢走远，或情绪渐渐平静"
    ),
    STEADY(
        symbol = "n",
        displayName = "持平",
        englishName = "Steady",
        description = "力度持平（n / 常态）：音量从头到尾保持一致，没有明显变化。" +
            "这是「没有渐强渐弱」的参照，用来与有变化的选项对比。",
        hint = "每个音的音量都差不多，从头到尾稳定不变"
    ),
    SWELL(
        symbol = "⌣",
        displayName = "渐强渐弱",
        englishName = "Swell",
        description = "渐强再渐弱（Swell / Messa di voce, ⌣）：音量先变大后变小，形成一个「山丘」。" +
            "这是美声唱法中著名的长音技巧，也是强调单个音/句的常用手法。",
        hint = "先变大再变小，像一座山丘，中间最高"
    ),
    REVERSE_SWELL(
        symbol = "⌢",
        displayName = "渐弱渐强",
        englishName = "Reverse Swell",
        description = "渐弱再渐强（Reverse Swell, ⌢）：音量先变小后变大，形成一个「山谷」。" +
            "较少见，制造从低谷重新升腾、压抑后释放的感觉。",
        hint = "先变小再变大，像一个山谷，中间最低"
    );

    /** 完整标签（如「渐强（Crescendo）」）。 */
    val fullLabel: String get() = "$displayName（$englishName）"

    /** 带符号的标签（如「< 渐强」）。 */
    val symbolLabel: String get() = "$symbol $displayName"

    companion object {
        val ALL: List<DynamicsDirection> = entries.toList()

        /** 初级难度候选：渐强 vs 渐弱（最根本的二元方向对比）。 */
        val BEGINNER_DIRECTIONS: List<DynamicsDirection> = listOf(CRESCENDO, DECRESCENDO)

        /** 中级难度候选：渐强/渐弱/持平（增加「无变化」参照）。 */
        val INTERMEDIATE_DIRECTIONS: List<DynamicsDirection> = listOf(CRESCENDO, DECRESCENDO, STEADY)

        /** 高级难度候选：全部 5 种方向（含双向山丘/山谷）。 */
        val ADVANCED_DIRECTIONS: List<DynamicsDirection> = ALL
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param directions 该难度可用的力度方向候选集
 * @param choiceCount 该难度的选项数量
 */
enum class DynamicsDirectionDifficulty(
    val displayName: String,
    val description: String,
    val directions: List<DynamicsDirection>,
    val choiceCount: Int
) {
    BEGINNER(
        "初级",
        "渐强 vs 渐弱 · 2 选项 · 最基础的力度方向",
        DynamicsDirection.BEGINNER_DIRECTIONS,
        2
    ),
    INTERMEDIATE(
        "中级",
        "+ 持平 · 3 选项 · 增加「无变化」参照",
        DynamicsDirection.INTERMEDIATE_DIRECTIONS,
        3
    ),
    ADVANCED(
        "高级",
        "+ 渐强渐弱 / 渐弱渐强 · 5 选项 · 双向山丘/山谷",
        DynamicsDirection.ADVANCED_DIRECTIONS,
        5
    );

    companion object {
        val ALL: List<DynamicsDirectionDifficulty> = entries.toList()
    }
}

/**
 * 力度变化方向辨识训练题目。
 *
 * @param direction 正确的力度方向
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param tonicMidi 旋律起始主音的 MIDI 音高（旋律围绕该音变化）
 * @param answerChoices 所有选项（力度方向完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案（完整标签）
 */
data class DynamicsDirectionQuestion(
    val direction: DynamicsDirection,
    val difficulty: DynamicsDirectionDifficulty,
    val seed: Long,
    val tonicMidi: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${direction.displayName}（${direction.englishName} ${direction.symbol}）"

    init {
        require(tonicMidi in MIN_TONIC_MIDI..MAX_TONIC_MIDI) {
            "主音 MIDI $tonicMidi 超出范围 [$MIN_TONIC_MIDI, $MAX_TONIC_MIDI]"
        }
        require(direction in difficulty.directions) {
            "力度方向 ${direction.displayName} 不在 ${difficulty.displayName} 的候选集中"
        }
        require(correctAnswer in answerChoices) {
            "正确答案不在选项中"
        }
    }
}

/**
 * 一次答题结果。
 */
data class DynamicsDirectionAnswerRecord(
    val question: DynamicsDirectionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 主音音域常量（C4=60 到 G4=67，白键起始音）。 */
const val MIN_TONIC_MIDI: Int = 60
const val MAX_TONIC_MIDI: Int = 67
