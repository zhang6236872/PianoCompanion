package com.pianocompanion.data.parser

import com.pianocompanion.data.model.Accidental
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MusicXmlExporter] 单元测试。
 *
 * 覆盖：
 *  - 文档结构（XML 声明、DOCTYPE、score-partwise 根、part-list）
 *  - 音高编码（MIDI → step/alter/octave，含黑键升号）
 *  - 节奏编码（ms → divisions → type，全/二/四/八/十六分及附点）
 *  - 小节推导（基于拍号 + 速度）
 *  - 和弦、间隙 forward、休止（forward 填充）
 *  - 双声部（TREBLE + BASS → 两个 part）
 *  - 元数据转义、速度/拍号写入
 *  - **往返(round-trip)**：export → [MusicXmlParser].parse → 校验音高/时值/节奏一致
 *  - 边界：空乐谱、单音符、临时记号、装饰音
 */
class MusicXmlExporterTest {

    private val exporter = MusicXmlExporter()

    // 四分音符时长 @120BPM
    private val qMs = 500L

    private fun note(
        midi: Int,
        startMs: Long,
        durationMs: Long,
        staff: Staff = Staff.TREBLE,
        accidental: Accidental = Accidental.NONE,
        isGrace: Boolean = false
    ): ScoreNote = ScoreNote(
        midiNumber = midi,
        noteName = MusicUtils.midiToNoteName(midi),
        startTime = startMs,
        duration = durationMs,
        staff = staff,
        accidental = accidental,
        isGraceNote = isGrace
    )

    private fun score(
        notes: List<ScoreNote>,
        title: String = "Test",
        composer: String = "Tester",
        tempo: Int = 120,
        timeSig: String = "4/4"
    ): Score = Score(
        id = "s1",
        title = title,
        composer = composer,
        notes = notes,
        tempo = tempo,
        timeSignature = timeSig,
        source = ScoreSource.OMR
    )

    // ==================== 文档结构 ====================

    @Test
    fun `export 生成合法 XML 声明与 DOCTYPE 与根元素`() {
        val xml = exporter.export(score(emptyList()))
        assertTrue("应包含 XML 声明", xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue("应包含 DOCTYPE", xml.contains("<!DOCTYPE score-partwise"))
        assertTrue("应包含 score-partwise 根", xml.contains("<score-partwise version=\"3.0\">"))
        assertTrue("应以根结束标签收尾", xml.trimEnd().endsWith("</score-partwise>"))
    }

    @Test
    fun `导出标题与作曲家`() {
        val xml = exporter.export(score(emptyList(), title = "月光奏鸣曲", composer = "贝多芬"))
        assertTrue(xml.contains("<work-title>月光奏鸣曲</work-title>"))
        assertTrue(xml.contains("<creator type=\"composer\">贝多芬</creator>"))
    }

    @Test
    fun `特殊字符被正确转义`() {
        val xml = exporter.export(score(emptyList(), title = "A & B <C> \"x\"", composer = "O'Brien"))
        assertTrue(xml.contains("<work-title>A &amp; B &lt;C&gt; &quot;x&quot;</work-title>"))
        assertTrue(xml.contains("<creator type=\"composer\">O&apos;Brien</creator>"))
        // 原始未转义符号不应出现于标题上下文
        assertFalse(xml.contains("<work-title>A & B <C>"))
    }

    @Test
    fun `part-list 包含 score-part 条目`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs))))
        assertTrue(xml.contains("<part-list>"))
        assertTrue(xml.contains("<score-part id=\"P1\">"))
        assertTrue(xml.contains("<part-name>Piano (Right)</part-name>"))
    }

    @Test
    fun `空乐谱输出含 attributes 的空小节`() {
        val xml = exporter.export(score(emptyList()))
        assertTrue(xml.contains("<measure number=\"1\">"))
        assertTrue(xml.contains("<divisions>4</divisions>"))
        assertTrue(xml.contains("<clef><sign>G</sign><line>2</line></clef>"))
    }

    // ==================== 音高编码 ====================

    @Test
    fun `midiToPitchComponents 白键 C4`() {
        val (step, alter, octave) = exporter.midiToPitchComponents(60)
        assertEquals("C", step)
        assertEquals(0, alter)
        assertEquals(4, octave)
    }

    @Test
    fun `midiToPitchComponents 黑键 F#5 用升号表示`() {
        val (step, alter, octave) = exporter.midiToPitchComponents(78) // F#5
        assertEquals("F", step)
        assertEquals(1, alter)
        assertEquals(5, octave)
    }

    @Test
    fun `midiToPitchComponents A0 与 C8 边界`() {
        val (s0, _, o0) = exporter.midiToPitchComponents(21) // A0
        assertEquals("A", s0)
        assertEquals(0, o0)
        val (s8, _, o8) = exporter.midiToPitchComponents(108) // C8
        assertEquals("C", s8)
        assertEquals(8, o8)
    }

    @Test
    fun `导出音符包含正确 pitch 子元素`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs)))) // C4
        assertTrue(xml.contains("<step>C</step>"))
        assertTrue(xml.contains("<octave>4</octave>"))
        assertFalse("白键不应输出 alter", xml.contains("<alter>"))
    }

    @Test
    fun `黑键音符输出 alter 等于 1`() {
        val xml = exporter.export(score(listOf(note(61, 0, qMs)))) // C#4
        assertTrue(xml.contains("<step>C</step>"))
        assertTrue(xml.contains("<alter>1</alter>"))
    }

    // ==================== 节奏编码 ====================

    @Test
    fun `四分音符 @120BPM 输出 duration 4 与 type quarter`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs))))
        assertTrue(xml.contains("<duration>4</duration>"))
        assertTrue(xml.contains("<type>quarter</type>"))
        assertFalse(xml.contains("<dot"))
    }

    @Test
    fun `二分音符输出 type half`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs * 2))))
        assertTrue(xml.contains("<type>half</type>"))
        assertTrue(xml.contains("<duration>8</duration>"))
    }

    @Test
    fun `全音符输出 type whole`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs * 4))))
        assertTrue(xml.contains("<type>whole</type>"))
        assertTrue(xml.contains("<duration>16</duration>"))
    }

    @Test
    fun `八分音符输出 type eighth`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs / 2))))
        assertTrue(xml.contains("<type>eighth</type>"))
        assertTrue(xml.contains("<duration>2</duration>"))
    }

    @Test
    fun `十六分音符输出 type 16th`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs / 4))))
        assertTrue(xml.contains("<type>16th</type>"))
        assertTrue(xml.contains("<duration>1</duration>"))
    }

    @Test
    fun `附点四分音符输出 dot`() {
        val xml = exporter.export(score(listOf(note(60, 0, 750L)))) // 1.5 * 500
        assertTrue(xml.contains("<type>quarter</type>"))
        assertTrue(xml.contains("<dot/>"))
        assertTrue(xml.contains("<duration>6</duration>"))
    }

    @Test
    fun `附点二分音符输出 dot`() {
        val xml = exporter.export(score(listOf(note(60, 0, 1500L)))) // 1.5 * 1000
        assertTrue(xml.contains("<type>half</type>"))
        assertTrue(xml.contains("<dot/>"))
        assertTrue(xml.contains("<duration>12</duration>"))
    }

    @Test
    fun `divisionsToType 附点全音符`() {
        val t = exporter.divisionsToType(24) // 4*4*3/2
        assertEquals("whole", t.type)
        assertEquals(1, t.dots)
    }

    @Test
    fun `divisionsToType 非标准时值按区间归类`() {
        assertEquals("whole", exporter.divisionsToType(17).type)
        assertEquals("half", exporter.divisionsToType(10).type)
        assertEquals("quarter", exporter.divisionsToType(4).type)
        assertEquals("eighth", exporter.divisionsToType(3).type)
        assertEquals("16th", exporter.divisionsToType(1).type)
    }

    // ==================== 小节推导 ====================

    @Test
    fun `四个四分音符归入同一小节`() {
        val notes = (0 until 4).map { note(60 + it, it * qMs, qMs) }
        val xml = exporter.export(score(notes))
        // 应只有一个 measure number="1"
        assertEquals(1, Regex("<measure number=\"1\">").findAll(xml).count())
    }

    @Test
    fun `第五个四分音符进入第二小节`() {
        val notes = (0 until 5).map { note(60 + it, it * qMs, qMs) }
        val xml = exporter.export(score(notes))
        assertTrue("应包含第二小节", xml.contains("<measure number=\"2\">"))
    }

    @Test
    fun `3-4 拍号下第三小节边界正确`() {
        // 3/4 拍：每小节 3 个四分音符 = 1500ms
        val notes = (0 until 7).map { note(60 + it, it * qMs, qMs) }
        val xml = exporter.export(score(notes, timeSig = "3/4"))
        assertTrue(xml.contains("<beats>3</beats>"))
        assertTrue(xml.contains("<beat-type>4</beat-type>"))
        // 0,1,2 -> m1; 3,4,5 -> m2; 6 -> m3
        assertTrue("应包含第三小节", xml.contains("<measure number=\"3\">"))
    }

    @Test
    fun `拍号写入 time 元素`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs)), timeSig = "6/8"))
        assertTrue(xml.contains("<beats>6</beats>"))
        assertTrue(xml.contains("<beat-type>8</beat-type>"))
    }

    @Test
    fun `tempo 写入 metronome 与 sound 元素`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs)), tempo = 90))
        assertTrue(xml.contains("<per-minute>90</per-minute>"))
        assertTrue(xml.contains("<sound tempo=\"90\"/>"))
    }

    // ==================== 和弦与间隙 ====================

    @Test
    fun `同时发音输出 chord 元素`() {
        // C4 与 E4 同时开始
        val notes = listOf(note(60, 0, qMs), note(64, 0, qMs))
        val xml = exporter.export(score(notes))
        assertTrue("第二个音符应有 chord 标记", xml.contains("<chord/>"))
    }

    @Test
    fun `顺序音符不输出 chord`() {
        val notes = listOf(note(60, 0, qMs), note(62, qMs, qMs))
        val xml = exporter.export(score(notes))
        assertFalse("不同时间音符不应有 chord", xml.contains("<chord/>"))
    }

    @Test
    fun `音符间间隙用 forward 填充`() {
        // 第一个音符在 0，第二个在 1000ms（间隔一个四分音符 = 500ms 休止）
        val notes = listOf(note(60, 0, qMs), note(62, 1000L, qMs))
        val xml = exporter.export(score(notes))
        assertTrue("间隙应输出 forward", xml.contains("<forward>"))
        assertTrue(xml.contains("<duration>4</duration>")) // 500ms 间隙 = 4 divisions
    }

    @Test
    fun `连续音符无间隙不输出 forward`() {
        val notes = listOf(note(60, 0, qMs), note(62, qMs, qMs))
        val xml = exporter.export(score(notes))
        assertFalse("无间隙不应有 forward", xml.contains("<forward>"))
    }

    // ==================== 双声部 ====================

    @Test
    fun `TREBLE 与 BASS 混合输出两个 part`() {
        val notes = listOf(
            note(60, 0, qMs, staff = Staff.TREBLE),
            note(40, 0, qMs, staff = Staff.BASS) // E2
        )
        val xml = exporter.export(score(notes))
        assertTrue("应有 P1 高音声部", xml.contains("<score-part id=\"P1\">"))
        assertTrue("应有 P2 低音声部", xml.contains("<score-part id=\"P2\">"))
        assertTrue("P1 应含 G 谱号", xml.contains("<sign>G</sign>"))
        assertTrue("P2 应含 F 谱号", xml.contains("<sign>F</sign>"))
        assertTrue("应有 BASS 音符 (E2)", xml.contains("<step>E</step>"))
    }

    @Test
    fun `仅 BASS 谱表输出单一低音声部`() {
        val notes = listOf(note(40, 0, qMs, staff = Staff.BASS))
        val xml = exporter.export(score(notes))
        assertTrue("F 谱号", xml.contains("<sign>F</sign>"))
        assertFalse("不应有 G 谱号", xml.contains("<sign>G</sign>"))
        assertFalse("不应有 P2", xml.contains("P2"))
    }

    @Test
    fun `C 谱号归入高音声部`() {
        val notes = listOf(note(60, 0, qMs, staff = Staff.ALTO))
        val xml = exporter.export(score(notes))
        assertTrue("C 谱号归入 G 谱号声部", xml.contains("<sign>G</sign>"))
    }

    // ==================== 临时记号与装饰音 ====================

    @Test
    fun `FLAT 临时记号输出 accidental flat`() {
        val n = note(61, 0, qMs).copy(accidental = Accidental.FLAT)
        val xml = exporter.export(score(listOf(n)))
        assertTrue(xml.contains("<accidental>flat</accidental>"))
    }

    @Test
    fun `NATURAL 临时记号输出 accidental natural`() {
        val n = note(60, 0, qMs).copy(accidental = Accidental.NATURAL)
        val xml = exporter.export(score(listOf(n)))
        assertTrue(xml.contains("<accidental>natural</accidental>"))
    }

    @Test
    fun `NONE 临时记号不输出 accidental 元素`() {
        val n = note(60, 0, qMs).copy(accidental = Accidental.NONE)
        val xml = exporter.export(score(listOf(n)))
        assertFalse("白键无临时记号不应输出 accidental", xml.contains("<accidental>"))
    }

    @Test
    fun `DOUBLE_FLAT 与 DOUBLE_SHARP 输出对应 accidental`() {
        val xmlFlat = exporter.export(
            score(listOf(note(60, 0, qMs).copy(accidental = Accidental.DOUBLE_FLAT)))
        )
        assertTrue(xmlFlat.contains("<accidental>flat-flat</accidental>"))
        val xmlSharp = exporter.export(
            score(listOf(note(60, 0, qMs).copy(accidental = Accidental.DOUBLE_SHARP)))
        )
        assertTrue(xmlSharp.contains("<accidental>double-sharp</accidental>"))
    }

    @Test
    fun `装饰音输出 grace 元素`() {
        val n = note(60, 0, qMs, isGrace = true)
        val xml = exporter.export(score(listOf(n)))
        assertTrue(xml.contains("<grace/>"))
    }

    // ==================== 往返测试 (round-trip) ====================

    @Test
    fun `往返 - 导出后重新解析音高一致`() {
        val original = listOf(
            note(60, 0, qMs),   // C4
            note(62, qMs, qMs), // D4
            note(64, 2 * qMs, qMs), // E4
            note(65, 3 * qMs, qMs)  // F4
        )
        val xml = exporter.export(score(original))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals(4, reparsed.notes.size)
        original.zip(reparsed.notes).forEach { (orig, parsed) ->
            assertEquals("音高应一致", orig.midiNumber, parsed.midiNumber)
        }
    }

    @Test
    fun `往返 - 节奏时值一致`() {
        // 注意：MusicXmlParser 内小节内 startTime 以 divisions 编码、跨小节以 ms 编码
        // （历史设计），因此 startTime 不可纯 ms 往返；但 duration 与 pitch 可靠往返。
        val original = listOf(
            note(60, 0, qMs),
            note(62, qMs, qMs * 2), // 二分音符
            note(64, 3 * qMs, qMs / 2) // 八分音符
        )
        val xml = exporter.export(score(original))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals(3, reparsed.notes.size)
        original.zip(reparsed.notes).forEach { (orig, parsed) ->
            assertEquals("duration 应一致 ${orig.noteName}", orig.duration, parsed.duration)
        }
        // 验证节奏类型：二分音符 duration=1000ms、八分音符 duration=250ms
        assertEquals(1000L, reparsed.notes[1].duration)
        assertEquals(250L, reparsed.notes[2].duration)
    }

    @Test
    fun `往返 - 标题与作曲家保留`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs)), title = "圆舞曲", composer = "肖邦"))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals("圆舞曲", reparsed.title)
        assertEquals("肖邦", reparsed.composer)
    }

    @Test
    fun `往返 - tempo 保留`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs)), tempo = 144))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals(144, reparsed.tempo)
    }

    @Test
    fun `往返 - 拍号保留`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs)), timeSig = "3/4"))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals("3/4", reparsed.timeSignature)
    }

    @Test
    fun `往返 - 黑键音高正确`() {
        val original = listOf(
            note(61, 0, qMs),  // C#4
            note(66, qMs, qMs) // F#4
        )
        val xml = exporter.export(score(original))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals(2, reparsed.notes.size)
        assertEquals(61, reparsed.notes[0].midiNumber)
        assertEquals(66, reparsed.notes[1].midiNumber)
    }

    @Test
    fun `往返 - 单音符乐谱`() {
        val original = listOf(note(72, 0, qMs)) // C5
        val xml = exporter.export(score(original))
        val reparsed = MusicXmlParser().parse(xml.byteInputStream())
        assertEquals(1, reparsed.notes.size)
        assertEquals(72, reparsed.notes[0].midiNumber)
    }

    // ==================== msToDivisions 工具 ====================

    @Test
    fun `msToDivisions 四分音符返回 4`() {
        assertEquals(4, exporter.msToDivisions(500.0, 500.0))
    }

    @Test
    fun `msToDivisions 零时长返回 0`() {
        assertEquals(0, exporter.msToDivisions(0.0, 500.0))
    }

    @Test
    fun `msToDivisions 四舍五入到最近 division`() {
        // 250ms @ quarter=500 → 2 divisions
        assertEquals(2, exporter.msToDivisions(250.0, 500.0))
    }

    @Test
    fun `msToDivisions 零 quarterMs 不除零`() {
        assertEquals(4, exporter.msToDivisions(500.0, 0.0))
    }

    // ==================== 边界与健壮性 ====================

    @Test
    fun `单音符乐谱生成完整文档`() {
        val xml = exporter.export(score(listOf(note(60, 0, qMs))))
        // 应只有 measure 1，含一个 note
        assertEquals(1, Regex("<note>").findAll(xml).count())
    }

    @Test
    fun `所有音符零时长仍能导出`() {
        val notes = listOf(note(60, 0, 0), note(62, qMs, 0))
        val xml = exporter.export(score(notes))
        assertTrue(xml.contains("<score-partwise"))
        // 零时长被钳制为至少 1 division
        assertTrue(xml.contains("<duration>1</duration>"))
    }

    @Test
    fun `高 MIDI 音区正确编码`() {
        val xml = exporter.export(score(listOf(note(108, 0, qMs)))) // C8
        assertTrue(xml.contains("<step>C</step>"))
        assertTrue(xml.contains("<octave>8</octave>"))
    }

    @Test
    fun `低 MIDI 音区正确编码`() {
        val xml = exporter.export(score(listOf(note(21, 0, qMs, staff = Staff.BASS)))) // A0
        assertTrue(xml.contains("<step>A</step>"))
        assertTrue(xml.contains("<octave>0</octave>"))
    }
}
