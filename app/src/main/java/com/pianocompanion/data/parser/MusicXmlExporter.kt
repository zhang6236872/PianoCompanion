package com.pianocompanion.data.parser

import com.pianocompanion.data.model.Accidental
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff

/**
 * 将 [Score] 对象序列化为 MusicXML 3.0 partwise 格式字符串。
 *
 * 这是 [MusicXmlParser] 的对称反向操作，用于：
 *  - **闭环 OMR 流程**：拍照识谱 → [Score] → 导出 MusicXML → 在 MuseScore / Finale /
 *    Dorico 等外部乐谱软件中打开、编辑、校对。
 *  - **导入乐谱再导出**：MIDI / MusicXML 导入后可重新导出为标准 MusicXML。
 *  - **乐谱分享**：将 app 内置乐谱或 OMR 识别结果分享给其他音乐工具。
 *
 * ## 设计要点
 *
 * - **纯 Kotlin，无 Android 依赖**：仅使用 [StringBuilder] 拼接 XML，完全可单元测试。
 * - **声部(part)划分**：钢琴大谱表(TREBLE+BASS)及单声部乐器均正确处理。
 *   非 BASS 谱表(TREBLE / 各类 C 谱号 / BOTH)归入**高音声部**（G 谱号），
 *   BASS 谱表归入**低音声部**（F 谱号）。仅有单一声部组时只输出一个 part。
 *   每个声部组在 MusicXML 中是独立的 part（各自独立的小节时间轴），
 *   这是 MuseScore / Finale / open-sheet-music-display 等外部软件均能正确解析的有效格式。
 * - **小节计算**：根据拍号(time signature)与速度(tempo)从 startTime 推导每个音符所属小节，
 *   不依赖 [ScoreNote.measureIndex]（该字段在不同来源中可靠性不一），保证来源无关的正确性。
 * - **节奏编码**：divisions=4（每个四分音符 4 个 division），可精确表示全/二/四/八/十六分音符。
 *   ms 时值 → division 数 → `<type>` 元素（whole/half/quarter/eighth/16th），附点音符输出 `<dot/>`。
 * - **和弦支持**：同一声部同一小节内 startTime 完全相同的音符输出为和弦
 *   （第二个及之后的音符加 `<chord/>` 元素）。
 * - **间隙填充**：小节内音符间的时间间隙输出为 `<forward>`（推进时间游标）。
 * - **XML 转义**：标题 / 作曲家字段做 XML 实体转义，防止特殊字符破坏文档。
 *
 * @property divisions 每个四分音符对应的 division 数，默认 4。
 */
class MusicXmlExporter(private val divisions: Int = DEFAULT_DIVISIONS) {

    /**
     * 将 [score] 序列化为 MusicXML 3.0 partwise 文档字符串。
     *
     * @param score 要导出的乐谱。
     * @return 完整的 MusicXML 文档（含 XML 声明、DOCTYPE、score-partwise 根元素）。
     */
    fun export(score: Score): String {
        val quarterMs = 60000.0 / score.tempo.coerceAtLeast(1)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 3.0 Partwise//EN\" ")
        sb.append("\"http://www.musicxml.org/dtds/partwise.dtd\">\n")
        sb.append("<score-partwise version=\"3.0\">\n")

        sb.append("  <work>\n")
        sb.append("    <work-title>").append(escape(score.title)).append("</work-title>\n")
        sb.append("  </work>\n")
        sb.append("  <identification>\n")
        sb.append("    <creator type=\"composer\">").append(escape(score.composer)).append("</creator>\n")
        sb.append("    <encoding>\n")
        sb.append("      <software>Piano Companion</software>\n")
        sb.append("    </encoding>\n")
        sb.append("  </identification>\n")

        val parts = groupByPart(score.notes)

        sb.append("  <part-list>\n")
        parts.forEachIndexed { i, (clef, _) ->
            sb.append("    <score-part id=\"").append(partId(i)).append("\">\n")
            sb.append("      <part-name>").append(partNameFor(clef)).append("</part-name>\n")
            sb.append("    </score-part>\n")
        }
        sb.append("  </part-list>\n")

        parts.forEachIndexed { i, (clef, notes) ->
            sb.append("  <part id=\"").append(partId(i)).append("\">\n")
            appendPartMeasures(sb, score, clef, notes, quarterMs)
            sb.append("  </part>\n")
        }

        sb.append("</score-partwise>\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------
    //  声部分组
    // ------------------------------------------------------------------

    /** 将音符按谱表分为高音声部(非 BASS)与低音声部(BASS)两组。 */
    private fun groupByPart(notes: List<ScoreNote>): List<Pair<ClefKind, List<ScoreNote>>> {
        if (notes.isEmpty()) {
            return listOf(ClefKind.TREBLE to emptyList())
        }
        val treble = notes.filter { it.staff != Staff.BASS }.sortedBy { it.startTime }
        val bass = notes.filter { it.staff == Staff.BASS }.sortedBy { it.startTime }
        val result = mutableListOf<Pair<ClefKind, List<ScoreNote>>>()
        if (treble.isNotEmpty()) result.add(ClefKind.TREBLE to treble)
        if (bass.isNotEmpty()) result.add(ClefKind.BASS to bass)
        return result
    }

    // ------------------------------------------------------------------
    //  小节 / 时间轴
    // ------------------------------------------------------------------

    private fun appendPartMeasures(
        sb: StringBuilder,
        score: Score,
        clef: ClefKind,
        notes: List<ScoreNote>,
        quarterMs: Double
    ) {
        val (beats, beatType) = parseTimeSignature(score.timeSignature)
        val measureMs = beats.toDouble() * 4.0 / beatType.toDouble() * quarterMs

        // 空声部：至少输出一个含 attributes 的空小节，保证文档有效。
        if (notes.isEmpty()) {
            appendMeasure(sb, 1, score, clef, beats, beatType, emptyList(), emitAttributes = true)
            return
        }

        // 按 startTime 归入小节。
        val byMeasure = LinkedHashMap<Int, MutableList<ScoreNote>>()
        for (note in notes) {
            val measureNum = if (measureMs <= 0) 1 else (note.startTime / measureMs).toInt() + 1
            byMeasure.getOrPut(measureNum) { mutableListOf() }.add(note)
        }

        val sortedMeasures = byMeasure.keys.sorted()
        sortedMeasures.forEachIndexed { idx, measureNum ->
            val measureNotes = byMeasure.getValue(measureNum).sortedBy { it.startTime }
            val events = buildMeasureEvents(measureNotes, measureMs, measureNum, quarterMs)
            appendMeasure(
                sb,
                number = measureNum,
                score,
                clef,
                beats,
                beatType,
                events,
                emitAttributes = idx == 0
            )
        }
    }

    /**
     * 将一个小节内的音符转换为有序的事件列表（音符 + forward 填充间隙）。
     * 相同 offsetDiv 的音符构成和弦（首个 isChord=false，其余 isChord=true）。
     */
    private fun buildMeasureEvents(
        measureNotes: List<ScoreNote>,
        measureMs: Double,
        measureNum: Int,
        quarterMs: Double
    ): List<MeasureEvent> {
        if (measureNotes.isEmpty()) return emptyList()
        val events = mutableListOf<MeasureEvent>()
        val measureStart = (measureNum - 1) * measureMs
        var cursor = 0         // 已输出的 division 位置（小节内）
        var lastNoteOffset = -1 // 上一个音符的小节内 offset（用于和弦判定）

        for (note in measureNotes) {
            val offsetMs = (note.startTime - measureStart).coerceAtLeast(0.0)
            val offsetDiv = msToDivisions(offsetMs, quarterMs)
            if (offsetDiv > cursor) {
                events.add(MeasureEvent.Forward(offsetDiv - cursor))
            }
            val durDiv = msToDivisions(note.duration.toDouble(), quarterMs).coerceAtLeast(1)
            // 和弦判定：与上一个音符的 offset 相同（同时发音）。注意不能与 cursor 比较，
            // 因为首个和弦音已将 cursor 推进。
            val isChord = offsetDiv == lastNoteOffset
            events.add(MeasureEvent.Note(note, durDiv, isChord))
            if (!isChord) {
                cursor = offsetDiv + durDiv
            }
            lastNoteOffset = offsetDiv
        }
        return events
    }

    private fun appendMeasure(
        sb: StringBuilder,
        number: Int,
        score: Score,
        clef: ClefKind,
        beats: Int,
        beatType: Int,
        events: List<MeasureEvent>,
        emitAttributes: Boolean
    ) {
        sb.append("    <measure number=\"$number\">\n")
        if (emitAttributes) {
            sb.append("      <attributes>\n")
            sb.append("        <divisions>").append(divisions).append("</divisions>\n")
            sb.append("        <key><fifths>0</fifths></key>\n")
            sb.append("        <time><beats>").append(beats).append("</beats>")
            sb.append("<beat-type>").append(beatType).append("</beat-type></time>\n")
            when (clef) {
                ClefKind.TREBLE -> sb.append("        <clef><sign>G</sign><line>2</line></clef>\n")
                ClefKind.BASS -> sb.append("        <clef><sign>F</sign><line>4</line></clef>\n")
            }
            sb.append("      </attributes>\n")
            sb.append("      <direction placement=\"above\">\n")
            sb.append("        <direction-type><metronome>\n")
            sb.append("          <beat-unit>quarter</beat-unit>\n")
            sb.append("          <per-minute>").append(score.tempo).append("</per-minute>\n")
            sb.append("        </metronome></direction-type>\n")
            sb.append("        <sound tempo=\"").append(score.tempo).append("\"/>\n")
            sb.append("      </direction>\n")
        }

        for (event in events) {
            when (event) {
                is MeasureEvent.Forward -> {
                    sb.append("      <forward>\n")
                    sb.append("        <duration>").append(event.div).append("</duration>\n")
                    sb.append("      </forward>\n")
                }
                is MeasureEvent.Note -> appendNote(sb, event)
            }
        }
        sb.append("    </measure>\n")
    }

    private fun appendNote(sb: StringBuilder, event: MeasureEvent.Note) {
        val note = event.note
        val durationDiv = event.durationDiv
        val (step, alter, octave) = midiToPitchComponents(note.midiNumber)

        sb.append("      <note>\n")
        if (event.isChord) {
            sb.append("        <chord/>\n")
        }
        if (note.isGraceNote) {
            sb.append("        <grace/>\n")
        }
        sb.append("        <pitch>\n")
        sb.append("          <step>").append(step).append("</step>\n")
        if (alter != 0) {
            sb.append("          <alter>").append(alter).append("</alter>\n")
        }
        sb.append("          <octave>").append(octave).append("</octave>\n")
        sb.append("        </pitch>\n")
        sb.append("        <duration>").append(durationDiv).append("</duration>\n")
        val typeAndDots = divisionsToType(durationDiv)
        sb.append("        <type>").append(typeAndDots.type).append("</type>\n")
        repeat(typeAndDots.dots) {
            sb.append("        <dot/>\n")
        }
        when (note.accidental) {
            Accidental.FLAT -> sb.append("        <accidental>flat</accidental>\n")
            Accidental.NATURAL -> sb.append("        <accidental>natural</accidental>\n")
            Accidental.DOUBLE_SHARP -> sb.append("        <accidental>double-sharp</accidental>\n")
            Accidental.DOUBLE_FLAT -> sb.append("        <accidental>flat-flat</accidental>\n")
            else -> { /* SHARP 已由 alter 编码，NONE 不输出 */ }
        }
        sb.append("      </note>\n")
    }

    // ------------------------------------------------------------------
    //  事件模型
    // ------------------------------------------------------------------

    private sealed interface MeasureEvent {
        data class Forward(val div: Int) : MeasureEvent
        data class Note(
            val note: ScoreNote,
            val durationDiv: Int,
            val isChord: Boolean
        ) : MeasureEvent
    }

    // ------------------------------------------------------------------
    //  音乐理论工具
    // ------------------------------------------------------------------

    /**
     * 将 MIDI 音高转换为 MusicXML pitch 组件 (step, alter, octave)。
     * 黑键统一使用升号(alter=1)表示。
     * @return Triple(step 字母, alter 半音偏移, octave 八度号)。其中 C4 = MIDI 60 → ("C", 0, 4)。
     */
    internal fun midiToPitchComponents(midi: Int): Triple<String, Int, Int> {
        val octave = midi / 12 - 1
        val pc = midi % 12
        val (step, alter) = when (pc) {
            0 -> "C" to 0
            1 -> "C" to 1
            2 -> "D" to 0
            3 -> "D" to 1
            4 -> "E" to 0
            5 -> "F" to 0
            6 -> "F" to 1
            7 -> "G" to 0
            8 -> "G" to 1
            9 -> "A" to 0
            10 -> "A" to 1
            11 -> "B" to 0
            else -> "C" to 0
        }
        return Triple(step, alter, octave)
    }

    /** ms 时值 → division 数（基于四分音符时长 quarterMs）。 */
    internal fun msToDivisions(ms: Double, quarterMs: Double): Int {
        if (quarterMs <= 0) return divisions
        return (ms / quarterMs * divisions).toInt().coerceAtLeast(0)
    }

    /** divisions → (type, dots)。先检测附点（恰好为标准时值的 1.5 倍），再按区间归类。 */
    internal fun divisionsToType(div: Int): TypeAndDots {
        val quarter = divisions
        // 附点：div == quarter*k*3/2（整数除法精确匹配 quarter=4 时：附点四分=6, 附点二分=12, 附点全=24）
        for (k in listOf(4, 2, 1)) {
            if (div == quarter * k * 3 / 2) return TypeAndDots(typeForMultiple(k), dots = 1)
        }
        return when {
            div >= 4 * quarter -> TypeAndDots("whole", 0)
            div >= 2 * quarter -> TypeAndDots("half", 0)
            div >= quarter -> TypeAndDots("quarter", 0)
            div >= quarter / 2 -> TypeAndDots("eighth", 0)
            else -> TypeAndDots("16th", 0)
        }
    }

    private fun typeForMultiple(k: Int): String = when (k) {
        1 -> "quarter"
        2 -> "half"
        4 -> "whole"
        else -> "quarter"
    }

    private fun parseTimeSignature(sig: String): Pair<Int, Int> {
        val parts = sig.split("/")
        val beats = parts.getOrNull(0)?.toIntOrNull() ?: 4
        val beatType = parts.getOrNull(1)?.toIntOrNull() ?: 4
        return beats.coerceAtLeast(1) to beatType.coerceAtLeast(1)
    }

    private fun partId(index: Int): String = "P${index + 1}"

    private fun partNameFor(clef: ClefKind): String = when (clef) {
        ClefKind.TREBLE -> "Piano (Right)"
        ClefKind.BASS -> "Piano (Left)"
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private enum class ClefKind { TREBLE, BASS }

    internal data class TypeAndDots(val type: String, val dots: Int)

    private companion object {
        const val DEFAULT_DIVISIONS = 4
    }
}
