package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 滑音/刮奏(glissando)检测器。
 *
 * 滑音是乐谱中连接两个音符的**斜向线**（直线或波浪线），指示演奏者从一个音符
 * 快速滑动到另一个音符。在钢琴音乐中极为常见——浪漫派/印象派/爵士作品中
 * 经常出现跨多个八度的白键刮奏(glissando)或黑键刮奏。
 *
 * **对 score-following 的影响**：滑音在演奏时会产生大量连续的快速 onset
 * （手指滑过每个琴键），而乐谱上只标记了起点和终点两个音符。若 OMR 不识别滑音，
 * score follower 会期待两个稀疏的 onset，而实际演奏产生几十个密集 onset，
 * 导致匹配混乱。检测到滑音后，score follower 可进入「宽松匹配」模式。
 *
 * **检测原理**：
 * 1. 将符头按系统分组，在同一系统内按 X 排序
 * 2. 对每对相邻符头（左→右），检查音高差是否足够大（≥ [MIN_PITCH_DIFF_FRAC]
 *    个谱线间距）——滑音连接的是音高相差较大的音符
 * 3. 沿两符头之间的对角线路径采样像素，检查墨迹覆盖率
 * 4. 滑音线是连续的对角墨迹，覆盖率 ≥ [MIN_INK_COVERAGE] 即判定为滑音
 *
 * **与其他符号的区分**：
 * - **延音线(tie) / 连音(slur)**：弧线位于符头上方/下方，不沿对角直线分布；
 *   沿对角采样时覆盖率很低（弧线仅在起止点附近与直线相交）
 * - **符干(stem)**：竖直线，在对角路径上仅在交叉点贡献少量像素
 * - **横梁(beam)**：水平线，在对角路径上仅在交叉高度处贡献少量像素
 * - **小节线(barline)**：竖直实线，在系统内两个相邻符头之间通常不存在
 */
object GlissandoDetector {

    /**
     * 检测到的滑音。
     *
     * @param fromNoteheadIdx 起始符头索引（左侧、时间在前）
     * @param toNoteheadIdx   结束符头索引（右侧、时间在后）
     */
    data class Glissando(
        val fromNoteheadIdx: Int,
        val toNoteheadIdx: Int
    )

    // --- 约束常量（谱线间距倍数）---

    /** 最小音高差（谱线间距倍数）：滑音连接音高相差较大的音符。 */
    private const val MIN_PITCH_DIFF_FRAC = 1.5

    /** 符头间最小水平间距（谱线间距倍数）：排除和弦成员（同 X）。 */
    private const val MIN_GAP_FRAC = 1.0

    /** 符头间最大水平间距（谱线间距倍数）：滑音不会跨越整个页面。 */
    private const val MAX_GAP_FRAC = 10.0

    /** 垂直采样窗口半宽（谱线间距倍数）：容忍滑音线的微小弯曲。 */
    private const val WINDOW_HALF_WIDTH_FRAC = 0.4

    /** 最小墨迹覆盖率：对角路径上至少此比例的采样点有墨迹。 */
    private const val MIN_INK_COVERAGE = 0.50

    /** 对角线采样步长（像素）：太密会重复采样，太疏会漏检。 */
    private const val SAMPLE_STEP = 2

    /**
     * 检测符头之间的滑音线。
     *
     * @param image         去谱线后的二值图（cleaned）
     * @param noteheads     全部符头列表
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）
     * @param lineSpacing   平均谱线间距
     * @return 检测到的滑音列表
     */
    fun detect(
        image: BinaryImage,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        lineSpacing: Int
    ): List<Glissando> {
        if (lineSpacing <= 0 || noteheads.size < 2) return emptyList()

        val s = lineSpacing.toDouble()
        val minPitchDiff = (MIN_PITCH_DIFF_FRAC * s).toInt().coerceAtLeast(2)
        val minGap = (MIN_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val maxGap = (MAX_GAP_FRAC * s).toInt()
        val windowHalf = max(2, (WINDOW_HALF_WIDTH_FRAC * s).toInt())

        // 按系统分组符头
        val bySystem = HashMap<Int, MutableList<Int>>()
        for (i in noteheads.indices) {
            val sys = systemIndices.getOrElse(i) { 0 }
            bySystem.getOrPut(sys) { mutableListOf() }.add(i)
        }

        val results = ArrayList<Glissando>()

        for ((_, indices) in bySystem) {
            // 按 X 坐标排序
            val sorted = indices.sortedBy { noteheads[it].centerX }

            // 对每对相邻符头检查是否有滑音线连接
            for (k in 0 until sorted.size - 1) {
                val idxA = sorted[k]
                val idxB = sorted[k + 1]
                val nhA = noteheads[idxA]
                val nhB = noteheads[idxB]

                val dx = nhB.centerX - nhA.centerX
                if (dx < minGap || dx > maxGap) continue

                val dy = abs(nhB.centerY - nhA.centerY)
                if (dy < minPitchDiff) continue

                // 从 A 的右边缘到 B 的左边缘采样对角路径
                val startX = nhA.centerX + nhA.width / 2
                val startY = nhA.centerY
                val endX = nhB.centerX - nhB.width / 2
                val endY = nhB.centerY

                val coverage = sampleDiagonalInk(
                    image, startX, startY, endX, endY, windowHalf
                )
                if (coverage >= MIN_INK_COVERAGE) {
                    results += Glissando(idxA, idxB)
                }
            }
        }
        return results
    }

    /**
     * 沿对角线路径采样，计算墨迹覆盖率。
     *
     * 在从 (x0,y0) 到 (x1,y1) 的直线上均匀取点，每个点检查以其为中心、
     * 边长 2×[windowHalf]+1 的正方形窗口内是否有黑像素。覆盖率 = 有墨采样点数 / 总采样点数。
     *
     * @return 0.0~1.0 的覆盖率
     */
    private fun sampleDiagonalInk(
        image: BinaryImage,
        x0: Int, y0: Int,
        x1: Int, y1: Int,
        windowHalf: Int
    ): Double {
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val totalSteps = max(dx, dy)
        if (totalSteps == 0) return 0.0

        var inked = 0
        var total = 0
        var i = 0
        while (i <= totalSteps) {
            val t = i.toDouble() / totalSteps
            val cx = (x0 + t * (x1 - x0)).toInt()
            val cy = (y0 + t * (y1 - y0)).toInt()

            // 检查窗口内是否有墨迹
            var found = false
            val wy0 = max(0, cy - windowHalf)
            val wy1 = min(image.height - 1, cy + windowHalf)
            val wx0 = max(0, cx - windowHalf)
            val wx1 = min(image.width - 1, cx + windowHalf)
            for (wy in wy0..wy1) {
                val rowBase = wy * image.width
                for (wx in wx0..wx1) {
                    if (image.pixels[rowBase + wx]) {
                        found = true
                        break
                    }
                }
                if (found) break
            }
            if (found) inked++
            total++

            i += SAMPLE_STEP
        }
        return if (total > 0) inked.toDouble() / total else 0.0
    }
}
