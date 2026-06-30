package com.pianocompanion.music

import com.pianocompanion.data.model.Score

/**
 * 检测到的调性信息。
 *
 * @param tonic 根音的音级类（pitch class），0=C, 1=C#, ..., 11=B。
 * @param mode 调式（大调 / 小调）。
 * @param confidence 置信度 [0, 1]，最高候选与次高候选得分的比值。
 *   越接近 1 表示主调越明确（两种候选得分差距大）；接近 0 表示调性模糊。
 */
data class KeyInfo(
    val tonic: Int,
    val mode: KeyMode,
    val confidence: Float
) {
    /** 调性显示名称，如 "C大调"、"a小调"（小调用小写字母表示）。 */
    val displayName: String
        get() {
            val name = SHARP_NAMES[tonic]
            return if (mode == KeyMode.MAJOR) "${name}大调" else "${name.lowercase()}小调"
        }

    companion object {
        /** 升号优先的音名（C, C#, D, ...）。 */
        val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /** 降号优先的音名（C, Db, D, ...），用于降号调的友好显示。 */
        val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    }
}

/** 调式。 */
enum class KeyMode { MAJOR, MINOR }

/**
 * 基于音级类（pitch-class）直方图的调性检测器。
 *
 * 实现 Krumhansl-Schmuckler 调性判定算法：统计乐谱中 12 个音级类的出现频率，
 * 再与预置的大调/小调音级分布轮廓（profile）做皮尔逊相关，取相关系数最大的调性
 * 作为检测结果。该算法是 MIR（音乐信息检索）领域最经典、最可靠的启发式调性判定方法，
 * 对旋律、和声、各种调式均有良好的鲁棒性。
 *
 * 参见: Krumhansl, C. L. (1990). *Cognitive Foundations of Musical Pitch.* Oxford UP.
 *
 * 本类为纯 Kotlin 实现，无 Android 依赖，完全可单元测试。
 */
object KeyDetector {

    // Krumhansl-Schmuckler 大调音级轮廓（profile）。
    // 数值反映每个音级在「大调主调」语境下的感知稳定性/出现倾向。
    // 主音(do)=6.35, 导音(ti)=2.52 等。
    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )

    // 小调音级轮廓（自然小调）。
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )

    /**
     * 检测乐谱的调性。
     *
     * @param score 乐谱（至少包含 [Score.notes]）。
     * @return 检测到的调性 [KeyInfo]；若乐谱为空或无有效音符，返回 C 大调（置信度 0）。
     */
    fun detect(score: Score): KeyInfo {
        return detectFromMidiNumbers(score.notes.map { it.midiNumber })
    }

    /**
     * 从一组 MIDI 音符号检测调性。
     *
     * 这是核心检测逻辑，分离出来方便用已知调性的旋律直接测试，无需构造完整 Score。
     *
     * @param midiNumbers MIDI 音符列表。
     * @return 检测到的调性。
     */
    fun detectFromMidiNumbers(midiNumbers: List<Int>): KeyInfo {
        if (midiNumbers.isEmpty()) return KeyInfo(0, KeyMode.MAJOR, 0f)

        // 统计 12 个音级类的累计权重。
        // 权重 = 1.0（每个音符等权）。更长时值的音符可加权，但对调性判定影响很小，
        // 此处保持简单等权以最大化鲁棒性和确定性。
        val histogram = DoubleArray(12)
        for (midi in midiNumbers) {
            if (midi in 0..127) {
                histogram[midi % 12] += 1.0
            }
        }

        // 全零直方图（全是无效音符）回退 C 大调。
        if (histogram.sum() == 0.0) return KeyInfo(0, KeyMode.MAJOR, 0f)

        return detectFromHistogram(histogram)
    }

    /**
     * 从 12 维音级类直方图检测调性。
     *
     * 对每个候选调性（12 大调 + 12 小调 = 24 个），将直方图旋转对齐到该调的主音，
     * 计算与对应轮廓的皮尔逊相关系数。取最大者。
     */
    fun detectFromHistogram(histogram: DoubleArray): KeyInfo {
        require(histogram.size == 12) { "直方图维度必须为 12" }

        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY
        var bestTonic = 0
        var bestMode = KeyMode.MAJOR

        for (mode in listOf(KeyMode.MAJOR, KeyMode.MINOR)) {
            val profile = if (mode == KeyMode.MAJOR) MAJOR_PROFILE else MINOR_PROFILE
            for (tonic in 0 until 12) {
                // 将直方图旋转 tonic 位，使其与从 tonic 起的主调轮廓对齐。
                val rotated = DoubleArray(12)
                for (i in 0 until 12) {
                    rotated[i] = histogram[(i + tonic) % 12]
                }
                val correlation = pearsonCorrelation(rotated, profile)

                if (correlation > bestScore) {
                    secondScore = bestScore
                    bestScore = correlation
                    bestTonic = tonic
                    bestMode = mode
                } else if (correlation > secondScore) {
                    secondScore = correlation
                }
            }
        }

        // 置信度 = 最佳得分 / (最佳 + 次佳)。
        // 两个候选得分差距越大，置信度越接近 1。
        val confidence = if (bestScore + secondScore > 0) {
            (bestScore / (bestScore + secondScore)).toFloat().coerceIn(0f, 1f)
        } else {
            // 相关系数可能为负（罕见），用差值比例回退。
            if (bestScore > 0) 1f else 0f
        }

        return KeyInfo(bestTonic, bestMode, confidence)
    }

    /**
     * 皮尔逊相关系数。
     *
     * r = Σ((x-x̄)(y-ȳ)) / √(Σ(x-x̄)² · Σ(y-ȳ)²)
     *
     * 衡量两个序列的线性相关程度，范围 [-1, 1]。
     * 使用相关系数（而非欧氏距离）是因为它对直方图的绝对幅度不敏感——
     * 音符多寡不影响调性判定。
     */
    private fun pearsonCorrelation(x: DoubleArray, y: DoubleArray): Double {
        val n = x.size
        var sumX = 0.0
        var sumY = 0.0
        for (i in 0 until n) {
            sumX += x[i]
            sumY += y[i]
        }
        val meanX = sumX / n
        val meanY = sumY / n

        var numerator = 0.0
        var sumSqX = 0.0
        var sumSqY = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            sumSqX += dx * dx
            sumSqY += dy * dy
        }

        val denominator = Math.sqrt(sumSqX * sumSqY)
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}
