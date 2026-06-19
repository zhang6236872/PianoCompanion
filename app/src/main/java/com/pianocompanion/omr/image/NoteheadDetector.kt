package com.pianocompanion.omr.image

/**
 * A localized notehead candidate.
 */
data class Notehead(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val area: Int
)

/**
 * Selects notehead-like blobs from a de-staffed binary image.
 *
 * Heuristics (scaled by staff line spacing `s`):
 *  - a filled notehead is roughly one staff space wide (~ `s`);
 *  - its bounding box is compact (not a long stem or beam);
 *  - its aspect ratio is close to 1.
 *
 * When an optional [BinaryImage] is supplied, a **secondary pass** additionally
 * recovers noteheads that are physically fused with their stem (a common case
 * in real scores): such a blob is tall (stem included) but its notehead portion
 * is the widest horizontal band. The widest band's centroid is then reported as
 * the notehead. This lets downstream rhythm analysis detect stems/beams on real
 * photographed scores where stem and notehead form one connected component.
 */
object NoteheadDetector {

    /**
     * @param blobs all connected components of the cleaned image.
     * @param lineSpacing staff line spacing (px) used to scale the thresholds.
     * @param image optional cleaned image enabling the notehead+stem recovery pass.
     */
    fun detect(blobs: List<Blob>, lineSpacing: Int, image: BinaryImage? = null): List<Notehead> {
        if (lineSpacing <= 0) return emptyList()
        val s = lineSpacing.toDouble()

        val minDim = (0.5 * s).toInt().coerceAtLeast(3)
        val maxDim = (2.5 * s).toInt()
        val minArea = (0.25 * s * s).toInt().coerceAtLeast(4)
        val maxStemBlobHeight = (5.0 * s).toInt() // 符头+符干的最大合理高度

        val results = ArrayList<Notehead>()

        for (blob in blobs) {
            val compactWidth = blob.width in minDim..maxDim
            val compactHeight = blob.height in minDim..maxDim

            // 主扫描：紧凑的符头形连通块。
            if (compactWidth && compactHeight && blob.area >= minArea && blob.aspectRatio in 0.5..2.0) {
                results += Notehead(blob.centerX, blob.centerY, blob.width, blob.height, blob.area)
                continue
            }

            // 二次扫描：宽度合适但偏高（符头+符干融合块），恢复最宽处的符头中心。
            if (image != null && compactWidth &&
                blob.height in (maxDim + 1)..maxStemBlobHeight
            ) {
                recoverNotehead(image, blob, s)?.let { results += it }
            }
        }

        return results.sortedBy { it.centerX }
    }

    /**
     * 在一个"符头+符干"融合块中，定位最宽的水平带（符头所在）并以其几何中心
     * 作为符头。符干很细，最宽带必然落在符头上。
     */
    /**
     * 在一个"符头+符干"融合块中，定位最宽的水平带（符头所在）并以其几何中心
     * 作为符头。符干很细，最宽带必然落在符头上。
     *
     * 注意：空心符头(环状)每行的黑像素较少，因此最宽阈值用 0.35 个谱线间距
     * （仍远高于符干的 ~1px 行宽），以同时兼容实心与空心符头。
     */
    private fun recoverNotehead(image: BinaryImage, blob: Blob, s: Double): Notehead? {
        val rowWidth = IntArray(blob.height)
        for (y in blob.minY..blob.maxY) {
            var c = 0
            for (x in blob.minX..blob.maxX) {
                if (image.isBlack(x, y)) c++
            }
            rowWidth[y - blob.minY] = c
        }
        val maxCount = rowWidth.maxOrNull() ?: return null
        val minNoteheadWidth = (0.35 * s).toInt().coerceAtLeast(3)
        if (maxCount < minNoteheadWidth) return null // 最宽处仍太窄，不像符头

        // 符头带 = 宽度 ≥ 50% 最大宽度的连续行。
        val bandThreshold = (maxCount * 0.5).toInt().coerceAtLeast(2)
        var bandStart = -1
        var bandEnd = -1
        for (i in rowWidth.indices) {
            if (rowWidth[i] >= bandThreshold) {
                if (bandStart < 0) bandStart = i
                bandEnd = i
            }
        }
        if (bandStart < 0) return null

        val nhHeight = (bandEnd - bandStart + 1).coerceAtLeast(1)
        val centerY = blob.minY + (bandStart + bandEnd) / 2

        // 取最宽行作为符头水平中心，避免符干把 x 重心带偏。
        var bestRow = bandStart
        for (i in bandStart..bandEnd) {
            if (rowWidth[i] > rowWidth[bestRow]) bestRow = i
        }
        val yBest = blob.minY + bestRow
        var sumX = 0
        var cnt = 0
        for (x in blob.minX..blob.maxX) {
            if (image.isBlack(x, yBest)) {
                sumX += x; cnt++
            }
        }
        if (cnt == 0) return null
        val centerX = sumX / cnt
        val nhWidth = rowWidth[bestRow].coerceAtLeast(1)

        return Notehead(centerX, centerY, nhWidth, nhHeight, nhWidth * nhHeight)
    }
}
