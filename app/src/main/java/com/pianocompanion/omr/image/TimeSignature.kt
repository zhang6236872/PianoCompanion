package com.pianocompanion.omr.image

/**
 * 拍号（分子/分母），纯 Kotlin 音乐理论模型（无 Android、无图像依赖）。
 *
 * 分子 = 每小节拍数；分母 = 以哪种音符为一拍（4=四分音符，8=八分音符）。
 * [quartersPerMeasure] 把一个小节换算成"等价四分音符数"，供 OMR 管线计算
 * 小节时长与 [measureIndex]。
 *
 * 例如：4/4 → 4；3/4 → 3；2/4 → 2；6/8 → 3。
 */
data class TimeSignature(val numerator: Int, val denominator: Int) {

    /** 每小节等价的四分音符数。 */
    val quartersPerMeasure: Double get() = numerator * 4.0 / denominator.coerceAtLeast(1)

    /** 是否为合法拍号（分子分母为正，分母为 2 的幂且 ∈ {1,2,4,8,16,32}）。 */
    val isValid: Boolean get() {
        if (numerator <= 0) return false
        return denominator in VALID_DENOMINATORS
    }

    override fun toString(): String = "$numerator/$denominator"

    companion object {
        private val VALID_DENOMINATORS = setOf(1, 2, 4, 8, 16, 32)

        val FOUR_FOUR = TimeSignature(4, 4)
        val THREE_FOUR = TimeSignature(3, 4)
        val TWO_FOUR = TimeSignature(2, 4)
        val THREE_EIGHT = TimeSignature(3, 8)
        val SIX_EIGHT = TimeSignature(6, 8)

        /** OMR 可识别的常见拍号模板（用于字符识别结果回填）。 */
        val COMMON = listOf(FOUR_FOUR, THREE_FOUR, TWO_FOUR, SIX_EIGHT, THREE_EIGHT)

        /**
         * 从两个数字构造拍号；不合法时返回 null。
         */
        fun fromDigits(top: Int, bottom: Int): TimeSignature? {
            val ts = TimeSignature(top, bottom)
            return if (ts.isValid) ts else null
        }
    }
}
