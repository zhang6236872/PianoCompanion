package com.pianocompanion.mixedpractice

/**
 * 综合练习进度跟踪模型（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 记录跨会话的综合练习进度：每种难度的累计统计 + 各题型的单独统计。
 * 通过 [toJson] / [fromJson] 进行 JSON 序列化（手动解析，无外部依赖）。
 *
 * @param difficultyStats 各难度的统计映射，键格式为 "BEGINNER"
 * @param typeStats 各题型的统计映射，键为题型名称（如 "NOTE_READING"）
 */
data class MixedPracticeProgress(
    val difficultyStats: MutableMap<String, MixedProgressEntry> = mutableMapOf(),
    val typeStats: MutableMap<String, MixedProgressEntry> = mutableMapOf()
) {
    /**
     * 记录一次会话的结果（按难度）。
     */
    fun recordSession(
        difficulty: MixedDifficulty,
        correct: Int,
        total: Int,
        bestStreak: Int
    ) {
        val entry = difficultyStats.getOrPut(difficulty.name) { MixedProgressEntry() }
        entry.totalAnswered += total
        entry.totalCorrect += correct
        entry.sessionCount++
        if (bestStreak > entry.bestStreak) entry.bestStreak = bestStreak
        if (total > 0) {
            val sessionAccuracy = correct.toDouble() / total
            if (sessionAccuracy > entry.bestAccuracy) entry.bestAccuracy = sessionAccuracy
        }
    }

    /**
     * 记录单题结果（按题型）。
     */
    fun recordQuestion(type: MixedQuestionType, isCorrect: Boolean) {
        val entry = typeStats.getOrPut(type.name) { MixedProgressEntry() }
        entry.totalAnswered++
        if (isCorrect) entry.totalCorrect++
        entry.sessionCount++ // 复用为题目计数
    }

    /** 获取指定难度的统计。 */
    fun getDifficultyProgress(difficulty: MixedDifficulty): MixedProgressEntry =
        difficultyStats[difficulty.name] ?: MixedProgressEntry()

    /** 获取指定题型的统计。 */
    fun getTypeProgress(type: MixedQuestionType): MixedProgressEntry =
        typeStats[type.name] ?: MixedProgressEntry()

    /** 总会话数。 */
    val totalSessions: Int get() = difficultyStats.values.sumOf { it.sessionCount }

    /** 总答题数。 */
    val totalAnswered: Int get() = difficultyStats.values.sumOf { it.totalAnswered }

    /** 总答对数。 */
    val totalCorrect: Int get() = difficultyStats.values.sumOf { it.totalCorrect }

    /** 全局准确率（0.0-1.0）。 */
    val overallAccuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered

    /**
     * 序列化为 JSON 字符串。
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"difficultyStats\":{")
        difficultyStats.entries.toList().forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(k).append("\":").append(v.toJson())
        }
        sb.append("},\"typeStats\":{")
        typeStats.entries.toList().forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"").append(k).append("\":").append(v.toJson())
        }
        sb.append("}}")
        return sb.toString()
    }

    companion object {
        /**
         * 从 JSON 字符串反序列化。解析失败返回空进度（向后兼容）。
         */
        fun fromJson(json: String): MixedPracticeProgress {
            val result = MixedPracticeProgress()
            try {
                val dObj = extractObject(json, "difficultyStats")
                if (dObj != null) {
                    for ((k, v) in splitKeyValuePairs(dObj)) {
                        MixedProgressEntry.fromJson(v)?.let { result.difficultyStats[k] = it }
                    }
                }
                val tObj = extractObject(json, "typeStats")
                if (tObj != null) {
                    for ((k, v) in splitKeyValuePairs(tObj)) {
                        MixedProgressEntry.fromJson(v)?.let { result.typeStats[k] = it }
                    }
                }
            } catch (_: Exception) {
                // 解析失败返回空进度
            }
            return result
        }

        private fun extractObject(json: String, key: String): String? {
            val keyPattern = "\"$key\""
            val keyIdx = json.indexOf(keyPattern)
            if (keyIdx < 0) return null
            var i = keyIdx + keyPattern.length
            while (i < json.length && (json[i] == ':' || json[i].isWhitespace())) i++
            if (i >= json.length || json[i] != '{') return null
            var depth = 0
            val start = i + 1
            while (i < json.length) {
                when (json[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return json.substring(start, i) }
                }
                i++
            }
            return null
        }

        private fun splitKeyValuePairs(objContent: String): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            var i = 0
            while (i < objContent.length) {
                while (i < objContent.length && (objContent[i].isWhitespace() || objContent[i] == ',')) i++
                if (i >= objContent.length || objContent[i] != '"') break
                val keyEnd = findClosingQuote(objContent, i)
                if (keyEnd < 0) break
                val key = objContent.substring(i + 1, keyEnd)
                i = keyEnd + 1
                while (i < objContent.length && (objContent[i] == ':' || objContent[i].isWhitespace())) i++
                if (i >= objContent.length || objContent[i] != '{') break
                val valueEnd = findMatchingBrace(objContent, i)
                if (valueEnd < 0) break
                result.add(key to objContent.substring(i, valueEnd + 1))
                i = valueEnd + 1
            }
            return result
        }

        private fun findClosingQuote(s: String, start: Int): Int {
            var i = start + 1
            while (i < s.length) {
                when {
                    s[i] == '\\' -> i += 2
                    s[i] == '"' -> return i
                    else -> i++
                }
            }
            return -1
        }

        private fun findMatchingBrace(s: String, start: Int): Int {
            var depth = 0
            var i = start
            while (i < s.length) {
                when (s[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return i }
                }
                i++
            }
            return -1
        }
    }
}

/**
 * 单个统计条目（可用于难度维度或题型维度）。
 */
data class MixedProgressEntry(
    var totalAnswered: Int = 0,
    var totalCorrect: Int = 0,
    var sessionCount: Int = 0,
    var bestStreak: Int = 0,
    var bestAccuracy: Double = 0.0
) {
    val cumulativeAccuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered

    fun toJson(): String =
        "{\"totalAnswered\":$totalAnswered,\"totalCorrect\":$totalCorrect," +
            "\"sessionCount\":$sessionCount,\"bestStreak\":$bestStreak," +
            "\"bestAccuracy\":${"%.4f".format(bestAccuracy)}}"

    companion object {
        fun fromJson(json: String): MixedProgressEntry? {
            return try {
                val trimmed = json.trim()
                if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
                MixedProgressEntry(
                    totalAnswered = extractLong(json, "totalAnswered").toInt(),
                    totalCorrect = extractLong(json, "totalCorrect").toInt(),
                    sessionCount = extractLong(json, "sessionCount").toInt(),
                    bestStreak = extractLong(json, "bestStreak").toInt(),
                    bestAccuracy = extractDouble(json, "bestAccuracy")
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun extractLong(json: String, key: String): Long {
            val pattern = "\"$key\""
            val idx = json.indexOf(pattern)
            if (idx < 0) return 0
            var i = idx + pattern.length
            while (i < json.length && (json[i] == ':' || json[i].isWhitespace())) i++
            val sb = StringBuilder()
            if (i < json.length && json[i] == '-') { sb.append('-'); i++ }
            while (i < json.length && json[i].isDigit()) { sb.append(json[i]); i++ }
            return sb.toString().toLongOrNull() ?: 0
        }

        private fun extractDouble(json: String, key: String): Double {
            val pattern = "\"$key\""
            val idx = json.indexOf(pattern)
            if (idx < 0) return 0.0
            var i = idx + pattern.length
            while (i < json.length && (json[i] == ':' || json[i].isWhitespace())) i++
            val sb = StringBuilder()
            while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] == '-' || json[i] == 'e' || json[i] == 'E')) {
                sb.append(json[i]); i++
            }
            return sb.toString().toDoubleOrNull() ?: 0.0
        }
    }
}
