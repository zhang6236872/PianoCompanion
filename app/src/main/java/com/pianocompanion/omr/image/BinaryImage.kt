package com.pianocompanion.omr.image

/**
 * A pure-Kotlin binary image representation used by the OMR pipeline.
 *
 * It intentionally has **no Android dependency** so the entire recognition
 * pipeline can be exercised in plain JVM unit tests with synthetic images.
 *
 * [pixels] is row-major: the value at column `x`, row `y` is `pixels[y * width + x]`.
 * `true`  = black (ink), `false` = white (paper/background).
 */
class BinaryImage(
    val width: Int,
    val height: Int,
    val pixels: BooleanArray
) {

    init {
        require(width > 0 && height > 0) { "width and height must be > 0" }
        require(pixels.size == width * height) {
            "pixels size ${pixels.size} does not match width*height (${width * height})"
        }
    }

    fun isBlack(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= width || y >= height) return false
        return pixels[y * width + x]
    }

    fun set(x: Int, y: Int, black: Boolean) {
        require(x in 0 until width && y in 0 until height) { "($x,$y) out of bounds" }
        pixels[y * width + x] = black
    }

    /** Number of black pixels in row `y` (0 if out of range). */
    fun rowBlackCount(y: Int): Int {
        if (y < 0 || y >= height) return 0
        var c = 0
        val base = y * width
        for (x in 0 until width) if (pixels[base + x]) c++
        return c
    }

    /** Total black pixels in the image. */
    fun totalBlack(): Int {
        var c = 0
        for (p in pixels) if (p) c++
        return c
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    companion object {
        /**
         * Build a BinaryImage from a grayscale luminance array and an explicit threshold.
         *
         * Pixels whose luminance is **<= [threshold]** become black. This matches the
         * inclusive class boundary used by [OtsuThresholder] (class {0..threshold}),
         * so the two stay consistent.
         */
        fun fromGrayscale(width: Int, height: Int, gray: IntArray, threshold: Int): BinaryImage {
            require(gray.size == width * height) { "gray size does not match width*height" }
            val px = BooleanArray(width * height)
            for (i in gray.indices) px[i] = gray[i] <= threshold
            return BinaryImage(width, height, px)
        }

        /** Create an all-white image of the given size. */
        fun blank(width: Int, height: Int): BinaryImage =
            BinaryImage(width, height, BooleanArray(width * height))
    }
}
