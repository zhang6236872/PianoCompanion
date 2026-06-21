package com.pianocompanion.omr.image

import kotlin.math.abs

/**
 * 检测到的小节线（barline）。
 *
 * @param centerX 水平中心 X 坐标
 * @param type    小节线类型（[BarlineType]）
 * @param width   竖线总宽度（像素）；双线/终止线/重复线为多根线合并后的宽度
 */
data class Barline(
    val centerX: Int,
    val type: BarlineType,
    val width: Int
)

/**
 * 小节线类型。
 *
 * - [SINGLE]        单竖线 `|`：分隔小节，最常见
 * - [DOUBLE]        双竖线 `||`：标记调号/拍号/风格变化
 * - [FINAL]         终止线 `|▎`：细线 + 粗线，乐曲结束
 * - [REPEAT_START]  反复开始 `‖:`：竖线 + 右侧两点
 * - [REPEAT_END]    反复结束 `:‖`：左侧两点 + 竖线
 * - [DASHED]        虚线/段线 `┊`：小节内细分（**不计入 measureIndex**）
 */
enum class BarlineType { SINGLE, DOUBLE, FINAL, REPEAT_START, REPEAT_END, DASHED }

/**
 * 纯 Kotlin 小节线检测器（无 Android 依赖，完全可单元测试）。
 *
 * 小节线是乐谱中最基本的垂直结构元素——贯穿整个五线谱高度（从最上线到最下线）
 * 的细竖线，将音乐分割成小节。此前 OMR 管线完全忽略小节线，导致
 * [com.pianocompanion.data.model.ScoreNote.measureIndex] 只能通过时间估算
 * （`startTime / measureMs`），在节奏估计有偏差时小节归属也跟着出错。
 *
 * ## 检测原理（实心竖线）
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
 * ## 实心竖线类型分类
 *
 * 按竖线群的宽度和间距模式分类（从左到右扫描相邻群）：
 * - 单独细群（宽 ≤ 0.5 谱线间距）→ **SINGLE**
 * - 两个相邻细群（间距 ≤ 1.5 谱线间距）→ **DOUBLE**
 * - 细群 + 紧邻粗群（宽 > 0.5 且 ≤ 1.0 谱线间距）→ **FINAL**（终止线）
 *
 * ## 反复记号（repeat）检测
 *
 * 反复开始 `‖:` / 反复结束 `:‖` 的标志特征是**两个圆点**——位于谱线间（间）内、
 * 竖直相距约 1 个谱线间距的小型紧凑黑色团块。对每条候选竖线，在其左右两侧
 * （≤1.5 谱线间距）扫描谱线间区域，检测圆点：
 * - 右侧有 ≥2 个圆点 → **REPEAT_START**
 * - 左侧有 ≥2 个圆点 → **REPEAT_END**
 *
 * 圆点判定：间内紧凑黑色团块，宽高均 ≤ 0.5 谱线间距（远小于符头），黑像素 ≥2。
 * 这天然排除符头（直径 >0.5 间距）和噪点（仅 1 像素）。
 *
 * ## 虚线/段线（dashed）检测
 *
 * 虚线小节线是带规则间隙的竖线，用于在**同一小节内**做视觉细分（例如 5/4 拍
 * 划分为 3+2）。其竖直投影填充率介于空列（12%）与实心线（80%）之间（约 35-75%），
 * 且列内存在规则的「墨-隙」交替。
 *
 * 检测：第二趟扫描填充率 35%≤f<80% 的列群，再验证**间内有 ≥3 次黑白跳变**
 * （每段虚线在间内制造一次进出跳变；连续符干 0 跳变；空列 0 跳变）。
 * **DASHED 不计入 measureIndex**——它是小节内细分而非小节边界。
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

        // ---- 2. 实心竖线群（填充率 ≥ 80%）----
        val solidFill = (bandH * 0.80).toInt()
        val solidGroups = collectGroups(colBlack, sx, image.width) { it >= solidFill }

        val minWidth = (s * 0.12).toInt().coerceAtLeast(2)
        val maxBarWidth = (s * 1.0).toInt()          // 单根竖线最大宽度
        val maxFinalWidth = (s * 2.0).toInt()         // 终止线/反复线总宽（细+粗+间距）
        val noteheadTol = (0.4 * s).toInt().coerceAtLeast(3)
        val dotGapMax = (1.5 * s).toInt()             // 圆点距竖线最大间距

        fun overlapsNotehead(cx: Int): Boolean =
            noteheadXs.isNotEmpty() && noteheadXs.any { abs(it - cx) <= noteheadTol }

        val solidFiltered = solidGroups.filter { g ->
            if (g.width < minWidth) return@filter false
            if (g.width > maxFinalWidth) return@filter false
            if (overlapsNotehead(g.centerX)) return@filter false
            true
        }

        val result = ArrayList<Barline>()

        // ---- 3. 实心竖线分类（含反复记号圆点检测）----
        val thinMax = (0.5 * s).toInt().coerceAtLeast(minWidth)  // 细竖线最大宽度
        val pairGapMax = (1.5 * s).toInt()                        // 配对竖线最大间距

        var i = 0
        while (i < solidFiltered.size) {
            val g = solidFiltered[i]

            // 尝试与下一个群配对（双竖线 / 终止线 / 反复线）
            if (i + 1 < solidFiltered.size) {
                val next = solidFiltered[i + 1]
                val gap = next.start - g.end - 1
                if (gap in 1..pairGapMax) {
                    val leftX = g.start
                    val rightX = next.end
                    val dotsRight = countRepeatDots(image, rightX + 1, rightX + dotGapMax, system, s)
                    val dotsLeft = countRepeatDots(image, leftX - dotGapMax, leftX - 1, system, s)
                    val combinedW = rightX - leftX + 1
                    when {
                        dotsRight >= 2 ->
                            result.add(Barline((leftX + rightX) / 2, BarlineType.REPEAT_START, combinedW))
                        dotsLeft >= 2 ->
                            result.add(Barline((leftX + rightX) / 2, BarlineType.REPEAT_END, combinedW))
                        // FINAL（终止线）：细线 + 紧邻粗线
                        g.width <= thinMax && next.width > thinMax && next.width <= maxBarWidth ->
                            result.add(Barline((leftX + rightX) / 2, BarlineType.FINAL, combinedW))
                        // DOUBLE（双竖线）：两条相邻细线
                        g.width <= thinMax && next.width <= thinMax ->
                            result.add(Barline((leftX + rightX) / 2, BarlineType.DOUBLE, combinedW))
                        else -> {
                            // 无法配对，当前群作为 SINGLE，下一群留待后续
                            result.add(Barline(g.centerX, BarlineType.SINGLE, g.width))
                            i++
                            continue
                        }
                    }
                    i += 2
                    continue
                }
            }

            // 单独竖线：检测反复记号圆点
            val leftX = g.start
            val rightX = g.end
            val dotsRight = countRepeatDots(image, rightX + 1, rightX + dotGapMax, system, s)
            val dotsLeft = countRepeatDots(image, leftX - dotGapMax, leftX - 1, system, s)
            when {
                dotsRight >= 2 -> result.add(Barline(g.centerX, BarlineType.REPEAT_START, g.width))
                dotsLeft >= 2 -> result.add(Barline(g.centerX, BarlineType.REPEAT_END, g.width))
                else -> result.add(Barline(g.centerX, BarlineType.SINGLE, g.width))
            }
            i++
        }

        // ---- 4. 虚线/段线群（填充率 35% ≤ f < 80%）----
        val dashFillLow = (bandH * 0.35).toInt()
        if (dashFillLow < solidFill) {
            val dashedRaw = collectGroups(colBlack, sx, image.width) { it >= dashFillLow && it < solidFill }
            for (g in dashedRaw) {
                if (g.width < minWidth || g.width > maxFinalWidth) continue
                if (overlapsNotehead(g.centerX)) continue
                // 排除与实心竖线相邻的边缘列（粗线/双线的渐变边缘）
                if (solidFiltered.any { sg -> g.start <= sg.end + 1 && g.end >= sg.start - 1 }) continue
                if (isDashedBarline(image, g, system, colBlack)) {
                    result.add(Barline(g.centerX, BarlineType.DASHED, g.width))
                }
            }
        }

        return result.sortedBy { it.centerX }
    }

    // ---- 内部：竖线群收集 ------------------------------------------------- //

    private data class Group(val start: Int, val end: Int) {
        val width: Int get() = end - start + 1
        val centerX: Int get() = (start + end) / 2
    }

    /** 将满足 [keep] 谓词的连续列分组成 [Group]。 */
    private fun collectGroups(
        colBlack: IntArray,
        fromX: Int,
        toX: Int,
        keep: (Int) -> Boolean
    ): List<Group> {
        val out = ArrayList<Group>()
        var gStart = -1
        for (x in fromX until toX) {
            if (keep(colBlack[x])) {
                if (gStart < 0) gStart = x
            } else {
                if (gStart >= 0) {
                    out.add(Group(gStart, x - 1))
                    gStart = -1
                }
            }
        }
        if (gStart >= 0) out.add(Group(gStart, toX - 1))
        return out
    }

    // ---- 内部：反复记号圆点检测 ------------------------------------------- //

    /**
     * 在水平区域 `[x0..x1]`、谱带范围内检测反复记号圆点。
     *
     * 遍历每个谱线间（间），统计间内紧凑黑色团块。圆点判定：
     * - 黑像素 ≥ 2（排除 1 像素噪点）
     * - 团块宽高均 ≤ 0.5 谱线间距（排除符头等大字形）
     *
     * **连通性过滤**：最后用「白行间隔」合并相连的圆点候选——一个横跨中线的
     * 大符头会在相邻两个间内各留一半，各自通过尺寸过滤，但二者之间无白行间隔
     * （符头墨迹连续穿过中线）。真正的两个圆点之间必有白行分隔。据此把相连的
     * 候选归为同一组（连通块），只返回**独立组数**。
     *
     * @return 独立圆点组数（相连候选算 1 组）。
     */
    private fun countRepeatDots(
        image: BinaryImage,
        x0: Int,
        x1: Int,
        system: StaffSystem,
        s: Int
    ): Int {
        val xa = x0.coerceIn(0, image.width - 1)
        val xb = x1.coerceIn(0, image.width - 1)
        if (xb < xa) return 0
        val lines = system.lines
        if (lines.size < 2) return 0

        val maxDotSize = (0.5 * s).toInt().coerceAtLeast(3)

        // 每个间内的圆点候选（竖直范围）
        data class DotSpan(val topY: Int, val botY: Int)
        val dots = ArrayList<DotSpan>()
        for (k in 0 until lines.size - 1) {
            // 排除谱线 ±1 行（谱线本身是黑的，会干扰间内团块统计）
            val spaceTop = lines[k].center + 2
            val spaceBot = lines[k + 1].center - 2
            if (spaceBot < spaceTop) continue

            var black = 0
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            for (x in xa..xb) {
                for (y in spaceTop..spaceBot) {
                    if (image.isBlack(x, y)) {
                        black++
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                    }
                }
            }
            if (black >= 2) {
                val h = maxY - minY + 1
                val w = maxX - minX + 1
                if (h <= maxDotSize && w <= maxDotSize) {
                    dots.add(DotSpan(minY, maxY))
                }
            }
        }
        if (dots.size < 2) return dots.size

        // 连通性过滤：相邻圆点候选间须有白行间隔，否则视为同一连通块（如横跨中线的符头）
        var groups = 1
        var prevBotY = dots[0].botY
        for (idx in 1 until dots.size) {
            val d = dots[idx]
            var hasGap = false
            for (y in (prevBotY + 1) until d.topY) {
                var rowHasBlack = false
                for (x in xa..xb) {
                    if (image.isBlack(x, y)) {
                        rowHasBlack = true
                        break
                    }
                }
                if (!rowHasBlack) {
                    hasGap = true
                    break
                }
            }
            if (hasGap) groups++
            prevBotY = maxOf(prevBotY, d.botY)
        }
        return groups
    }

    // ---- 内部：虚线/段线验证 ---------------------------------------------- //

    /**
     * 验证候选群是否为虚线/段线小节线。
     *
     * 取群内填充率最高的列为代表列，在谱线**间**内（排除谱线 ±1 行）统计：
     * 1. **黑白跳变次数**：虚线在间内有规则的「墨段-间隙」交替，制造 ≥3 次跳变；
     *    连续符干在间内全黑（0 跳变）；空列全白（0 跳变）。
     * 2. **竖直跨度**：虚线小节线贯穿大部分谱高，间内墨迹跨度 ≥ 0.5 谱带高度；
     *    反复记号圆点仅占据中央 2 个间（跨度小），据此排除——圆点列虽可达 ~37%
     *    填充率并产生跳变，但其墨迹集中在谱表中央，跨度远小于 0.5 谱带高。
     */
    private fun isDashedBarline(
        image: BinaryImage,
        group: Group,
        system: StaffSystem,
        colBlack: IntArray
    ): Boolean {
        val topY = system.topLine.center
        val botY = system.bottomLine.center
        val bandH = botY - topY + 1

        // 谱线行集合（±1），统计时排除（谱线恒黑，会干扰间内模式判定）
        val lineRows = HashSet<Int>()
        for (ln in system.lines) {
            for (d in -1..1) lineRows.add(ln.center + d)
        }

        // 代表列：群内黑像素最多的列
        var cx = group.centerX
        var best = -1
        for (x in group.start..group.end) {
            if (colBlack[x] > best) {
                best = colBlack[x]
                cx = x
            }
        }

        var transitions = 0
        var prev: Boolean? = null
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (y in topY..botY) {
            if (y in lineRows) continue
            val cur = image.isBlack(cx, y)
            if (cur) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            if (prev != null && cur != prev) transitions++
            prev = cur
        }
        if (minY == Int.MAX_VALUE) return false  // 间内全白
        val extent = maxY - minY + 1
        // ≥3 次跳变（多段虚线）+ 跨度 ≥ 0.5 谱带高（排除中央圆点）
        return transitions >= 3 && extent >= (bandH * 0.5).toInt().coerceAtLeast(4)
    }
}
