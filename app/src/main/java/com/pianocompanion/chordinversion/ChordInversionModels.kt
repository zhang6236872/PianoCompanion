package com.pianocompanion.chordinversion

/**
 * 和弦转位听辨训练（Chord Inversion Recognition Training）数据模型。
 *
 * 纯 Kotlin（无 Android 依赖），完全可单元测试。
 *
 * 核心概念：
 * - **和弦转位（Chord Inversion）**：和弦的根音以外的其他音位于最低声部（bass）时，
 *   和弦即处于转位状态。转位类型由低音是哪个和弦成员决定：
 *   - **原位（Root Position）**：根音在低音 → 听感最稳定、最完整
 *   - **第一转位（First Inversion / 六和弦）**：三音在低音 → 略不稳定但仍协和
 *   - **第二转位（Second Inversion / 四六和弦）**：五音在低音 → 悬浮感、需要解决
 *   - **第三转位（Third Inversion / 二和弦，仅七和弦）**：七音在低音 → 强烈的不稳定感
 *
 * 训练流程：
 * 1. 听一段同时发响的和弦（柱式和弦 block chord）
 * 2. 判断这段和弦处于哪种转位（从选项中选出）
 *
 * **听辨线索**：
 * - 专注于**最低音**（bass note）的音高
 * - 原位和弦低沉稳定；转位和弦低音偏高，整体色彩变化
 * - 第二转位有特有的「悬浮」感（四度音程在低音）
 */
enum class ChordType(
    val displayName: String,
    val category: String,
    val description: String,
    val intervals: List<Int>,
    val memberNames: List<String>
) {
    MAJOR_TRIAD(
        displayName = "大三和弦",
        category = "三和弦",
        description = "明亮稳定 · 根音-大三度-纯五度",
        intervals = listOf(0, 4, 7),
        memberNames = listOf("根音", "三音", "五音")
    ),
    MINOR_TRIAD(
        displayName = "小三和弦",
        category = "三和弦",
        description = "柔和暗淡 · 根音-小三度-纯五度",
        intervals = listOf(0, 3, 7),
        memberNames = listOf("根音", "三音", "五音")
    ),
    DIMINISHED_TRIAD(
        displayName = "减三和弦",
        category = "三和弦",
        description = "紧张不安 · 根音-小三度-减五度",
        intervals = listOf(0, 3, 6),
        memberNames = listOf("根音", "三音", "五音")
    ),
    AUGMENTED_TRIAD(
        displayName = "增三和弦",
        category = "三和弦",
        description = "悬浮扩张 · 根音-大三度-增五度",
        intervals = listOf(0, 4, 8),
        memberNames = listOf("根音", "三音", "五音")
    ),
    DOMINANT_SEVENTH(
        displayName = "属七和弦",
        category = "七和弦",
        description = "需要解决 · 根音-大三度-纯五度-小七度",
        intervals = listOf(0, 4, 7, 10),
        memberNames = listOf("根音", "三音", "五音", "七音")
    ),
    MAJOR_SEVENTH(
        displayName = "大七和弦",
        category = "七和弦",
        description = "柔和丰富 · 根音-大三度-纯五度-大七度",
        intervals = listOf(0, 4, 7, 11),
        memberNames = listOf("根音", "三音", "五音", "七音")
    );

    /** 和弦成员数量（三和弦=3，七和弦=4）。 */
    val memberCount: Int get() = intervals.size

    /** 最大转位数（三和弦=2 即第二转位，七和弦=3 即第三转位）。 */
    val maxInversionOrder: Int get() = intervals.size - 1

    /** 完整标签。 */
    val fullLabel: String get() = "$displayName（$category）"

    /** 教学描述。 */
    val teachingDescription: String get() = "$displayName · $description · ${memberCount}个音"

    companion object {
        /** 初级难度使用的和弦（仅大三和弦）。 */
        val BEGINNER_CHORDS: List<ChordType> = listOf(MAJOR_TRIAD)

        /** 中级难度使用的和弦（大、小三和弦）。 */
        val INTERMEDIATE_CHORDS: List<ChordType> = listOf(MAJOR_TRIAD, MINOR_TRIAD)

        /** 高级难度使用的和弦（全部和弦类型，含七和弦）。 */
        val ADVANCED_CHORDS: List<ChordType> = listOf(
            MAJOR_TRIAD, MINOR_TRIAD, DIMINISHED_TRIAD, AUGMENTED_TRIAD,
            DOMINANT_SEVENTH, MAJOR_SEVENTH
        )
    }
}

/**
 * 和弦转位类型。
 *
 * @param order 转位序号（0=原位, 1=第一转位, 2=第二转位, 3=第三转位）
 * @param displayName 中文显示名
 * @param traditionalName 传统和声学名称（如「六和弦」「四六和弦」）
 * @param bassMember 低音声部对应的和弦成员名
 * @param description 教学描述
 */
enum class ChordInversion(
    val order: Int,
    val displayName: String,
    val traditionalName: String,
    val bassMember: String,
    val description: String
) {
    ROOT_POSITION(
        order = 0,
        displayName = "原位",
        traditionalName = "原位",
        bassMember = "根音",
        description = "根音在最低声部 · 听感最稳定完整"
    ),
    FIRST_INVERSION(
        order = 1,
        displayName = "第一转位",
        traditionalName = "六和弦",
        bassMember = "三音",
        description = "三音在最低声部 · 略不稳定但仍协和"
    ),
    SECOND_INVERSION(
        order = 2,
        displayName = "第二转位",
        traditionalName = "四六和弦",
        bassMember = "五音",
        description = "五音在最低声部 · 悬浮感、需解决"
    ),
    THIRD_INVERSION(
        order = 3,
        displayName = "第三转位",
        traditionalName = "二和弦",
        bassMember = "七音",
        description = "七音在最低声部（仅七和弦）· 强烈不稳定"
    );

    /** 完整显示名（含传统名称）。 */
    val fullDisplayName: String
        get() = if (traditionalName == displayName) displayName else "$displayName（$traditionalName）"

    /** 选项显示文本。 */
    val choiceLabel: String get() = "$displayName（${bassMember}在低音）"

    companion object {
        /** 根据序号获取转位类型。 */
        fun forOrder(order: Int): ChordInversion =
            entries.firstOrNull { it.order == order } ?: ROOT_POSITION

        /** 三和弦可用的转位（原位、第一、第二）。 */
        val TRIAD_INVERSIONS: List<ChordInversion> = listOf(ROOT_POSITION, FIRST_INVERSION, SECOND_INVERSION)

        /** 七和弦可用的转位（含第三转位）。 */
        val SEVENTH_INVERSIONS: List<ChordInversion> =
            listOf(ROOT_POSITION, FIRST_INVERSION, SECOND_INVERSION, THIRD_INVERSION)
    }
}

/**
 * 难度等级。
 *
 * @param displayName 中文显示名
 * @param description 该难度的说明
 * @param choiceCount 选项数量
 * @param chords 该难度使用的和弦集合
 * @param inversionOptions 该难度显示的转位选项集合
 * @param chordDurationMs 和弦播放时长（毫秒）
 * @param rootMidiMin 根音 MIDI 最小值（含）
 * @param rootMidiMax 根音 MIDI 最大值（含）
 */
enum class ChordInversionDifficulty(
    val displayName: String,
    val description: String,
    val choiceCount: Int,
    val chords: List<ChordType>,
    val inversionOptions: List<ChordInversion>,
    val chordDurationMs: Int,
    val rootMidiMin: Int,
    val rootMidiMax: Int
) {
    BEGINNER(
        displayName = "初级",
        description = "大三和弦 · 原位 vs 第一转位 · 2选项",
        choiceCount = 2,
        chords = ChordType.BEGINNER_CHORDS,
        inversionOptions = listOf(ChordInversion.ROOT_POSITION, ChordInversion.FIRST_INVERSION),
        chordDurationMs = 1300,
        rootMidiMin = 57,   // A3
        rootMidiMax = 62    // D4
    ),
    INTERMEDIATE(
        displayName = "中级",
        description = "大三/小三和弦 · 原位/第一/第二转位 · 3选项",
        choiceCount = 3,
        chords = ChordType.INTERMEDIATE_CHORDS,
        inversionOptions = listOf(
            ChordInversion.ROOT_POSITION, ChordInversion.FIRST_INVERSION, ChordInversion.SECOND_INVERSION
        ),
        chordDurationMs = 1100,
        rootMidiMin = 55,   // G3
        rootMidiMax = 62    // D4
    ),
    ADVANCED(
        displayName = "高级",
        description = "全部和弦+七和弦 · 原位/第一/第二/第三转位 · 4选项",
        choiceCount = 4,
        chords = ChordType.ADVANCED_CHORDS,
        inversionOptions = ChordInversion.SEVENTH_INVERSIONS,
        chordDurationMs = 950,
        rootMidiMin = 53,   // F3
        rootMidiMax = 62    // D4
    );

    companion object {
        val ALL: List<ChordInversionDifficulty> = entries.toList()
    }
}

/**
 * 和弦转位听辨训练题目。
 *
 * @param difficulty 难度
 * @param seed 生成种子（用于音频确定性渲染与测试复现）
 * @param rootMidi 根音 MIDI 音高
 * @param chordType 和弦类型
 * @param targetInversion 正确的转位类型
 * @param answerChoices 所有选项标签（含正确答案，已打乱）
 * @param correctAnswer 正确答案标签
 */
data class ChordInversionQuestion(
    val difficulty: ChordInversionDifficulty,
    val seed: Long,
    val rootMidi: Int,
    val chordType: ChordType,
    val targetInversion: ChordInversion,
    val answerChoices: List<String>,
    val correctAnswer: String
) {
    init {
        require(chordType in difficulty.chords) {
            "目标和弦 ${chordType.displayName} 不在难度 ${difficulty.displayName} 的和弦集合中"
        }
        require(targetInversion.order <= chordType.maxInversionOrder) {
            "转位 ${targetInversion.displayName} 不适用于 ${chordType.displayName}" +
                "（最大转位序号=${chordType.maxInversionOrder}）"
        }
        require(targetInversion in difficulty.inversionOptions) {
            "转位 ${targetInversion.displayName} 不在难度 ${difficulty.displayName} 的选项中"
        }
        require(correctAnswer in answerChoices) { "正确答案不在选项中" }
        require(answerChoices.distinct().size == answerChoices.size) { "选项存在重复" }
        require(answerChoices.size == difficulty.choiceCount) {
            "选项数 (${answerChoices.size}) 与难度配置 (${difficulty.choiceCount}) 不一致"
        }
        require(rootMidi in difficulty.rootMidiMin..difficulty.rootMidiMax) {
            "根音 MIDI $rootMidi 不在难度 ${difficulty.displayName} 的范围" +
                "[${difficulty.rootMidiMin}, ${difficulty.rootMidiMax}] 中"
        }
    }

    /** 完整描述（用于教学反馈）。 */
    val fullDescription: String
        get() = "${chordType.teachingDescription} · ${targetInversion.fullDisplayName} · ${targetInversion.description}"

    /** 和弦播放时长。 */
    val chordDurationMs: Int get() = difficulty.chordDurationMs

    /**
     * 计算该题目和弦的实际 MIDI 音符序列（从低到高排列）。
     *
     * 转位通过旋转和弦成员的音程实现：将 inv 个低音成员移到高八度。
     */
    val midiNotes: List<Int>
        get() {
            val intervals = chordType.intervals
            val inv = targetInversion.order
            val rotated = intervals.drop(inv) + intervals.take(inv).map { it + 12 }
            return rotated.map { rootMidi + it }
        }

    /** 低音音符的 MIDI 值（最低音）。 */
    val bassMidi: Int get() = midiNotes.minOrNull() ?: rootMidi

    /** 低音对应的和弦成员名。 */
    val bassMemberName: String get() = targetInversion.bassMember
}

/**
 * 一次答题结果。
 */
data class ChordInversionAnswerRecord(
    val question: ChordInversionQuestion,
    val userAnswer: String,
    val isCorrect: Boolean
) {
    /** 答错时的正确答案（答对时为 null）。 */
    val correctAnswer: String? get() = if (isCorrect) null else question.correctAnswer
}
