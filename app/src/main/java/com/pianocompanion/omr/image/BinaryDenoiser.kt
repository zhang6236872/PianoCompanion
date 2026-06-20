package com.pianocompanion.omr.image

/**
 * 二值图像降噪模块（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 真实拍照 / 扫描的乐谱经二值化后常带有两类噪声：
 *
 *  - **椒噪声 (pepper)** —— 纸面上的孤立黑色斑点（传感器噪声、JPEG 块效应、
 *    自适应二值化在瓦片边界处的少量伪迹）。这些斑点会被下游连通块标记成
 *    虚假组件，干扰谱线检测的水平投影、制造伪符头/伪休止符。
 *  - **盐噪声 (salt)** —— 实心笔画内部的白色孔洞（符头/符干内部的灰度跳变）。
 *    这会降低 [RhythmAnalyzer] 的「填充率」判定准确性：实心符头若布满孔洞，
 *    黑像素占比下降，可能被误判为空心（二分/全音符）；也会打断符干的
 *    连续游程检测。
 *
 * 本模块提供两个**保守**的清理操作，二者均**不破坏 1px 谱线**等细笔画：
 *
 *  1. [removePepper]：基于 8-连通组件面积阈值，擦除面积过小的黑色组件。
 *     一条贯穿全图的 1px 谱线是一个面积 = 图宽的大组件，必然保留。
 *  2. [fillSalt]：把「8 邻域中黑邻居数 ≥ 阈值」的白色像素填黑。保守默认
 *     （6/8）只填充近乎被墨迹完全包围的孔洞，绝不会触碰谱线之间的白色
 *     间隙或背景。
 *
 * [denoise] 依次执行两步，并返回统计信息供管线给出降噪提示。
 */
object BinaryDenoiser {

    /**
     * 降噪统计。
     *
     * @property pepperRemoved 被擦除的椒噪声（黑色斑点）像素总数。
     * @property saltFilled    被填充的盐噪声（白色孔洞）像素总数。
     */
    data class Stats(val pepperRemoved: Int, val saltFilled: Int) {
        /** 被修改的像素总数。 */
        val totalChanged: Int get() = pepperRemoved + saltFilled
    }

    /**
     * 擦除孤立黑色斑点（椒噪声）：面积小于 [pepperMinArea] 的 8-连通黑色
     * 组件全部置白。
     *
     * 采用自包含的迭代洪填充（显式栈，无递归，大图安全），单趟完成「标记 +
     * 筛选 + 输出」——只有面积达标的组件被复制到结果图。
     *
     * @param pepperMinArea 黑色组件保留所需的最小像素数（含端点的 1px 谱线
     *        面积 = 其长度，远超此阈值，因此细笔画安全）。
     * @return 降噪后的新图像，以及被擦除的像素数。
     */
    fun removePepper(
        image: BinaryImage,
        pepperMinArea: Int = DEFAULT_PEPPER_MIN_AREA
    ): Pair<BinaryImage, Int> {
        val w = image.width
        val h = image.height
        val src = image.pixels
        val out = BooleanArray(w * h) // 默认全白，仅复制达标组件
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val component = ArrayList<Int>()

        var removed = 0
        for (seed in src.indices) {
            if (!src[seed] || visited[seed]) continue

            component.clear()
            var sp = 0
            stack[sp++] = seed
            visited[seed] = true
            while (sp > 0) {
                val cur = stack[--sp]
                component += cur
                val cx = cur % w
                val cy = cur / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val nidx = ny * w + nx
                    if (src[nidx] && !visited[nidx]) {
                        visited[nidx] = true
                        stack[sp++] = nidx
                    }
                }
            }

            if (component.size >= pepperMinArea) {
                for (idx in component) out[idx] = true
            } else {
                removed += component.size
            }
        }
        return BinaryImage(w, h, out) to removed
    }

    /**
     * 填充实心笔画内部的白色孔洞（盐噪声）：8 邻域中黑邻居数 ≥
     * [saltMinNeighbors] 的白色像素被置黑。
     *
     * 保守默认（[DEFAULT_SALT_MIN_NEIGHBORS] = 6/8）只填充近乎被墨迹完全
     * 包围的孔洞——符头/符干内部的 1px 裂隙。谱线之间的白色间隙（仅有
     * 上下 2 个黑邻居）和背景（0 个）远低于阈值，绝不误填。
     *
     * @param saltMinNeighbors 白色像素被填黑所需的（8 邻域中）最小黑邻居数。
     * @return 填充后的新图像，以及被填充的像素数。
     */
    fun fillSalt(
        image: BinaryImage,
        saltMinNeighbors: Int = DEFAULT_SALT_MIN_NEIGHBORS
    ): Pair<BinaryImage, Int> {
        val w = image.width
        val h = image.height
        val src = image.pixels
        val out = src.copyOf()
        var filled = 0

        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                val idx = base + x
                if (src[idx]) continue // 仅处理白像素
                var blackNeighbors = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    if (image.isBlack(x + dx, y + dy)) blackNeighbors++
                }
                if (blackNeighbors >= saltMinNeighbors) {
                    out[idx] = true
                    filled++
                }
            }
        }
        return BinaryImage(w, h, out) to filled
    }

    /**
     * 组合降噪：先 [removePepper]（擦除孤立斑点），再 [fillSalt]（填充内部
     * 孔洞）。先去椒后填盐的顺序，确保被噪声斑点「撑开」的笔画在填盐前已
     * 清理干净，避免把噪声周围的孔洞误判为笔画孔洞。
     *
     * @return 降噪后的图像及其 [Stats] 统计。
     */
    fun denoise(
        image: BinaryImage,
        pepperMinArea: Int = DEFAULT_PEPPER_MIN_AREA,
        saltMinNeighbors: Int = DEFAULT_SALT_MIN_NEIGHBORS
    ): Pair<BinaryImage, Stats> {
        val (afterPepper, pepperRemoved) = removePepper(image, pepperMinArea)
        val (afterSalt, saltFilled) = fillSalt(afterPepper, saltMinNeighbors)
        return afterSalt to Stats(pepperRemoved, saltFilled)
    }

    /** 椒噪声擦除的默认面积阈值（与管线 [ConnectedComponents] 的 minPixels 一致）。 */
    const val DEFAULT_PEPPER_MIN_AREA: Int = 4

    /** 盐噪声填充的默认黑邻居阈值（8 邻域中至少 6 个为黑）。 */
    const val DEFAULT_SALT_MIN_NEIGHBORS: Int = 6
}
