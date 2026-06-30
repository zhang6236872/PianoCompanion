package com.pianocompanion.music

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.util.MusicUtils

/**
 * 乐谱移调引擎。
 *
 * 移调（transposition）是将一首乐曲的全部音符升高或降低相同的半音数，
 * 保持音符间的相对音程关系不变。这是钢琴学习中最常用的功能之一：
 * - 降到更简单的调号（如 5 个降号 → C 大调）降低视奏难度
 * - 匹配歌手音域或移调乐器（如 Bb 单簧管 / F 圆号）
 * - 为儿童练习简化指法
 *
 * 本引擎在 MIDI 数值层面做半音平移（`midiNumber + semitones`），同时：
 * - 钳位到有效钢琴范围（A0=21 ~ C8=108），越界音符截断到边界
 * - 更新 `noteName` 以反映移调后的实际音高
 * - 保留所有其他音符属性（时值、力度、谱表、演奏法、指法、连音组等）
 *
 * 本类为纯 Kotlin 实现，无 Android 依赖，完全可单元测试。
 */
object Transposer {

    /** 最小有效钢琴音符号（A0）。 */
    const val MIN_MIDI = 21

    /** 最大有效钢琴音符号（C8）。 */
    const val MAX_MIDI = 108

    /** 常用目标调（升号优先），供 UI 快速选择。 */
    val COMMON_KEYS = listOf(
        KeyOption(0, "C大调 / a小调", "原调(0)", 0),
        KeyOption(1, "C#大调", "+1", 1),
        KeyOption(2, "D大调", "+2", 2),
        KeyOption(3, "Eb大调", "+3", 3),
        KeyOption(4, "E大调", "+4", 4),
        KeyOption(5, "F大调", "+5", 5),
        KeyOption(6, "F#大调", "+6", 6),
        KeyOption(7, "G大调", "+7", 7),
        KeyOption(-1, "B大调", "-1", -1),
        KeyOption(-2, "Bb大调", "-2", -2),
        KeyOption(-3, "A大调", "-3", -3),
        KeyOption(-4, "Ab大调", "-4", -4),
        KeyOption(-5, "G大调", "-5", -5),
    )

    /**
     * 将乐谱的全部音符移调 [semitones] 个半音。
     *
     * @param score 原始乐谱。
     * @param semitones 半音移位量（正=升高，负=降低，0=不变）。
     * @return 移调后的新乐谱（原始乐谱不受影响）。标题后缀「[移调 ±N]」标注移调量。
     */
    fun transpose(score: Score, semitones: Int): Score {
        if (semitones == 0) return score
        return score.copy(
            notes = score.notes.map { note -> transposeNote(note, semitones) },
            title = annotateTitle(score.title, semitones),
            id = score.id + "_transposed_${semitones}"
        )
    }

    /**
     * 移调到指定目标调性。
     *
     * 先检测原调性，计算从原调到目标调的半音差，再移调。
     * 例如原调 C 大调 → 目标 G 大调 = +7 半音。
     *
     * @param score 原始乐谱。
     * @param targetKey 目标调性。
     * @return 移调后的新乐谱。
     */
    fun transposeToKey(score: Score, targetKey: KeyInfo): Score {
        val currentKey = KeyDetector.detect(score)
        val semitones = computeSemitoneOffset(currentKey, targetKey)
        return transpose(score, semitones)
    }

    /**
     * 计算从原调到目标调的最短半音移位量。
     *
     * 在八度内取最短路径（如 C→B 可以是 -1 而非 +11）。
     *
     * @param from 原调。
     * @param to 目标调。
     * @return 半音移位量 [-6, +6]（最短路径）。
     */
    fun computeSemitoneOffset(from: KeyInfo, to: KeyInfo): Int {
        var diff = (to.tonic - from.tonic) % 12
        // 范围 [-5, +7]：对于纯五度（+7）偏好向上移调（五度圈惯例），
        // 而非向下的等价纯四度（-5），使旋律保持在相近音区。
        if (diff > 7) diff -= 12
        if (diff < -5) diff += 12
        return diff
    }

    /**
     * 移调单个音符。
     *
     * 钳位到有效钢琴范围，更新音名。保留所有其他属性。
     */
    fun transposeNote(note: ScoreNote, semitones: Int): ScoreNote {
        val newMidi = (note.midiNumber + semitones).coerceIn(MIN_MIDI, MAX_MIDI)
        return note.copy(
            midiNumber = newMidi,
            noteName = MusicUtils.midiToNoteName(newMidi)
        )
    }

    /**
     * 检测乐谱移调后是否会有音符超出有效钢琴范围。
     *
     * @return 越界音符数量（0 表示移调安全）。
     */
    fun countOutOfRange(score: Score, semitones: Int): Int {
        return score.notes.count { note ->
            val shifted = note.midiNumber + semitones
            shifted < MIN_MIDI || shifted > MAX_MIDI
        }
    }

    /**
     * 在标题后追加移调标注。
     *
     * "欢乐颂" → "欢乐颂 [移调 +3]"
     */
    private fun annotateTitle(title: String, semitones: Int): String {
        val sign = if (semitones >= 0) "+" else ""
        return "$title [移调 ${sign}${semitones}]"
    }
}

/**
 * UI 用的目标调选项。
 *
 * @param semitoneOffset 相对 C 大调的半音偏移量。
 * @param label 显示标签。
 * @param shortLabel 简短标签（步进器用）。
 * @param step 步进器步序（用于排序）。
 */
data class KeyOption(
    val semitoneOffset: Int,
    val label: String,
    val shortLabel: String,
    val step: Int
)
