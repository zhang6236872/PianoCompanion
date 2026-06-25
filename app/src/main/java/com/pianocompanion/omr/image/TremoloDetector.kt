package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 颤音记号/震音(tremolo)检测器。
 *
 * 震音(tremolo)用音符符干上的 2~3 条短斜线（slashes）表示，指示演奏者将该音符
 * 快速反复弹奏（而非持续保持）。在钢琴音乐中极为常见——伴奏织体中的震音和弦、
 * 模仿铃鼓效果的 tremolando 等。
 *
 * - **2 条斜线** → 八分音符震音（将音符细分为快速反复的八分音符）
 * - **3 条斜线** → 三十二分音符震音（更密集的反复）
 *
 * **对 score-following 的影响**：震音音符在演奏时会产生大量快速的重复 onset，
 * 但乐谱上只标记了一个音符。若 OMR 不识别震音，score follower 会期待单一 onset，
 * 而实际演奏产生几十个 onset，导致匹配混乱。检测到震音后，可通知 score follower
 * 进入\"宽松匹配\"模式。
 *
 * **检测原理**：
 * 1. 对每个检测到符干的符头，取符干中段（跳过两端各 20%）作为搜索区域
 * 2. 对搜索区域内每一行，统计符干 X 坐标周围水平窗口内的黑像素总数
 * 3. 裸符干行仅有符干本身的墨迹（1~2px），斜线穿过处行有额外墨迹（符干 + 斜线）
 * 4. 以全区域最小墨迹数为基线（裸符干），墨迹 > 基线的行标记为\"斜线行\"
 * 5. 连续斜线行（允许 1 行间断）归为一组（一条斜线），每组至少 2 个实际斜线行
 * 6. 组数 ≥ [MIN_SLASHES] 即判定为震音
 *
 * **与其他符号的区分**：
 * - **符尾(flag)**：位于符干**末端**（扫描区域排除末端 20%），方向感知的水平墨迹投影
 *   与本检测器的方法不同；末端区域不参与震音扫描
 * - **横梁(beam)**：同样位于符干末端附近；带横梁的音符通常不使用震音斜线
 *   （用横梁表示节奏），故 beamCount > 0 的符头直接跳过
 * - **符头本身**：扫描区域排除了符头附近（margin ≥ 符头高度一半 + 1）
 * - **小节线(barline)**：贯穿全谱高的实心竖线，不在符干搜索窗口内
 */
object TremoloDetector {

    /**
     * 检测到的震音标记。
     *
     * @param noteheadIdx 对应符头在 noteheads 列表中的索引
     * @param slashCount  斜线数量（2 = 八分震音, 3 = 三十二分震音）
     * @param centerX     震音区域中心 X 坐标（≈ 符干 X）
     * @param centerY     震音区域中心 Y 坐标（≈ 符干中段中心）
     */
    data class Tremolo(
        val noteheadIdx: Int,
        val slashCount: Int,
        val centerX: Int,
        val centerY: Int
    )

    // --- 约束常量（谱线间距倍数）---

    /** 符干最小长度（谱线间距倍数）：太短的符干容不下两条斜线。 */
    private const val MIN_STEM_LEN_FRAC = 1.5

    /** 搜索窗口半宽（谱线间距倍数）：围绕符干 X 统计墨迹的水平范围。 */
    private const val HALF_WINDOW_FRAC = 0.6

    /** 符干两端各跳过的比例（排除符头和符干末端的符尾/横梁）。 */
    private const val STEM_SKIP_FRAC = 0.20

    /** 判定震音所需的最少斜线数。 */
    private const val MIN_SLASHES = 2

    /** 斜线数上限（标准记谱最多 3 条）。 */
    private const val MAX_SLASHES = 3

    /** 每组斜线的最小实际斜线行数（过滤单行噪声）。 */
    private const val MIN_BAND_SLASH_ROWS = 2

    /** 斜线组间允许的间断行数（容忍斜线中间的薄行）。 */
    private const val BAND_GAP_TOLERANCE = 1

    /**
     * 检测每个带干符头上的震音斜线。
     *
     * @param image       去谱线后的二值图（cleaned）。
     * @param noteheads   已检测到的符头列表。
     * @param rhythms     与 [noteheads] 一一对应的节奏特征（提供符干方向与远端坐标）。
     * @param lineSpacing 五线谱线间距（像素）。
     * @return 检测到的震音列表。
     */
    fun detect(
        image: BinaryImage,
        noteheads: List<Notehead>,
        rhythms: List<RhythmFeatures>,
        lineSpacing: Int
    ): List<Tremolo> {
        if (lineSpacing <= 0 || noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()

        val results = ArrayList<Tremolo>()
        for (idx in noteheads.indices) {
            val rhythm = rhythms.getOrNull(idx) ?: continue
            // 震音需要符干；带横梁的音符用横梁表示节奏，通常不使用震音斜线。
            if (!rhythm.hasStem || rhythm.beamCount > 0) continue

            val nh = noteheads[idx]
            val stemX = rhythm.stemEndX
            val stemYStart = nh.centerY
            val stemYEnd = rhythm.stemEndY

            val stemLen = abs(stemYEnd - stemYStart)
            if (stemLen < MIN_STEM_LEN_FRAC * s) continue

            // 搜索区域：符干中段（跳过两端）。margin 同时确保排除符头墨迹。
            val noteheadMargin = max((STEM_SKIP_FRAC * stemLen).toInt(), nh.height / 2 + 1)
            val lowY = min(stemYStart, stemYEnd) + noteheadMargin
            val highY = max(stemYStart, stemYEnd) - noteheadMargin
            if (highY - lowY + 1 < 4) continue  // 搜索区域太短

            val halfWindow = (HALF_WINDOW_FRAC * s).toInt().coerceAtLeast(4)
            val xStart = max(0, stemX - halfWindow)
            val xEnd = min(image.width - 1, stemX + halfWindow)

            // 逐行统计窗口内黑像素数。
            val scanLen = highY - lowY + 1
            val inkCounts = IntArray(scanLen)
            for (k in 0 until scanLen) {
                val y = lowY + k
                if (y < 0 || y >= image.height) continue
                val rowBase = y * image.width
                var cnt = 0
                for (x in xStart..xEnd) {
                    if (image.pixels[rowBase + x]) cnt++
                }
                inkCounts[k] = cnt
            }

            // 基线 = 搜索区域内最小墨迹数（裸符干行的墨迹）。
            val minInk = inkCounts.minOrNull() ?: 0
            // 斜线行：墨迹 > 基线（至少多 1 个像素）。
            val isSlashRow = BooleanArray(scanLen) { inkCounts[it] > minInk }

            // 分组：连续斜线行（允许 1 行间断）为一条斜线，每组至少 MIN_BAND_SLASH_ROWS 个实际斜线行。
            val slashCount = countSlashBands(isSlashRow)
            if (slashCount >= MIN_SLASHES) {
                val clamped = min(slashCount, MAX_SLASHES)
                results += Tremolo(idx, clamped, stemX, (lowY + highY) / 2)
            }
        }
        return results
    }

    /**
     * 统计斜线组数。每组由连续的斜线行组成（允许 [BAND_GAP_TOLERANCE] 行间断），
     * 且每组必须包含至少 [MIN_BAND_SLASH_ROWS] 个实际斜线行（过滤噪声）。
     */
    private fun countSlashBands(rows: BooleanArray): Int {
        var count = 0
        var i = 0
        while (i < rows.size) {
            if (!rows[i]) { i++; continue }
            // 开始一组
            var slashRowsInBand = 0
            var gap = 0
            while (i < rows.size) {
                if (rows[i]) {
                    slashRowsInBand++
                    gap = 0
                } else {
                    gap++
                    if (gap > BAND_GAP_TOLERANCE) break
                }
                i++
            }
            if (slashRowsInBand >= MIN_BAND_SLASH_ROWS) count++
        }
        return count
    }
}
