@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pianocompanion.ui.chord

import android.app.Application
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.chord.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 和弦词典页面。
 *
 * 功能：
 * - 根音选择器（12 个音级类 FilterChip）
 * - 和弦类型选择器（按分类分组的 FlowRow/LazyRow）
 * - 转位选择器（根据和弦类型动态显示可用转位）
 * - 可视化钢琴键盘（高亮和弦音符位置）
 * - 和弦信息卡（音名、音程、指法）
 * - 音频播放（柱式/琶音切换）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordDictionaryScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: ChordDictionaryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChordDictionaryViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val voicing = state.currentVoicing

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎹 和弦词典", fontWeight = FontWeight.Bold) })
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

            // ── 当前和弦大标题 ──
            voicing?.let { CurrentChordHeader(it) }

            // ── 可视化钢琴键盘 ──
            voicing?.let {
                ChordKeyboard(
                    voicing = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 播放控制 ──
            PlaybackControls(
                isPlaying = state.isPlaying,
                isArpeggio = state.isArpeggioMode,
                audioReady = state.audioReady,
                onPlay = { viewModel.playChord() },
                onStop = { viewModel.stopPlayback() },
                onToggleArpeggio = { viewModel.toggleArpeggioMode() }
            )

            // ── 根音选择器 ──
            SectionTitle("根音")
            RootNoteSelector(
                selectedRoot = state.selectedRoot,
                onSelect = { viewModel.selectRoot(it) }
            )

            // ── 和弦类型选择器 ──
            SectionTitle("和弦类型")
            ChordTypeSelector(
                categories = state.categories,
                selectedType = state.selectedType,
                onSelect = { viewModel.selectType(it) }
            )

            // ── 转位选择器 ──
            if (state.availableInversions.size > 1) {
                SectionTitle("转位")
                InversionSelector(
                    inversions = state.availableInversions,
                    selected = state.selectedInversion,
                    onSelect = { viewModel.selectInversion(it) }
                )
            }

            // ── 和弦信息卡 ──
            voicing?.let {
                ChordInfoCard(
                    voicing = it,
                    intervalNames = state.intervalNames,
                    fingering = state.fingering
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
private fun CurrentChordHeader(voicing: ChordVoicing) {
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
                text = voicing.fullName,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (voicing.inversion != ChordInversion.ROOT_POSITION) {
                Text(
                    text = voicing.inversion.displayName,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = voicing.type.displayName,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isArpeggio: Boolean,
    audioReady: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onToggleArpeggio: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 播放/停止按钮
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

        // 柱式/琶音切换
        FilterChip(
            selected = isArpeggio,
            onClick = onToggleArpeggio,
            label = { Text(if (isArpeggio) "琶音" else "柱式") },
            leadingIcon = {
                Icon(
                    if (isArpeggio) Icons.Filled.Waves else Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

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
    selectedRoot: ChordRoot,
    onSelect: (ChordRoot) -> Unit
) {
    val roots = ChordRoot.entries
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        roots.forEach { root ->
            FilterChip(
                selected = root == selectedRoot,
                onClick = { onSelect(root) },
                label = { Text(root.displayName, fontSize = 13.sp) }
            )
        }
    }
}

@Composable
private fun ChordTypeSelector(
    categories: Map<ChordCategory, List<ChordType>>,
    selectedType: ChordType,
    onSelect: (ChordType) -> Unit
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (type.symbol.isEmpty()) type.rootSymbolLabel() else type.symbol,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                )
            }
        }
    }
}

// Extension: 大三和弦的符号显示
private fun ChordType.rootSymbolLabel(): String = "M"

@Composable
private fun InversionSelector(
    inversions: List<ChordInversion>,
    selected: ChordInversion,
    onSelect: (ChordInversion) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        inversions.forEach { inv ->
            FilterChip(
                selected = inv == selected,
                onClick = { onSelect(inv) },
                label = { Text(inv.displayName, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun ChordInfoCard(
    voicing: ChordVoicing,
    intervalNames: List<String>,
    fingering: List<Int>
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
                value = voicing.noteNames.joinToString("  ")
            )
            HorizontalDivider()
            // 音程
            InfoRow(
                icon = Icons.Filled.StackedBarChart,
                label = "音程",
                value = intervalNames.joinToString("  ")
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ════════════════════════════════════════════════════════════
//  可视化钢琴键盘
// ════════════════════════════════════════════════════════════

private val WHITE_NOTE_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11) // C D E F G A B
private val BLACK_NOTE_OFFSETS = mapOf(0 to 1, 1 to 1, 3 to 1, 4 to 1, 5 to 1) // C# D# F# G# A#

/**
 * 可视化钢琴键盘组件——高亮显示和弦中的音符。
 *
 * 渲染两个八度的钢琴键盘，和弦中包含的音键用主色高亮。
 */
@Composable
private fun ChordKeyboard(
    voicing: ChordVoicing,
    modifier: Modifier = Modifier
) {
    val highlightPcs = remember(voicing) {
        voicing.midiNotes.map { it % 12 }.toSet()
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

/**
 * 绘制钢琴键盘（2 个八度），高亮指定音级类的白键和黑键。
 */
private fun DrawScope.drawPianoKeyboard(highlightPcs: Set<Int>) {
    val whiteKeyWidth = size.width / 14f // 2 个八度 = 14 个白键
    val whiteKeyHeight = size.height
    val blackKeyWidth = whiteKeyWidth * 0.6f
    val blackKeyHeight = whiteKeyHeight * 0.62f

    // 音名标签的白键音级（C D E F G A B）
    val whiteKeyPcs = listOf(0, 2, 4, 5, 7, 9, 11)

    // ── 第一遍：绘制白键 ──
    for (octave in 0..1) {
        for ((idx, pc) in whiteKeyPcs.withIndex()) {
            val whiteIndex = octave * 7 + idx
            val x = whiteIndex * whiteKeyWidth
            val isHighlighted = pc in highlightPcs
            val isC = pc == 0

            // 白键底色
            drawRect(
                color = if (isHighlighted) Color(0xFF6750A4) else Color.White,
                topLeft = Offset(x + 1, 0f),
                size = Size(whiteKeyWidth - 2, whiteKeyHeight),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            // 白键边框
            drawRect(
                color = Color(0xFFBDBDBD),
                topLeft = Offset(x + 1, 0f),
                size = Size(whiteKeyWidth - 2, whiteKeyHeight),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
            )
            // C 音标注
            if (isC) {
                drawText(
                    text = if (octave == 0) "C4" else "C5",
                    x = x + whiteKeyWidth * 0.3f,
                    y = whiteKeyHeight - 20f,
                    color = if (isHighlighted) Color.White else Color(0xFF757575)
                )
            }
        }
    }

    // ── 第二遍：绘制黑键（覆盖在白键上）──
    // 每个八度中黑键出现在白键之间：
    // C# 在 C-D 之间, D# 在 D-E 之间, F# 在 F-G 之间, G# 在 G-A 之间, A# 在 A-B 之间
    val blackKeyPositions = listOf(
        // (白键索引, 音级类)
        0 to 1,  // C# (在 C 后)
        1 to 3,  // D# (在 D 后)
        3 to 6,  // F# (在 F 后)
        4 to 8,  // G# (在 G 后)
        5 to 10  // A# (在 A 后)
    )

    for (octave in 0..1) {
        for ((whiteIdx, pc) in blackKeyPositions) {
            val globalWhiteIdx = octave * 7 + whiteIdx
            val x = (globalWhiteIdx + 1) * whiteKeyWidth - blackKeyWidth / 2f
            val isHighlighted = pc in highlightPcs

            drawRect(
                color = if (isHighlighted) Color(0xFFD0BCFF) else Color(0xFF424242),
                topLeft = Offset(x, 0f),
                size = Size(blackKeyWidth, blackKeyHeight),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            // 黑键高亮边框
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

/**
 * 在 DrawScope 中绘制简单文字。
 * 由于 DrawScope 不直接支持 drawText，这里用简化的矩形标记替代。
 */
private fun DrawScope.drawText(text: String, x: Float, y: Float, color: Color) {
    // 简化：绘制一个小圆点标记 C 音位置
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = 3f,
        center = Offset(x + 8f, y + 8f)
    )
}

// FlowRow 需要 OptIn
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
