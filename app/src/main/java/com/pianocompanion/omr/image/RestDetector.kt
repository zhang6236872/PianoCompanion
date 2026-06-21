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
 *  - **十六分休止符 / 三十二分休止符**（sixteenth / thirty-second rest）：与八分
 *    休止符形状相似，但分别带 2 个 / 3 个旗钩。通过 [countFlags] 逐行墨迹密度分析
 *    统计旗钩层数来区分（需传入二值图 [BinaryImage]）。**高大的**十六分/三十二分
 *    休止符（高度 ≥1.5 个谱线间距）由 [tallFlaggedRest] 在四分休止符之前拦截，用
 *    [countStrongFlags] 的强对比旗钩计数（中位数基线 + 高对比阈值）正确分类，避免
 *    被 [quarterRest] 的"高锯齿形"启发式误判。
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
     * @param image         去谱线后的二值图（可选）；提供时启用八分/十六分/三十二分休止符的
     *                      旗钩（flag）层数计数，从而区分十六分/三十二分休止符。为 null 时
     *                      所有匹配旗形包络的连通块均按八分休止符处理（向后兼容）。
     * @return 检测到的休止符列表，按 X 坐标排序。
     */
    fun detect(
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        lineSpacing: Int,
        staffLineYs: List<Int>,
        signatureEndX: Int = 0,
        image: BinaryImage? = null
    ): List<Rest> {
        if (lineSpacing <= 0) return emptyList()
        val s = lineSpacing.toDouble()

        val results = ArrayList<Rest>()
        for (blob in blobs) {
            // 跳过签名区内的连通块（谱号/拍号等）。
            if (blob.centerX < signatureEndX) continue

            // 跳过与已检测符头重叠的连通块。
            if (overlapsAnyNotehead(blob, noteheads, s)) continue

            val rest = classify(blob, s, staffLineYs, image)
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
    private fun classify(blob: Blob, s: Double, staffLineYs: List<Int>, image: BinaryImage?): Rest? {
        val bw = blob.width
        val bh = blob.height
        if (bw <= 0 || bh <= 0) return null
        val fillRatio = blob.area.toDouble() / (bw * bh)

        // 1) 全/二分休止符（小型实心矩形）
        blockRest(blob, bw, bh, fillRatio, s, staffLineYs)?.let { return it }

        // 2) 高位旗形休止符（高大的十六分/三十二分休止符）——必须在四分休止符之前
        //    判定。高大的三十二分休止符（3 个旗钩时较高）其高度可能 ≥1.5 个谱线间距，
        //    落入四分休止符的高度区间，会被四分休止符的"高锯齿形"启发式抢先匹配而误判。
        //    仅在提供二值图时可判定（需旗钩层数分析）；无图时跳过以保持向后兼容。
        if (image != null) {
            tallFlaggedRest(blob, bw, bh, fillRatio, s, image)?.let { return it }
        }

        // 3) 四分休止符（高锯齿形）
        quarterRest(blob, bw, bh, fillRatio, s)?.let { return it }

        // 4) 八分/十六分/三十二分休止符（正常高度 0.7–1.5 间距，按旗钩层数区分）
        flaggedRest(blob, bw, bh, fillRatio, s, image)?.let { return it }

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

    // ---- 高位旗形休止符（区分高大的十六/三十二分休止符与四分休止符）-----------

    /**
     * 处理高度 ≥ 1.5 个谱线间距的旗形休止符（高大的十六分/三十二分休止符）。
     *
     * **解决的歧义**：高大的三十二分休止符（3 个旗钩时整体偏高）其高度可能
     * ≥ 1.5 个谱线间距，落入四分休止符的高度区间（1.5–3.5 间距），会被
     * [quarterRest] 的"高锯齿形"启发式抢先匹配而误判为四分休止符。本方法在
     * [quarterRest] 之前拦截此类连通块。
     *
     * **判定依据（强对比旗钩结构）**：旗形休止符具有清晰的「竖直符干脊 + 水平
     * 旗钩带」结构——旗钩所在行的墨迹密度显著高于纯符干脊行（对比度大）；而
     * 四分休止符是单根连续锯齿笔画，各行墨迹密度相近，无明显脊/峰对比。
     * [countStrongFlags] 用中位数基线 + 高对比阈值统计强旗钩带层数。仅当检测到
     * ≥ 2 层强旗钩（对应十六分/三十二分）时才判定为旗形休止符——八分休止符仅
     * 1 旗钩且通常较矮（<1.5 间距），不会进入本分支。
     *
     * 几何约束与 [flaggedRest] 的宽度/填充率区间保持一致，仅高度上限放宽到 3.5
     * 个谱线间距（标准记谱中休止符不会超出谱表高度）。
     *
     * @return 十六分或三十二分休止符；若不符合旗形结构则返回 null（交由四分休止符判定）。
     */
    private fun tallFlaggedRest(
        blob: Blob, bw: Int, bh: Int, fillRatio: Double, s: Double,
        image: BinaryImage
    ): Rest? {
        val minH = (1.5 * s).toInt()
        val maxH = (3.5 * s).toInt()
        if (bh !in minH..maxH) return null

        val minW = (0.4 * s).toInt().coerceAtLeast(3)
        val maxW = (1.2 * s).toInt()
        if (bw !in minW..maxW) return null
        if (fillRatio < 0.15 || fillRatio > 0.60) return null

        val flagCount = countStrongFlags(blob, image)
        if (flagCount < 2) return null // < 2 层强旗钩 → 不是高位旗形休止符
        val duration = if (flagCount >= 3) NoteDuration.THIRTY_SECOND else NoteDuration.SIXTEENTH
        return Rest(blob.centerX, blob.centerY, bw, bh, duration)
    }

    /**
     * 用**强对比阈值**统计旗钩层数——比 [countFlags] 更严格，专门用于区分高大的
     * 旗形休止符与四分休止符锯齿。
     *
     * 与 [countFlags] 的关键区别：
     *  - 基线取所有非零行的**中位数**（而非最小值），对个别高密度行（如锯齿转弯
     *    处的密集行）更鲁棒；
     *  - 旗钩行阈值 = `max(基线×2, 基线+3)`（且 ≥4），要求旗钩行密度**远高于**
     *    脊行——四分休止符锯齿各行密度接近（如 2 vs 3），不满足此强对比阈值，
     *    因此返回 0，不会被误判；而旗形休止符的旗钩行密度通常为脊行的 3 倍以上。
     *
     * @return 强旗钩带的层数（0=无、1=八分、2=十六分、3=三十二分）。
     */
    private fun countStrongFlags(blob: Blob, image: BinaryImage): Int {
        val h = blob.height
        if (h <= 0) return 0

        // 1. 逐行墨迹密度
        val rowCounts = IntArray(h)
        for (row in 0 until h) {
            val y = blob.minY + row
            var cnt = 0
            for (x in blob.minX..blob.maxX) {
                if (image.isBlack(x, y)) cnt++
            }
            rowCounts[row] = cnt
        }

        // 2. 中位数基线（非零行），对个别高密度行更鲁棒。
        val nonzero = rowCounts.filter { it > 0 }.sorted()
        if (nonzero.isEmpty()) return 0
        val baseline = nonzero[nonzero.size / 2]

        // 3. 强对比阈值：旗钩行密度必须远高于脊行。
        val threshold = maxOf(baseline * 2, baseline + 3).coerceAtLeast(4)

        // 4. 统计独立强旗钩组（允许 1 行间断桥接）。
        var flagCount = 0
        var i = 0
        while (i < h) {
            if (rowCounts[i] < threshold) {
                i++
                continue
            }
            flagCount++
            i++
            var gap = 0
            while (i < h) {
                if (rowCounts[i] >= threshold) {
                    gap = 0
                    i++
                } else {
                    gap++
                    if (gap > 1) break
                    i++
                }
            }
        }
        return flagCount
    }

    // ---- 八分/十六分/三十二分休止符 -------------------------------------------

    /**
     * 旗形休止符：八分（1 旗钩）、十六分（2 旗钩）、三十二分（3 旗钩）。
     *
     * 三者在整体包络上非常相似——都是中等高度（≈0.7–1.5 个谱线间距）、中等宽度
     * （≈0.4–1.2 个谱线间距）、中等填充率（0.15–0.60）的旗形/钩形符号。区别在于
     * **旗钩（flag）的层数**：每多一层旗钩，时值减半。
     *
     * 旗钩层数通过 [countFlags] 在二值图上分析逐行墨迹密度分布得到——旗钩所在的行
     * 墨迹密度显著高于纯符干行，据此统计独立旗钩带的数量。
     *
     * 若未提供 [image]（向后兼容），无法计数旗钩，默认返回八分休止符。
     *
     * 已知限制（已解决）：高度超过 1.5 个谱线间距的三十二分休止符此前会被
     * [quarterRest]（四分休止符也检查高度 ≥1.5s）抢先匹配而误判。现已由
     * [tallFlaggedRest] 在 [quarterRest] 之前拦截，通过强对比旗钩计数正确分类。
     */
    private fun flaggedRest(
        blob: Blob, bw: Int, bh: Int, fillRatio: Double, s: Double,
        image: BinaryImage?
    ): Rest? {
        val minH = (0.7 * s).toInt()
        val maxH = (1.5 * s).toInt()
        val minW = (0.4 * s).toInt().coerceAtLeast(3)
        val maxW = (1.2 * s).toInt()

        if (bh !in minH..maxH) return null
        if (bw !in minW..maxW) return null
        if (fillRatio < 0.15 || fillRatio > 0.60) return null

        val flagCount = if (image != null) countFlags(blob, image) else 0
        val duration = when {
            flagCount >= 3 -> NoteDuration.THIRTY_SECOND
            flagCount == 2 -> NoteDuration.SIXTEENTH
            else -> NoteDuration.EIGHTH
        }
        return Rest(blob.centerX, blob.centerY, bw, bh, duration)
    }

    /**
     * 统计旗形休止符的旗钩（flag）层数（八分=1、十六分=2、三十二分=3）。
     *
     * 算法：
     *  1. 在连通块边界框内，逐行统计黑像素数（行墨迹密度 profile）。
     *  2. 取所有非零行的**最小值**作为基线（代表纯符干/斜线的墨迹密度）。
     *  3. 墨迹密度 ≥ 基线+1（且 ≥3）的行标记为「旗钩行」——旗钩增加了该行的水平墨迹量。
     *  4. 将连续的旗钩行（允许 1 行间断桥接）归为一组，每组 = 1 层旗钩。
     *
     * 该方法只读取 [image] 像素，不修改任何状态。
     */
    private fun countFlags(blob: Blob, image: BinaryImage): Int {
        val h = blob.height
        if (h <= 0) return 0

        // 1. 逐行墨迹密度
        val rowCounts = IntArray(h)
        for (row in 0 until h) {
            val y = blob.minY + row
            var cnt = 0
            for (x in blob.minX..blob.maxX) {
                if (image.isBlack(x, y)) cnt++
            }
            rowCounts[row] = cnt
        }

        // 2. 基线 = 非零行的最小值（纯符干行）
        var baseline = Int.MAX_VALUE
        for (cnt in rowCounts) {
            if (cnt in 1 until baseline) baseline = cnt
        }
        if (baseline == Int.MAX_VALUE) return 0

        // 3. 旗钩行阈值
        val threshold = (baseline + 1).coerceAtLeast(3)

        // 4. 统计独立旗钩组（允许 1 行间断桥接）
        var flagCount = 0
        var i = 0
        while (i < h) {
            if (rowCounts[i] < threshold) {
                i++
                continue
            }
            // 旗钩组开始
            flagCount++
            i++
            var gap = 0
            while (i < h) {
                if (rowCounts[i] >= threshold) {
                    gap = 0
                    i++
                } else {
                    gap++
                    if (gap > 1) break
                    i++
                }
            }
        }
        return flagCount
    }
}
