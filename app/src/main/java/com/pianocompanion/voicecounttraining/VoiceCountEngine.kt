package com.pianocompanion.voicecounttraining

import kotlin.random.Random

/**
 * 声部数量听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [VoiceCountDifficulty] 生成 [VoiceCountQuestion]：
 *
 * 1. **随机选声部数**：从该难度的 [VoiceCountDifficulty.voiceCountRange] 中随机一个。
 * 2. **随机选根音**：从合法根音范围中随机一个。
 * 3. **构造 voicing**：根据该难度间距 [VoiceCountDifficulty.spacing]，从根音向上逐个叠加音符，
 *    每个新音 = 前一音 + 间距候选池中随机一个半音值；保证无重复音；整体平移到合法音域。
 * 4. **构建选项**：按顺序生成「1 个音 … N 个音」（数量型选项保持自然顺序，便于用户对应）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class VoiceCountEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: VoiceCountDifficulty): VoiceCountQuestion {
        // 随机选声部数（从该难度范围）
        val voiceCount = difficulty.voiceCountRange.random(random)

        // 构造 voicing
        val voicing = buildVoicing(
            voiceCount = voiceCount,
            spacing = difficulty.spacing,
            random = random
        )

        // 选项：按自然顺序生成「1 个音 … maxCount 个音」
        val maxCount = difficulty.voiceCountRange.last
        val choices = (1..maxCount).map { VoiceCountQuestion.countLabelText(it) }

        return VoiceCountQuestion(
            difficulty = difficulty,
            voiceCount = voiceCount,
            rootMidi = voicing.first(),
            voicing = voicing,
            spacing = difficulty.spacing,
            durationMs = NOTE_DURATION_MS,
            answerChoices = choices,
            correctAnswer = VoiceCountQuestion.countLabelText(voiceCount)
        )
    }

    companion object {
        /** 默认和弦持续时长（毫秒）。 */
        const val NOTE_DURATION_MS = 1400L

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): VoiceCountEngine = VoiceCountEngine(Random(seed))

        /**
         * 从根音向上构造一组 voicing（保证无重复音、间距遵循 [spacing] 的候选池）。
         *
         * 先用 [baseRoot] 作为根音候选向上构造；若超出上限则整组向下平移以落入合法音域。
         * 保证返回的 voicing 长度恰好等于 [voiceCount]、严格递增、均在 [VoiceCountQuestion.MIN_MIDI]..
         * [VoiceCountQuestion.MAX_MIDI] 之间。
         *
         * @param voiceCount 声部数（≥1）
         * @param spacing 音符间距
         * @param random 随机源
         * @param baseRoot 根音起始 MIDI（默认 C4=60）
         */
        fun buildVoicing(
            voiceCount: Int,
            spacing: NoteSpacing,
            random: Random,
            baseRoot: Int = 60
        ): List<Int> {
            require(voiceCount >= 1) { "声部数必须 ≥ 1，实际 $voiceCount" }
            if (voiceCount == 1) {
                val single = baseRoot.coerceIn(
                    VoiceCountQuestion.MIN_MIDI,
                    VoiceCountQuestion.MAX_MIDI
                )
                return listOf(single)
            }

            // 向上逐个叠加
            val notes = mutableListOf(baseRoot)
            var prev = baseRoot
            repeat(voiceCount - 1) {
                val interval = spacing.intervalPool.random(random)
                prev += interval
                notes.add(prev)
            }

            // 平移到合法音域：若最高音超出上限，整组下移
            val maxAllowed = VoiceCountQuestion.MAX_MIDI
            val minAllowed = VoiceCountQuestion.MIN_MIDI
            val top = notes.last()
            if (top > maxAllowed) {
                val shift = top - maxAllowed
                val shifted = notes.map { it - shift }
                // 保证最低音不低于下限（极端密集 + 高根音情形下做最终钳制）
                return shifted.map { it.coerceIn(minAllowed, maxAllowed) }
                    .let { ensureStrictlyIncreasingUnique(it) }
            }
            // 最低音低于下限则上移（baseRoot 一般在范围内，此处防御性处理）
            if (notes.first() < minAllowed) {
                val shift = minAllowed - notes.first()
                return notes.map { it + shift }.let { ensureStrictlyIncreasingUnique(it) }
            }
            return ensureStrictlyIncreasingUnique(notes)
        }

        /**
         * 保证列表严格递增且唯一：若有因 coerce 造成的重复，则把每个后续元素强制 +1 推开。
         * （仅在边界钳制后理论上可能产生相邻重复，这里做最终修复。）
         */
        private fun ensureStrictlyIncreasingUnique(notes: List<Int>): List<Int> {
            if (notes.size <= 1) return notes
            val result = mutableListOf(notes[0])
            for (i in 1 until notes.size) {
                var v = notes[i]
                if (v <= result.last()) v = result.last() + 1
                result.add(v.coerceIn(VoiceCountQuestion.MIN_MIDI, VoiceCountQuestion.MAX_MIDI))
            }
            return result
        }
    }
}
