@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pianocompanion.ui.cadence

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.cadence.*
import com.pianocompanion.chord.ChordRoot

/**
 * 终止式参考库页面。
 *
 * 功能：
 * - 终止式类型选择（按分类：完全终止/变格终止/阻碍终止/半终止）
 * - 调性选择器（12 个根音）+ 调式切换（大调/和声小调）
 * - 罗马数字进行展示
 * - 可视化钢琴键盘（高亮当前和弦音符）
 * - 和弦步骤卡片（每个和弦的音名、罗马数字）
 * - 音频播放（终止式连续试听）
 * - 终止式说明（类型特点、使用场景）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceLibraryScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: CadenceLibraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CadenceLibraryViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val instance = state.currentInstance

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎼 终止式参考库", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 终止式标题卡片 ──
            instance?.let { CadenceHeader(it) }

            // ── 罗马数字进行 ──
            instance?.let { NumeralSequence(it) }

            // ── 播放控制 ──
            PlaybackControls(
                isPlaying = state.isPlaying,
                audioReady = state.audioReady,
                onPlay = { viewModel.playCadence() },
                onStop = { viewModel.stopPlayback() }
            )

            // ── 和弦步骤卡片 ──
            instance?.let { ChordStepCards(it) }

            // ── 调性选择器 ──
            SectionTitle("调性")
            KeySelector(
                selectedKey = state.selectedKey,
                onSelect = { viewModel.selectKey(it) }
            )

            // ── 调式选择器 ──
            SectionTitle("调式")
            ModeSelector(
                selectedMode = state.selectedMode,
                cadenceType = state.selectedCadence,
                onSelect = { viewModel.selectMode(it) }
            )

            // ── 终止式类型选择器 ──
            SectionTitle("终止式类型")
            CadenceTypeSelector(
                categories = state.categories,
                selectedType = state.selectedCadence,
                currentMode = state.selectedMode,
                onSelect = { viewModel.selectCadence(it) }
            )

            // ── 终止式说明 ──
            CadenceDescription(state.selectedCadence)

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  UI 子组件
// ════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CadenceHeader(instance: CadenceInstance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = instance.type.displayName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = instance.romanNumeralSummary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(instance.keyName, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(instance.type.abbreviation, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun NumeralSequence(instance: CadenceInstance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            instance.steps.forEachIndexed { index, step ->
                if (index > 0) {
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = step.romanNumeral,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = step.voicing.fullName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    audioReady: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FilledIconButton(
                onClick = { if (isPlaying) onStop() else onPlay() },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                enabled = audioReady
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "播放",
                    modifier = Modifier.size(28.dp)
                )
            }
            if (!audioReady) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "音频准备中…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChordStepCards(instance: CadenceInstance) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(instance.steps.size) { index ->
            val step = instance.steps[index]
            ChordStepCard(step, index + 1)
        }
    }
}

@Composable
private fun ChordStepCard(step: CadenceStep, position: Int) {
    Card(
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "和弦 $position",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = step.romanNumeral,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = step.voicing.fullName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(6.dp))
            // 音名列表
            step.voicing.noteNames.forEach { name ->
                Text(
                    text = name,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(6.dp))
            // 可视化键盘
            CadenceKeyboard(step.voicing.midiNotes.toSet())
        }
    }
}

@Composable
private fun CadenceKeyboard(highlightMidi: Set<Int>) {
    // 将 MIDI 音符转换为音级类集合用于高亮
    val highlightPcs = highlightMidi.map { it % 12 }.toSet()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(2.dp)
        ) {
            drawPianoKeyboard(highlightPcs)
        }
    }
}

/**
 * 绘制钢琴键盘（2 个八度），高亮指定音级类的白键和黑键。
 */
private fun DrawScope.drawPianoKeyboard(highlightPcs: Set<Int>) {
    val whiteKeyWidth = size.width / 14f // 2 个八度 = 14 个白键
    val whiteKeyHeight = size.height
    val blackKeyWidth = whiteKeyWidth * 0.6f
    val blackKeyHeight = whiteKeyHeight * 0.62f

    val whiteKeyPcs = listOf(0, 2, 4, 5, 7, 9, 11)

    // ── 第一遍：绘制白键 ──
    for (octave in 0..1) {
        for ((idx, pc) in whiteKeyPcs.withIndex()) {
            val whiteIndex = octave * 7 + idx
            val x = whiteIndex * whiteKeyWidth
            val isHighlighted = pc in highlightPcs

            drawRect(
                color = if (isHighlighted) Color(0xFF6750A4) else Color.White,
                topLeft = Offset(x + 1, 0f),
                size = Size(whiteKeyWidth - 2, whiteKeyHeight)
            )
            drawRect(
                color = Color(0xFFBDBDBD),
                topLeft = Offset(x + 1, 0f),
                size = Size(whiteKeyWidth - 2, whiteKeyHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
        }
    }

    // ── 第二遍：绘制黑键 ──
    val blackKeyPositions = listOf(
        0 to 1,  // C#
        1 to 3,  // D#
        3 to 6,  // F#
        4 to 8,  // G#
        5 to 10  // A#
    )

    for (octave in 0..1) {
        for ((whiteIdx, pc) in blackKeyPositions) {
            val globalWhiteIdx = octave * 7 + whiteIdx
            val x = (globalWhiteIdx + 1) * whiteKeyWidth - blackKeyWidth / 2f
            val isHighlighted = pc in highlightPcs

            drawRect(
                color = if (isHighlighted) Color(0xFFD0BCFF) else Color(0xFF424242),
                topLeft = Offset(x, 0f),
                size = Size(blackKeyWidth, blackKeyHeight)
            )
            if (isHighlighted) {
                drawRect(
                    color = Color(0xFF6750A4),
                    topLeft = Offset(x, 0f),
                    size = Size(blackKeyWidth, blackKeyHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
private fun KeySelector(
    selectedKey: ChordRoot,
    onSelect: (ChordRoot) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(ChordRoot.entries.size) { index ->
            val root = ChordRoot.entries[index]
            FilterChip(
                selected = root == selectedKey,
                onClick = { onSelect(root) },
                label = { Text(root.displayName, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: CadenceMode,
    cadenceType: CadenceType,
    onSelect: (CadenceMode) -> Unit
) {
    val modes = CadenceEngine.supportedModes(cadenceType)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { mode ->
            FilterChip(
                selected = mode == selectedMode,
                onClick = { onSelect(mode) },
                label = { Text(mode.displayName, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun CadenceTypeSelector(
    categories: Map<CadenceCategory, List<CadenceType>>,
    selectedType: CadenceType,
    currentMode: CadenceMode,
    onSelect: (CadenceType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.forEach { (category, cadences) ->
            Text(
                text = category.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cadences.forEach { cadence ->
                    val isSupported = currentMode in CadenceEngine.supportedModes(cadence)
                    FilterChip(
                        selected = cadence == selectedType,
                        onClick = { if (isSupported) onSelect(cadence) },
                        enabled = isSupported,
                        label = {
                            Column {
                                Text(cadence.displayName, fontSize = 11.sp)
                                Text(
                                    "${cadence.abbreviation}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (cadence == selectedType)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CadenceDescription(cadenceType: CadenceType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "关于此终止式",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = cadenceType.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

// FlowRow wrapper
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}
