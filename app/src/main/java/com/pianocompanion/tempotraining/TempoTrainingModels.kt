package com.pianocompanion.tempotraining

/**
 * 速度辨识训练（Tempo Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **速度辨识（Tempo Recognition）**：用户听到一段匀速节拍序列（节拍器般的「哒哒哒」），
 *   需要根据节拍之间的时间间距判断出大致的 BPM（每分钟拍数），从而匹配正确的意大利语速度术语。
 * - 与拍号听辨的区别：
 *   - **拍号听辨（MeterRecognition）**：关注每小节有几个拍子（强弱分组），时间间距相同
 *   - **速度辨识（TempoTraining）**：所有 click 等高等响，唯一区分依据是**间距（即 BPM）**，
 *     训练内在的节拍速度感知（tempo feel）
 *
 * 本模块支持的 6 种速度（意大利语术语）：
 *   1. Largo（广板）50 BPM — 缓慢、庄严
 *   2. Adagio（柔板）70 BPM — 从容、舒展
 *   3. Andante（行板）90 BPM — 步速、行走
 *   4. Moderato（中板）120 BPM — 中速、适中
 *   5. Allegro（快板）140 BPM — 快速、活跃
 *   6. Presto（急板）180 BPM — 极快、急促
 */

/**
 * 速度类型（意大利语速度术语）。
 *
 * @param italianName 意大利语原文（如 "Largo"）
 * @param displayName 中文名（如 "广板"）
 * @param bpm 代表性 BPM（每分钟拍数）
 * @param description 听感描述（答题后的教学反馈）
 */
enum class TempoCategory(
    val italianName: String,
    val displayName: String,
    val bpm: Int,
    val description: String
) {
    LARGO(
        italianName = "Largo",
        displayName = "广板",
        bpm = 50,
        description = "Largo（广板）约 50 BPM：缓慢而庄严，每个拍子之间留有很大的间隙。葬礼进行曲、宏伟颂歌的典型速度。"
    ),
    ADAGIO(
        italianName = "Adagio",
        displayName = "柔板",
        bpm = 70,
        description = "Adagio（柔板）约 70 BPM：从容而舒展，比广板稍快但仍保持悠然。抒情慢乐章的常见速度。"
    ),
    ANDANTE(
        italianName = "Andante",
        displayName = "行板",
        bpm = 90,
        description = "Andante（行板）约 90 BPM：步速，如同人自然行走的节奏。轻柔但不拖沓，介于慢与快之间。"
    ),
    MODERATO(
        italianName = "Moderato",
        displayName = "中板",
        bpm = 120,
        description = "Moderato（中板）约 120 BPM：中速、不快不慢。最常见的练习速度，一切从这里开始加速或放慢。"
    ),
    ALLEGRO(
        italianName = "Allegro",
        displayName = "快板",
        bpm = 140,
        description = "Allegro（快板）约 140 BPM：快速而活跃，充满生命力。绝大多数古典乐第一乐章和流行乐的典型速度。"
    ),
    PRESTO(
        italianName = "Presto",
        displayName = "急板",
        bpm = 180,
        description = "Presto（急板）约 180 BPM：极快而急促，几乎来不及数拍子。炫技乐曲终曲、激烈战斗音乐的标志速度。"
    );

    /** 相邻两拍之间的时间间隔（毫秒）。 */
    val intervalMs: Double get() = 60_000.0 / bpm

    /** 完整标识（如 "Largo  广板"）。 */
    val fullLabel: String get() = "$italianName  $displayName"

    init {
        check(bpm in 1..300) { "$italianName: bpm=$bpm 超出合理范围" }
        check(bpm > 0) { "$italianName: bpm 必须为正数" }
    }

    companion object {
        val ALL: List<TempoCategory> = entries.toList()

        /** 初级速度：极端差距（广板/中板/急板），凭直觉即可区分。 */
        val BEGINNER_TEMPOS: List<TempoCategory> = listOf(LARGO, MODERATO, PRESTO)

        /** 中级速度：中等差距（广板/行板/中板/急板）。 */
        val INTERMEDIATE_TEMPOS: List<TempoCategory> = listOf(LARGO, ANDANTE, MODERATO, PRESTO)

        /**
         * 按难度返回可用速度集合。
         * - 初级：3 种极端速度（50/120/180 BPM）
         * - 中级：4 种中等差距速度（50/90/120/180 BPM）
         * - 高级：全部 6 种，包含相邻速度（70 vs 90、120 vs 140），考验精细辨识
         */
        fun forDifficulty(difficulty: TempoTrainingDifficulty): List<TempoCategory> = when (difficulty) {
            TempoTrainingDifficulty.BEGINNER -> BEGINNER_TEMPOS
            TempoTrainingDifficulty.INTERMEDIATE -> INTERMEDIATE_TEMPOS
            TempoTrainingDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量和速度范围）
 * @param choiceCount 该难度的选项数量
 */
enum class TempoTrainingDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int
) {
    BEGINNER("初级", "3 种极端速度（3 选项）· 广板 50 / 中板 120 / 急板 180 BPM", 3),
    INTERMEDIATE("中级", "4 种中等速度（4 选项）· 加入行板 90 BPM", 4),
    ADVANCED("高级", "全部 6 种含相邻速度（6 选项）· 加入柔板 70 / 快板 140 BPM", 6);

    companion object {
        val ALL: List<TempoTrainingDifficulty> = entries.toList()
    }
}

/**
 * 速度辨识训练题目。
 *
 * @param tempo 正确的速度类型
 * @param difficulty 难度
 * @param clickCount 播放的 click 总次数
 * @param answerChoices 所有选项（速度术语+中文，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class TempoTrainingQuestion(
    val tempo: TempoCategory,
    val difficulty: TempoTrainingDifficulty,
    val clickCount: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 完整描述（如 "Largo 广板 · 50 BPM"）。 */
    val fullName: String
        get() = "${tempo.italianName} ${tempo.displayName} · ${tempo.bpm} BPM"

    /** 拍间距（毫秒）。 */
    val intervalMs: Double get() = tempo.intervalMs
}

/**
 * 一次答题结果。
 */
data class TempoTrainingAnswerRecord(
    val question: TempoTrainingQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
