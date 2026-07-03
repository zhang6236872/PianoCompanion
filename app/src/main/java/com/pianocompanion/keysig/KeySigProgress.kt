package com.pianocompanion.keysig

/**
 * 调号识别训练进度跟踪模型（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 记录跨会话的练习进度：每种谱号 + 难度组合的累计统计。
 * 通过 [toJson] / [fromJson] 进行 JSON 序列化（手动解析，无外部依赖）。
 *
 * @param stats 各谱号+难度的统计映射，键格式为 "TREBLE_BEGINNER"
 */
data class KeySigProgress(
    val stats: MutableMap<String, KeySigProgressEntry> = mutableMapOf()
) {
    /**
     * 记录一次会话的结果。
     *
     * @param clef 谱号
     * @param difficulty 难度
     * @param correct 答对数
     * @param total 总答题数
     * @param bestStreak 本次会话最长连击
     */
    fun recordSession(
        clef: KeySigClef,
        difficulty: KeySigDifficulty,
        correct: Int,
        total: Int,
        bestStreak: Int
    ) {
        val key = key(clef, difficulty)
        val entry = stats.getOrPut(key) { KeySigProgressEntry() }
        entry.totalAnswered += total
        entry.totalCorrect += correct
        entry.sessionCount++
        if (bestStreak > entry.bestStreak) {
            entry.bestStreak = bestStreak
        }
        if (total > 0) {
            val sessionAccuracy = correct.toDouble() / total
            if (sessionAccuracy > entry.bestAccuracy) {
                entry.bestAccuracy = sessionAccuracy
            }
        }
    }

    /**
     * 获取指定组合的进度统计。
     */
    fun getProgress(clef: KeySigClef, difficulty: KeySigDifficulty): KeySigProgressEntry {
        return stats[key(clef, difficulty)] ?: KeySigProgressEntry()
    }

    /** 总会话数。 */
    val totalSessions: Int get() = stats.values.sumOf { it.sessionCount }

    /** 总答题数。 */
    val totalAnswered: Int get() = stats.values.sumOf { it.totalAnswered }

    /** 总答对数。 */
    val totalCorrect: Int get() = stats.values.sumOf { it.totalCorrect }

    /** 全局准确率（0.0-1.0）。 */
    val overallAccuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered

    /**
     * 序列化为 JSON 字符串（手动实现，无外部依赖）。
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"stats\":{")
        val entries = stats.entries.toList()
        entries.forEachIndexed { index, (k, v) ->
            if (index > 0) sb.append(",")
            sb.append("\"").append(escape(k)).append("\":")
            sb.append(v.toJson())
        }
        sb.append("}}")
        return sb.toString()
    }

    companion object {
        fun key(clef: KeySigClef, difficulty: KeySigDifficulty): String =
            "${clef.name}_${difficulty.name}"

        /**
         * 从 JSON 字符串反序列化。
         */
        fun fromJson(json: String): KeySigProgress {
            val result = KeySigProgress()
            try {
                val statsObj = extractObject(json, "stats") ?: return result
                val pairs = splitKeyValuePairs(statsObj)
                for ((key, valueStr) in pairs) {
                    val entry = KeySigProgressEntry.fromJson(valueStr)
                    if (entry != null) {
                        result.stats[unescape(key)] = entry
                    }
                }
            } catch (_: Exception) {
                // 解析失败返回空进度（向后兼容）
            }
            return result
        }

        private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
        private fun unescape(s: String): String = s.replace("\\\"", "\"").replace("\\\\", "\\")

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
                if (i >= objContent.length) break
                if (objContent[i] != '"') break
                val keyEnd = findClosingQuote(objContent, i)
                if (keyEnd < 0) break
                val key = objContent.substring(i + 1, keyEnd)
                i = keyEnd + 1
                while (i < objContent.length && (objContent[i] == ':' || objContent[i].isWhitespace())) i++
                if (i >= objContent.length || objContent[i] != '{') break
                val valueEnd = findMatchingBrace(objContent, i)
                if (valueEnd < 0) break
                val value = objContent.substring(i, valueEnd + 1)
                result.add(unescape(key) to value)
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
 * 单个谱号+难度组合的进度统计。
 */
data class KeySigProgressEntry(
    var totalAnswered: Int = 0,
    var totalCorrect: Int = 0,
    var sessionCount: Int = 0,
    var bestStreak: Int = 0,
    var bestAccuracy: Double = 0.0
) {
    /** 累计准确率（0.0-1.0）。 */
    val cumulativeAccuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered

    fun toJson(): String {
        return """{"totalAnswered":$totalAnswered,"totalCorrect":$totalCorrect,""" +
            """"sessionCount":$sessionCount,"bestStreak":$bestStreak,""" +
            """"bestAccuracy":${"%.4f".format(bestAccuracy)}}"""
    }

    companion object {
        fun fromJson(json: String): KeySigProgressEntry? {
            return try {
                val trimmed = json.trim()
                if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
                val entry = KeySigProgressEntry()
                entry.totalAnswered = extractLong(json, "totalAnswered").toInt()
                entry.totalCorrect = extractLong(json, "totalCorrect").toInt()
                entry.sessionCount = extractLong(json, "sessionCount").toInt()
                entry.bestStreak = extractLong(json, "bestStreak").toInt()
                entry.bestAccuracy = extractDouble(json, "bestAccuracy")
                entry
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
