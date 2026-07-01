@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pianocompanion.ui.scale

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.scale.*
import kotlin.math.absoluteValue

/**
 * 音阶词典页面。
 *
 * 功能：
 * - 根音选择器（12 个音级类 FilterChip）
 * - 音阶类型选择器（按分类分组）
 * - 播放方向选择器（上行/下行/上下行）
 * - 可视化钢琴键盘（高亮音阶音符位置）
 * - 音阶信息卡（音名、级数、音程步进、指法、关系调）
 * - 音频播放
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleLibraryScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: ScaleLibraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ScaleLibraryViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val scale = state.currentScale

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎼 音阶词典", fontWeight = FontWeight.Bold) })
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

            // ── 当前音阶大标题 ──
            scale?.let { CurrentScaleHeader(it, state.relativeKeyName) }

            // ── 可视化钢琴键盘 ──
            scale?.let {
                ScaleKeyboard(
                    scale = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 播放控制 ──
            PlaybackControls(
                isPlaying = state.isPlaying,
                direction = state.playDirection,
                audioReady = state.audioReady,
                onPlay = { viewModel.playScale() },
                onStop = { viewModel.stopPlayback() },
                onDirectionChange = { viewModel.selectDirection(it) }
            )

            // ── 根音选择器 ──
            SectionTitle("根音")
            RootNoteSelector(
                selectedRoot = state.selectedRoot,
                onSelect = { viewModel.selectRoot(it) }
            )

            // ── 音阶类型选择器 ──
            SectionTitle("音阶类型")
            ScaleTypeSelector(
                categories = state.categories,
                selectedType = state.selectedType,
                onSelect = { viewModel.selectType(it) }
            )

            // ── 音阶信息卡 ──
            scale?.let {
                ScaleInfoCard(
                    scale = it,
                    degreeNames = state.degreeNames,
                    fingering = state.fingering,
                    intervalSteps = state.intervalSteps
                )
            }

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
private fun CurrentScaleHeader(scale: ScaleInfo, relativeKey: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                text = scale.fullName,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = scale.type.symbol,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            if (relativeKey.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = relativeKey,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    direction: PlayDirection,
    audioReady: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDirectionChange: (PlayDirection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
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

        PlayDirection.entries.forEach { dir ->
            FilterChip(
                selected = direction == dir,
                onClick = { onDirectionChange(dir) },
                label = { Text(dir.displayName, fontSize = 13.sp) }
            )
        }

        if (!audioReady) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun RootNoteSelector(
    selectedRoot: ScaleRoot,
    onSelect: (ScaleRoot) -> Unit
) {
    val roots = ScaleRoot.entries
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        roots.forEach { root ->
            FilterChip(
                selected = root == selectedRoot,
                onClick = { onSelect(root) },
                label = {
                    Text(
                        root.name(ScaleEngine.preferFlatsKey(root)),
                        fontSize = 13.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun ScaleTypeSelector(
    categories: Map<ScaleCategory, List<ScaleType>>,
    selectedType: ScaleType,
    onSelect: (ScaleType) -> Unit
) {
    categories.forEach { (category, types) ->
        Text(
            text = category.displayName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            types.forEach { type ->
                FilterChip(
                    selected = type == selectedType,
                    onClick = { onSelect(type) },
                    label = {
                        Text(
                            type.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ScaleInfoCard(
    scale: ScaleInfo,
    degreeNames: List<String>,
    fingering: List<Int>,
    intervalSteps: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 音名
            InfoRow(
                icon = Icons.Filled.MusicNote,
                label = "音名",
                value = scale.noteNames.joinToString("  ")
            )
            HorizontalDivider()
            // 级数
            InfoRow(
                icon = Icons.Filled.StackedBarChart,
                label = "级数",
                value = degreeNames.joinToString("  ")
            )
            // 音程步进 (W=全音 H=半音)
            HorizontalDivider()
            val stepLabels = intervalSteps.map { if (it == 1) "H" else "W" }
            InfoRow(
                icon = Icons.Filled.Stairs,
                label = "步进",
                value = stepLabels.joinToString("-")
            )
            // 指法
            if (fingering.isNotEmpty()) {
                HorizontalDivider()
                InfoRow(
                    icon = Icons.Filled.PanTool,
                    label = "指法",
                    value = fingering.joinToString("-")
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ════════════════════════════════════════════════════════════
//  可视化钢琴键盘
// ════════════════════════════════════════════════════════════

@Composable
private fun ScaleKeyboard(
    scale: ScaleInfo,
    modifier: Modifier = Modifier
) {
    val highlightPcs = remember(scale) {
        scale.ascendingMidiNotes.map { it % 12 }.toSet()
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(8.dp)
        ) {
            drawPianoKeyboard(highlightPcs)
        }
    }
}

private fun DrawScope.drawPianoKeyboard(highlightPcs: Set<Int>) {
    val whiteKeyWidth = size.width / 14f
    val whiteKeyHeight = size.height
    val blackKeyWidth = whiteKeyWidth * 0.6f
    val blackKeyHeight = whiteKeyHeight * 0.62f

    val whiteKeyPcs = listOf(0, 2, 4, 5, 7, 9, 11)

    // 白键
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
            // C 音标记
            if (pc == 0) {
                drawCircle(
                    color = if (isHighlighted) Color.White.copy(alpha = 0.3f) else Color(0xFF757575).copy(alpha = 0.3f),
                    radius = 3f,
                    center = Offset(x + whiteKeyWidth * 0.5f, whiteKeyHeight - 12f)
                )
            }
        }
    }

    // 黑键
    val blackKeyPositions = listOf(
        0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10
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

// FlowRow
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
