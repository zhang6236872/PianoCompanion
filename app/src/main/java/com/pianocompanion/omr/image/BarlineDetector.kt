package com.pianocompanion.omr.image

import kotlin.math.abs

/**
 * 检测到的小节线（barline）。
 *
 * @param centerX 水平中心 X 坐标
 * @param type    小节线类型（[BarlineType]）
 * @param width   竖线总宽度（像素）；双线/终止线为两根线合并后的宽度
 */
data class Barline(
    val centerX: Int,
    val type: BarlineType,
    val width: Int
)

/**
 * 小节线类型。
 *
 * - [SINGLE] 单竖线 `|`：分隔小节，最常见
 * - [DOUBLE] 双竖线 `||`：标记调号/拍号/风格变化
 * - [FINAL]  终止线 `|▎`：细线 + 粗线，乐曲结束
 */
enum class BarlineType { SINGLE, DOUBLE, FINAL }

/**
 * 纯 Kotlin 小节线检测器（无 Android 依赖，完全可单元测试）。
 *
 * 小节线是乐谱中最基本的垂直结构元素——贯穿整个五线谱高度（从最上线到最下线）
 * 的细竖线，将音乐分割成小节。此前 OMR 管线完全忽略小节线，导致
 * [com.pianocompanion.data.model.ScoreNote.measureIndex] 只能通过时间估算
 * （`startTime / measureMs`），在节奏估计有偏差时小节归属也跟着出错。
 *
 * ## 检测原理
 *
 * 在**含谱线**的二值图上（去谱线前的 `warped` 图），对五线谱带 `[topY..botY]` 内
 * 每一列做**竖直投影**（统计该列在带内的黑像素数）。
 *
 * | 列类型     | 谱线行(5) | 行间空隙 | 填充率  |
 * |-----------|----------|---------|---------|
 * | 小节线列   | 全黑      | 全黑     | ≈100%  |
 * | 普通空列   | 全黑      | 全白     | ≈12%   |
 * | 符头列     | 全黑      | ~6 行黑  | ≈27%   |
 * | 符干列(1px)| 全黑      | ~25 行黑 | ≈68%   |
 *
 * 两个过滤器排除非小节线竖线：
 * 1. **填充率阈值** ≥ 80%：排除符头（27%）、普通列（12%）、以及大多数符干（68%）
 * 2. **最小宽度** ≥ max(2, lineSpacing×0.12)：排除 1px 宽的符干
 *
 * 可选的**符头排除**：传入已知符头 X 坐标后，与符头重叠（容差 0.4 谱线间距）的
 * 竖线被排除，进一步防止罕见的 2px 宽超长符干被误判。
 *
 * ## 类型分类
 *
 * 按竖线群的宽度和间距模式分类（从左到右扫描相邻群）：
 * - 单独细群（宽 ≤ 0.5 谱线间距）→ **SINGLE**
 * - 两个相邻细群（间距 ≤ 1.5 谱线间距）→ **DOUBLE**
 * - 细群 + 紧邻粗群（宽 > 0.5 且 ≤ 1.0 谱线间距）→ **FINAL**（终止线）
 *
 * 全程只读取 [BinaryImage] 像素，不修改任何状态。
 *
 * @see StaffSystem 谱表系统，提供 topLine / bottomLine / lineSpacing
 */
object BarlineDetector {

    /**
     * 在含谱线的二值图上检测小节线。
     *
     * @param image         含谱线的二值图（去谱线前的原图，小节线需贯穿全谱高）。
     * @param system        当前谱表系统。
     * @param signatureEndX 签名区（谱号/调号/拍号）右边缘 X 坐标；此值左侧的列被跳过。
     *                      默认 0（不跳过）。
     * @param noteheadXs    已检测到的符头 X 坐标列表，用于排除符干伪小节线。
     *                      默认空列表（不排除）。
     * @return 小节线列表，按 X 坐标排序。
     */
    fun detect(
        image: BinaryImage,
        system: StaffSystem,
        signatureEndX: Int = 0,
        noteheadXs: List<Int> = emptyList()
    ): List<Barline> {
        val s = system.lineSpacing
        if (s <= 0) return emptyList()

        val topY = system.topLine.center
        val botY = system.bottomLine.center
        val bandH = botY - topY + 1
        if (bandH <= 0) return emptyList()

        val sx = signatureEndX.coerceAtLeast(0)

        // ---- 1. 竖直投影：每列在谱带 [topY..botY] 内的黑像素数 ----
        val colBlack = IntArray(image.width)
        for (x in sx until image.width) {
            var cnt = 0
            for (y in topY..botY) {
                if (image.isBlack(x, y)) cnt++
            }
            colBlack[x] = cnt
        }

        // ---- 2. 分组：填充率 ≥ 80% 的连续列形成候选群 ----
        val fillThreshold = (bandH * 0.80).toInt()
        data class Group(val start: Int, val end: Int) {
            val width: Int get() = end - start + 1
            val centerX: Int get() = (start + end) / 2
        }
        val raw = ArrayList<Group>()
        var gStart = -1
        for (x in sx until image.width) {
            if (colBlack[x] >= fillThreshold) {
                if (gStart < 0) gStart = x
            } else {
                if (gStart >= 0) {
                    raw.add(Group(gStart, x - 1))
                    gStart = -1
                }
            }
        }
        if (gStart >= 0) raw.add(Group(gStart, image.width - 1))

        // ---- 3. 过滤 ----
        val minWidth = (s * 0.12).toInt().coerceAtLeast(2)
        val maxBarWidth = (s * 1.0).toInt()          // 单根竖线最大宽度
        val maxFinalWidth = (s * 2.0).toInt()         // 终止线总宽（细+粗+间距）
        val noteheadTol = (0.4 * s).toInt().coerceAtLeast(3)

        val groups = raw.filter { g ->
            val w = g.width
            // 宽度范围：太窄（符干）或太宽（非竖线）的群被排除
            if (w < minWidth) return@filter false
            if (w > maxFinalWidth) return@filter false
            // 符头排除：群中心附近若有符头，很可能是符干而非小节线
            if (noteheadXs.isNotEmpty() && noteheadXs.any { abs(it - g.centerX) <= noteheadTol }) {
                return@filter false
            }
            true
        }

        if (groups.isEmpty()) return emptyList()

        // ---- 4. 分类：SINGLE / DOUBLE / FINAL ----
        val thinMax = (0.5 * s).toInt().coerceAtLeast(minWidth)  // 细竖线最大宽度
        val pairGapMax = (1.5 * s).toInt()                        // 配对竖线最大间距

        val barlines = ArrayList<Barline>()
        var i = 0
        while (i < groups.size) {
            val g = groups[i]
            // 尝试与下一个群配对（双竖线 / 终止线）
            if (i + 1 < groups.size) {
                val next = groups[i + 1]
                val gap = next.start - g.end - 1
                if (gap in 1..pairGapMax) {
                    // FINAL（终止线）：细线 + 紧邻粗线
                    if (g.width <= thinMax && next.width > thinMax && next.width <= maxBarWidth) {
                        barlines.add(Barline((g.start + next.end) / 2, BarlineType.FINAL, next.end - g.start + 1))
                        i += 2
                        continue
                    }
                    // DOUBLE（双竖线）：两条相邻细线
                    if (g.width <= thinMax && next.width <= thinMax) {
                        barlines.add(Barline((g.start + next.end) / 2, BarlineType.DOUBLE, next.end - g.start + 1))
                        i += 2
                        continue
                    }
                }
            }
            // SINGLE（单独细线，或粗线单独出现）
            barlines.add(Barline(g.centerX, BarlineType.SINGLE, g.width))
            i++
        }

        return barlines.sortedBy { it.centerX }
    }
}
