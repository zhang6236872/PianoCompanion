package com.pianocompanion.analytics

// ──────────────────────────────────────────────────────────────────────────
//  Goal Editor — 目标编辑逻辑（纯 Kotlin，无 Android 依赖，完全可单元测试）
// ──────────────────────────────────────────────────────────────────────────

/**
 * 目标编辑验证结果。
 */
sealed interface GoalValidation {
    /** 目标值合法。 */
    object Valid : GoalValidation

    /**
     * 目标值非法。
     * @param reason 中文原因说明（用于 UI 提示）
     */
    data class Invalid(val reason: String) : GoalValidation
}

/**
 * 练习目标编辑器（纯 Kotlin，无 Android 依赖）。
 *
 * 核心职责：
 * 1. **序列化/反序列化** [serializeGoals] / [deserializeGoals]：在
 *    `List<GoalDefinition>` 与 SharedPreferences 字符串格式
 *    `"PERIOD_METRIC:target,PERIOD_METRIC:target,..."` 之间转换。
 *    —— 将原先内联在 `StatsViewModel.loadGoals` 中的脆弱字符串解析逻辑提取为
 *    可单元测试的独立函数，并增加往返（round-trip）一致性保证。
 * 2. **校验** [validateTarget] / [isValidGoal]：根据指标类型校验目标值范围
 *    （如准确率必须在 0~1 之间、其他指标必须为正）。
 * 3. **增删改** [addOrUpdateGoal] / [removeGoal] / [toggleGoal]：对目标列表的
 *    不可变操作（返回新列表），以目标唯一键 [GoalDefinition.key] 去重/定位。
 * 4. **建议值** [suggestedTargets]：为编辑 UI 提供每个指标类型的合理目标值选项。
 *
 * 该对象不直接读写 SharedPreferences（持久化由 ViewModel 负责），仅处理
 * 纯数据转换，保证 100% 可单元测试。
 */
object GoalEditor {

    // ── 校验 ──────────────────────────────────────────────────────

    /**
     * 校验目标值是否合法。
     *
     * 规则：
     * - [GoalMetric.ACCURACY]：必须在 0.0~1.0 之间（含边界，0% 和 100% 都合法）
     * - 其他指标：必须严格 > 0（时长/次数/音符/曲目不能为负或零）
     *
     * @param metric 指标类型
     * @param target 目标值
     * @return [GoalValidation.Valid] 或 [GoalValidation.Invalid]（含中文原因）
     */
    fun validateTarget(metric: GoalMetric, target: Double): GoalValidation {
        if (target.isNaN() || target.isInfinite()) {
            return GoalValidation.Invalid("目标值无效")
        }
        return when (metric) {
            GoalMetric.ACCURACY -> {
                if (target < 0.0 || target > 1.0) {
                    GoalValidation.Invalid("准确率目标需在 0~100% 之间")
                } else {
                    GoalValidation.Valid
                }
            }
            else -> {
                if (target <= 0.0) {
                    GoalValidation.Invalid("目标值需大于 0")
                } else {
                    GoalValidation.Valid
                }
            }
        }
    }

    /**
     * 快速判断目标定义是否合法（组合校验）。
     */
    fun isValidGoal(metric: GoalMetric, period: GoalPeriod, target: Double): Boolean {
        return validateTarget(metric, target) is GoalValidation.Valid
    }

    // ── 序列化 ────────────────────────────────────────────────────

    /**
     * 将目标列表序列化为 SharedPreferences 存储字符串。
     *
     * 格式：`"PERIOD_METRIC:target,PERIOD_METRIC:target,..."`
     * （如 `"DAILY_PRACTICE_TIME:30.0,DAILY_SESSION_COUNT:2.0"`）。
     *
     * 空列表返回空字符串。
     */
    fun serializeGoals(goals: List<GoalDefinition>): String {
        return goals.joinToString(",") { goal ->
            "${goal.key}:${goal.target}"
        }
    }

    /**
     * 将 SharedPreferences 存储字符串反序列化为目标列表。
     *
     * 解析格式 `"PERIOD_METRIC:target,..."`。对格式错误的条目静默跳过
     * （逐条解析，单条错误不影响其他条目）。
     *
     * @param raw 原始字符串（可能为 null / 空 / 部分损坏）
     * @return 解析出的目标列表（可能为空）；原始为 null 或空时返回空列表
     */
    fun deserializeGoals(raw: String?): List<GoalDefinition> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            parseEntry(entry.trim())
        }
    }

    /**
     * 解析单条 `"PERIOD_METRIC:target"` 格式。
     */
    private fun parseEntry(entry: String): GoalDefinition? {
        if (entry.isBlank()) return null
        // 用 lastIndexOf ':' 以防 target 是科学计数法（虽然不会出现，但稳妥）
        val colonIdx = entry.lastIndexOf(':')
        if (colonIdx < 0) return null
        val key = entry.substring(0, colonIdx).trim()
        val target = entry.substring(colonIdx + 1).trim().toDoubleOrNull() ?: return null
        val parts = key.split("_", limit = 2)
        if (parts.size != 2) return null
        val period = runCatching { GoalPeriod.valueOf(parts[0]) }.getOrNull() ?: return null
        val metric = runCatching { GoalMetric.valueOf(parts[1]) }.getOrNull() ?: return null
        return GoalDefinition(metric, period, target)
    }

    // ── 增删改（不可变操作，返回新列表）─────────────────────────

    /**
     * 添加或更新一个目标。如果已存在相同 key 的目标，则替换其 target 值；
     * 否则追加到列表末尾。
     *
     * @param goals 当前目标列表
     * @param goal 要添加/更新的目标
     * @return 更新后的新列表
     */
    fun addOrUpdateGoal(goals: List<GoalDefinition>, goal: GoalDefinition): List<GoalDefinition> {
        val updated = goals.map { if (it.key == goal.key) goal else it }
        return if (updated.any { it.key == goal.key }) {
            updated
        } else {
            updated + goal
        }
    }

    /**
     * 按 key 移除一个目标。若不存在则原样返回。
     *
     * @param goals 当前目标列表
     * @param key 目标唯一键 [GoalDefinition.key]
     * @return 移除后的新列表
     */
    fun removeGoal(goals: List<GoalDefinition>, key: String): List<GoalDefinition> {
        return goals.filterNot { it.key == key }
    }

    /**
     * 切换目标的启用/禁用状态（通过添加或移除）。
     *
     * @param goals 当前目标列表
     * @param goal 要切换的目标
     * @param enabled true 则确保存在（添加），false 则确保不存在（移除）
     * @return 更新后的新列表
     */
    fun toggleGoal(goals: List<GoalDefinition>, goal: GoalDefinition, enabled: Boolean): List<GoalDefinition> {
        return if (enabled) {
            addOrUpdateGoal(goals, goal)
        } else {
            removeGoal(goals, goal.key)
        }
    }

    /**
     * 判断指定 key 的目标是否存在于列表中（即是否"已启用"）。
     */
    fun isGoalEnabled(goals: List<GoalDefinition>, key: String): Boolean {
        return goals.any { it.key == key }
    }

    // ── 预设 ──────────────────────────────────────────────────────

    /**
     * 应用预设目标包，完全替换当前目标列表。
     *
     * @param presetName 预设名称（"轻松"/"适中"/"挑战"）
     * @return 预设目标列表；未知预设名返回默认目标（适中）
     */
    fun applyPreset(presetName: String): List<GoalDefinition> {
        return GoalTracker.presets()[presetName] ?: GoalTracker.defaultGoals()
    }

    // ── 建议值 ────────────────────────────────────────────────────

    /**
     * 为指定指标类型返回建议的目标值列表（用于编辑 UI 的选择器）。
     *
     * 这些值基于常见钢琴练习场景精心选取，用户也可手动输入自定义值。
     *
     * @param metric 指标类型
     * @return 建议目标值列表（按难度递增）
     */
    fun suggestedTargets(metric: GoalMetric): List<Double> = when (metric) {
        GoalMetric.PRACTICE_TIME -> listOf(10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0)
        GoalMetric.SESSION_COUNT -> listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        GoalMetric.NOTES_PLAYED -> listOf(100.0, 300.0, 500.0, 1000.0, 2000.0, 5000.0)
        GoalMetric.ACCURACY -> listOf(0.70, 0.75, 0.80, 0.85, 0.90, 0.95, 1.0)
        GoalMetric.UNIQUE_PIECES -> listOf(1.0, 2.0, 3.0, 5.0, 7.0, 10.0)
    }

    /**
     * 返回所有可用的目标组合（每个 周期×指标）及其建议默认值，
     * 用于编辑 UI 初始化"全部可添加目标"列表。
     *
     * @return 以 (period, metric) 为键、建议默认 target 为值的列表
     */
    fun allAvailableGoals(): List<GoalDefinition> {
        val result = mutableListOf<GoalDefinition>()
        for (period in GoalPeriod.values()) {
            for (metric in GoalMetric.values()) {
                val default = suggestedTargets(metric).let {
                    // 取中间偏低的合理默认值
                    it[it.size / 3]
                }
                result.add(GoalDefinition(metric, period, default))
            }
        }
        return result
    }

    /**
     * 格式化目标值用于编辑 UI 的输入展示。
     * 准确率以百分比整数显示，其他以原值整数显示。
     */
    fun formatTargetForInput(metric: GoalMetric, target: Double): String {
        return if (metric == GoalMetric.ACCURACY) {
            "${(target * 100).toInt()}"
        } else {
            "${target.toInt()}"
        }
    }

    /**
     * 将编辑 UI 的输入文本解析为目标值。
     * 准确率：用户输入 85 → 0.85；其他：用户输入 30 → 30.0。
     *
     * @return 解析成功返回 Double，失败返回 null
     */
    fun parseInputToTarget(metric: GoalMetric, input: String): Double? {
        val raw = input.trim().toDoubleOrNull() ?: return null
        return if (metric == GoalMetric.ACCURACY) {
            raw / 100.0
        } else {
            raw
        }
    }
}
