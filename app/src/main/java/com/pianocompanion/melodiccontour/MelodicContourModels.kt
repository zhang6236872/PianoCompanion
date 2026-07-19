package com.pianocompanion.melodiccontour

/**
 * 旋律轮廓辨识训练（Melodic Contour Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **旋律轮廓（Melodic Contour）**：一段由多个音符构成的旋律在音高上的整体「形状」。
 *   不关心具体是哪些音，只关心线条如何运动——是持续上行、持续下行、先上后下（拱形）、
 *   先下后上（谷形），还是上下交替（波浪）。
 * - 这是旋律听写和视唱的核心前置能力：能抓住旋律的「大形状」，才能在脑海中形成记忆框架，
 *   再填充细节音高。
 *
 * 与已有训练模块的区别：
 * - **旋律方向（[com.pianocompanion.melodicdirectiontraining]）**：判断**单个**音程的上行/下行/同音
 *   ——关注**一次**跳进的方向，答案是「上 / 下 / 同」。
 * - **旋律记忆（[com.pianocompanion.melodymemory]）**：听完后**复现**整段旋律的具体音高序列。
 * - **模进辨识（[com.pianocompanion.sequencetraining]）**：识别旋律是否以模进（移位重复）方式发展。
 * - **本模块（旋律轮廓）**：判断一段**多音符旋律的整体形状分类**（上行/下行/拱形/谷形/波浪），
 *   答案是「形状类别」。不要求记住具体音高，只要求抓住线条的宏观走势——是听写之前
 *   先建立「形状骨架」的能力。
 *
 * 训练流程：
 * 1. 播放一段由 4-6 个音符构成的短旋律。
 * 2. 用户聆听后，判断这段旋律的整体轮廓形状。
 * 3. 从「上行 / 下行 / 拱形 / 谷形 / 波浪」选项中选出正确答案。
 */

/**
 * 旋律轮廓类型。
 *
 * @param displayName 中文显示名
 * @param arrowPattern 箭头示意图（用于 UI 展示，如 "↑ ↑ ↓ ↓"）
 * @param description 听感描述（答题后的教学反馈）
 * @param hint 听辨提示
 */
enum class ContourType(
    val displayName: String,
    val arrowPattern: String,
    val description: String,
    val hint: String
) {
    /** 持续上行：所有相邻音程均向上。 */
    ASCENDING(
        displayName = "上行",
        arrowPattern = "↑ ↑ ↑",
        description = "旋律持续向上攀升，像爬楼梯一样越来越高。这是最直观的轮廓类型。",
        hint = "听感逐渐升高，每一个音都比前一个高。"
    ),

    /** 持续下行：所有相邻音程均向下。 */
    DESCENDING(
        displayName = "下行",
        arrowPattern = "↓ ↓ ↓",
        description = "旋律持续向下跌落，像下楼梯一样越来越低。与上行正好相反。",
        hint = "听感逐渐降低，每一个音都比前一个低。"
    ),

    /** 拱形：先上行后下行，最高点在中部。 */
    ARCH(
        displayName = "拱形",
        arrowPattern = "↑ ↑ ↓ ↓",
        description = "旋律先升高再降低，形成一个「拱」的形状，最高点出现在中间。这是最常见的旋律轮廓。",
        hint = "开头升高、中间到达顶点、然后回落——像一座山丘。"
    ),

    /** 谷形：先下行后上行，最低点在中部。 */
    VALLEY(
        displayName = "谷形",
        arrowPattern = "↓ ↓ ↑ ↑",
        description = "旋律先降低再升高，形成一个「谷」的形状，最低点出现在中间。与拱形正好相反。",
        hint = "开头降低、中间到达谷底、然后回升——像一个山谷。"
    ),

    /** 波浪：上下交替起伏，有多个转折点。 */
    WAVE(
        displayName = "波浪",
        arrowPattern = "↑ ↓ ↑ ↓",
        description = "旋律上下交替起伏，像波浪一样有多个高低转折点，没有单一的峰或谷。",
        hint = "高低交替出现，有多次转折，像水波纹。"
    );

    companion object {
        val ALL: List<ContourType> = entries.toList()
    }
}

/**
 * 难度等级。
 *
 * 难度通过**音符数、音程跨度、速度、候选轮廓种类**四个维度递进：
 * - 初级 4 个音符、宽音程（3-5 半音，方向变化鲜明）、慢速、4 选项。
 * - 中级 5 个音符、中等音程（2-4 半音）、中速、4 选项。
 * - 高级 6 个音符、窄音程（1-3 半音，方向变化微妙）、快速、5 选项（加入波浪）。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param noteCount 旋律音符数
 * @param contourOptions 本难度可能的轮廓类型集合（每题从中随机一种）
 * @param stepPool 相邻音程的候选半音数池（从中随机抽取）
 * @param noteDurationMs 每个音符的时长（毫秒），越短越快越难
 */
enum class ContourDifficulty(
    val displayName: String,
    val description: String,
    val noteCount: Int,
    val contourOptions: List<ContourType>,
    val stepPool: List<Int>,
    val noteDurationMs: Double
) {
    BEGINNER(
        displayName = "初级",
        description = "4 音符 · 宽音程（方向鲜明）· 慢速 · 4 选项",
        noteCount = 4,
        contourOptions = listOf(ContourType.ASCENDING, ContourType.DESCENDING, ContourType.ARCH, ContourType.VALLEY),
        stepPool = listOf(3, 4, 5),
        noteDurationMs = 550.0
    ),

    INTERMEDIATE(
        displayName = "中级",
        description = "5 音符 · 中等音程 · 中速 · 4 选项",
        noteCount = 5,
        contourOptions = listOf(ContourType.ASCENDING, ContourType.DESCENDING, ContourType.ARCH, ContourType.VALLEY),
        stepPool = listOf(2, 3, 4),
        noteDurationMs = 450.0
    ),

    ADVANCED(
        displayName = "高级",
        description = "6 音符 · 窄音程（方向微妙）· 快速 · 5 选项（含波浪）",
        noteCount = 6,
        contourOptions = listOf(
            ContourType.ASCENDING, ContourType.DESCENDING,
            ContourType.ARCH, ContourType.VALLEY, ContourType.WAVE
        ),
        stepPool = listOf(1, 2, 3),
        noteDurationMs = 370.0
    );

    companion object {
        val ALL: List<ContourDifficulty> = entries.toList()
    }
}

/**
 * 旋律轮廓辨识训练题目。
 *
 * @param difficulty 难度
 * @param contour 正确的轮廓类型
 * @param pitches MIDI 音高序列（与 [contour] 一致），已校验整体形状匹配
 * @param noteDurationMs 每个音符时长（毫秒）
 * @param answerChoices 所有选项（轮廓显示名，含正确答案）
 * @param correctAnswer 正确答案文本
 */
data class ContourQuestion(
    val difficulty: ContourDifficulty,
    val contour: ContourType,
    val pitches: List<Int>,
    val noteDurationMs: Double,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 音符数。 */
    val noteCount: Int get() = pitches.size

    /** 相邻音高方向序列（+1 上行 / -1 下行 / 0 同音）。 */
    val directions: List<Int>
        get() = pitches.zipWithNext { a, b -> b.compareTo(a) }

    /** 箭头可视化（基于实际音高方向，如 "↑ ↑ ↓ ↓"）。 */
    val arrowVisualization: String
        get() = directions.joinToString(" ") { d ->
            when {
                d > 0 -> "↑"
                d < 0 -> "↓"
                else -> "→"
            }
        }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${contour.displayName}（${contour.arrowPattern}）· ${noteCount} 音符 · " +
            "${"%.0f".format(noteDurationMs)}ms/音"

    init {
        require(pitches.size >= 2) { "旋律至少需要 2 个音符，实际 ${pitches.size}" }
        require(pitches.size == difficulty.noteCount) {
            "音符数 ${pitches.size} 与难度 ${difficulty.displayName} 要求的 ${difficulty.noteCount} 不符"
        }
        require(noteDurationMs > 0) { "音符时长必须为正数" }
        require(contour in difficulty.contourOptions) {
            "轮廓类型 ${contour.displayName} 不在 ${difficulty.displayName} 的候选集合中"
        }
        require(answerChoices.isNotEmpty()) { "选项不能为空" }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        // 校验生成的音高序列确实匹配所声称的轮廓类型
        val classified = classifyContour(pitches)
        require(classified == contour) {
            "音高序列的实际轮廓 $classified 与声称的 ${contour.displayName} 不匹配"
        }
    }
}

/**
 * 一次答题结果。
 */
data class ContourAnswerRecord(
    val question: ContourQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/**
 * 根据音高序列推断轮廓类型（纯函数）。
 *
 * 规则：
 * - 全部上行（所有方向 > 0）→ [ContourType.ASCENDING]
 * - 全部下行（所有方向 < 0）→ [ContourType.DESCENDING]
 * - 恰好一次「由升转降」的符号变化 → [ContourType.ARCH]（拱形，中部为峰）
 * - 恰好一次「由降转升」的符号变化 → [ContourType.VALLEY]（谷形，中部为谷）
 * - 多于一次符号变化 → [ContourType.WAVE]（波浪）
 */
fun classifyContour(pitches: List<Int>): ContourType {
    require(pitches.size >= 2) { "至少需要 2 个音高" }
    val signs = pitches.zipWithNext { a, b -> b.compareTo(a) }
        .map { when { it > 0 -> 1; it < 0 -> -1; else -> 0 } }

    // 忽略同音（0），只看升降的符号变化序列
    val nonZero = signs.filter { it != 0 }
    if (nonZero.isEmpty()) return ContourType.ASCENDING // 全同音，退化处理

    val allUp = nonZero.all { it > 0 }
    val allDown = nonZero.all { it < 0 }
    if (allUp) return ContourType.ASCENDING
    if (allDown) return ContourType.DESCENDING

    // 统计符号变化次数
    var signChanges = 0
    for (i in 1 until nonZero.size) {
        if (nonZero[i] != nonZero[i - 1]) signChanges++
    }

    return when {
        signChanges == 1 && nonZero.first() > 0 -> ContourType.ARCH   // 升→降 = 拱形
        signChanges == 1 && nonZero.first() < 0 -> ContourType.VALLEY // 降→升 = 谷形
        else -> ContourType.WAVE                                       // 多次变化 = 波浪
    }
}
