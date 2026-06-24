package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Accidental

/**
 * [AccidentalDetector.detect] 的返回结果。
 *
 * @param byNotehead 符头索引 → 临时记号类型的映射。
 * @param accidentalBlobCenters 被识别为临时记号的连通块中心 X 坐标集合。
 *   管线据此从符头列表中排除这些位置上的伪符头（升号/降号/还原号被
 *   NoteheadDetector 误判为符头的情况）。
 */
data class AccidentalDetection(
    val byNotehead: Map<Int, Accidental>,
    val accidentalBlobCenters: Set<Int>
)

/**
 * 检测乐谱中写在音符前方的**临时记号**（accidental）：升号(♯)、降号(♭)、
 * 还原号(♮)。
 *
 * 临时记号是音乐中最常见的音高修饰符号。在 C 大调中一个 F 前面的升号把 F 变成 F#，
 * 在 G 大调中一个还原号把本该升的 F 还原为 F 自然音。此前 OMR 管线完全忽略临时记号，
 * 仅依赖调号（key signature）做音高修正——所有有临时升降的音符都被映射为错误的音高。
 *
 * ## 检测原理
 *
 * 临时记号是紧挨音符左侧（X 更小）的小型独立符号，竖直方向与音符中心对齐。
 * 搜索每个符头左侧 0.3~2.5 个谱线间距内的连通块，按以下几何特征分类：
 *
 * - **升号(♯)**：两根垂直笔画 + 两条对角交叉线。交叉线在两笔画之间的**内部区域**
 *   产生较多墨迹行（对角线跨越多行）。
 * - **还原号(♮)**：两根垂直笔画 + 两条水平交叉线。水平交叉线仅在特定行出现，
 *   内部区域的墨迹行更少。
 * - **降号(♭)**：一根垂直笔画 + 右下方圆弧凸起。只有一根垂直笔画。
 *
 * ### 关键判别
 *
 * 1. **垂直笔画数**：降号 1 根、升号/还原号各 2 根。逐列统计黑像素数 ≥55% 笔画高度
 *    的列，按连续性分组计数。
 * 2. **升号 vs 还原号**：检查两根笔画之间**内部区域**的墨迹行占比——升号的对角
 *    交叉线产生更多墨迹行（占比 >35%），还原号的水平交叉线仅 2 行（占比 ≤35%）。
 *
 * 纯 Kotlin 实现，无 Android 依赖，完全可单元测试。
 */
object AccidentalDetector {

    // 搜索窗口（相对于符头中心 + 谱线间距 s）
    private const val SEARCH_LEFT_FRAC = 2.5   // 最远向左搜索 2.5 个间距
    private const val SEARCH_GAP_FRAC = 0.3    // 距离符头左边缘至少 0.3 个间距
    private const val SEARCH_Y_FRAC = 1.2      // 竖直搜索 ±1.2 个间距

    // 候选尺寸约束（相对于谱线间距 s）
    private const val MIN_WIDTH_FRAC = 0.25
    private const val MAX_WIDTH_FRAC = 1.0
    private const val MIN_HEIGHT_FRAC = 0.5
    private const val MAX_HEIGHT_FRAC = 1.6
    private const val MIN_AREA_FRAC = 0.12   // area ≥ 0.12 * s^2

    // 垂直笔画判定
    private const val STROKE_COL_THRESHOLD = 0.55  // 列黑像素 ≥55% 笔画高度 = 垂直笔画列
    private const val STROKE_GAP_TOLERANCE = 1      // 允许 1 列间断仍归为同一笔画

    // 升号 vs 还原号判别
    private const val INTERIOR_INK_RATIO_THRESHOLD = 0.35  // 内部墨迹行占比阈值

    /**
     * 为每个符头检测其前方（左侧）的临时记号。
     *
     * @param cleaned 去谱线 + 降噪后的二值图像（临时记号在此图上保持完整）。
     * @param blobs cleaned 图像上的所有连通块。
     * @param noteheads 已检测到的符头列表（与 [systemIndices] 一一对应）。
     * @param systemIndices 每个符头所属的系统索引。
     * @param signatureEndXBySystem 每个系统的签名区右界 X（谱号/调号/拍号区域），
     *        临时记号搜索不会越过此界（避免把调号中的升降号误判为临时记号）。
     * @param lineSpacing 谱线间距（像素），用于缩放所有阈值。
     * @return 符头索引 → 临时记号类型的映射。未检测到的符头不出现在映射中。
     */
    fun detect(
        cleaned: BinaryImage,
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        signatureEndXBySystem: List<Int>,
        lineSpacing: Int
    ): AccidentalDetection {
        if (lineSpacing <= 0 || noteheads.isEmpty())
            return AccidentalDetection(emptyMap(), emptySet())

        val s = lineSpacing.toDouble()
        val result = HashMap<Int, Accidental>()
        val blobCenters = mutableSetOf<Int>()

        for (idx in noteheads.indices) {
            val nh = noteheads[idx]
            val sysIdx = systemIndices.getOrElse(idx) { -1 }
            if (sysIdx < 0) continue
            val sigEndX = signatureEndXBySystem.getOrElse(sysIdx) { 0 }

            // 搜索窗口
            val searchLeft = (nh.centerX - nh.width / 2 - SEARCH_LEFT_FRAC * s).toInt()
            val searchRight = (nh.centerX - nh.width / 2 - SEARCH_GAP_FRAC * s).toInt()
            val yMin = (nh.centerY - SEARCH_Y_FRAC * s).toInt()
            val yMax = (nh.centerY + SEARCH_Y_FRAC * s).toInt()

            // 在搜索窗口内找到候选临时记号连通块
            val candidate = blobs
                .filter { blob ->
                    blob.maxX in (sigEndX + 1)..searchRight &&
                        blob.minX >= searchLeft &&
                        blob.centerY in yMin..yMax
                }
                // 取最右侧（最靠近音符）的候选
                .maxByOrNull { it.maxX }
                ?: continue

            val accidental = classify(cleaned, candidate, s)
            if (accidental != Accidental.NONE) {
                result[idx] = accidental
                blobCenters.add(candidate.centerX)
            }
        }

        return AccidentalDetection(result, blobCenters)
    }

    /**
     * 基于几何特征分类候选连通块为升号/降号/还原号。
     */
    private fun classify(image: BinaryImage, blob: Blob, s: Double): Accidental {
        // 尺寸过滤
        val minW = (MIN_WIDTH_FRAC * s).toInt().coerceAtLeast(2)
        val maxW = (MAX_WIDTH_FRAC * s).toInt()
        val minH = (MIN_HEIGHT_FRAC * s).toInt().coerceAtLeast(3)
        val maxH = (MAX_HEIGHT_FRAC * s).toInt()
        val minArea = (MIN_AREA_FRAC * s * s).toInt().coerceAtLeast(4)

        if (blob.width !in minW..maxW) return Accidental.NONE
        if (blob.height !in minH..maxH) return Accidental.NONE
        if (blob.area < minArea) return Accidental.NONE

        // 垂直笔画检测
        val strokeGroups = findVerticalStrokeGroups(image, blob)
        val strokeCount = strokeGroups.size

        return when {
            strokeCount == 1 -> Accidental.FLAT
            strokeCount == 2 -> {
                // 升号 vs 还原号：内部区域墨迹行占比
                val interiorInkRatio = computeInteriorInkRatio(image, blob, strokeGroups)
                if (interiorInkRatio > INTERIOR_INK_RATIO_THRESHOLD) {
                    Accidental.SHARP
                } else {
                    Accidental.NATURAL
                }
            }
            else -> Accidental.NONE
        }
    }

    /**
     * 逐列统计黑像素数，找出"垂直笔画列"（黑像素 ≥ [STROKE_COL_THRESHOLD] × 笔画高度），
     * 按连续性（允许 [STROKE_GAP_TOLERANCE] 列间断）分组。
     *
     * @return 每个笔画组的 (minX, maxX) 列范围列表。
     */
    private fun findVerticalStrokeGroups(image: BinaryImage, blob: Blob): List<IntRange> {
        val h = blob.height
        val threshold = (h * STROKE_COL_THRESHOLD).toInt().coerceAtLeast(2)
        val colCounts = IntArray(blob.width)
        for (xi in 0 until blob.width) {
            var c = 0
            for (y in blob.minY..blob.maxY) {
                if (image.isBlack(blob.minX + xi, y)) c++
            }
            colCounts[xi] = c
        }

        val groups = ArrayList<IntRange>()
        var runStart = -1
        var gapLen = 0
        for (xi in 0 until blob.width) {
            if (colCounts[xi] >= threshold) {
                if (runStart < 0) runStart = xi
                gapLen = 0
            } else {
                if (runStart >= 0) {
                    gapLen++
                    if (gapLen > STROKE_GAP_TOLERANCE) {
                        groups += runStart..(xi - gapLen)
                        runStart = -1
                        gapLen = 0
                    }
                }
            }
        }
        if (runStart >= 0) {
            groups += runStart..(blob.width - 1)
        }
        return groups
    }

    /**
     * 计算两根垂直笔画之间的"内部区域"中，有多少行存在黑像素。
     *
     * 升号(♯)的对角交叉线在内部区域跨越多行（高占比），
     * 还原号(♮)的水平交叉线仅 2 行（低占比）。
     *
     * @param strokeGroups 两根笔画组的列范围（取 [0] 右界到 [1] 左界之间为内部）
     * @return 内部区域有墨迹的行数 / 总行数
     */
    private fun computeInteriorInkRatio(
        image: BinaryImage,
        blob: Blob,
        strokeGroups: List<IntRange>
    ): Double {
        if (strokeGroups.size < 2) return 0.0
        val leftStrokeEnd = strokeGroups[0].last
        val rightStrokeStart = strokeGroups[1].first

        // 内部列范围（两笔画之间）
        val interiorStart = leftStrokeEnd + 1
        val interiorEnd = rightStrokeStart - 1
        if (interiorEnd < interiorStart) return 0.0

        var inkRows = 0
        for (y in blob.minY..blob.maxY) {
            var hasInk = false
            for (xi in interiorStart..interiorEnd) {
                if (image.isBlack(blob.minX + xi, y)) {
                    hasInk = true
                    break
                }
            }
            if (hasInk) inkRows++
        }
        return inkRows.toDouble() / blob.height.coerceAtLeast(1)
    }
}
