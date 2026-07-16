package com.pianocompanion.nonchordtonetraining

/**
 * 和弦外音辨识训练（Non-Chord Tone / Embellishing Tone Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和弦外音（Non-Chord Tone / Non-Harmonic Tone / Embellishing Tone）**：旋律中出现
 *   的、不属于当前正在响起的和弦的音。它在瞬间产生不协和（紧张感），然后解决（释放）到
 *   和弦音上。和弦外音是旋律装饰、声部进行与表情的核心手法，辨识它们是理解旋律与和声
 *   关系的关键听力技能。
 *
 * - **与「非调内音」([com.pianocompanion.nonscaletonetraining]) 的区别**：
 *   - 非调内音 = 不属于当前**调性/音阶**的音（如 C 大调中的 #C），属于调外半音。
 *   - 和弦外音 = 可能**仍在调内**，只是不属于当前**和弦**的音（如 C 大调 I 和弦 C-E-G
 *     旋律经过的 D、F、A、B）。本训练的和弦外音全部为调内音，强调「和弦音 vs 和弦外音」
 *     的瞬间色彩变化与解决倾向。
 *
 * - **四种基本和弦外音**（按旋律的「接近方式」与「解决方式」分类）：
 *   - 接近/解决各只有两种可能：**级进（step，1~2 个半音）** 或 **跳进（leap，≥3 个半音）**。
 *   - 由此唯一确定四种类型：
 *     1. **经过音（Passing Tone）**：级进接近 + 级进解决，**同向**——在两个和弦音之间
 *        平滑连接（如 C→D→E）。
 *     2. **辅助音/邻音（Neighbor Tone）**：级进接近 + 级进解决，**反向**（回到原音）——
 *        在和弦音上方或下方「摆动」装饰（如 E→F→E）。
 *     3. **倚音（Appoggiatura）**：跳进接近 + 级进解决——戏剧性地「跳」到和弦外音，
 *        再柔和地级进解决，表情性强（如 C→A→G）。
 *     4. **逃逸音（Escape Tone / Échappée）**：级进接近 + 跳进解决——柔和地级进到和弦
 *        外音，再戏剧性地「跳」走（如 E→F→C）。
 *
 * 本训练在和弦持续响起的背景下播放 3 音旋律，用户辨识中间的和弦外音属于哪种类型——
 * 即辨识旋律的「接近 + 解决」轮廓是级进还是跳进。
 */

/** 判定一个音程（半音数）是否为级进（1~2 个半音）。 */
fun isStep(semitones: Int): Boolean = abs(semitones) in 1..2

/** 判定一个音程（半音数）是否为跳进（≥3 个半音）。 */
fun isLeap(semitones: Int): Boolean = abs(semitones) >= 3

/**
 * 根据三音旋律轮廓（模板）反推其和弦外音类型。
 *
 * @param template 三个相对半音数（相对根音），如 [0, 2, 4] = C→D→E
 * @return 推断出的类型；若轮廓不符合任何标准类型则返回 null
 */
fun classifyContour(template: List<Int>): NonChordToneType? {
    if (template.size != 3) return null
    val approach = template[1] - template[0]
    val resolution = template[2] - template[1]
    return when {
        isStep(approach) && isStep(resolution) && (approach > 0) == (resolution > 0) ->
            NonChordToneType.PASSING_TONE
        isStep(approach) && isStep(resolution) ->
            NonChordToneType.NEIGHBOR_TONE
        isLeap(approach) && isStep(resolution) ->
            NonChordToneType.APPOGGIATURA
        isStep(approach) && isLeap(resolution) ->
            NonChordToneType.ESCAPE_TONE
        else -> null
    }
}

/**
 * 和弦外音类型（辨识目标）。
 *
 * @param englishName 英文名
 * @param displayName 中文名
 * @param emoji 表情符号
 * @param contour 轮廓描述（接近 + 解决方式）
 * @param description 类别描述（答题后的教学反馈）
 * @param hint 听辨提示
 * @param templates 3 音旋律模板（相对根音的半音数列表），每个模板的中间音为该类型的和弦外音，
 *   两端为和弦音。模板在和弦音 {0,4,7}（C 大三和弦根三五音）下设计。
 */
enum class NonChordToneType(
    val englishName: String,
    val displayName: String,
    val emoji: String,
    val contour: String,
    val description: String,
    val hint: String,
    val templates: List<List<Int>>
) {
    PASSING_TONE(
        englishName = "Passing Tone",
        displayName = "经过音",
        emoji = "➡️",
        contour = "级进 + 级进（同向）",
        description = "经过音（Passing Tone）：以级进接近、级进离开，且方向相同——在两个和弦音之间平滑地「经过」。" +
            "声音像是把两个和弦音用一条平滑的线连接起来，没有明显的「跳」或「摆动」。",
        hint = "三个音像走台阶一样，朝同一方向匀速滑动",
        templates = listOf(
            listOf(0, 2, 4),  // C→D→E（上行）
            listOf(4, 2, 0),  // E→D→C（下行）
            listOf(4, 5, 7),  // E→F→G（上行）
            listOf(7, 5, 4)   // G→F→E（下行）
        )
    ),
    NEIGHBOR_TONE(
        englishName = "Neighbor Tone",
        displayName = "辅助音",
        emoji = "↔️",
        contour = "级进 + 级进（反向，回到原音）",
        description = "辅助音/邻音（Neighbor Tone）：以级进接近、级进离开，但方向相反——离开后又级进回到原来的音。" +
            "声音像在和弦音的上方或下方轻轻「摆动」一下再回来，有装饰、点缀的感觉。",
        hint = "上去（或下去）一步，又立刻回到原音，像「点头」",
        templates = listOf(
            listOf(4, 5, 4),  // E→F→E（上辅助音）
            listOf(4, 2, 4),  // E→D→E（下辅助音）
            listOf(0, 2, 0),  // C→D→C（上辅助音）
            listOf(7, 9, 7)   // G→A→G（上辅助音）
        )
    ),
    APPOGGIATURA(
        englishName = "Appoggiatura",
        displayName = "倚音",
        emoji = "🎯",
        contour = "跳进 + 级进",
        description = "倚音（Appoggiatura）：以跳进接近、级进解决——戏剧性地「跳」到一个和弦外音，制造强烈的紧张感，" +
            "再柔和地级进解决。这是最具表情、最富戏剧性的和弦外音，常出现在歌唱性旋律的高点。",
        hint = "突然「跳」上来一个刺耳的音，然后柔和地级进滑下去",
        templates = listOf(
            listOf(0, 9, 7),  // C→A→G（上行跳进，级进解决）
            listOf(0, 11, 12),// C→B→C（大七度跳进，级进解决）
            listOf(4, 9, 7),  // E→A→G
            listOf(7, 2, 4)   // G→D→E（下行跳进，级进解决）
        )
    ),
    ESCAPE_TONE(
        englishName = "Escape Tone",
        displayName = "逃逸音",
        emoji = "🏃",
        contour = "级进 + 跳进",
        description = "逃逸音/跳助音（Escape Tone / Échappée）：以级进接近、跳进离开——柔和地级进到一个和弦外音，" +
            "然后突然「跳」走解决。与倚音相反：倚音是跳进而来，逃逸音是跳进而去。",
        hint = "级进上来一个不协和音，然后突然「跳」走，不再回头",
        templates = listOf(
            listOf(0, 2, 7),  // C→D→G（级进，上行跳进）
            listOf(4, 5, 0),  // E→F→C（级进，下行跳进）
            listOf(7, 9, 12), // G→A→C（级进，上行跳进）
            listOf(4, 2, 7)   // E→D→G（级进，上行跳进）
        )
    );

    /** 完整标识（如 \"经过音 (Passing Tone)\"）。 */
    val fullLabel: String get() = "$displayName ($englishName)"

    companion object {
        val ALL: List<NonChordToneType> = entries.toList()

        /** 初级难度的 2 种类型（级进经过 vs 跳进倚音，对比最鲜明）。 */
        val BEGINNER_TYPES: List<NonChordToneType> = listOf(PASSING_TONE, APPOGGIATURA)

        /** 中级难度的 3 种类型（加入辅助音）。 */
        val INTERMEDIATE_TYPES: List<NonChordToneType> =
            listOf(PASSING_TONE, NEIGHBOR_TONE, APPOGGIATURA)
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param types 该难度可能出现的和弦外音类型集合
 */
enum class NonChordToneDifficulty(
    val displayName: String,
    val description: String,
    val types: List<NonChordToneType>
) {
    BEGINNER(
        "初级",
        "经过音 vs 倚音 · 2 选项（级进经过 vs 跳进倚音）",
        NonChordToneType.BEGINNER_TYPES
    ),
    INTERMEDIATE(
        "中级",
        "经过音 / 辅助音 / 倚音 · 3 选项（加入辅助音摆动）",
        NonChordToneType.INTERMEDIATE_TYPES
    ),
    ADVANCED(
        "高级",
        "经过音 / 辅助音 / 倚音 / 逃逸音 · 4 选项（全 4 种和弦外音）",
        NonChordToneType.ALL
    );

    companion object {
        val ALL: List<NonChordToneDifficulty> = entries.toList()
    }
}

/**
 * 和弦外音辨识训练题目。
 *
 * @param type 正确的和弦外音类型
 * @param template 选中的 3 音旋律模板（相对根音半音数）
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染）
 * @param rootMidi 根音 MIDI 音高（旋律基准，默认 C4=60）
 * @param answerChoices 所有选项（类型完整标签，含正确答案，已打乱）
 * @param correctAnswer 正确答案
 */
data class NonChordToneQuestion(
    val type: NonChordToneType,
    val template: List<Int>,
    val difficulty: NonChordToneDifficulty,
    val seed: Long,
    val rootMidi: Int,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    /** 旋律的三个 MIDI 音高（相对根音偏移后）。 */
    val melodyMidi: List<Int> get() = template.map { rootMidi + it }

    /** 完整描述。 */
    val fullDescription: String
        get() = "${type.displayName}（${type.englishName}）· ${type.contour}"
}

/**
 * 一次答题结果。
 */
data class NonChordToneAnswerRecord(
    val question: NonChordToneQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 取绝对值（顶层辅助，避免在多处 import kotlin.math）。 */
internal fun abs(v: Int): Int = if (v < 0) -v else v
