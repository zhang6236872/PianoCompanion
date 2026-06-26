package com.pianocompanion.omr.image

import kotlin.math.max

/**
 * 导航符号(navigation symbol)检测器——识别乐谱中的 Segno (𝄋) 和 Coda (𝄐) 标记。
 *
 * **Segno (𝄋)**：Dal Segno 反复的锚点标记。乐谱中出现 "D.S." (Dal Segno) 时，
 * 演奏者从最近的 Segno 处反复。它由两条交叉曲线和两个圆点组成，呈美元符号 ($)
 * 或分号 (¢) 形状，放置在谱表顶线上方。
 *
 * **Coda (𝄐)**：乐谱的结尾段标记。出现 "D.S. al Coda" 或 "D.C. al Coda" 时，
 * 演奏者反复到 Segno/开头后，跳到 Coda 处演奏结尾段。它是圆圈内套一个十字 (+) 的
 * 形状，放置在谱表顶线上方。
 *
 * **对 score-following 的影响**：D.C./D.S./al Coda 指令会改变乐曲的线性播放顺序——
 * 演奏者从某处跳回前面已弹过的位置，再从那里跳到结尾段。如果不识别这些导航符号，
 * score follower 会按线性顺序处理，期待一个永远不会出现的音符序列。检测到 Segno/Coda
 * 后，score follower 可据此实现非线性跳转逻辑。
 *
 * **检测原理**：
 * 1. 在每个谱表系统顶线上方 [SEARCH_GAP_FRAC]~[SEARCH_RANGE_FRAC] 个谱线间距的
 *    搜索区域内，从连通块中筛选尺寸合适的候选
 * 2. 对每个候选连通块进行形状分类：
 *    - **Coda**：有封闭空洞（ring 包围的白色区域被十字笔画分割）+ 中心十字模式
 *    - **Segno**：双侧墨迹（S 曲线在左右两侧都有墨迹）+ 多行段墨迹分布 + 非穹顶形状
 * 3. CodA 优先判定（空洞+十字是非常独特的特征），再判 Segno
 *
 * **与其他符号的区分**：
 * - **Fermata (⌒)**：穹顶形状（中心顶高于两侧），宽 > 高；不通过 Segno 的非穹顶检查
 * - **Trill (tr)**：由独立字母组成，非单一连通块；不会通过双侧墨迹+多行段检查
 * - **Tuplet/Fingering 数字**：宽高比极端（数字是高>宽），且无双侧墨迹
 * - **Volta 括号**：水平线为主，宽远大于高，不满足尺寸约束
 * - **拍号/速度记号数字**：位于签名区或谱表上方但通常更小，无封闭空洞
 */
object NavigationSymbolDetector {

    /** 导航符号类型。 */
    enum class NavigationSymbolType { SEGNO, CODA }

    /**
     * 检测到的导航符号。
     *
     * @param type 符号类型（SEGNO 或 CODA）
     * @param centerX 符号中心 X 坐标
     * @param centerY 符号中心 Y 坐标
     * @param systemIdx 所属谱表系统索引
     */
    data class NavigationSymbol(
        val type: NavigationSymbolType,
        val centerX: Int,
        val centerY: Int,
        val systemIdx: Int
    )

    // --- 搜索区域约束 ---

    /** 搜索起始间隙（谱线间距倍数）：从顶线向上偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索范围（谱线间距倍数）：搜索向上延伸的总距离。 */
    private const val SEARCH_RANGE_FRAC = 5.0

    // --- 尺寸约束 ---

    /** 候选最小宽度/高度（谱线间距倍数）。 */
    private const val MIN_DIM_FRAC = 0.8

    /** 候选最大宽度/高度（谱线间距倍数）。 */
    private const val MAX_DIM_FRAC = 3.5

    /** 候选最大宽高比（排除过宽/过高的连通块）。 */
    private const val MAX_ASPECT = 2.0

    /** 候选最小宽高比。 */
    private const val MIN_ASPECT = 0.5

    // --- Coda 判定参数 ---

    /** Coda 封闭空洞最小数量：ring+cross 至少产生几个被墨迹四面围住的白色像素。 */
    private const val MIN_CODA_HOLES = 3

    /** 十字笔画占包围盒维度的最小比例（水平/竖直笔画各需 ≥ 此比例）。 */
    private const val CROSS_STROKE_FRAC = 0.35

    // --- Segno 判定参数 ---

    /** Segno 双侧墨迹比例范围（左/右 ink 比值需在此范围内）。 */
    private const val SEGNO_BILATERAL_MIN = 0.15
    private const val SEGNO_BILATERAL_MAX = 6.0

    /** Segno 穹顶拒绝阈值：中心顶比两侧顶高出 blob 高度的此比例即判为 fermata。 */
    private const val DOME_REJECT_FRAC = 0.25

    /**
     * 在所有谱表系统顶线上方检测 Segno 和 Coda 导航符号。
     *
     * @param image 去谱线+降噪后的二值图像
     * @param blobs 连通块列表（与 image 一致的坐标系）
     * @param systems 谱表系统列表
     * @param lineSpacing 平均谱线间距
     * @return 检测到的导航符号列表（按系统索引排序）
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<NavigationSymbol> {
        if (lineSpacing < 1 || systems.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<NavigationSymbol>()

        val minWidth = (MIN_DIM_FRAC * s).toInt().coerceAtLeast(3)
        val maxWidth = (MAX_DIM_FRAC * s).toInt()
        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()

        systems.forEachIndexed { sysIdx, system ->
            val topLineY = system.topLine.center

            // 搜索区域：顶线上方 searchGap ~ searchGap+searchRange
            val searchBottom = topLineY - searchGap
            val searchTop = (searchBottom - searchRange).coerceAtLeast(0)

            // 多系统页面：搜索上界不超过上一个系统的底线 + 1 间距
            val upperLimit = if (sysIdx > 0) {
                systems[sysIdx - 1].bottomLine.center + lineSpacing
            } else {
                0
            }
            val effectiveTop = max(searchTop, upperLimit)
            if (effectiveTop >= searchBottom) return@forEachIndexed

            for (blob in blobs) {
                // 连通块中心 Y 必须在搜索区域内
                if (blob.centerY < effectiveTop || blob.centerY > searchBottom) continue

                // 尺寸约束
                val w = blob.width
                val h = blob.height
                if (w < minWidth || w > maxWidth) continue
                if (h < minWidth || h > maxWidth) continue

                val aspect = w.toDouble() / h.coerceAtLeast(1)
                if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue

                // 面积约束：排除噪点碎片（太小）或大型混合块（太大）
                val area = blob.area
                val minArea = (minWidth * minWidth).coerceAtLeast(4)
                val maxArea = (maxWidth * maxWidth * 4)
                if (area < minArea || area > maxArea) continue

                // 分类
                val type = classifyBlob(image, blob, s)
                if (type != null) {
                    results += NavigationSymbol(type, blob.centerX, blob.centerY, sysIdx)
                }
            }
        }

        return results
    }

    /**
     * 对候选连通块进行形状分类。
     * 先尝试 Coda（空洞+十字特征最独特），再尝试 Segno。
     */
    private fun classifyBlob(image: BinaryImage, blob: Blob, s: Double): NavigationSymbolType? {
        // --- Coda 判定：封闭空洞 + 中心十字 ---
        if (isCoda(image, blob)) {
            return NavigationSymbolType.CODA
        }

        // --- Segno 判定：双侧墨迹 + 多行段 + 非穹顶 ---
        if (isSegno(image, blob)) {
            return NavigationSymbolType.SEGNO
        }

        return null
    }

    // ====================================================================
    // Coda 检测
    // ====================================================================

    /**
     * Coda = 圆环 + 十字。判定依据：
     * 1. 包围盒内有被墨迹四面（上下左右）围住的白色像素（ring+cross 形成的封闭区域）
     * 2. 中心区域有水平笔画 + 垂直笔画（十字模式）
     */
    private fun isCoda(image: BinaryImage, blob: Blob): Boolean {
        val holes = countEnclosedHoles(image, blob)
        if (holes < MIN_CODA_HOLES) return false
        return hasCrossPattern(image, blob)
    }

    /**
     * 计算包围盒内被墨迹四面围住的白色像素数量。
     *
     * 对于一个白色像素 (x, y)，如果在同一行中其左侧有黑像素、右侧有黑像素，
     * 且在同一列中其上方有黑像素、下方有黑像素，则计为"封闭空洞"。
     * Coda 的圆环 + 十字会在四角区域产生多个这样的空洞。
     */
    private fun countEnclosedHoles(image: BinaryImage, blob: Blob): Int {
        var count = 0
        for (y in (blob.minY + 1) until blob.maxY) {
            for (x in (blob.minX + 1) until blob.maxX) {
                if (image.isBlack(x, y)) continue
                // 检查四个方向是否都有墨迹
                var hasLeft = false
                for (lx in (blob.minX) until x) {
                    if (image.isBlack(lx, y)) { hasLeft = true; break }
                }
                if (!hasLeft) continue

                var hasRight = false
                for (rx in (x + 1)..blob.maxX) {
                    if (image.isBlack(rx, y)) { hasRight = true; break }
                }
                if (!hasRight) continue

                var hasTop = false
                for (ty in (blob.minY) until y) {
                    if (image.isBlack(x, ty)) { hasTop = true; break }
                }
                if (!hasTop) continue

                var hasBottom = false
                for (by in (y + 1)..blob.maxY) {
                    if (image.isBlack(x, by)) { hasBottom = true; break }
                }
                if (!hasBottom) continue

                count++
            }
        }
        return count
    }

    /**
     * 检查中心区域是否存在十字模式（水平笔画 + 垂直笔画都穿过中心）。
     *
     * 在中心行检查最长水平连续墨迹游程，在中心列检查最长竖直连续墨迹游程。
     * 两者各需 ≥ 包围盒对应维度的 [CROSS_STROKE_FRAC] 比例。
     */
    private fun hasCrossPattern(image: BinaryImage, blob: Blob): Boolean {
        val cx = blob.centerX
        val cy = blob.centerY

        // 中心行的最长水平墨迹游程
        var hRun = 0
        var maxHRun = 0
        for (x in blob.minX..blob.maxX) {
            if (image.isBlack(x, cy)) {
                hRun++
                maxHRun = max(maxHRun, hRun)
            } else {
                hRun = 0
            }
        }

        // 中心列的最长竖直墨迹游程
        var vRun = 0
        var maxVRun = 0
        for (y in blob.minY..blob.maxY) {
            if (image.isBlack(cx, y)) {
                vRun++
                maxVRun = max(maxVRun, vRun)
            } else {
                vRun = 0
            }
        }

        val minHStroke = (blob.width * CROSS_STROKE_FRAC).toInt().coerceAtLeast(2)
        val minVStroke = (blob.height * CROSS_STROKE_FRAC).toInt().coerceAtLeast(2)

        return maxHRun >= minHStroke && maxVRun >= minVStroke
    }

    // ====================================================================
    // Segno 检测
    // ====================================================================

    /**
     * Segno = S 曲线 + 圆点。判定依据：
     * 1. 双侧墨迹：包围盒左半和右半都有显著墨迹（S 曲线横跨两侧）
     * 2. 多行段墨迹分布：上/中/下三个垂直区间都有墨迹（S 曲线竖向延展）
     * 3. 非穹顶形状：排除 fermata（中心顶远高于两侧顶）
     */
    private fun isSegno(image: BinaryImage, blob: Blob): Boolean {
        if (!isBilateralInk(image, blob)) return false
        if (!isMultiBandVertical(image, blob)) return false
        if (isDomeShaped(image, blob)) return false
        return true
    }

    /**
     * 检查包围盒左半和右半是否都有显著墨迹。
     * Segno 的 S 曲线和两侧圆点使墨迹均匀分布在左右两侧。
     */
    private fun isBilateralInk(image: BinaryImage, blob: Blob): Boolean {
        val midX = (blob.minX + blob.maxX) / 2
        var leftInk = 0
        var rightInk = 0
        for (y in blob.minY..blob.maxY) {
            for (x in blob.minX until midX) {
                if (image.isBlack(x, y)) leftInk++
            }
            for (x in midX..blob.maxX) {
                if (image.isBlack(x, y)) rightInk++
            }
        }
        if (leftInk == 0 || rightInk == 0) return false
        val ratio = leftInk.toDouble() / rightInk
        return ratio in SEGNO_BILATERAL_MIN..SEGNO_BILATERAL_MAX
    }

    /**
     * 检查包围盒的上、中、下三个垂直区间是否都有墨迹。
     * Segno 的 S 曲线在多个高度产生墨迹。
     */
    private fun isMultiBandVertical(image: BinaryImage, blob: Blob): Boolean {
        val h = blob.maxY - blob.minY + 1
        val thirdH = h / 3
        if (thirdH < 1) return false

        val band1End = blob.minY + thirdH
        val band2End = blob.minY + 2 * thirdH

        var topInk = 0
        var midInk = 0
        var botInk = 0

        for (y in blob.minY..blob.maxY) {
            for (x in blob.minX..blob.maxX) {
                if (!image.isBlack(x, y)) continue
                when {
                    y < band1End -> topInk++
                    y < band2End -> midInk++
                    else -> botInk++
                }
            }
        }

        // 每个区间至少需要 2 个墨迹像素（排除单像素噪点）
        return topInk >= 2 && midInk >= 2 && botInk >= 2
    }

    /**
     * 检查是否为穹顶形状（fermata 特征）。
     * 如果中心列的最顶黑像素比两侧 1/4 处列的最顶黑像素高出 blob 高度的
     * [DOME_REJECT_FRAC]，则判定为穹顶。
     */
    private fun isDomeShaped(image: BinaryImage, blob: Blob): Boolean {
        val cx = blob.centerX
        val leftX = blob.minX + blob.width / 4
        val rightX = blob.maxX - blob.width / 4

        val centerTop = topMostBlack(image, cx, blob.minY, blob.maxY)
        val leftTop = topMostBlack(image, leftX, blob.minY, blob.maxY)
        val rightTop = topMostBlack(image, rightX, blob.minY, blob.maxY)

        if (centerTop == Int.MAX_VALUE || leftTop == Int.MAX_VALUE || rightTop == Int.MAX_VALUE) {
            return false
        }

        val threshold = (blob.height * DOME_REJECT_FRAC).toInt().coerceAtLeast(1)
        // 穹顶：中心顶（小 Y）比两侧顶（大 Y）高出 threshold
        return (leftTop - centerTop >= threshold) && (rightTop - centerTop >= threshold)
    }

    /** 找到列 x 在 [minY, maxY] 范围内的最顶（最小 Y）黑像素。 */
    private fun topMostBlack(image: BinaryImage, x: Int, minY: Int, maxY: Int): Int {
        for (y in minY..maxY) {
            if (image.isBlack(x, y)) return y
        }
        return Int.MAX_VALUE
    }
}
