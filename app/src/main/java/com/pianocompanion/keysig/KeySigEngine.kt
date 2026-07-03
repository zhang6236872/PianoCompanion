package com.pianocompanion.keysig

import kotlin.random.Random

/**
 * 调号识别训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [KeySigClef]、[KeySigDifficulty] 和随机种子生成 [KeySigQuestion]。
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * 设计要点：
 * - 基于五度圈调号表，从升号调（C→G→D→A→E→B→F♯→C♯）和降号调
 *   （C→F→B♭→E♭→A♭→D♭→G♭→C♭）中选择
 * - 初级：仅大调，0-3 个升降号（C/G/D/A/F/B♭/E♭ 大调，共 7 种）
 * - 中级：仅大调，最多 5 个升降号（加入 B/E♭/A♭/D♭ 大调，共 11 种）
 * - 高级：大调 + 小调，最多 7 个升降号（含关系小调，共 30 种）
 * - 选项固定为 4 个，包含正确答案和 3 个干扰项
 * - 干扰项策略：同级数（相邻升降号数）、关系调（同调号不同调式）
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class KeySigEngine(
    private val root: Random = Random.Default
) {

    /**
     * 生成一道题目。
     *
     * @param clef 谱号（影响升降号的五线谱位置）
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(clef: KeySigClef, difficulty: KeySigDifficulty): KeySigQuestion {
        // 根据难度选择候选调性池
        val candidates = candidateKeys(difficulty)

        // 随机选择正确调性
        val correctKey = candidates.random(root)

        // 构建调号升降号位置（根据谱号选择正确的位置表）
        val steps = computeAccidentalSteps(clef, correctKey)

        // 构建答案选项
        val choices = buildChoices(correctKey, candidates, difficulty)

        return KeySigQuestion(
            clef = clef,
            difficulty = difficulty,
            keyInfo = correctKey,
            accidentalStaffSteps = steps,
            answerChoices = choices,
            correctAnswer = correctKey.displayName
        )
    }

    // ── 调性候选池 ──────────────────────────────────────────

    /**
     * 根据难度返回候选调性列表。
     *
     * - 初级：0-3 个升降号的大调（7 种）
     * - 中级：0-5 个升降号的大调（11 种）
     * - 高级：0-7 个升降号的大调和小调（15 大调 + 15 小调 = 30 种）
     */
    fun candidateKeys(difficulty: KeySigDifficulty): List<KeyInfo> {
        return when (difficulty) {
            KeySigDifficulty.BEGINNER -> {
                // 0-3 个升降号的大调
                ALL_MAJOR_KEYS.filter { it.accidentalCount <= 3 }
            }
            KeySigDifficulty.INTERMEDIATE -> {
                // 0-5 个升降号的大调
                ALL_MAJOR_KEYS.filter { it.accidentalCount <= 5 }
            }
            KeySigDifficulty.ADVANCED -> {
                // 0-7 个升降号的大调和小调
                ALL_MAJOR_KEYS + ALL_MINOR_KEYS
            }
        }
    }

    // ── 调号升降号位置计算 ─────────────────────────────────

    /**
     * 计算调号升降号在五线谱上的位置。
     *
     * @param clef 谱号
     * @param key 调性信息
     * @return staff step 列表
     */
    fun computeAccidentalSteps(clef: KeySigClef, key: KeyInfo): List<Int> {
        if (key.accidentalCount <= 0) return emptyList()
        val count = key.accidentalCount
        return when (key.accidentalType) {
            AccidentalType.SHARP -> when (clef) {
                KeySigClef.TREBLE -> KeyInfo.TREBLE_SHARP_STEPS.take(count)
                KeySigClef.BASS -> KeyInfo.BASS_SHARP_STEPS.take(count)
            }
            AccidentalType.FLAT -> when (clef) {
                KeySigClef.TREBLE -> KeyInfo.TREBLE_FLAT_STEPS.take(count)
                KeySigClef.BASS -> KeyInfo.BASS_FLAT_STEPS.take(count)
            }
            else -> emptyList()
        }
    }

    // ── 答案选项构建 ────────────────────────────────────────

    /**
     * 构建 4 个答案选项（含正确答案）。
     *
     * 干扰项策略：
     * 1. 关系调（同调号、不同调式）— 高级难度常考
     * 2. 相邻升降号数的调性
     * 3. 从候选池中随机选取不同的调性
     *
     * @param correctKey 正确调性
     * @param candidates 候选池
     * @param difficulty 难度
     * @return 打乱后的 4 个选项（含正确答案）
     */
    private fun buildChoices(
        correctKey: KeyInfo,
        candidates: List<KeyInfo>,
        difficulty: KeySigDifficulty
    ): List<String> {
        val correctName = correctKey.displayName
        val distractors = mutableSetOf<String>()

        // 策略 1：关系调（同调号不同调式）
        if (difficulty == KeySigDifficulty.ADVANCED) {
            val relativeKey = findRelativeKey(correctKey)
            if (relativeKey != null) {
                distractors.add(relativeKey.displayName)
            }
        }

        // 策略 2：相邻升降号数的调性（±1 个升降号）
        val adjacent = candidates.filter { other ->
            other.displayName != correctName &&
                other.displayName !in distractors &&
                kotlin.math.abs(other.accidentalCount - correctKey.accidentalCount) == 1
        }.shuffled(root)
        for (k in adjacent) {
            if (distractors.size >= 3) break
            distractors.add(k.displayName)
        }

        // 策略 3：从候选池随机补充
        val pool = candidates.filter { it.displayName != correctName && it.displayName !in distractors }
            .shuffled(root)
        for (k in pool) {
            if (distractors.size >= 3) break
            distractors.add(k.displayName)
        }

        // 合并正确答案并打乱
        val allChoices = (distractors.toList() + correctName).distinct().take(4).shuffled(root)
        return allChoices
    }

    /**
     * 查找关系调（同调号、不同调式）。
     */
    private fun findRelativeKey(key: KeyInfo): KeyInfo? {
        val targetMode = if (key.mode == KeyMode.MAJOR) KeyMode.MINOR else KeyMode.MAJOR
        return ALL_KEYS.find { other ->
            other.mode == targetMode &&
                other.sharpCount == key.sharpCount &&
                other.flatCount == key.flatCount
        }
    }

    // ── 辅助：谱表位置 → MIDI（用于答案验证/音频） ─────────

    /**
     * 将谱表位置（step）转换为 MIDI 音符号（与其它训练模块一致的算法）。
     *
     * @param clef 谱号
     * @param step 谱表位置（底线 = 0）
     * @return MIDI 音符号
     */
    fun diatonicStepToMidi(clef: KeySigClef, step: Int): Int {
        val baseLetter = if (clef == KeySigClef.TREBLE) LETTER_E else LETTER_G
        val baseOctave = if (clef == KeySigClef.TREBLE) 4 else 2
        val totalDiatonic = baseLetter + step
        val letter = Math.floorMod(totalDiatonic, 7)
        val octaveOffset = Math.floorDiv(totalDiatonic, 7)
        val octave = baseOctave + octaveOffset
        return LETTER_SEMITONES[letter] + 12 * (octave + 1)
    }

    companion object {
        const val LETTER_C = 0
        const val LETTER_D = 1
        const val LETTER_E = 2
        const val LETTER_F = 3
        const val LETTER_G = 4
        const val LETTER_A = 5
        const val LETTER_B = 6

        /** 各字母对应的半音数（相对 C）。 */
        val LETTER_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        // ── 五度圈调号表 ────────────────────────────────────

        /**
         * 升号调大调主音（五度圈顺时针）。
         * 索引 = 升号数；值为 (pitchClass, letter, modifier)。
         */
        private val SHARP_MAJOR_ROOTS = listOf(
            Triple(0, LETTER_C, AccidentalType.NONE),   // 0♯: C
            Triple(7, LETTER_G, AccidentalType.NONE),   // 1♯: G
            Triple(2, LETTER_D, AccidentalType.NONE),   // 2♯: D
            Triple(9, LETTER_A, AccidentalType.NONE),   // 3♯: A
            Triple(4, LETTER_E, AccidentalType.NONE),   // 4♯: E
            Triple(11, LETTER_B, AccidentalType.NONE),  // 5♯: B
            Triple(6, LETTER_F, AccidentalType.SHARP),  // 6♯: F♯
            Triple(1, LETTER_C, AccidentalType.SHARP)   // 7♯: C♯
        )

        /**
         * 降号调大调主音（五度圈逆时针）。
         */
        private val FLAT_MAJOR_ROOTS = listOf(
            Triple(0, LETTER_C, AccidentalType.NONE),   // 0♭: C
            Triple(5, LETTER_F, AccidentalType.NONE),   // 1♭: F
            Triple(10, LETTER_B, AccidentalType.FLAT),  // 2♭: B♭
            Triple(3, LETTER_E, AccidentalType.FLAT),   // 3♭: E♭
            Triple(8, LETTER_A, AccidentalType.FLAT),   // 4♭: A♭
            Triple(1, LETTER_D, AccidentalType.FLAT),   // 5♭: D♭
            Triple(6, LETTER_G, AccidentalType.FLAT),   // 6♭: G♭
            Triple(11, LETTER_C, AccidentalType.FLAT)   // 7♭: C♭
        )

        /** 构建指定大调的 KeyInfo。 */
        private fun majorKey(
            sharps: Int,
            flats: Int,
            pc: Int,
            letter: Int,
            modifier: AccidentalType
        ): KeyInfo {
            val accidentalType = when {
                sharps > 0 -> AccidentalType.SHARP
                flats > 0 -> AccidentalType.FLAT
                else -> AccidentalType.NONE
            }
            return KeyInfo(
                tonicPitchClass = pc,
                tonicLetter = letter,
                accidentalModifier = modifier,
                mode = KeyMode.MAJOR,
                sharpCount = sharps,
                flatCount = flats,
                accidentalType = accidentalType,
                preferFlats = flats > 0
            )
        }

        /** 构建指定小调的 KeyInfo（关系小调主音 = 大调主音下方小三度）。 */
        private fun minorKey(
            sharps: Int,
            flats: Int,
            majorPc: Int
        ): KeyInfo {
            // 小调主音 pitchClass = 大调主音 - 3 (mod 12)
            val minorPc = Math.floorMod(majorPc - 3, 12)
            val accidentalType = when {
                sharps > 0 -> AccidentalType.SHARP
                flats > 0 -> AccidentalType.FLAT
                else -> AccidentalType.NONE
            }
            // 根据升降号偏好确定字母和变音记号
            val preferFlats = flats > 0
            val (letter, modifier) = pitchClassToLetter(minorPc, preferFlats)
            return KeyInfo(
                tonicPitchClass = minorPc,
                tonicLetter = letter,
                accidentalModifier = modifier,
                mode = KeyMode.MINOR,
                sharpCount = sharps,
                flatCount = flats,
                accidentalType = accidentalType,
                preferFlats = preferFlats
            )
        }

        /**
         * 将 pitch class 转换为 (letter, modifier)。
         */
        private fun pitchClassToLetter(pc: Int, preferFlats: Boolean): Pair<Int, AccidentalType> {
            val lookup = if (preferFlats) {
                mapOf(
                    0 to Pair(LETTER_C, AccidentalType.NONE),
                    1 to Pair(LETTER_D, AccidentalType.FLAT),
                    2 to Pair(LETTER_D, AccidentalType.NONE),
                    3 to Pair(LETTER_E, AccidentalType.FLAT),
                    4 to Pair(LETTER_E, AccidentalType.NONE),
                    5 to Pair(LETTER_F, AccidentalType.NONE),
                    6 to Pair(LETTER_G, AccidentalType.FLAT),
                    7 to Pair(LETTER_G, AccidentalType.NONE),
                    8 to Pair(LETTER_A, AccidentalType.FLAT),
                    9 to Pair(LETTER_A, AccidentalType.NONE),
                    10 to Pair(LETTER_B, AccidentalType.FLAT),
                    11 to Pair(LETTER_B, AccidentalType.NONE)
                )
            } else {
                mapOf(
                    0 to Pair(LETTER_C, AccidentalType.NONE),
                    1 to Pair(LETTER_C, AccidentalType.SHARP),
                    2 to Pair(LETTER_D, AccidentalType.NONE),
                    3 to Pair(LETTER_D, AccidentalType.SHARP),
                    4 to Pair(LETTER_E, AccidentalType.NONE),
                    5 to Pair(LETTER_F, AccidentalType.NONE),
                    6 to Pair(LETTER_F, AccidentalType.SHARP),
                    7 to Pair(LETTER_G, AccidentalType.NONE),
                    8 to Pair(LETTER_G, AccidentalType.SHARP),
                    9 to Pair(LETTER_A, AccidentalType.NONE),
                    10 to Pair(LETTER_A, AccidentalType.SHARP),
                    11 to Pair(LETTER_B, AccidentalType.NONE)
                )
            }
            return lookup[pc] ?: Pair(LETTER_C, AccidentalType.NONE)
        }

        /** 所有升号大调（0-7 升号）。 */
        private val SHARP_MAJOR_KEYS: List<KeyInfo> = (0..7).map { i ->
            val (pc, letter, mod) = SHARP_MAJOR_ROOTS[i]
            majorKey(i, 0, pc, letter, mod)
        }

        /** 所有降号大调（1-7 降号，0 降号 = C 大调与 0 升号重复）。 */
        private val FLAT_MAJOR_KEYS: List<KeyInfo> = (1..7).map { i ->
            val (pc, letter, mod) = FLAT_MAJOR_ROOTS[i]
            majorKey(0, i, pc, letter, mod)
        }

        /** 所有大调（升号调 + 降号调，去重 C 大调）。 */
        val ALL_MAJOR_KEYS: List<KeyInfo> = SHARP_MAJOR_KEYS + FLAT_MAJOR_KEYS

        /** 所有升号小调（0-7 升号）。 */
        private val SHARP_MINOR_KEYS: List<KeyInfo> = (0..7).map { i ->
            val (pc, _, _) = SHARP_MAJOR_ROOTS[i]
            minorKey(i, 0, pc)
        }

        /** 所有降号小调（1-7 降号）。 */
        private val FLAT_MINOR_KEYS: List<KeyInfo> = (1..7).map { i ->
            val (pc, _, _) = FLAT_MAJOR_ROOTS[i]
            minorKey(0, i, pc)
        }

        /** 所有小调。 */
        val ALL_MINOR_KEYS: List<KeyInfo> = SHARP_MINOR_KEYS + FLAT_MINOR_KEYS

        /** 所有大调和小调。 */
        val ALL_KEYS: List<KeyInfo> = ALL_MAJOR_KEYS + ALL_MINOR_KEYS

        /**
         * 创建带固定种子的引擎实例（用于测试确定性）。
         */
        fun withSeed(seed: Long): KeySigEngine = KeySigEngine(Random(seed))
    }
}
