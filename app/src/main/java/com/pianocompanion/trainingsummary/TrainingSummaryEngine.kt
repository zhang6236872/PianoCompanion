package com.pianocompanion.trainingsummary

/**
 * 训练数据汇总统计引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 各视唱练耳/听觉训练模块（识谱/音程/和弦/调号/节奏视读/听音/节奏训练）都有各自的
 * 进度持久化结构，但它们共享相同的统计语义（总会话数/总答题/总答对/准确率/最佳连击）。
 *
 * 本引擎定义一个统一的 [TrainerSummary] 扁平数据模型，由各 ViewModel 负责从各自进度
 * 结构填充后传入。引擎负责聚合、排名、技能等级评估和改进建议生成——所有逻辑均在纯
 * Kotlin 中实现，不依赖任何 Android API，保证 100% 可单元测试。
 */

/**
 * 训练模块类型枚举。
 *
 * 视觉训练（看谱判断）和听觉训练（听音判断）都在此统一枚举。
 */
enum class TrainerType(val displayName: String, val isVisual: Boolean) {
    NOTE_READING("识谱训练", isVisual = true),
    INTERVAL("音程识别", isVisual = true),
    CHORD_READING("和弦识别", isVisual = true),
    KEY_SIGNATURE("调号识别", isVisual = true),
    RHYTHM_READING("节奏视读", isVisual = true),
    EAR_TRAINING("听音训练", isVisual = false),
    RHYTHM("节奏训练", isVisual = false);

    /** emoji 图标，用于 UI 展示。 */
    val emoji: String
        get() = when (this) {
            NOTE_READING -> "🎹"
            INTERVAL -> "📐"
            CHORD_READING -> "🗂"
            KEY_SIGNATURE -> "升降"
            RHYTHM_READING -> "🥁"
            EAR_TRAINING -> "👂"
            RHYTHM -> "🎵"
        }
}

/**
 * 单个训练模块的摘要统计（由 ViewModel 从各模块的 Progress 结构填充）。
 *
 * @param type 训练类型
 * @param totalSessions 总会话数
 * @param totalAnswered 总答题/练习数
 * @param totalCorrect 总答对/通过数
 * @param bestStreak 历史最佳连击（所有难度/谱号组合中的最大值）
 * @param bestAccuracy 历史最佳单次会话准确率（0.0-1.0）
 */
data class TrainerSummary(
    val type: TrainerType,
    val totalSessions: Int = 0,
    val totalAnswered: Int = 0,
    val totalCorrect: Int = 0,
    val bestStreak: Int = 0,
    val bestAccuracy: Double = 0.0
) {
    /** 累计准确率（0.0-1.0），无答题记录时为 0.0。 */
    val accuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered

    /** 是否有练习记录（至少答过一题）。 */
    val hasActivity: Boolean get() = totalAnswered > 0
}

/**
 * 技能等级（综合准确率 + 训练广度评估）。
 *
 * @param minAccuracy 达到此等级所需的最低综合准确率（无活动时为入门）
 * @param minActiveCount 达到此等级所需的最少活跃训练模块数（鼓励全面发展）
 * @param colorHex 该等级的主题色（十六进制），供 UI 使用
 */
enum class SkillLevel(
    val displayName: String,
    val description: String,
    val minAccuracy: Double,
    val minActiveCount: Int,
    val emoji: String,
    val colorHex: String
) {
    BEGINNER(
        displayName = "入门",
        description = "刚开始探索音乐理论训练，继续保持！",
        minAccuracy = 0.0,
        minActiveCount = 0,
        emoji = "🌱",
        colorHex = "#9E9E9E"
    ),
    NOVICE(
        displayName = "初学",
        description = "基础正在建立，多练习薄弱模块以巩固记忆。",
        minAccuracy = 0.50,
        minActiveCount = 2,
        emoji = "📖",
        colorHex = "#8D6E63"
    ),
    INTERMEDIATE(
        displayName = "进阶",
        description = "已经有不错的音乐理论基础，向更高准确率迈进！",
        minAccuracy = 0.65,
        minActiveCount = 3,
        emoji = "🎓",
        colorHex = "#1976D2"
    ),
    PROFICIENT(
        displayName = "熟练",
        description = "熟练掌握多种音乐理论技能，继续精益求精。",
        minAccuracy = 0.80,
        minActiveCount = 4,
        emoji = "🏆",
        colorHex = "#2E7D32"
    ),
    MASTER(
        displayName = "精通",
        description = "音乐理论大师级水平！你已全面而深入地掌握了训练内容。",
        minAccuracy = 0.90,
        minActiveCount = 5,
        emoji = "👑",
        colorHex = "#F57F17"
    );

    companion object {
        /** 内部按等级从高到低排列的顺序（用于匹配）。 */
        private val RANKED = listOf(MASTER, PROFICIENT, INTERMEDIATE, NOVICE, BEGINNER)

        /**
         * 根据综合准确率和活跃训练数评估技能等级。
         *
         * 等级需同时满足 [minAccuracy] 和 [minActiveCount] 两个条件；若活跃数不足，
         * 即使准确率高也会被限制在较低等级（鼓励全面发展而非只刷一个模块）。
         */
        fun evaluate(overallAccuracy: Double, activeCount: Int): SkillLevel {
            for (level in RANKED) {
                if (overallAccuracy >= level.minAccuracy && activeCount >= level.minActiveCount) {
                    return level
                }
            }
            return BEGINNER
        }
    }
}

/**
 * 训练汇总报告（引擎的核心输出）。
 *
 * 包含全局聚合统计、各模块排名、以及智能改进建议。
 */
data class TrainingSummaryReport(
    /** 所有模块的摘要列表（按用户偏好排序，通常按活跃度降序）。 */
    val trainers: List<TrainerSummary>,
    /** 全局总会话数。 */
    val totalSessions: Int,
    /** 全局总答题数。 */
    val totalAnswered: Int,
    /** 全局总答对数。 */
    val totalCorrect: Int,
    /** 全局综合准确率（0.0-1.0）。 */
    val overallAccuracy: Double,
    /** 活跃训练模块数（totalAnswered > 0 的模块）。 */
    val activeTrainerCount: Int,
    /** 综合技能等级。 */
    val skillLevel: SkillLevel,
    /** 练习最多的模块（按 totalAnswered）。 */
    val mostPracticed: TrainerSummary?,
    /** 准确率最高的模块（要求 answered >= minSampleSize）。 */
    val accuracyLeader: TrainerSummary?,
    /** 最佳连击记录所属的模块。 */
    val streakLeader: TrainerSummary?,
    /** 最需改进的模块（准确率最低且有足够样本）。 */
    val weakestLink: TrainerSummary?,
    /** 智能改进建议（中文）。 */
    val suggestions: List<String>
) {
    /** 是否完全没有任何训练记录。 */
    val isEmpty: Boolean get() = totalAnswered == 0
}

/**
 * 训练汇总引擎。
 *
 * 纯函数式对象，所有方法无副作用、无状态，完全确定（相同输入→相同输出），
 * 适合单元测试。
 */
object TrainingSummaryEngine {

    /** 准确率排名要求的最小样本量（答对太少时准确率无统计意义）。 */
    const val MIN_SAMPLE_FOR_ACCURACY = 5

    /**
     * 汇总所有训练模块的统计，生成完整报告。
     *
     * @param trainers 各模块的摘要列表（可为空或包含全零条目）
     */
    fun summarize(trainers: List<TrainerSummary>): TrainingSummaryReport {
        val totalSessions = trainers.sumOf { it.totalSessions }
        val totalAnswered = trainers.sumOf { it.totalAnswered }
        val totalCorrect = trainers.sumOf { it.totalCorrect }
        val overallAccuracy = if (totalAnswered == 0) 0.0
            else totalCorrect.toDouble() / totalAnswered
        val active = trainers.filter { it.hasActivity }
        val activeCount = active.size

        val skillLevel = SkillLevel.evaluate(overallAccuracy, activeCount)

        val mostPracticed = active.maxByOrNull { it.totalAnswered }
        val accuracyLeader = active
            .filter { it.totalAnswered >= MIN_SAMPLE_FOR_ACCURACY }
            .maxByOrNull { it.accuracy }
        val streakLeader = active.maxByOrNull { it.bestStreak }
        val weakestLink = active
            .filter { it.totalAnswered >= MIN_SAMPLE_FOR_ACCURACY }
            .minByOrNull { it.accuracy }

        // 按活跃度降序排列（答题多的在前），其次按准确率
        val sorted = trainers.sortedWith(
            compareByDescending<TrainerSummary> { it.totalAnswered }
                .thenByDescending { it.accuracy }
        )

        val suggestions = generateSuggestions(
            activeCount = activeCount,
            overallAccuracy = overallAccuracy,
            weakest = weakestLink,
            accuracyLeader = accuracyLeader,
            skillLevel = skillLevel,
            totalTrainers = trainers.size
        )

        return TrainingSummaryReport(
            trainers = sorted,
            totalSessions = totalSessions,
            totalAnswered = totalAnswered,
            totalCorrect = totalCorrect,
            overallAccuracy = overallAccuracy,
            activeTrainerCount = activeCount,
            skillLevel = skillLevel,
            mostPracticed = mostPracticed,
            accuracyLeader = accuracyLeader,
            streakLeader = streakLeader,
            weakestLink = weakestLink,
            suggestions = suggestions
        )
    }

    /**
     * 按活跃度排名（练习最多的在前），返回前 [limit] 名。
     */
    fun rankByActivity(trainers: List<TrainerSummary>, limit: Int = Int.MAX_VALUE): List<TrainerSummary> {
        return trainers
            .filter { it.hasActivity }
            .sortedByDescending { it.totalAnswered }
            .take(limit)
    }

    /**
     * 生成个性化的中文改进建议。
     *
     * 建议按优先级排序（最值得改进的在前），至少返回 1 条。
     */
    private fun generateSuggestions(
        activeCount: Int,
        overallAccuracy: Double,
        weakest: TrainerSummary?,
        accuracyLeader: TrainerSummary?,
        skillLevel: SkillLevel,
        totalTrainers: Int
    ): List<String> {
        val suggestions = mutableListOf<String>()

        // 1. 最需改进的模块（准确率最低且有样本）
        if (weakest != null && weakest.accuracy < 0.70) {
            suggestions.add(
                "「${weakest.type.displayName}」准确率仅 ${pct(weakest.accuracy)}，" +
                    "建议优先加强该模块的练习。"
            )
        }

        // 2. 训练广度不足
        if (activeCount < 3 && totalTrainers > activeCount) {
            val untried = totalTrainers - activeCount
            suggestions.add(
                "你只尝试了 $activeCount 个训练模块，还有 $untried 个未使用。" +
                    "全面发展各项音乐理论技能能更快提升整体水平。"
            )
        }

        // 3. 整体准确率偏低
        if (activeCount >= 3 && overallAccuracy in 0.01..0.55) {
            suggestions.add(
                "整体准确率 ${pct(overallAccuracy)} 偏低，建议放慢答题速度、" +
                    "确保理解每个知识点后再提速。"
            )
        }

        // 4. 表现优异时的鼓励
        if (activeCount >= 4 && overallAccuracy >= 0.85) {
            suggestions.add(
                "太棒了！整体准确率 ${pct(overallAccuracy)}，已达「${skillLevel.displayName}」水平，" +
                    "可尝试更高难度挑战自己。"
            )
        }

        // 5. 准确率领先模块的肯定
        if (accuracyLeader != null && accuracyLeader.accuracy >= 0.90 && activeCount > 1) {
            suggestions.add(
                "「${accuracyLeader.type.displayName}」是你的强项（${pct(accuracyLeader.accuracy)}），" +
                    "保持优势的同时均衡发展其他模块。"
            )
        }

        // 兜底：如果没有任何特定建议，给出通用鼓励
        if (suggestions.isEmpty()) {
            suggestions.add("坚持每日练习，音乐理论能力会稳步提升！${
                if (activeCount == 0) "从下方任意一个训练模块开始吧。" else ""
            }")
        }

        return suggestions
    }

    /** 将 0.0-1.0 的比例格式化为百分比字符串（如 0.875 → "87.5%"）。 */
    fun pct(ratio: Double): String {
        val percent = (ratio * 1000).toInt() / 10.0
        // 去除多余的 .0
        return if (percent % 1.0 == 0.0) "${percent.toInt()}%"
        else "${"%.1f".format(percent)}%"
    }
}
