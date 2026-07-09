package com.pianocompanion.progressiontraining

/**
 * 和弦进行听辨训练（Chord Progression Ear Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和弦进行（Chord Progression）**：多个和弦按时间顺序依次弹奏形成的和声运动。
 *   不同的进行拥有截然不同的情感色彩和风格——流行万能进行明亮昂扬、
 *   爵士 ii-V-I 圆润典雅、Doo-Wop 进行怀旧浪漫。
 *
 * - **训练流程**：依次播放一组和弦（每个和弦都是柱式和弦，多个和弦按时间排列），
 *   用户凭听觉判断这是哪种和弦进行。关键听觉线索是和声的整体走向、
 *   「张力-释放」模式以及根音运动的「距离感」。
 *
 * 难度分级：
 * - **初级**：3 种进行（3 选项）——最基础的流行/古典进行
 * - **中级**：4 种进行（4 选项）——增加循环进行
 * - **高级**：5 种进行（5 选项）——增加爵士回转
 */

/**
 * 大调音阶调内音级（罗马数字标记）。
 *
 * 在 C 大调中的具体和弦：I=C, ii=Dm, iii=Em, IV=F, V=G, vi=Am, vii°=Bdim
 *
 * @param romanNumeral 罗马数字标记（大写=大三和弦，小写=小三和弦，°=减三和弦）
 * @param semitoneFromTonic 在大调音阶中距主音的半音数
 * @param isMajor 是否大三和弦
 * @param isDiminished 是否减三和弦
 */
enum class DiatonicDegree(
    val romanNumeral: String,
    val semitoneFromTonic: Int,
    val isMajor: Boolean,
    val isDiminished: Boolean
) {
    I("I", 0, true, false),
    II("ii", 2, false, false),
    III("iii", 4, false, false),
    IV("IV", 5, true, false),
    V("V", 7, true, false),
    VI("vi", 9, false, false),
    VII("vii°", 11, false, true);

    /**
     * 返回该音级三和弦的音程结构 [根音偏移, 三音偏移, 五音偏移]。
     * - 大三和弦：[0, 4, 7]
     * - 小三和弦：[0, 3, 7]
     * - 减三和弦：[0, 3, 6]
     */
    fun chordIntervals(): List<Int> = when {
        isDiminished -> listOf(0, 3, 6)
        isMajor -> listOf(0, 4, 7)
        else -> listOf(0, 3, 7)
    }
}

/**
 * 和弦进行类型。
 *
 * 每种进行由一列调内音级定义。在 C 大调中的示例和弦：
 *
 * @param displayName 中文显示名
 * @param englishName 英文名
 * @param romanNumerals 罗马数字标记（如 "I-IV-V-I"）
 * @param degrees 进行中的调内音级序列
 * @param description 听感描述（用于答题后的教学反馈）
 * @param style 音乐风格标签（用于分类教学）
 */
enum class ProgressionType(
    val displayName: String,
    val englishName: String,
    val romanNumerals: String,
    val degrees: List<DiatonicDegree>,
    val description: String,
    val style: String
) {
    /** I-IV-V-I：最常见的古典/流行进行。下属→属→主，层层推进最终解决。 */
    CLASSIC(
        displayName = "I-IV-V-I 经典进行",
        englishName = "Classic I-IV-V-I",
        romanNumerals = "I-IV-V-I",
        degrees = listOf(DiatonicDegree.I, DiatonicDegree.IV, DiatonicDegree.V, DiatonicDegree.I),
        description = "下属→属→主，最经典的功能进行。从稳定出发，经下属扩展，属和弦制造张力，最终完美解决回主和弦。听感层层推进、充满「出发-冒险-回家」的故事感。",
        style = "古典/流行"
    ),

    /** I-V-vi-IV：流行歌曲万能进行（Adele、Bon Jovi 等）。明亮昂扬。 */
    POP_ANTHEM(
        displayName = "I-V-vi-IV 流行万能",
        englishName = "Pop Anthem I-V-vi-IV",
        romanNumerals = "I-V-vi-IV",
        degrees = listOf(DiatonicDegree.I, DiatonicDegree.V, DiatonicDegree.VI, DiatonicDegree.IV),
        description = "当代流行音乐最常用的进行之一（Adele《Someone Like You》、Bon Jovi《It's My Life》）。从主和弦出发，经属和弦上行到小调色彩的 vi，再落到下属，形成不断循环的上行波浪。听感明亮、昂扬、充满力量。",
        style = "流行"
    ),

    /** I-vi-IV-V：50 年代 Doo-Wop 进行。怀旧浪漫。 */
    DOO_WOP(
        displayName = "I-vi-IV-V Doo-Wop",
        englishName = "50s Doo-Wop I-vi-IV-V",
        romanNumerals = "I-vi-IV-V",
        degrees = listOf(DiatonicDegree.I, DiatonicDegree.VI, DiatonicDegree.IV, DiatonicDegree.V),
        description = "50 年代经典进行（Stand By Me、Earth Angel）。主和弦后立即转入小调色彩的 vi（产生温柔的情感波动），然后经下属上行到属和弦，形成怀旧浪漫的听感。这种进行在无数经典老歌中被反复使用。",
        style = "怀旧/Doo-Wop"
    ),

    /** vi-IV-I-V：流行循环进行（循环播放时不分首尾）。 */
    POP_LOOP(
        displayName = "vi-IV-I-V 流行循环",
        englishName = "Pop Loop vi-IV-I-V",
        romanNumerals = "vi-IV-I-V",
        degrees = listOf(DiatonicDegree.VI, DiatonicDegree.IV, DiatonicDegree.I, DiatonicDegree.V),
        description = "从小调和弦 vi 开始的循环进行。因为从小调色彩起步，整体听感比 I 开头的进行更忧郁、更深沉。 endlessly loopable——循环播放时分不清首尾。常见于许多 2000 年代流行歌曲。",
        style = "流行"
    ),

    /** ii-V-I：爵士回转（3 和弦）。圆润典雅，充满爵士色彩。 */
    JAZZ_TURNAROUND(
        displayName = "ii-V-I 爵士回转",
        englishName = "Jazz ii-V-I",
        romanNumerals = "ii-V-I",
        degrees = listOf(DiatonicDegree.II, DiatonicDegree.V, DiatonicDegree.I),
        description = "爵士乐最核心的和声进行。从小调 ii 出发（柔和的下属功能），经属和弦 V（制造张力），解决到主和弦 I。整个进行仅有 3 个和弦却充满色彩变化——这是爵士即兴的基础。听感圆润、优雅、带有微妙的忧郁色彩。",
        style = "爵士"
    );

    companion object {
        val ALL: List<ProgressionType> = entries.toList()

        /**
         * 按难度返回可用的进行类型集合。
         * - 初级：3 种基础进行（经典/流行万能/Doo-Wop）
         * - 中级：+流行循环（4 种）
         * - 高级：+爵士回转（5 种）
         */
        fun forDifficulty(difficulty: ProgressionDifficulty): List<ProgressionType> = when (difficulty) {
            ProgressionDifficulty.BEGINNER -> listOf(CLASSIC, POP_ANTHEM, DOO_WOP)
            ProgressionDifficulty.INTERMEDIATE -> listOf(CLASSIC, POP_ANTHEM, DOO_WOP, POP_LOOP)
            ProgressionDifficulty.ADVANCED -> ALL
        }
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明（含选项数量与进行风格）
 */
enum class ProgressionDifficulty(val displayName: String, val description: String) {
    BEGINNER("初级", "3 种进行（3 选项）· 古典/流行/Doo-Wop"),
    INTERMEDIATE("中级", "4 种进行（4 选项）· + 流行循环"),
    ADVANCED("高级", "5 种进行（5 选项）· + 爵士回转");

    companion object {
        val ALL: List<ProgressionDifficulty> = entries.toList()
    }
}

/**
 * 和弦进行听辨训练题目。
 *
 * @param type 正确的进行类型
 * @param tonicMidi 主音 MIDI 音符号（决定调性）
 * @param tonicName 主音名（如 "C", "G"）
 * @param difficulty 难度
 * @param chordProgression 各和弦的 MIDI 音符号列表（每个和弦是一个柱式三和弦）
 * @param answerChoices 所有选项（进行类型显示名，含正确答案，已打乱）
 * @param correctAnswer 正确答案（进行类型显示名）
 */
data class ProgressionQuestion(
    val type: ProgressionType,
    val tonicMidi: Int,
    val tonicName: String,
    val difficulty: ProgressionDifficulty,
    val chordProgression: List<List<Int>>,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(chordProgression.isNotEmpty()) { "和弦进行不能为空" }
        require(chordProgression.all { it.size == 3 }) { "每个和弦必须有 3 个音符" }
        require(chordProgression.flatten().all { it in MIN_MIDI..MAX_MIDI }) { "MIDI 音符超出钢琴范围" }
    }

    /** 和弦数量。 */
    val chordCount: Int get() = chordProgression.size

    /** 完整描述（如 "C 大调 · I-IV-V-I 经典进行"）。 */
    val fullDescription: String
        get() = "$tonicName 大调 · ${type.romanNumerals}（${type.displayName}）"

    /** 进行风格描述。 */
    val styleDescription: String get() = "风格：${type.style}"
}

/**
 * 一次答题结果。
 */
data class ProgressionAnswerRecord(
    val question: ProgressionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswerOrNull: String? get() = if (isCorrect) null else question.correctAnswer
}

/** 钢琴 MIDI 音域常量。 */
const val MIN_MIDI = 21
const val MAX_MIDI = 108
