package com.pianocompanion.ui.library

import com.pianocompanion.data.model.Staff
import com.pianocompanion.generator.SightReadingDifficulty
import com.pianocompanion.generator.SightReadingOptions
import com.pianocompanion.omr.image.KeySignature

/**
 * 视奏练习生成器对话框使用的常见调号子集。
 *
 * 完整的 [KeySignature] 枚举包含 15 个调（7 升 + 7 降 + C），但其中大部分
 * （如 C#大调、bC大调、bG大调）在钢琴视奏练习中极少使用且读谱困难。这里挑选
 * 钢琴教学中最常用的 7 个调，按五度圈顺序排列，兼顾升号调与降号调，覆盖了
 * 绝大多数初学/中级练习需求。
 */
internal object SightReadingKeys {

    /** 对话框中供选择的常用调号（五度圈顺序：C → 升号方向 → 降号方向）。 */
    val common: List<KeySignature> = listOf(
        KeySignature.C_MAJOR_A_MINOR,   // C大调 — 0 升降，最基础
        KeySignature.G_MAJOR_E_MINOR,   // G大调 — 1 升 (F#)
        KeySignature.D_MAJOR,           // D大调 — 2 升 (F#, C#)
        KeySignature.A_MAJOR,           // A大调 — 3 升 (F#, C#, G#)
        KeySignature.F_MAJOR_D_MINOR,   // F大调 — 1 降 (Bb)
        KeySignature.B_FLAT_MAJOR,      // bB大调 — 2 降 (Bb, Eb)
        KeySignature.E_FLAT_MAJOR       // bE大调 — 3 降 (Bb, Eb, Ab)
    )

    /** 对话框中供选择的拍号。 */
    val timeSignatures: List<String> = listOf("4/4", "3/4", "2/4")

    /** 对话框中供选择的小节数（4 的倍数，形成完整 4 小节乐句）。 */
    val measures: List<Int> = listOf(4, 8, 12, 16)

    /** 对话框中可选择的默认 BPM 预设档位。 */
    val tempoPresets: List<Int> = listOf(60, 80, 100, 120, 144)
}

/**
 * 视奏练习生成器对话框的可编辑状态。
 *
 * 该 data class 是纯 Kotlin 数据（无 Compose 依赖），可被对话框 UI 持有，
 * 也可在单元测试中直接验证 [toOptions] 的转换逻辑。
 */
data class SightReadingDialogState(
    val keySignature: KeySignature = KeySignature.C_MAJOR_A_MINOR,
    val difficulty: SightReadingDifficulty = SightReadingDifficulty.BEGINNER,
    val measures: Int = 8,
    val timeSignature: String = "4/4",
    val tempo: Int = 100,
    val staff: Staff = Staff.TREBLE
) {
    /**
     * 将对话框状态转换为生成器配置。每次调用都生成一个新的随机种子，
     * 使「再来一组」按钮能产生不同乐谱。
     */
    fun toOptions(): SightReadingOptions = SightReadingOptions(
        keySignature = keySignature,
        difficulty = difficulty,
        measures = measures,
        timeSignature = timeSignature,
        tempo = tempo,
        staff = staff,
        seed = System.currentTimeMillis()
    )

    /**
     * 将对话框状态转换为生成器配置，使用指定种子（主要用于测试与复现）。
     */
    fun toOptions(seed: Long): SightReadingOptions = SightReadingOptions(
        keySignature = keySignature,
        difficulty = difficulty,
        measures = measures,
        timeSignature = timeSignature,
        tempo = tempo,
        staff = staff,
        seed = seed
    )
}
