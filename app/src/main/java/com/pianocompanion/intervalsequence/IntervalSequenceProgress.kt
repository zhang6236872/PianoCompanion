package com.pianocompanion.intervalsequence

import kotlin.math.abs
import kotlin.math.max

/**
 * 音程序列记忆跨会话进度跟踪（纯 Kotlin，无 Android 依赖）。
 *
 * 按难度隔离统计：每难度独立记录 attempts/correct/streak/bestAccuracy。
 * 手动 JSON 序列化（容错解析，严格字段校验）。
 */
class IntervalSequenceProgress {

    private val stats = mutableMapOf<IntervalSequenceDifficulty, DifficultyStats>()

    /** 总答题数。 */
    val totalAnswered: Int get() = stats.values.sumOf { it.totalAnswered }

    /** 总正确数。 */
    val totalCorrect: Int get() = stats.values.sumOf { it.totalCorrect }

    /** 总体准确率。 */
    val overallAccuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered

    /** 最长连击。 */
    val longestStreak: Int get() = stats.values.maxOfOrNull { it.bestStreak } ?: 0

    /**
     * 记录一次会话结果。
     *
     * @param difficulty 难度
     * @param correct 正确数
     * @param total 总题数
     * @param bestStreak 本次最佳连击
     */
    fun recordSession(
        difficulty: IntervalSequenceDifficulty,
        correct: Int,
        total: Int,
        bestStreak: Int
    ) {
        val entry = stats.getOrPut(difficulty) { DifficultyStats() }
        entry.totalAnswered += max(0, total)
        entry.totalCorrect += max(0, correct).coerceAtMost(max(0, total))
        if (total > 0) {
            val sessionAccuracy = correct.toDouble() / total
            if (sessionAccuracy > entry.bestAccuracy) {
                entry.bestAccuracy = sessionAccuracy
            }
        }
        if (bestStreak > entry.bestStreak) {
            entry.bestStreak = bestStreak
        }
    }

    /**
     * 获取某难度的进度（不存在则返回空）。
     */
    fun getProgress(difficulty: IntervalSequenceDifficulty): DifficultyStats {
        return stats[difficulty] ?: DifficultyStats()
    }

    /**
     * 序列化为 JSON 字符串。
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"stats\":[")
        val parts = stats.entries.map { (diff, s) ->
            val sb2 = StringBuilder()
            sb2.append("{")
            sb2.append("\"difficulty\":\"").append(diff.name).append("\"")
            sb2.append(",\"totalAnswered\":").append(s.totalAnswered)
            sb2.append(",\"totalCorrect\":").append(s.totalCorrect)
            sb2.append(",\"bestAccuracy\":").append(s.bestAccuracy)
            sb2.append(",\"bestStreak\":").append(s.bestStreak)
            sb2.append("}")
            sb2.toString()
        }
        sb.append(parts.joinToString(","))
        sb.append("]}")
        return sb.toString()
    }

    companion object {
        /**
         * 从 JSON 字符串反序列化（容错解析）。
         */
        fun fromJson(json: String): IntervalSequenceProgress {
            val progress = IntervalSequenceProgress()
            try {
                val statsArray = extractArray(json, "stats")
                for (obj in statsArray) {
                    val difficultyName = extractString(obj, "difficulty") ?: continue
                    val difficulty = IntervalSequenceDifficulty.values()
                        .find { it.name == difficultyName } ?: continue

                    val totalAnswered = extractInt(obj, "totalAnswered")
                    val totalCorrect = extractInt(obj, "totalCorrect")
                    val bestAccuracy = extractDouble(obj, "bestAccuracy")
                    val bestStreak = extractInt(obj, "bestStreak")

                    // 严格 5 字段校验
                    if (totalAnswered == null || totalCorrect == null ||
                        bestAccuracy == null || bestStreak == null
                    ) continue

                    // 负数回退
                    val safeTotal = max(0, totalAnswered)
                    val safeCorrect = max(0, totalCorrect).coerceAtMost(safeTotal)
                    val safeAccuracy = if (bestAccuracy < 0 || bestAccuracy.isNaN()) 0.0
                                       else if (bestAccuracy > 1.0) 1.0 else bestAccuracy
                    val safeStreak = max(0, bestStreak)

                    val stats = DifficultyStats()
                    stats.totalAnswered = safeTotal
                    stats.totalCorrect = safeCorrect
                    stats.bestAccuracy = safeAccuracy
                    stats.bestStreak = safeStreak
                    progress.stats[difficulty] = stats
                }
            } catch (_: Exception) {
                // 损坏 JSON 返回空进度
            }
            return progress
        }

        // ── 手动 JSON 解析工具 ──────────────────────────

        private fun extractArray(json: String, key: String): List<String> {
            val results = mutableListOf<String>()
            val keyPattern = "\"$key\""
            val keyIdx = json.indexOf(keyPattern)
            if (keyIdx < 0) return results

            val afterKey = json.indexOf("[", keyIdx)
            if (afterKey < 0) return results

            var depth = 0
            var objStart = -1
            var i = afterKey + 1
            while (i < json.length) {
                when (json[i]) {
                    '{' -> {
                        if (depth == 0) objStart = i
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && objStart >= 0) {
                            results.add(json.substring(objStart, i + 1))
                            objStart = -1
                        }
                    }
                    ']' -> {
                        if (depth == 0) break
                    }
                }
                i++
            }
            return results
        }

        private fun extractString(obj: String, key: String): String? {
            val pattern = "\"$key\""
            val idx = obj.indexOf(pattern)
            if (idx < 0) return null
            val colonIdx = obj.indexOf(":", idx + pattern.length)
            if (colonIdx < 0) return null
            val startQuote = obj.indexOf("\"", colonIdx + 1)
            if (startQuote < 0) return null
            val endQuote = obj.indexOf("\"", startQuote + 1)
            if (endQuote < 0) return null
            return obj.substring(startQuote + 1, endQuote)
        }

        private fun extractInt(obj: String, key: String): Int? {
            val d = extractNumber(obj, key) ?: return null
            return d.toInt()
        }

        private fun extractDouble(obj: String, key: String): Double? {
            return extractNumber(obj, key)
        }

        private fun extractNumber(obj: String, key: String): Double? {
            val pattern = "\"$key\""
            val idx = obj.indexOf(pattern)
            if (idx < 0) return null
            val colonIdx = obj.indexOf(":", idx + pattern.length)
            if (colonIdx < 0) return null
            val start = colonIdx + 1
            // 跳过空格
            var i = start
            while (i < obj.length && obj[i] == ' ') i++
            if (i >= obj.length) return null
            val sb = StringBuilder()
            if (obj[i] == '-' || obj[i] == '+') {
                sb.append(obj[i])
                i++
            }
            var hasDigit = false
            while (i < obj.length && (obj[i].isDigit() || obj[i] == '.')) {
                sb.append(obj[i])
                if (obj[i].isDigit()) hasDigit = true
                i++
            }
            if (!hasDigit) return null
            return sb.toString().toDoubleOrNull()
        }
    }
}

/**
 * 单难度统计。
 */
data class DifficultyStats(
    var totalAnswered: Int = 0,
    var totalCorrect: Int = 0,
    var bestAccuracy: Double = 0.0,
    var bestStreak: Int = 0
) {
    /** 准确率（当次会话维度）。 */
    val accuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered
}
