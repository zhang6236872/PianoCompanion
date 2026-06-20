package com.pianocompanion.omr.image

/**
 * 识别到的休止符（rest）：表示一段静默，在时间轴上推进 [duration] 的时长
 * 但不产生任何音符。
 *
 * @param centerX  水平中心（用于与音符一起按 x 排序，确定时间轴位置）
 * @param centerY  竖直中心（用于全/二分休止符的位置判定）
 * @param width    连通块宽度
 * @param height   连通块高度
 * @param duration 休止符时值（全/二分/四分等，与音符共用 [NoteDuration]）
 */
data class Rest(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val duration: NoteDuration
)

/**
 * 纯 Kotlin 休止符检测器（无 Android 依赖，完全可单元测试）。
 *
 * 在去谱线后的二值图上，从尚未被判定为符头的连通块中，依据几何形状识别
 * 常见的休止符类型：
 *
 *  - **全休止符 / 二分休止符**（block rest）：小型实心矩形，约 1 个谱线间距
 *    宽、0.5 个谱线间距高，填充率 > 0.5。两者的区别在于竖直位置——
 *    全休止符挂在第 4 线（自下而上）下方、中心偏上；二分休止符坐在第 3 线
 *    （中线）上方、中心偏下，均位于五线谱中央间内；以中央间的中点为界判定。
 *
 *  - **四分休止符**（quarter rest）：高而窄的锯齿形/闪电形墨迹，
 *    高约 2–3 个谱线间距、宽约 0.5–1 个谱线间距，填充率 0.15–0.55。
 *
 *  - **八分休止符**（eighth rest）：旗形符号——一根斜线顶端带一个卷曲的旗钩，
 *    高约 1–1.5 个谱线间距、中等填充率。通过尺寸和填充率与其它休止符区分。
 *
 * 全程只读取 [Blob] 的几何特征与（可选的）五线谱线位置，不修改像素。
 *
 * @see NoteDuration  休止符与音符共用相同的时值枚举。
 */
object RestDetector {

    /**
     * @param blobs         去谱线后的所有连通块。
     * @param noteheads     已检测到的符头列表（用于排除对应的连通块，避免重复识别）。
     * @param lineSpacing   谱线间距（像素）。
     * @param staffLineYs   五线谱各线中心 Y 坐标，自上而下排列（标准为 5 根线）。
     * @param signatureEndX 签名区（谱号/调号/拍号）右边缘 X 坐标；中心在此左侧的连通块被忽略。
     * @return 检测到的休止符列表，按 X 坐标排序。
     */
    fun detect(
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        lineSpacing: Int,
        staffLineYs: List<Int>,
        signatureEndX: Int = 0
    ): List<Rest> {
        if (lineSpacing <= 0) return emptyList()
        val s = lineSpacing.toDouble()

        val results = ArrayList<Rest>()
        for (blob in blobs) {
            // 跳过签名区内的连通块（谱号/拍号等）。
            if (blob.centerX < signatureEndX) continue

            // 跳过与已检测符头重叠的连通块。
            if (overlapsAnyNotehead(blob, noteheads, s)) continue

            val rest = classify(blob, s, staffLineYs)
            if (rest != null) results += rest
        }
        return results.sortedBy { it.centerX }
    }

    /**
     * 检查连通块是否与任何已检测符头重叠（符头中心在连通块边界框 ±tol 内）。
     */
    private fun overlapsAnyNotehead(blob: Blob, noteheads: List<Notehead>, s: Double): Boolean {
        val tol = (0.7 * s).toInt().coerceAtLeast(3)
        for (nh in noteheads) {
            if (nh.centerX in blob.minX - tol..blob.maxX + tol &&
                nh.centerY in blob.minY - tol..blob.maxY + tol
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 对单个连通块依次尝试各类休止符的分类。判定顺序很重要：先检查最具体的形状
     * （实心矩形），再检查更宽泛的形状（高锯齿、中等旗形），避免误分类。
     */
    private fun classify(blob: Blob, s: Double, staffLineYs: List<Int>): Rest? {
        val bw = blob.width
        val bh = blob.height
        if (bw <= 0 || bh <= 0) return null
        val fillRatio = blob.area.toDouble() / (bw * bh)

        // 1) 全/二分休止符（小型实心矩形）
        blockRest(blob, bw, bh, fillRatio, s, staffLineYs)?.let { return it }

        // 2) 四分休止符（高锯齿形）
        quarterRest(blob, bw, bh, fillRatio, s)?.let { return it }

        // 3) 八分休止符（旗形，需图像辅助判定）
        eighthRest(blob, bw, bh, fillRatio, s)?.let { return it }

        return null
    }

    // ---- 全/二分休止符 --------------------------------------------------------

    /**
     * 全休止符 / 二分休止符：小型实心矩形。
     *
     * 几何特征：
     *  - 宽 ≈ 1 个谱线间距（0.5s – 1.8s）
     *  - 高 ≈ 0.5 个谱线间距（0.2s – 0.85s）
     *  - 填充率 > 0.5（实心）
     *
     * 全 vs 二分的判定：标准记谱法中，两者都位于五线谱中央间（第 4 线与第 3 线
     * 之间，自上而下数 lines[1] 与 lines[2]）。全休止符挂在上方线（lines[1]）
     * 下方，中心靠近上方线（Y 值较小）；二分休止符坐在下方线（lines[2]）上方，
     * 中心靠近下方线（Y 值较大）。以中央间中点为界判定：
     * 中心 < 中点 → 全休止符；中心 >= 中点 → 二分休止符。
     */
    private fun blockRest(
        blob: Blob, bw: Int, bh: Int, fillRatio: Double, s: Double, staffLineYs: List<Int>
    ): Rest? {
        val minW = (0.5 * s).toInt().coerceAtLeast(3)
        val maxW = (1.8 * s).toInt()
        val minH = (0.2 * s).toInt().coerceAtLeast(2)
        val maxH = (0.85 * s).toInt()

        if (bw !in minW..maxW) return null
        if (bh !in minH..maxH) return null
        if (fillRatio < 0.5) return null

        // 需要五线谱线位置才能区分全/二分。
        if (staffLineYs.size < 3) return null

        val line2 = staffLineYs[1]   // 自上而下第 2 根线（自下而上第 4 线）
        val line3 = staffLineYs[2]   // 自上而下第 3 根线（中线）

        // 休止符应位于中央间内（容差 0.6s）。
        val tol = (0.6 * s).toInt()
        val lo = minOf(line2, line3) - tol
        val hi = maxOf(line2, line3) + tol
        if (blob.centerY !in lo..hi) return null

        // 以中央间中点为界：全休止符挂在上方线下方、中心偏上（更靠近 lines[1]，Y 更小）；
        // 二分休止符坐在中线上方、中心偏下（更靠近 lines[2]，Y 更大）。
        val midSpace = (line2 + line3) / 2
        val duration = if (blob.centerY < midSpace) NoteDuration.WHOLE else NoteDuration.HALF
        return Rest(blob.centerX, blob.centerY, bw, bh, duration)
    }

    // ---- 四分休止符 -----------------------------------------------------------

    /**
     * 四分休止符：高而窄的锯齿形/闪电形墨迹。
     *
     * 几何特征：
     *  - 高 ≈ 2–3 个谱线间距（1.5s – 3.5s）
     *  - 宽 ≈ 0.5–1 个谱线间距（0.35s – 1.2s）
     *  - 高 > 宽（纵向延伸）
     *  - 填充率 0.15–0.55（锯齿形轮廓，内部不饱满）
     *
     * 该形状在五线谱中非常独特：没有任何其他常见符号同时满足\"高 > 2 个谱线间距\"
     * 且\"填充率 < 0.55\"。
     */
    private fun quarterRest(
        blob: Blob, bw: Int, bh: Int, fillRatio: Double, s: Double
    ): Rest? {
        val minH = (1.5 * s).toInt()
        val maxH = (3.5 * s).toInt()
        val minW = (0.35 * s).toInt().coerceAtLeast(3)
        val maxW = (1.2 * s).toInt()

        if (bh !in minH..maxH) return null
        if (bw !in minW..maxW) return null
        if (bh <= bw) return null           // 必须高 > 宽
        if (fillRatio < 0.15 || fillRatio > 0.55) return null

        return Rest(blob.centerX, blob.centerY, bw, bh, NoteDuration.QUARTER)
    }

    // ---- 八分休止符 -----------------------------------------------------------

    /**
     * 八分休止符：旗形符号——一根从右上到左下的斜线，顶端带一个卷曲的旗钩。
     *
     * 几何特征：
     *  - 高 ≈ 1–1.5 个谱线间距（0.8s – 1.8s）
     *  - 宽 ≈ 0.5–1 个谱线间距（0.4s – 1.1s）
     *  - 填充率 0.20–0.55（中等）
     *
     * 与噪声的区分：八分休止符的尺寸（高 ≈ 1s）和中等填充率（0.20–0.55）使其
     * 与实心矩形（全/二分休止符，填充率 > 0.5）和高锯齿（四分休止符，高 > 1.5s）
     * 区分开。已知限制：仅凭几何特征难以完全排除噪声碎片，真实照片可能需人工校对。
     */
    private fun eighthRest(
        blob: Blob, bw: Int, bh: Int, fillRatio: Double, s: Double
    ): Rest? {
        val minH = (0.8 * s).toInt()
        val maxH = (1.8 * s).toInt()
        val minW = (0.4 * s).toInt().coerceAtLeast(3)
        val maxW = (1.1 * s).toInt()

        if (bh !in minH..maxH) return null
        if (bw !in minW..maxW) return null
        if (fillRatio < 0.20 || fillRatio > 0.55) return null

        return Rest(blob.centerX, blob.centerY, bw, bh, NoteDuration.EIGHTH)
    }
}
