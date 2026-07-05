package com.pianocompanion.audio

/**
 * 节拍器细分模式（Metronome Subdivision）。
 *
 * 将每个主拍（quarter beat）均分为 [clicksPerBeat] 个等长的"子拍点"，
 * 让节拍器不仅在每个主拍上响，也能在更细的节奏位置上响，帮助练习者
 * 稳定节奏感。例如八分音符 = 每拍响 2 次、三连音 = 每拍响 3 次。
 *
 * 纯 Kotlin，无 Android 依赖，完全可单元测试。
 */
enum class Subdivision(val clicksPerBeat: Int, val displayName: String, val symbol: String) {
    /** 四分音符：每拍 1 次点击（即不分细分）。 */
    QUARTER(1, "四分音符", "♩"),

    /** 八分音符：每拍 2 次点击。 */
    EIGHTH(2, "八分音符", "♪♪"),

    /** 三连音：每拍 3 次点击。 */
    TRIPLET(3, "三连音", "3"),

    /** 十六分音符：每拍 4 次点击。 */
    SIXTEENTH(4, "十六分音符", "♬"),

    /** 六连音：每拍 6 次点击。 */
    SEXTUPLET(6, "六连音", "6"),

    /** 三十二分音符：每拍 8 次点击。 */
    THIRTY_SECOND(8, "三十二分音符", "…");

    val isQuarter: Boolean get() = clicksPerBeat == 1

    companion object {
        /**
         * 整个小节（measure）的子拍点总数。
         */
        fun totalClicks(beatsPerMeasure: Int, subdivision: Subdivision): Int =
            beatsPerMeasure * subdivision.clicksPerBeat
    }
}

/**
 * 单个点击点的类型，决定其音高/音量/视觉强调程度。
 */
enum class ClickType {
    /** 强拍（每小节第一拍）。 */
    ACCENT,

    /** 弱拍（小节内其余主拍）。 */
    BEAT,

    /** 子拍点（主拍之间的细分点击）。 */
    SUB,
}

/**
 * 节拍器点击模式生成器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 给定 [beatsPerMeasure] 与 [subdivision]，生成一小节内所有点击点的 [ClickType] 序列，
 * 以及计算主拍/子拍之间的时间间隔。
 */
object ClickPatternGenerator {

    /**
     * 生成一小节内的点击类型序列。
     *
     * - 序列首元素恒为 [ClickType.ACCENT]（强拍）。
     * - 每 [Subdivision.clicksPerBeat] 个元素为一组，组的首元素为 [ClickType.BEAT]
     *   （或首组为 ACCENT），组内其余为 [ClickType.SUB]。
     *
     * @return 长度为 `beatsPerMeasure * subdivision.clicksPerBeat` 的列表。
     */
    fun pattern(beatsPerMeasure: Int, subdivision: Subdivision): List<ClickType> {
        require(beatsPerMeasure >= 1) { "beatsPerMeasure must be >= 1, got $beatsPerMeasure" }
        val total = Subdivision.totalClicks(beatsPerMeasure, subdivision)
        return (0 until total).map { i ->
            when {
                i == 0 -> ClickType.ACCENT
                i % subdivision.clicksPerBeat == 0 -> ClickType.BEAT
                else -> ClickType.SUB
            }
        }
    }

    /**
     * 相邻两次子拍点之间的时间间隔（毫秒）。
     *
     * 一个主拍 = 60_000 / bpm 毫秒，再均分为 [Subdivision.clicksPerBeat] 份。
     * 保证返回值 >= 1（避免极快细分下产生 0ms 间隔导致死循环）。
     */
    fun subClickIntervalMs(bpm: Int, subdivision: Subdivision): Long {
        require(bpm > 0) { "bpm must be > 0, got $bpm" }
        val beatMs = 60_000L / bpm
        return (beatMs / subdivision.clicksPerBeat).coerceAtLeast(1L)
    }

    /**
     * 一次完整小节的总时长（毫秒），与细分无关：恒为 `beatsPerMeasure * 60_000 / bpm`。
     */
    fun measureDurationMs(bpm: Int, beatsPerMeasure: Int): Long {
        require(bpm > 0) { "bpm must be > 0, got $bpm" }
        require(beatsPerMeasure >= 1) { "beatsPerMeasure must be >= 1" }
        return beatsPerMeasure * 60_000L / bpm
    }

    /**
     * 给定子拍点在整小节序列中的索引，返回它对应的主拍序号（0-based）。
     */
    fun beatIndexOf(clickIndex: Int, subdivision: Subdivision): Int =
        clickIndex / subdivision.clicksPerBeat
}
