package com.pianocompanion.audio

/**
 * # 节拍器预设 (Metronome Preset)
 *
 * 将一组完整的节拍器配置（速度 + 拍号 + 细分模式）保存为命名的预设，
 * 方便用户一键召回常用的练习设定，无需每次手动调整三个参数。
 *
 * 这是专业节拍器 App（Pro Metronome、Soundbrenner 等）的标准功能。
 *
 * @param name            预设名称（用户自定义，非空）
 * @param bpm             速度（每分钟拍数，40–240）
 * @param beatsPerMeasure 拍号分子（每小节拍数，1–12）
 * @param subdivision     细分模式（四分/八分/三连音/十六分/六连音/三十二分）
 */
data class MetronomePreset(
    val name: String,
    val bpm: Int,
    val beatsPerMeasure: Int,
    val subdivision: Subdivision,
) {
    /**
     * 一行人类可读的配置摘要，如 "120 · 4/4 · 八分音符"。
     */
    val summary: String
        get() = "$bpm · $beatsPerMeasure/4 · ${subdivision.displayName}"

    init {
        require(name.isNotBlank()) { "预设名称不能为空" }
        require(bpm in MIN_BPM..MAX_BPM) { "BPM 必须在 $MIN_BPM–$MAX_BPM 之间，实际为 $bpm" }
        require(beatsPerMeasure in MIN_BEATS..MAX_BEATS) {
            "拍号分子必须在 $MIN_BEATS–$MAX_BEATS 之间，实际为 $beatsPerMeasure"
        }
    }

    companion object {
        const val MIN_BPM = 40
        const val MAX_BPM = 240
        const val MIN_BEATS = 1
        const val MAX_BEATS = 12
        /** 单个名称的最大长度，防止恶意/意外超长输入。 */
        const val MAX_NAME_LENGTH = 40

        /**
         * 内置默认预设工厂——开箱即用的常用练习设定。
         */
        fun defaults(): List<MetronomePreset> = listOf(
            MetronomePreset("哈农热身",  80, 4, Subdivision.QUARTER),
            MetronomePreset("音阶练习", 100, 4, Subdivision.EIGHTH),
            MetronomePreset("三连音特训", 90, 4, Subdivision.TRIPLET),
            MetronomePreset("十六分提速", 120, 4, Subdivision.SIXTEENTH),
            MetronomePreset("华尔兹",  150, 3, Subdivision.QUARTER),
            MetronomePreset("慢板放松",  60, 4, Subdivision.QUARTER),
        )
    }
}

/**
 * 节拍器预设的验证结果。
 */
sealed class PresetValidationResult {
    /** 验证通过。 */
    data object Ok : PresetValidationResult()
    /** 名称不能为空。 */
    data object NameBlank : PresetValidationResult()
    /** 名称过长（超过 [MetronomePreset.MAX_NAME_LENGTH]）。 */
    data object NameTooLong : PresetValidationResult()
    /** 名称已被占用（重复）。 */
    data class NameTaken(val existingName: String) : PresetValidationResult()
    /** BPM 超出范围。 */
    data object BpmOutOfRange : PresetValidationResult()
    /** 拍号分子超出范围。 */
    data object BeatsOutOfRange : PresetValidationResult()
}

/**
 * 纯 Kotlin 预设存储引擎（无 Android 依赖，完全可单元测试）。
 *
 * 管理一个有序的 [MetronomePreset] 集合，提供增删改查、验证和
 * 手动 JSON 序列化（无外部依赖，与项目其他 Progress 类的序列化策略一致）。
 *
 * 设计决策：
 * - 名称唯一：每个预设以名称为键，保存时名称冲突则覆盖（但调用方可先验证）。
 * - 有序存储：预设按名称字母序排列，确保跨会话稳定。
 * - JSON 容错：损坏的 JSON 字符串返回空列表而非崩溃（与 [KeySigProgress] 等一致）。
 */
class MetronomePresetStore {

    private val presets: MutableMap<String, MetronomePreset> = LinkedHashMap()

    /**
     * 返回当前所有预设，按名称排序的快照（不可变列表）。
     */
    fun list(): List<MetronomePreset> =
        presets.values.sortedBy { it.name }

    /**
     * 是否存在指定名称的预设。
     */
    fun exists(name: String): Boolean = presets.containsKey(name)

    /**
     * 按名称查找预设，不存在返回 null。
     */
    fun find(name: String): MetronomePreset? = presets[name]

    /**
     * 当前预设数量。
     */
    val size: Int get() = presets.size

    /**
     * 验证一个待保存的预设（不实际写入），返回 [PresetValidationResult]。
     *
     * 当 [ignoreExisting] 指定的名称在改名操作中允许保持自身时使用。
     */
    fun validate(
        name: String,
        bpm: Int,
        beatsPerMeasure: Int,
        ignoreExisting: String? = null,
    ): PresetValidationResult {
        if (name.isBlank()) return PresetValidationResult.NameBlank
        if (name.length > MetronomePreset.MAX_NAME_LENGTH) return PresetValidationResult.NameTooLong
        if (name != ignoreExisting && presets.containsKey(name)) {
            return PresetValidationResult.NameTaken(name)
        }
        if (bpm !in MetronomePreset.MIN_BPM..MetronomePreset.MAX_BPM) {
            return PresetValidationResult.BpmOutOfRange
        }
        if (beatsPerMeasure !in MetronomePreset.MIN_BEATS..MetronomePreset.MAX_BEATS) {
            return PresetValidationResult.BeatsOutOfRange
        }
        return PresetValidationResult.Ok
    }

    /**
     * 保存（或覆盖）一个预设。返回 [PresetValidationResult]。
     *
     * 如果名称已存在则覆盖旧值（"另存为" 语义）。
     * 如果 [MetronomePreset] 构造本身就违反不变式（如 BPM 越界），
     * 会在 data class 的 init 块中直接抛 [IllegalArgumentException]——
     * 调用方应先用 [validate] 做软验证以获得友好的错误码。
     */
    fun save(preset: MetronomePreset): PresetValidationResult {
        presets[preset.name] = preset
        return PresetValidationResult.Ok
    }

    /**
     * 删除指定名称的预设。返回是否实际删除了（false = 名称不存在）。
     */
    fun delete(name: String): Boolean = presets.remove(name) != null

    /**
     * 重命名一个预设（保留其 BPM/拍号/细分不变）。
     *
     * @return 重命名后的验证结果；[PresetValidationResult.Ok] 表示成功。
     *         若旧名称不存在，返回 [PresetValidationResult.NameBlank]（非致命，调用方可忽略）。
     */
    fun rename(oldName: String, newName: String): PresetValidationResult {
        val existing = presets[oldName] ?: return PresetValidationResult.NameBlank
        val result = validate(newName, existing.bpm, existing.beatsPerMeasure, ignoreExisting = oldName)
        if (result is PresetValidationResult.Ok) {
            val renamed = existing.copy(name = newName)
            presets.remove(oldName)
            presets[newName] = renamed
        }
        return result
    }

    /**
     * 清空所有预设。
     */
    fun clear() {
        presets.clear()
    }

    /**
     * 用一组预设替换当前全部内容（批量导入）。
     */
    fun replaceAll(newPresets: List<MetronomePreset>) {
        presets.clear()
        newPresets.forEach { presets[it.name] = it }
    }

    // ───────────────────────── JSON 序列化 ─────────────────────────

    /**
     * 将当前所有预设序列化为紧凑 JSON 字符串。
     *
     * 格式（无缩进，无外部依赖）：
     * ```json
     * [{"n":"哈农热身","b":80,"m":4,"s":"QUARTER"}, ...]
     * ```
     */
    fun toJson(): String {
        if (presets.isEmpty()) return "[]"
        val items = presets.values.joinToString(",") { p ->
            val n = jsonEscape(p.name)
            val s = jsonEscape(p.subdivision.name)
            """{"n":"$n","b":${p.bpm},"m":${p.beatsPerMeasure},"s":"$s"}"""
        }
        return "[$items]"
    }

    /**
     * 从 JSON 字符串反序列化预设列表，**替换**当前全部内容。
     *
     * 容错策略：
     * - 空字符串 / 非 JSON → 清空（返回空）
     * - 单条预设缺少字段或 Subdivision 名称非法 → 跳过该条（不崩溃）
     * - 解析整体异常 → 清空（不抛出）
     *
     * @return 实际解析出的预设数量（用于诊断）。
     */
    fun fromJson(json: String?): Int {
        presets.clear()
        if (json.isNullOrBlank()) return 0
        val text = json.trim()
        if (text == "[]" || text == "null") return 0
        // 粗略提取对象数组
        var count = 0
        try {
            val objects = splitJsonObjects(text)
            for (obj in objects) {
                val n = extractStringField(obj, "n") ?: continue
                val b = extractIntField(obj, "b") ?: continue
                val m = extractIntField(obj, "m") ?: continue
                val sName = extractStringField(obj, "s") ?: continue
                val sub = runCatching { Subdivision.valueOf(sName) }.getOrNull() ?: continue
                val preset = runCatching { MetronomePreset(n, b, m, sub) }.getOrNull() ?: continue
                if (!presets.containsKey(preset.name)) {
                    presets[preset.name] = preset
                    count++
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // 容错：任何解析异常都清空，返回已解析的数量
        }
        return count
    }

    // ───────────────────────── 私有 JSON 辅助 ─────────────────────────

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * 将 JSON 数组字符串拆分为单个 `{...}` 对象的字符串列表（粗略括号匹配）。
     */
    private fun splitJsonObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        for (i in text.indices) {
            val c = text[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                c == '{' && !inString -> {
                    if (depth == 0) start = i
                    depth++
                }
                c == '}' && !inString -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        result.add(text.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun extractStringField(obj: String, field: String): String? {
        val pattern = "\"$field\"\\s*:\\s*\""
        val keyMatch = Regex(pattern).find(obj) ?: return null
        val start = keyMatch.range.last + 1
        val sb = StringBuilder()
        var i = start
        var escape = false
        while (i < obj.length) {
            val c = obj[i]
            when {
                escape -> {
                    when (c) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        '/' -> sb.append('/')
                        else -> sb.append(c)
                    }
                    escape = false
                }
                c == '\\' -> escape = true
                c == '"' -> return sb.toString()
                else -> sb.append(c)
            }
            i++
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

    private fun extractIntField(obj: String, field: String): Int? {
        val pattern = "\"$field\"\\s*:\\s*(-?\\d+)"
        val match = Regex(pattern).find(obj) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
