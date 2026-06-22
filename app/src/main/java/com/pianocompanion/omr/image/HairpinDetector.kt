package com.pianocompanion.omr.image

/**
 * 渐强/渐弱符号(hairpin)检测器。
 *
 * Hairpin 是乐谱中用图形方式表示渐强(crescendo `<`)和渐弱(decrescendo `>`)
 * 的表情记号——两条从一端汇聚到另一端发散的斜线，位于谱表下方。
 * 与文字力度记号(pp/mf 等)互补：hairpin 表达**连续的**音量变化方向。
 *
 * **检测原理**：
 * 1. 在每个谱表系统的下方（底线之下 0.5~4 个谱线间距）搜索连通块
 * 2. 按尺寸筛选候选：宽度 ≥ 1.5 个间距（hairpin 跨越多个音符，远宽于字母）、
 *    高度 0.4~2.5 个间距、填充率 < 0.35（hairpin 是两条线的轮廓，非实心）
 * 3. **逐列竖直跨度分析**（核心判据）：将候选宽度分为左三分之一和右三分之一，
 *    分别计算每列的竖直墨迹跨度（该列最顶黑像素到最底黑像素的距离）。
 *    - **渐强(crescendo)**：右侧平均跨度 / 左侧平均跨度 ≥ 1.5（左窄右宽）
 *    - **渐弱(decrescendo)**：左侧平均跨度 / 右侧平均跨度 ≥ 1.5（左宽右窄）
 *
 * **与其他标记的区分**：
 * - 文字力度记号(pp/mf)：紧凑字母形状，宽度通常 < 1.5 间距，填充率高（>0.4）
 * - 休止符：在谱线之间而非谱表下方；锯齿形/旗形的逐列跨度变化不规则
 * - 符干：1-2px 宽的竖线，宽度远小于 1.5 间距
 * - 加线(ledger line)：短水平线，逐列跨度恒定（不发散/收敛）
 *
 * Hairpin 仅产生提示信息，不修改音符数据模型。
 *
 * 已知限制：当 hairpin 的两条线在窄端**不相交**（各自独立的斜线段）时，
 * 它们是两个独立的连通块，不会被此检测器识别为 hairpin。标准印刷乐谱中
 * 两条线通常在窄端相交形成 V 形连通块，此情况下检测可靠。
 */
object HairpinDetector {

    /**
     * Hairpin 类型。
     */
    enum class HairpinType {
        /** 渐强 `<`：左窄右宽 */
        CRESCENDO,
        /** 渐弱 `>`：左宽右窄 */
        DECRESCENDO
    }

    /**
     * 检测到的 hairpin。
     *
     * @param type 渐强或渐弱
     * @param startX 窄端 X 坐标
     * @param endX 宽端 X 坐标
     * @param centerY 中心 Y 坐标
     * @param systemIdx 所属谱表系统索引
     */
    data class Hairpin(
        val type: HairpinType,
        val startX: Int,
        val endX: Int,
        val centerY: Int,
        val systemIdx: Int
    )

    /** 候选最小宽度（谱线间距倍数）。 */
    private const val MIN_WIDTH_FRAC = 1.5

    /** 候选最小高度（谱线间距倍数）。 */
    private const val MIN_HEIGHT_FRAC = 0.4

    /** 候选最大高度（谱线间距倍数）。 */
    private const val MAX_HEIGHT_FRAC = 2.5

    /** 候选最大宽度（谱线间距倍数）。hairpin 可跨越多个小节，允许较宽。 */
    private const val MAX_WIDTH_FRAC = 20.0

    /** hairpin 最大填充率（hairpin 是两条线的轮廓，低填充率）。 */
    private const val MAX_FILL_RATIO = 0.35

    /** 跨度比阈值：右/左 ≥ 此值 → 渐强；左/右 ≥ 此值 → 渐弱。 */
    private const val EXTENT_RATIO_THRESHOLD = 1.5

    /**
     * 在每个谱表系统下方检测 hairpin。
     *
     * @param image 去谱线+降噪后的二值图像
     * @param blobs 连通块列表（与 image 一致的坐标系）
     * @param systems 谱表系统列表
     * @param lineSpacing 平均谱线间距
     * @return 检测到的 hairpin 列表（按系统、X 排序）
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<Hairpin> {
        if (lineSpacing < 1) return emptyList()
        val results = ArrayList<Hairpin>()

        systems.forEachIndexed { sysIdx, system ->
            val bottomY = system.bottomLine.center

            // 搜索区：底线之下 0.5~4 个谱线间距（与力度记号相同的区域）
            val searchTop = bottomY + (lineSpacing * 0.5).toInt()
            val searchBottom = (bottomY + lineSpacing * 4).coerceAtMost(image.height - 1)

            if (searchTop >= searchBottom) return@forEachIndexed

            // 过滤搜索区域内的候选墨块
            val candidates = blobs.filter { blob ->
                blob.centerY in searchTop..searchBottom &&
                    blob.minY >= searchTop - lineSpacing / 2 &&
                    blob.width >= (lineSpacing * MIN_WIDTH_FRAC).toInt() &&
                    blob.height >= (lineSpacing * MIN_HEIGHT_FRAC).toInt() &&
                    blob.height <= (lineSpacing * MAX_HEIGHT_FRAC).toInt() &&
                    blob.width <= (lineSpacing * MAX_WIDTH_FRAC).toInt()
            }.sortedBy { it.minX }

            for (blob in candidates) {
                // 填充率过滤：hairpin 是低密度轮廓
                val fillRatio = blob.area.toDouble() / (blob.width * blob.height)
                if (fillRatio >= MAX_FILL_RATIO) continue

                // 逐列竖直跨度分析
                val type = classifyByColumnExtent(image, blob, lineSpacing) ?: continue
                val startX: Int
                val endX: Int
                when (type) {
                    HairpinType.CRESCENDO -> {
                        startX = blob.minX  // 窄端在左
                        endX = blob.maxX    // 宽端在右
                    }
                    HairpinType.DECRESCENDO -> {
                        startX = blob.maxX  // 窄端在右
                        endX = blob.minX    // 宽端在左
                    }
                }
                results += Hairpin(type, startX, endX, blob.centerY, sysIdx)
            }
        }

        return results
    }

    /**
     * 逐列竖直跨度分析：将候选宽度分为左/右各三分之一，
     * 分别计算每列的竖直墨迹跨度（最顶黑像素到最底黑像素），
     * 比较两侧平均跨度判定渐强（右>左）或渐弱（左>右）。
     *
     * @return [HairpinType]，若不符合 hairpin 特征则 null
     */
    private fun classifyByColumnExtent(
        image: BinaryImage,
        blob: Blob,
        lineSpacing: Int
    ): HairpinType? {
        val bw = blob.width
        // 将宽度分为左三分之一和右三分之一（中间三分之一跳过，避免过渡区干扰）
        val leftStart = blob.minX
        val leftEnd = blob.minX + bw / 3
        val rightStart = blob.maxX - bw / 3 + 1
        val rightEnd = blob.maxX

        val leftAvg = averageColumnExtent(image, leftStart, leftEnd, blob.minY, blob.maxY)
        val rightAvg = averageColumnExtent(image, rightStart, rightEnd, blob.minY, blob.maxY)

        // 两侧都必须有有效墨迹
        if (leftAvg < 1.0 || rightAvg < 1.0) return null

        val ratioRightLeft = rightAvg / leftAvg

        return when {
            ratioRightLeft >= EXTENT_RATIO_THRESHOLD -> HairpinType.CRESCENDO
            (1.0 / ratioRightLeft) >= EXTENT_RATIO_THRESHOLD -> HairpinType.DECRESCENDO
            else -> null
        }
    }

    /**
     * 计算 [xStart..xEnd] 范围内每列的竖直墨迹跨度的平均值。
     * 每列跨度 = 该列在 [yMin..yMax] 范围内最顶黑像素到最底黑像素的距离。
     * 无黑像素的列不计入。
     */
    private fun averageColumnExtent(
        image: BinaryImage,
        xStart: Int,
        xEnd: Int,
        yMin: Int,
        yMax: Int
    ): Double {
        var sum = 0
        var count = 0
        for (x in xStart..xEnd) {
            if (x < 0 || x >= image.width) continue
            var topY = -1
            var botY = -1
            for (y in yMin..yMax) {
                if (image.isBlack(x, y)) {
                    if (topY < 0) topY = y
                    botY = y
                }
            }
            if (topY >= 0) {
                sum += (botY - topY + 1)
                count++
            }
        }
        return if (count > 0) sum.toDouble() / count else 0.0
    }
}
