package com.pianocompanion.omr.image

/**
 * A connected blob found via 8-connectivity flood fill.
 */
data class Blob(
    val label: Int,
    val area: Int,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
    val centerX: Int get() = (minX + maxX) / 2
    val centerY: Int get() = (minY + maxY) / 2
    val aspectRatio: Double get() = width.toDouble() / height.coerceAtLeast(1)
}

/**
 * 8-connectivity connected-component labeling using an explicit stack
 * (iterative flood fill — no recursion, safe for large images).
 */
object ConnectedComponents {

    /**
     * Label all black blobs in [image].
     * @param minPixels discard blobs smaller than this (noise filter).
     */
    fun label(image: BinaryImage, minPixels: Int = 1): List<Blob> {
        val w = image.width
        val h = image.height
        val pixels = image.pixels.copyOf() // consumed (cleared) as we visit
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        val blobs = ArrayList<Blob>()
        var nextLabel = 1

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!pixels[idx] || labels[idx] != 0) continue

                var sp = 0
                stack[sp++] = idx
                labels[idx] = nextLabel
                pixels[idx] = false

                var area = 0
                var minX = x; var maxX = x; var minY = y; var maxY = y

                while (sp > 0) {
                    val cur = stack[--sp]
                    val cx = cur % w
                    val cy = cur / w
                    area++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val nidx = ny * w + nx
                        if (pixels[nidx] && labels[nidx] == 0) {
                            labels[nidx] = nextLabel
                            pixels[nidx] = false
                            stack[sp++] = nidx
                        }
                    }
                }

                if (area >= minPixels) {
                    blobs += Blob(nextLabel, area, minX, maxX, minY, maxY)
                }
                nextLabel++
            }
        }
        return blobs
    }
}
