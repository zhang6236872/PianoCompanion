package com.pianocompanion.omr.image

import kotlin.math.abs

/**
 * 纯 Kotlin（无 Android 依赖）的"左侧签名区"识别器：在去谱线后的二值图上，
 * 对每个谱表系统依次识别
 *
 *   谱号(clef) → 调号(key signature) → 拍号(time signature)
 *
 * 这三组符号固定出现在每个谱表最左侧、第一个音符之前，从左到右排列。
 *
 * ## 谱号识别
 * 高音谱号(𝄞) 与 低音谱号(𝄢) 用稳定的几何特征区分（替代旧的"按竖直位置推断"）：
 *  - **bass 双点**：低音谱号右侧紧挨着两个小实心圆点，位于自上而下第 2 条线
 *    (F 线) 附近。这是最可靠的特征——高音谱号从不出现双点。
 *  - **向上延伸**：高音谱号明显向上超出顶线（通常 ≥ 半个谱表高度）；低音谱号基本
 *    限制在谱表范围内。
 *  - **整体高度比**：高音谱号更高更窄。
 *
 * 当无法确定时返回 [ClefType.UNKNOWN]，调用方回退到旧启发式。
 *
 * ## 调号识别
 * 谱号右侧的升号(♯)/降号(♭) 序列。每个临时记号是紧凑的小字形；通过统计其内部
 * 的"竖直长笔画"数量区分：升号=2 根，降号=1 根。按多数票确定类型，个数即调号。
 *
 * ## 拍号识别
 * 调号右侧两个上下堆叠的大数字。把每个数字字形降采样到 5×7 网格，与内置 0-9 模板
 * 做汉明距离匹配。能可靠识别合成/规整数字；真实手写体可能需人工校对。
 *
 * 全程只读取 [BinaryImage] 像素，完全可在 JVM 单元测试中用合成图验证。
 */
object SignatureDetector {

    /**
     * 谱号类型。C 谱号(中音/次中音)用同一个 𝄡 字形，区别在于它"框住"的谱线位置：
     * 中音谱号框中央线(C4)，次中音谱号框自上而下第 2 条线(C4)。
     */
    enum class ClefType { TREBLE, BASS, ALTO, TENOR, UNKNOWN }

    /** 单个谱表系统的签名识别结果。 */
    data class SystemSignatures(
        val clef: ClefType,
        val keySignature: KeySignature,
        val timeSignature: TimeSignature?,
        /**
         * 本系统签名区（谱号+调号+拍号）占据的最右像素 x。调用方据此把签名区内的
         * 连通块从符头检测结果中排除——谱号曲线/拍号数字等高大字形会被符头恢复扫描
         * 误判为符头。无签名时为 0（即不做排除）。
         */
        val signatureEndX: Int = 0
    )

    /** 整体识别结果。 */
    data class Result(
        val perSystem: List<SystemSignatures>,
        val timeSignature: TimeSignature?,
        val warnings: List<String>
    )

    /**
     * 对所有谱表系统识别签名。
     *
     * @param cleaned 去谱线后的二值图。
     * @param systems 检测到的谱表系统。
     * @param blobs   [cleaned] 的全部连通块。
     * @param noteheadsBySystem 每个系统归属的符头（用于确定"音符起点"，签名区在其左侧）。
     */
    fun detect(
        cleaned: BinaryImage,
        systems: List<StaffSystem>,
        blobs: List<Blob>,
        noteheadsBySystem: List<List<Notehead>>
    ): Result {
        if (systems.isEmpty()) return Result(emptyList(), null, emptyList())
        val perSystem = systems.mapIndexed { idx, system ->
            detectForSystem(cleaned, system, blobs, noteheadsBySystem.getOrElse(idx) { emptyList() })
        }
        // 拍号通常全曲统一，取第一个系统识别到的拍号。
        val ts = perSystem.firstNotNullOfOrNull { it.timeSignature }
        val warnings = ArrayList<String>()
        val unknownClefs = perSystem.count { it.clef == ClefType.UNKNOWN }
        if (unknownClefs > 0) {
            warnings += "第 ${perSystem.indexOfFirst { it.clef == ClefType.UNKNOWN } + 1} 个谱表未识别到明确谱号，已按默认谱表处理"
        }
        if (perSystem.any { it.keySignature.accidentalCount > 0 }) {
            val k = perSystem.first { it.keySignature.accidentalCount > 0 }.keySignature
            warnings += "识别到调号：${k.label}（${k.accidentalCount} 个${if (k.hasSharps) "升" else "降"}号）"
        }
        return Result(perSystem, ts, warnings)
    }

    /** 单系统的签名识别。 */
    fun detectForSystem(
        cleaned: BinaryImage,
        system: StaffSystem,
        blobs: List<Blob>,
        noteheads: List<Notehead>
    ): SystemSignatures {
        val s = system.lineSpacing.coerceAtLeast(1)
        val staffHeight = (system.bottomLine.center - system.topLine.center).coerceAtLeast(s)
        val top = system.topLine.center
        val bottom = system.bottomLine.center
        // 第一个音符的 x；无音符时取图宽（整个左侧都算签名区）。
        val firstNoteX = noteheads.minOf { it.centerX }
        val leftLimit = if (firstNoteX == Int.MAX_VALUE) cleaned.width else firstNoteX

        // 与本谱表竖向重叠、且位于第一个音符左侧的连通块，按 x 排序。
        val leftBlobs = blobs.asSequence()
            .filter { it.maxX < leftLimit }
            .filter { overlapsStaffVertically(it, top, bottom, s) }
            .sortedBy { it.minX }
            .toList()
        if (leftBlobs.isEmpty()) {
            return SystemSignatures(ClefType.UNKNOWN, KeySignature.C_MAJOR_A_MINOR, null)
        }

        // 1) 谱号 = 最左侧的"高大"连通块（高度 ≥ 0.6 谱表高度且覆盖谱表核心竖向范围）。
        val clefBlob = leftBlobs.firstOrNull { isLargeTall(it, staffHeight, s, top, bottom) }
        val clef = if (clefBlob != null) classifyClef(cleaned, system, blobs, clefBlob) else ClefType.UNKNOWN

        // 记录签名区最右 x，供调用方排除签名区内的连通块。
        var sigEndX = 0
        if (clefBlob != null) {
            sigEndX = maxOf(sigEndX, clefBlob.maxX)
            if (clef == ClefType.BASS) {
                // 低音谱号右侧的两个圆点位于谱号右约 1~2 个间距内。
                sigEndX = maxOf(sigEndX, clefBlob.maxX + (2.0 * s).toInt())
            }
        }

        // 2) 调号 + 拍号 = 谱号之后、按字形大小分类。
        val restStart = leftBlobs.indexOf(clefBlob) + 1
        val rest = if (clefBlob != null) leftBlobs.subList(restStart, leftBlobs.size).toList() else leftBlobs
        var key = KeySignature.C_MAJOR_A_MINOR
        val timeSigBlobs = ArrayList<Blob>()
        for (blob in rest) {
            when (classifyLeftGlyph(blob, s)) {
                GlyphKind.ACCIDENTAL -> {
                    // 累计计入调号（升/降）。
                    key = accumulateAccidental(key, blob, cleaned, s)
                    sigEndX = maxOf(sigEndX, blob.maxX)
                }
                GlyphKind.TIME_DIGIT -> {
                    timeSigBlobs += blob
                    sigEndX = maxOf(sigEndX, blob.maxX)
                }
                GlyphKind.OTHER -> { /* 忽略：噪声或无法归类 */ }
            }
        }
        // 3) 拍号：把拍号数字按竖向位置分成上/下两组并做数字识别。
        val ts = recognizeTimeSignature(cleaned, timeSigBlobs, system)
        return SystemSignatures(clef, key, ts, sigEndX)
    }

    // ---- 谱号分类 -----------------------------------------------------------

    private enum class GlyphKind { ACCIDENTAL, TIME_DIGIT, OTHER }

    private fun classifyLeftGlyph(blob: Blob, s: Int): GlyphKind {
        // 临时记号(升/降)：紧凑，高度约 1~2.5 个间距。
        if (blob.height in (0.6 * s).toInt()..(2.6 * s).toInt() &&
            blob.width in (0.4 * s).toInt()..(2.2 * s).toInt()
        ) {
            return GlyphKind.ACCIDENTAL
        }
        // 拍号数字：高大（≥ 2.5 间距）且较宽。
        if (blob.height >= (2.5 * s).toInt() && blob.width >= (0.8 * s).toInt()) {
            return GlyphKind.TIME_DIGIT
        }
        return GlyphKind.OTHER
    }

    private fun classifyClef(
        image: BinaryImage,
        system: StaffSystem,
        blobs: List<Blob>,
        clef: Blob
    ): ClefType {
        val s = system.lineSpacing.coerceAtLeast(1)
        val staffHeight = (system.bottomLine.center - system.topLine.center).coerceAtLeast(s)
        val reachAbove = system.topLine.center - clef.minY
        val heightRatio = clef.height.toDouble() / staffHeight

        // 强信号 1：低音谱号双点（右侧、第 2 条线附近、两个小实心圆）。
        if (hasBassDots(image, system, blobs, clef, s)) return ClefType.BASS

        // 强信号 2：高音谱号明显向上超出顶线。
        if (reachAbove >= 0.45 * staffHeight) return ClefType.TREBLE

        // 强信号 3：C 谱号(中音/次中音)。紧凑且不向上延伸，但框住某条谱线
        // (黑像质心落在该线上，且线上下两侧均有墨迹)。C 谱号从不带双点、不向上延伸，
        // 故在已排除低音双点与高音向上延伸之后，用质心位置区分中音/次中音。
        val cClef = classifyCClef(image, system, clef, s)
        if (cClef != ClefType.UNKNOWN) return cClef

        // 紧凑且不向上延伸 → 倾向低音谱号（无 C 谱号特征时的回退）。
        if (reachAbove < 0.25 * staffHeight && heightRatio <= 1.4) return ClefType.BASS

        return ClefType.UNKNOWN
    }

    /**
     * C 谱号(中音/次中音)判定。C 谱号框住 C4 所在的谱线：中音谱号 = 中央线，
     * 次中音谱号 = 自上而下第 2 条线。判定依据：
     *   1) 谱号连通块的黑像素竖直质心落在该谱线容差内；
     *   2) 该谱线上、下两侧均存在墨迹(说明谱号确实横跨/框住该线)。
     * 满足上述条件即返回对应类型，否则 [ClefType.UNKNOWN]（调用方回退低音谱号）。
     *
     * 已知限制：真实低音谱号若两点未被检测到，其竖直质心可能恰好落在中央线附近，
     * 此时会被误判为中音谱号；低音谱号的两点是最可靠的特征，正常情况下在此步之前
     * 已被 [hasBassDots] 命中。
     */
    internal fun classifyCClef(
        image: BinaryImage,
        system: StaffSystem,
        clef: Blob,
        s: Int
    ): ClefType {
        if (system.lines.size < 3) return ClefType.UNKNOWN
        val centerY = verticalCenterOfMass(image, clef)
        // C 谱号框住的 C4 线：中音=中央线(lines[2])，次中音=自上而下第 2 线(lines[1])。
        val middleLineY = system.lines[2].center
        val secondLineY = system.lines[1].center
        val distMiddle = abs(centerY - middleLineY)
        val distSecond = abs(centerY - secondLineY)
        val tol = (s * 0.6).toInt().coerceAtLeast(2)
        return when {
            distMiddle <= distSecond && distMiddle <= tol &&
                straddlesLine(image, clef, middleLineY, s) -> ClefType.ALTO
            distSecond < distMiddle && distSecond <= tol &&
                straddlesLine(image, clef, secondLineY, s) -> ClefType.TENOR
            else -> ClefType.UNKNOWN
        }
    }

    /** 谱号连通块内黑像素的竖直质心(y 坐标)。 */
    internal fun verticalCenterOfMass(image: BinaryImage, blob: Blob): Int {
        var sum = 0L
        var count = 0
        for (y in blob.minY..blob.maxY) {
            for (x in blob.minX..blob.maxX) {
                if (image.isBlack(x, y)) { sum += y; count++ }
            }
        }
        return if (count > 0) (sum / count).toInt() else blob.centerY
    }

    /**
     * 谱号连通块在 [lineY] 上、下两侧是否都存在黑像素(各偏离至少半个间距)，
     * 即谱号是否横跨该谱线。
     */
    private fun straddlesLine(image: BinaryImage, blob: Blob, lineY: Int, s: Int): Boolean {
        val margin = (s * 0.5).toInt().coerceAtLeast(2)
        var above = false
        var below = false
        for (y in blob.minY..blob.maxY) {
            if (!above && y <= lineY - margin) {
                for (x in blob.minX..blob.maxX) if (image.isBlack(x, y)) { above = true; break }
            }
            if (!below && y >= lineY + margin) {
                for (x in blob.minX..blob.maxX) if (image.isBlack(x, y)) { below = true; break }
            }
            if (above && below) return true
        }
        return above && below
    }

    /**
     * 在谱号右侧、自上而下第 2 条线(F 线) 附近寻找两个小实心圆点。
     */
    private fun hasBassDots(
        image: BinaryImage,
        system: StaffSystem,
        blobs: List<Blob>,
        clef: Blob,
        s: Int
    ): Boolean {
        if (system.lines.size < 2) return false
        val fLineY = system.lines[1].center // 自上而下第 2 条线
        val xStart = clef.maxX + 1
        val xEnd = clef.maxX + (2.0 * s).toInt()
        val tolY = s
        val dotMax = (1.0 * s).toInt()
        val dotMin = 2
        var dots = 0
        for (blob in blobs) {
            if (blob.minX < xStart || blob.minX > xEnd) continue
            if (abs(blob.centerY - fLineY) > tolY) continue
            if (blob.width in dotMin..dotMax && blob.height in dotMin..dotMax) {
                dots++
            }
        }
        return dots >= 2
    }

    // ---- 调号 ---------------------------------------------------------------

    private fun accumulateAccidental(
        current: KeySignature,
        blob: Blob,
        image: BinaryImage,
        s: Int
    ): KeySignature {
        val strokes = countVerticalStrokes(image, blob)
        val isSharp = strokes >= 2
        return if (isSharp) {
            KeySignature.fromAccidentals(current.sharpCount + 1, 0)
        } else {
            KeySignature.fromAccidentals(0, current.flatCount + 1)
        }
    }

    /**
     * 统计连通块内部"竖直长笔画"数量（升号=2，降号=1）。
     * 对 blob 的每一列，计算该列内最长的连续黑像素行数；长度 ≥ 55% blob 高度
     * 视为长笔画列；相邻列合并为同一笔画后计数。
     */
    internal fun countVerticalStrokes(image: BinaryImage, blob: Blob): Int {
        val minLen = (blob.height * 0.55).toInt().coerceAtLeast(3)
        val cols = BooleanArray(blob.width)
        for (x in blob.minX..blob.maxX) {
            var run = 0
            var best = 0
            for (y in blob.minY..blob.maxY) {
                run = if (image.isBlack(x, y)) run + 1 else 0
                if (run > best) best = run
            }
            if (best >= minLen) cols[x - blob.minX] = true
        }
        // 合并相邻列。
        var strokes = 0
        var i = 0
        while (i < cols.size) {
            if (!cols[i]) { i++; continue }
            strokes++
            while (i < cols.size && cols[i]) i++
        }
        return strokes
    }

    // ---- 拍号数字识别 -------------------------------------------------------

    /**
     * 把拍号数字按竖直位置分成"上数字/下数字"，分别识别后组成拍号。
     * 单数字（如 𝄴 4/4 简写）不在此处理，返回 null。
     */
    private fun recognizeTimeSignature(
        image: BinaryImage,
        digitBlobs: List<Blob>,
        system: StaffSystem
    ): TimeSignature? {
        if (digitBlobs.size < 2) return null
        val midY = system.centerY
        val upper = digitBlobs.filter { it.centerY < midY }
        val lower = digitBlobs.filter { it.centerY >= midY }
        if (upper.isEmpty() || lower.isEmpty()) return null
        val topDigit = classifyDigit(image, upper.sortedByDescending { it.height }.first()) ?: return null
        val bottomDigit = classifyDigit(image, lower.sortedByDescending { it.height }.first()) ?: return null
        return TimeSignature.fromDigits(topDigit, bottomDigit)
    }

    /**
     * @param image 原始去谱线二值图，用于读取 blob 像素。
     */
    internal fun classifyDigit(image: BinaryImage, blob: Blob): Int? {
        val grid = downsampleBlob(image, blob, GRID_W, GRID_H) ?: return null
        var bestDigit = -1
        var bestDist = Int.MAX_VALUE
        var secondDist = Int.MAX_VALUE
        for ((digit, tmpl) in DIGIT_TEMPLATES) {
            val d = hamming(grid, tmpl)
            if (d < bestDist) {
                secondDist = bestDist
                bestDist = d
                bestDigit = digit
            } else if (d < secondDist) {
                secondDist = d
            }
        }
        // 最近距离需足够小，且与次近拉开差距（避免误识）。
        val maxAccept = (GRID_W * GRID_H * 0.30).toInt()
        if (bestDigit < 0 || bestDist > maxAccept) return null
        if (secondDist - bestDist < 2) return null // 模糊，放弃
        return bestDigit
    }

    private fun hamming(a: BooleanArray, b: BooleanArray): Int {
        var d = 0
        for (i in a.indices) if (a[i] != b[i]) d++
        return d
    }

    /** 把 blob 区域降采样到 cols×rows 的布尔网格（每格按黑像素占比 ≥ 0.4 判定）。 */
    private fun downsampleBlob(image: BinaryImage, blob: Blob, cols: Int, rows: Int): BooleanArray? {
        val bw = blob.width
        val bh = blob.height
        if (bw < 2 || bh < 2) return null
        val out = BooleanArray(cols * rows)
        for (r in 0 until rows) {
            val y0 = blob.minY + bh * r / rows
            val y1 = (blob.minY + bh * (r + 1) / rows).coerceAtMost(blob.maxY + 1)
            for (c in 0 until cols) {
                val x0 = blob.minX + bw * c / cols
                val x1 = (blob.minX + bw * (c + 1) / cols).coerceAtMost(blob.maxX + 1)
                var black = 0
                var total = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    if (image.isBlack(x, y)) black++
                    total++
                }
                out[r * cols + c] = total > 0 && black.toDouble() / total >= 0.4
            }
        }
        return out
    }

    private fun overlapsStaffVertically(blob: Blob, top: Int, bottom: Int, s: Int): Boolean {
        val band = s * 2
        return blob.maxY >= top - band && blob.minY <= bottom + band
    }

    private fun isLargeTall(blob: Blob, staffHeight: Int, s: Int, top: Int, bottom: Int): Boolean {
        if (blob.height < staffHeight * 0.6) return false
        // 必须覆盖谱表核心竖向范围的中部（排除远离谱表的漂浮噪声）。
        return blob.maxY >= top && blob.minY <= bottom
    }

    // ---- 5×7 数字模板（公开，便于测试时渲染合成数字）------------------------

    const val GRID_W = 5
    const val GRID_H = 7

    /**
     * 内置 0-9 的 5×7 点阵模板。测试可用 [renderTemplate] 把模板按倍率画入合成图，
     * 这样降采样回去能精确复原模板，验证识别链路。
     */
    val DIGIT_TEMPLATES: Map<Int, BooleanArray> by lazy { buildTemplates() }

    private fun buildTemplates(): Map<Int, BooleanArray> {
        val glyphs = mapOf(
            0 to arrayOf(
                "01110",
                "10001",
                "10001",
                "10001",
                "10001",
                "10001",
                "01110"
            ),
            1 to arrayOf(
                "00100",
                "01100",
                "00100",
                "00100",
                "00100",
                "00100",
                "01110"
            ),
            2 to arrayOf(
                "01110",
                "10001",
                "00001",
                "00010",
                "00100",
                "01000",
                "11111"
            ),
            3 to arrayOf(
                "11110",
                "00001",
                "00001",
                "01110",
                "00001",
                "00001",
                "11110"
            ),
            4 to arrayOf(
                "00010",
                "00110",
                "01010",
                "10010",
                "11111",
                "00010",
                "00010"
            ),
            5 to arrayOf(
                "11111",
                "10000",
                "11110",
                "00001",
                "00001",
                "10001",
                "01110"
            ),
            6 to arrayOf(
                "00110",
                "01000",
                "10000",
                "11110",
                "10001",
                "10001",
                "01110"
            ),
            7 to arrayOf(
                "11111",
                "00001",
                "00010",
                "00100",
                "01000",
                "01000",
                "01000"
            ),
            8 to arrayOf(
                "01110",
                "10001",
                "10001",
                "01110",
                "10001",
                "10001",
                "01110"
            ),
            9 to arrayOf(
                "01110",
                "10001",
                "10001",
                "01111",
                "00001",
                "00010",
                "01100"
            )
        )
        val out = LinkedHashMap<Int, BooleanArray>()
        for ((digit, rows) in glyphs) {
            val arr = BooleanArray(GRID_W * GRID_H)
            for (r in rows.indices) {
                for (c in 0 until GRID_W) {
                    if (rows[r][c] == '1') arr[r * GRID_W + c] = true
                }
            }
            out[digit] = arr
        }
        return out
    }
}
