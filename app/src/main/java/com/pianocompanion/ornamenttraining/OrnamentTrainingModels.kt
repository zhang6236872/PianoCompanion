package com.pianocompanion.ornamenttraining

/**
 * 装饰音辨识训练（Ornament Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **装饰音（Ornament）**：用以美化主音的快速、短小的辅助音符组合。它们不改变旋律骨架，
 *   而是在主音周围「点缀」出华丽的色彩。辨识装饰音是听觉分析高级技巧——要求听者
 *   在极短时间内抓住音符的排列形态（颤动/点头/环绕/倚靠）。
 *
 * - **五种核心装饰音**：
 *   - **颤音（Trill, tr）**：主音与上方音快速交替多次，如鸟鸣般颤动不息。最具装饰性的华丽技巧。
 *   - **波音（Mordent, mo）**：主音快速向上方音一掠而后回到主音，如同轻轻「点头」。
 *   - **回音（Turn, tu）**：围绕主音转一圈——上方音、主音、下方音、再回主音，四音环绕如花环。
 *   - **短倚音（Acciaccatura, ♪→）**：极短的一记装饰音迅速滑入主音，主音长而稳，装饰音一闪而过。
 *   - **长倚音（Appoggiatura, app.）**：一个较长、带重音的倚音「倚靠」在主音上再解决，甜蜜延宕。
 *
 * - **训练流程**：播放一段装饰音，用户从选项中选择正确的装饰音类型。
 *
 * 难度分级：
 * - **初级**：颤音 vs 短倚音（2 选项）——区分「快速多次颤动」与「一闪而过的轻点」
 * - **中级**：颤音 + 波音 + 短倚音（3 选项）——增加「单次点头」识别
 * - **高级**：全部 5 种（5 选项）——增加「环绕回音」与「长倚音」识别
 */

/**
 * 装饰音中的一个音符事件。
 *
 * @param semitoneOffset 相对于主音的半音偏移（0 = 主音本身，+2 = 上方音，-2 = 下方音）
 * @param durationMs 该音符的持续时长（毫秒）
 * @param accent 重音强度（0.0 = 无重音，1.0 = 最强），用于模拟倚音的重音
 */
data class OrnamentNote(
    val semitoneOffset: Int,
    val durationMs: Long,
    val accent: Float = 0.0f
)

/**
 * 装饰音类型。
 *
 * @param displayName 中文显示名
 * @param symbol 记号符号（如 "tr", "mo", "tu"）
 * @param englishName 英文名
 * @param description 听感描述（用于答题后的教学反馈）
 * @param listeningHint 听辨提示（用于 UI 引导）
 * @param notes 该装饰音的音符序列（相对主音的半音偏移）
 */
enum class OrnamentType(
    val displayName: String,
    val symbol: String,
    val englishName: String,
    val description: String,
    val listeningHint: String,
    private val notes: List<OrnamentNote>
) {
    /**
     * 颤音（Trill）：主音与上方音（大二度）快速交替多次，最后落回主音。
     * 9 个音符的连续颤动，最具「华丽颤抖」听感。
     */
    TRILL(
        displayName = "颤音",
        symbol = "tr",
        englishName = "Trill",
        description = "颤音（tr）：主音与上方音快速交替弹奏，如鸟鸣般颤动不息，是最具装饰性的华丽技巧。",
        listeningHint = "一连串快速的上下交替，像「颤抖」或「打转」",
        notes = listOf(
            OrnamentNote(0, 85), OrnamentNote(2, 85), OrnamentNote(0, 85),
            OrnamentNote(2, 85), OrnamentNote(0, 85), OrnamentNote(2, 85),
            OrnamentNote(0, 85), OrnamentNote(2, 85), OrnamentNote(0, 130)
        )
    ),

    /**
     * 波音（Mordent）：主音快速向上方音一掠而后回到主音。单次「点头」。
     */
    MORDENT(
        displayName = "波音",
        symbol = "mo",
        englishName = "Mordent",
        description = "波音（mo）：主音快速向上方音一掠而后回到主音，如同轻轻一「点头」的装饰。",
        listeningHint = "只「点」一下头——快速上跳再立刻回来",
        notes = listOf(
            OrnamentNote(0, 160), OrnamentNote(2, 110), OrnamentNote(0, 220)
        )
    ),

    /**
     * 回音（Turn）：围绕主音转一圈——上方音、主音、下方音、再回主音。
     */
    TURN(
        displayName = "回音",
        symbol = "tu",
        englishName = "Turn",
        description = "回音（tu）：围绕主音转一圈——上方音、主音、下方音、再回主音，四音环绕如「花环」。",
        listeningHint = "绕主音「转一圈」——上、主、下、主",
        notes = listOf(
            OrnamentNote(2, 135), OrnamentNote(0, 135), OrnamentNote(-2, 135), OrnamentNote(0, 180)
        )
    ),

    /**
     * 短倚音（Acciaccatura）：极短的装饰音（约 65ms）迅速滑入长主音（约 720ms）。
     * 装饰音一闪而过，主音长而稳。
     */
    GRACE_NOTE(
        displayName = "短倚音",
        symbol = "♪→",
        englishName = "Acciaccatura",
        description = "短倚音（acciaccatura）：极短的一记装饰音迅速「滑」入主音，主音长而稳，装饰音一闪而过。",
        listeningHint = "一记极短的「轻点」紧接一个长音",
        notes = listOf(
            OrnamentNote(2, 65, accent = 0.35f), OrnamentNote(0, 720)
        )
    ),

    /**
     * 长倚音（Appoggiatura）：较长、带重音的倚音（约 360ms）「倚靠」后解决到主音（约 420ms）。
     */
    APPOGGIATURA(
        displayName = "长倚音",
        symbol = "app.",
        englishName = "Appoggiatura",
        description = "长倚音（appoggiatura）：一个较长、带重音的倚音「倚靠」在主音上再解决，产生甜蜜的延宕感。",
        listeningHint = "一个较长的重音「倚靠」后解决到主音",
        notes = listOf(
            OrnamentNote(2, 360, accent = 0.55f), OrnamentNote(0, 420)
        )
    );

    /** 该装饰音的音符数量。 */
    val noteCount: Int get() = notes.size

    /** 该装饰音音符序列的总时长（毫秒）。 */
    val totalDurationMs: Long get() = notes.sumOf { it.durationMs }

    /** 返回该装饰音的音符序列（相对主音的半音偏移）。 */
    fun noteSequence(): List<OrnamentNote> = notes

    companion object {
        /** 所有装饰音类型。 */
        val ALL: List<OrnamentType> = entries.toList()

        /**
         * 按难度返回可用的装饰音类型集合。
         * - 初级：颤音 vs 短倚音（最易区分的两种——多次颤动 vs 单次轻点）
         * - 中级：颤音 + 波音 + 短倚音（增加单次「点头」识别）
         * - 高级：全部 5 种（增加环绕回音与长倚音识别）
         */
        fun forDifficulty(difficulty: OrnamentDifficulty): List<OrnamentType> = when (difficulty) {
            OrnamentDifficulty.BEGINNER -> listOf(TRILL, GRACE_NOTE)
            OrnamentDifficulty.INTERMEDIATE -> listOf(TRILL, MORDENT, GRACE_NOTE)
            OrnamentDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量）
 */
enum class OrnamentDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "颤音 vs 短倚音（2 选项）"),
    INTERMEDIATE("中级", "颤音 + 波音 + 短倚音（3 选项）"),
    ADVANCED("高级", "全部 5 种装饰音（5 选项）");

    companion object {
        val ALL: List<OrnamentDifficulty> = entries.toList()
    }
}

/**
 * 装饰音辨识训练题目。
 *
 * @param type 正确的装饰音类型
 * @param mainMidi 主音 MIDI 音符号
 * @param mainNoteName 主音名（如 "C5", "G5"）
 * @param difficulty 难度
 * @param noteEvents 装饰音音符序列（相对主音的半音偏移）
 * @param answerChoices 所有选项（装饰音类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（装饰音类型显示名）
 */
data class OrnamentQuestion(
    val type: OrnamentType,
    val mainMidi: Int,
    val mainNoteName: String,
    val difficulty: OrnamentDifficulty,
    val noteEvents: List<OrnamentNote>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(noteEvents.isNotEmpty()) { "装饰音音符序列不能为空" }
        require(mainMidi in MIN_MIDI..MAX_MIDI) { "主音 MIDI 超出钢琴范围: $mainMidi" }
    }

    /** 完整描述（如 "C5 上的颤音"）。 */
    val fullDescription: String
        get() = "$mainNoteName 上的${type.displayName}"

    /** 装饰音音符数。 */
    val ornamentNoteCount: Int get() = noteEvents.size

    /** 音符序列的总时长（毫秒）。 */
    val sequenceDurationMs: Long get() = noteEvents.sumOf { it.durationMs }
}

/**
 * 一次答题结果。
 */
data class OrnamentAnswerRecord(
    val question: OrnamentQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
private const val MIN_MIDI = 21
private const val MAX_MIDI = 108
