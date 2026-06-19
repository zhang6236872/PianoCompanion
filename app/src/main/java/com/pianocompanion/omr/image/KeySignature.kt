package com.pianocompanion.omr.image

/**
 * 音乐调号（五度圈），表示写在谱号右侧的升/降号组合。
 *
 * 本类是纯音乐理论模型（无 Android 依赖、无图像依赖），用于把 OMR 识别到的
 * "谱号后方有 N 个升/降号" 转化为对每个音高的实际半音修正。
 *
 * 字母索引约定：C=0, D=1, E=2, F=3, G=4, A=5, B=6（与 [PitchMapper] 的
 * `C_ORDER_SEMITONES` 一致）。
 *
 * 升号按顺序加入：F C G D A E B（对应字母 3,0,4,1,5,2,6）。
 * 降号按顺序加入：B E A D G C F（对应字母 6,2,5,1,4,0,3）。
 *
 * 例如：1 个升号 → F 升（G 大调）；2 个降号 → B、E 降（bB 大调）。
 */
enum class KeySignature(
    val sharpCount: Int,
    val flatCount: Int,
    val label: String
) {
    C_MAJOR_A_MINOR(0, 0, "C大调"),
    G_MAJOR_E_MINOR(1, 0, "G大调"),
    D_MAJOR(2, 0, "D大调"),
    A_MAJOR(3, 0, "A大调"),
    E_MAJOR(4, 0, "E大调"),
    B_MAJOR(5, 0, "B大调"),
    F_SHARP_MAJOR(6, 0, "F#大调"),
    C_SHARP_MAJOR(7, 0, "C#大调"),
    F_MAJOR_D_MINOR(0, 1, "F大调"),
    B_FLAT_MAJOR(0, 2, "bB大调"),
    E_FLAT_MAJOR(0, 3, "bE大调"),
    A_FLAT_MAJOR(0, 4, "bA大调"),
    D_FLAT_MAJOR(0, 5, "bD大调"),
    G_FLAT_MAJOR(0, 6, "bG大调"),
    C_FLAT_MAJOR(0, 7, "bC大调");

    /** 是否含升号。 */
    val hasSharps: Boolean get() = sharpCount > 0

    /** 是否含降号。 */
    val hasFlats: Boolean get() = flatCount > 0

    /** 调号中升降号总数。 */
    val accidentalCount: Int get() = maxOf(sharpCount, flatCount)

    companion object {
        // 升号加入顺序（按字母索引）
        private val SHARP_LETTERS = intArrayOf(3, 0, 4, 1, 5, 2, 6) // F C G D A E B
        // 降号加入顺序（按字母索引）
        private val FLAT_LETTERS = intArrayOf(6, 2, 5, 1, 4, 0, 3) // B E A D G C F

        /**
         * 根据识别到的升/降号数量构造调号。
         * 同一调号不可能既有升号又有降号；当两者都 > 0 时以升号为准（极少见）。
         */
        fun fromAccidentals(sharps: Int, flats: Int): KeySignature {
            val s = sharps.coerceIn(0, 7)
            val f = flats.coerceIn(0, 7)
            return when {
                s > 0 -> values().first { it.sharpCount == s && it.flatCount == 0 }
                f > 0 -> values().first { it.flatCount == f && it.sharpCount == 0 }
                else -> C_MAJOR_A_MINOR
            }
        }

        /** 升号顺序中第 [i]（0 基）个升号作用于哪个字母。 */
        fun sharpLetterAt(i: Int): Int = SHARP_LETTERS[i.coerceIn(0, 6)]

        /** 降号顺序中第 [i]（0 基）个降号作用于哪个字母。 */
        fun flatLetterAt(i: Int): Int = FLAT_LETTERS[i.coerceIn(0, 6)]
    }

    /**
     * 给定一个字母（C=0..B=6），返回该调号对它的半音修正：+1（升）、-1（降）、0（不变）。
     *
     * 例如 G 大调（1 升）下，字母 F(3) → +1；其余 → 0。
     */
    fun accidentalOffset(letter: Int): Int {
        val l = Math.floorMod(letter, 7)
        if (sharpCount > 0) {
            for (i in 0 until sharpCount.coerceAtMost(7)) {
                if (SHARP_LETTERS[i] == l) return +1
            }
        }
        if (flatCount > 0) {
            for (i in 0 until flatCount.coerceAtMost(7)) {
                if (FLAT_LETTERS[i] == l) return -1
            }
        }
        return 0
    }

    /**
     * 该字母在本调号下是否被升/降。
     */
    fun isAltered(letter: Int): Boolean = accidentalOffset(letter) != 0
}
