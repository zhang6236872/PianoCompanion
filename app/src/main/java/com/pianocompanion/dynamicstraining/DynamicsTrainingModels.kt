package com.pianocompanion.dynamicstraining

/**
 * 力度辨识训练（Dynamics Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **力度辨识（Dynamics Recognition）**：用户听到一段旋律短语以特定的音量演奏，
 *   需要根据响度判断对应的意大利语力度术语（pp/p/mp/mf/f/ff）。
 * - 与速度辨识的区别：
 *   - **速度辨识（TempoTraining）**：关注节拍间距（BPM）
 *   - **力度辨识（DynamicsTraining）**：所有音符音高和节奏相同，唯一区分依据是**振幅（即响度）**，
 *     训练对不同力度级别的感知能力（dynamic feel）
 *
 * 本模块支持的 6 种力度（意大利语术语）：
 *   1. pp（pianissimo 极弱）振幅 0.15 — 非常轻柔、细腻
 *   2. p（piano 弱）振幅 0.30 — 轻柔、安静
 *   3. mp（mezzo-piano 中弱）振幅 0.45 — 适中偏轻
 *   4. mf（mezzo-forte 中强）振幅 0.60 — 适中偏强
 *   5. f（forte 强）振幅 0.80 — 有力、明亮
 *   6. ff（fortissimo 极强）振幅 0.95 — 非常响亮、激烈
 */

/**
 * 力度类型（意大利语力度术语）。
 *
 * @param symbol 标准记谱缩写（如 "pp"、"mf"）
 * @param italianName 意大利语全称（如 "pianissimo"、"mezzo-forte"）
 * @param displayName 中文名（如 "极弱"、"中强"）
 * @param amplitude 振幅（0.0-1.0），用于 PCM 合成的音量参数
 * @param description 听感描述（答题后的教学反馈）
 */
enum class DynamicLevel(
    val symbol: String,
    val italianName: String,
    val displayName: String,
    val amplitude: Float,
    val description: String
) {
    PIANISSIMO(
        symbol = "pp",
        italianName = "Pianissimo",
        displayName = "极弱",
        amplitude = 0.15f,
        description = "pp（Pianissimo 极弱）：非常轻柔细腻，如同耳语。摇篮曲的轻哼、远处传来的音乐。"
    ),
    PIANO(
        symbol = "p",
        italianName = "Piano",
        displayName = "弱",
        amplitude = 0.30f,
        description = "p（Piano 弱）：轻柔安静，像在说悄悄话。抒情乐段、柔和的对话感。"
    ),
    MEZZO_PIANO(
        symbol = "mp",
        italianName = "Mezzo-piano",
        displayName = "中弱",
        amplitude = 0.45f,
        description = "mp（Mezzo-piano 中弱）：适中偏轻，像是在温和地交谈。大多数叙事性乐段的自然音量。"
    ),
    MEZZO_FORTE(
        symbol = "mf",
        italianName = "Mezzo-forte",
        displayName = "中强",
        amplitude = 0.60f,
        description = "mf（Mezzo-forte 中强）：适中偏强，清晰而不过分。最常见的演奏力度，日常演奏的默认音量。"
    ),
    FORTE(
        symbol = "f",
        italianName = "Forte",
        displayName = "强",
        amplitude = 0.80f,
        description = "f（Forte 强）：有力明亮，充满自信。进行曲、雄壮的主题旋律。"
    ),
    FORTISSIMO(
        symbol = "ff",
        italianName = "Fortissimo",
        displayName = "极强",
        amplitude = 0.95f,
        description = "ff（Fortissimo 极强）：非常响亮激烈，如同怒吼。高潮段落的磅礴力量、胜利的号角。"
    );

    init {
        check(amplitude in 0.0f..1.0f) { "$italianName: amplitude=$amplitude 超出 [0,1] 范围" }
    }

    /** 完整标识（如 "pp  极弱"）。 */
    val fullLabel: String get() = "$symbol  $displayName"

    /** 摘要（如 "pp Pianissimo 极弱"）。 */
    val summary: String get() = "$symbol $italianName $displayName"

    companion object {
        val ALL: List<DynamicLevel> = entries.toList()

        /** 初级力度：极端差距（pp/mf/ff），凭直觉即可区分。 */
        val BEGINNER_DYNAMICS: List<DynamicLevel> = listOf(PIANISSIMO, MEZZO_FORTE, FORTISSIMO)

        /** 中级力度：中等差距（pp/p/mf/f），加入相邻力度。 */
        val INTERMEDIATE_DYNAMICS: List<DynamicLevel> = listOf(PIANISSIMO, PIANO, MEZZO_FORTE, FORTE)

        /**
         * 按难度返回可用力度集合。
         * - 初级：3 种极端力度（pp/mf/ff），振幅差异最大
         * - 中级：4 种中等差距力度（pp/p/mf/f），加入相邻力度
         * - 高级：全部 6 种，包含相邻力度（p vs mp、mf vs f），考验精细辨识
         */
        fun forDifficulty(difficulty: DynamicsTrainingDifficulty): List<DynamicLevel> = when (difficulty) {
            DynamicsTrainingDifficulty.BEGINNER -> BEGINNER_DYNAMICS
            DynamicsTrainingDifficulty.INTERMEDIATE -> INTERMEDIATE_DYNAMICS
            DynamicsTrainingDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和力度范围）
 * @param choiceCount 该难度的选项数量
 */
enum class DynamicsTrainingDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种极端力度（3 选项）· pp 极弱 / mf 中强 / ff 极强", 3),
    INTERMEDIATE("中级", "4 种中等力度（4 选项）· 加入 p 弱 / f 强", 4),
    ADVANCED("高级", "全部 6 种含相邻力度（6 选项）· 加入 mp 中弱 / mf 中强 细微区分", 6);

    companion object {
        val ALL: List<DynamicsTrainingDifficulty> = entries.toList()
    }
}

/**
 * 力度辨识训练题目。
 *
 * @param dynamic 正确的力度类型
 * @param difficulty 难度
 * @param noteCount 播放的音符数量
 * @param answerChoices 所有选项（力度术语+中文名，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class DynamicsTrainingQuestion(
    val dynamic: DynamicLevel,
    val difficulty: DynamicsTrainingDifficulty,
    val noteCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "pp Pianissimo 极弱"）。 */
    val fullName: String get() = dynamic.summary
}

/**
 * 一次答题结果。
 */
data class DynamicsTrainingAnswerRecord(
    val question: DynamicsTrainingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
