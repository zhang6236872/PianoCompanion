package com.pianocompanion.data

/**
 * 乐谱收藏存储引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 管理一组乐谱的收藏标记，提供增删改查、切换、排序与持久化序列化。
 *
 * 收藏键（key）的约定：
 * - 内置乐谱使用 [Score][com.pianocompanion.data.model.Score] 的 `id`
 *   （例如 `"ode_to_joy"`、`"twinkle"`）。
 * - 导入乐谱使用 [keyForImported] 生成的带前缀键
 *   （例如 `"imported:欢乐颂.xml"`），前缀避免与内置乐谱 id 冲突。
 *
 * 这种「统一字符串键」的设计使得收藏集合可以跨内置/导入乐谱统一管理，
 * 同时保持内置乐谱 id 的可读性（无前缀污染）。
 */
class FavoriteStore {

    private val favorites: MutableSet<String> = LinkedHashSet()

    /** 收藏数量。 */
    val size: Int get() = favorites.size

    /** 是否没有任何收藏。 */
    fun isEmpty(): Boolean = favorites.isEmpty()

    /** 是否存在至少一个收藏。 */
    fun isNotEmpty(): Boolean = favorites.isNotEmpty()

    /**
     * 将 [key] 标记为收藏。已存在则无副作用。
     *
     * @return 是否发生了变化（即 [key] 此前未被收藏）。
     */
    fun add(key: String): Boolean = favorites.add(key)

    /**
     * 取消收藏 [key]。不存在则无副作用。
     *
     * @return 是否发生了变化（即 [key] 此前已被收藏）。
     */
    fun remove(key: String): Boolean = favorites.remove(key)

    /**
     * 切换 [key] 的收藏状态。
     *
     * @return 切换后该 [key] 是否处于收藏状态（true = 已收藏，false = 未收藏）。
     */
    fun toggle(key: String): Boolean {
        return if (favorites.contains(key)) {
            favorites.remove(key)
            false
        } else {
            favorites.add(key)
            true
        }
    }

    /** 判断 [key] 是否已被收藏。 */
    fun isFavorite(key: String): Boolean = favorites.contains(key)

    /**
     * 返回收藏键的不可变快照（保持插入顺序）。
     *
     * 调用者可安全地迭代返回列表而不会触发 ConcurrentModificationException，
     * 也不会因为持有该引用而看到后续的修改。
     */
    fun list(): List<String> = favorites.toList()

    /**
     * 对一组 [keys] 进行**稳定排序**，使被收藏的元素排在最前，
     * 而未被收藏的元素保持原有相对顺序；被收藏元素之间也保持原顺序。
     *
     * 稳定排序保证 UI 列表在「收藏置顶」时不会打乱用户的既有浏览习惯。
     * 重复键只保留首次出现的位置（去重），避免列表中出现重复项。
     */
    fun sortByFavorites(keys: List<String>): List<String> {
        val seen = HashSet<String>()
        val favs = ArrayList<String>()
        val rest = ArrayList<String>()
        for (key in keys) {
            if (!seen.add(key)) continue // 去重
            if (favorites.contains(key)) favs.add(key) else rest.add(key)
        }
        return favs + rest
    }

    /**
     * 过滤 [keys]，只保留被收藏的元素（保持原顺序、去重）。
     *
     * 用于 UI 的「只看收藏」筛选模式。
     */
    fun filterToFavorites(keys: List<String>): List<String> {
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (key in keys) {
            if (!seen.add(key)) continue
            if (favorites.contains(key)) result.add(key)
        }
        return result
    }

    /**
     * 从另一组键批量设置收藏（**替换**当前全部内容）。
     * 主要用于测试与数据迁移场景。
     */
    fun replaceAll(keys: Collection<String>) {
        favorites.clear()
        favorites.addAll(keys)
    }

    /** 清空全部收藏。 */
    fun clear() {
        favorites.clear()
    }

    // ───────────────────────── JSON 序列化 ─────────────────────────

    /**
     * 将收藏集合序列化为紧凑 JSON 字符串。
     *
     * 格式（无缩进，无外部依赖）：
     * ```json
     * ["ode_to_joy","imported:欢乐颂.xml"]
     * ```
     *
     * 空集合序列化为 `[]`。
     */
    fun toJson(): String {
        if (favorites.isEmpty()) return "[]"
        val items = favorites.joinToString(",") { key ->
            "\"${jsonEscape(key)}\""
        }
        return "[$items]"
    }

    /**
     * 从 JSON 字符串反序列化收藏集合，**替换**当前全部内容。
     *
     * 容错策略：
     * - 空字符串 / `null` / `"[]"` / `"null"` → 清空（返回 0）
     * - 整体非合法 JSON → 清空（返回 0，不抛异常）
     * - 单个键解析失败（如字符串未闭合）→ 跳过该键
     *
     * @return 实际解析出的收藏数量（用于诊断）。
     */
    fun fromJson(json: String?): Int {
        favorites.clear()
        if (json.isNullOrBlank()) return 0
        val text = json.trim()
        if (text == "[]" || text == "null") return 0
        return try {
            val keys = parseJsonStringArray(text)
            favorites.addAll(keys)
            favorites.size
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // 容错：任何解析异常都清空
            favorites.clear()
            0
        }
    }

    // ───────────────────────── 私有辅助 ─────────────────────────

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * 解析 JSON 字符串数组 `[\"a\",\"b\",...]` 为 List<String>。
     *
     * 手写状态机（无外部依赖），正确处理转义序列与 Unicode 文本。
     */
    private fun parseJsonStringArray(text: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val n = text.length
        // 跳过到第一个 '['
        while (i < n && text[i] != '[') i++
        if (i >= n) return result // 没有找到 '['
        i++ // 跳过 '['
        while (i < n) {
            // 跳过空白与逗号
            while (i < n && (text[i].isWhitespace() || text[i] == ',')) i++
            if (i >= n || text[i] == ']') break
            if (text[i] != '"') break // 非字符串元素，停止
            i++ // 跳过开引号
            val sb = StringBuilder()
            var closed = false
            while (i < n) {
                val c = text[i]
                when {
                    c == '\\' && i + 1 < n -> {
                        val next = text[i + 1]
                        when (next) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            '/' -> sb.append('/')
                            else -> sb.append(next) // 未知转义，保留字符
                        }
                        i += 2
                    }
                    c == '"' -> { closed = true; i++; break }
                    else -> { sb.append(c); i++ }
                }
            }
            if (closed) result.add(sb.toString())
            else break // 字符串未闭合，停止解析
        }
        return result
    }

    companion object {
        /**
         * 为导入乐谱（由磁盘文件名 [fileName] 标识）构造统一的收藏键。
         *
         * 添加 `"imported:"` 前缀以避免与内置乐谱的 id（如 `"ode_to_joy"`）冲突。
         */
        fun keyForImported(fileName: String): String = "imported:$fileName"

        /**
         * 为内置乐谱（由 [scoreId] 标识）构造收藏键。
         *
         * 内置乐谱的 id 本身就是稳定且唯一的，直接使用无需前缀。
         */
        fun keyForBuiltIn(scoreId: String): String = scoreId
    }
}
