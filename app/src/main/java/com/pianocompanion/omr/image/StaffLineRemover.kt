package com.pianocompanion.omr.image

/**
 * Removes staff-line ink while preserving music glyphs (noteheads, stems, beams).
 *
 * The key insight: a staff-line pixel belongs to a **long horizontal** run of ink
 * but a **short vertical** run (the line is thin). A notehead pixel also lies on a
 * long horizontal run (because it merges with the line at that row), but it belongs
 * to a **tall vertical** run (the notehead body). Therefore we remove a pixel only
 * when its horizontal run is long *and* its vertical run is thin — this keeps
 * noteheads fully connected instead of slicing them in half.
 */
object StaffLineRemover {

    /**
     * @param image binarized score image.
     * @param minLineRun minimum horizontal run length (px) to be considered a staff line.
     * @param maxLineThickness maximum vertical run length (px) still treated as "thin" line ink.
     */
    fun remove(image: BinaryImage, minLineRun: Int, maxLineThickness: Int): BinaryImage {
        val w = image.width
        val h = image.height
        val src = image.pixels
        val out = src.copyOf()

        // 1) Tag every pixel with the length of the vertical black run it belongs to.
        val vRun = IntArray(w * h)
        for (x in 0 until w) {
            var y = 0
            while (y < h) {
                if (src[y * w + x]) {
                    val start = y
                    while (y < h && src[y * w + x]) y++
                    val len = y - start
                    for (k in start until y) vRun[k * w + x] = len
                } else {
                    y++
                }
            }
        }

        // 2) Walk horizontal runs; for long runs, erase pixels that are thin vertically.
        for (y in 0 until h) {
            var x = 0
            while (x < w) {
                if (out[y * w + x]) {
                    val start = x
                    while (x < w && src[y * w + x]) x++
                    val len = x - start
                    if (len >= minLineRun) {
                        for (k in start until x) {
                            val idx = y * w + k
                            if (vRun[idx] <= maxLineThickness) out[idx] = false
                        }
                    }
                } else {
                    x++
                }
            }
        }
        return BinaryImage(w, h, out)
    }
}
