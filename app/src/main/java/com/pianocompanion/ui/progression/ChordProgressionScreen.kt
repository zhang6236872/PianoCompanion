@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pianocompanion.ui.progression

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
import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.progression.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * 和弦进行词典页面。
 *
 * 功能：
 * - 进行模板选择器（按风格分组：流行/爵士/古典/蓝调/民歌/摇滚）
 * - 调性选择器（12 个根音）
 * - 罗马数字序列展示
 * - 可视化钢琴键盘（高亮当前和弦音符）
 * - 和弦序列卡片（每个和弦的音名、罗马数字）
 * - 音频播放（完整进行连续试听）
 * - 进行说明（风格背景、使用场景）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordProgressionScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: ProgressionLibraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProgressionLibraryViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val instance = state.currentInstance

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎶 和弦进行词典", fontWeight = FontWeight.Bold) })
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

            // ── 进行标题卡片 ──
            instance?.let { ProgressionHeader(it) }

            // ── 罗马数字序列 ──
            instance?.let { NumeralSequence(it) }

            // ── 和弦序列 ──
            instance?.let { ChordSequenceCards(it) }

            // ── 播放控制 ──
            PlaybackControls(
                isPlaying = state.isPlaying,
                audioReady = state.audioReady,
                durationMs = state.audioDurationMs,
                onPlay = { viewModel.playProgression() },
                onStop = { viewModel.stopPlayback() }
            )

            // ── 调性选择器 ──
            SectionTitle("调性")
            KeySelector(
                selectedKey = state.selectedKey,
                onSelect = { viewModel.selectKey(it) }
            )

            // ── 进行模板选择器 ──
            SectionTitle("进行模板")
            ProgressionTemplateSelector(
                templatesByGenre = state.templatesByGenre,
                selectedId = state.selectedTemplate?.id,
                onSelect = { viewModel.selectTemplate(it) }
            )

            // ── 进行说明 ──
            state.selectedTemplate?.let { ProgressionDescription(it) }

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
private fun ProgressionHeader(instance: ProgressionInstance) {
    val template = instance.template
    val keyName = if (instance.preferFlats) instance.key.flatName else instance.key.sharpName
    val modeLabel = if (template.mode == ProgressionMode.MINOR) "小调" else "大调"

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
                text = template.displayName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = template.name,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("$keyName$modeLabel", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(template.genre.displayName, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun NumeralSequence(instance: ProgressionInstance) {
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
            instance.chords.forEachIndexed { index, chord ->
                if (index > 0) {
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = chord.romanNumeral.numeral,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = chord.voicing.fullName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordSequenceCards(instance: ProgressionInstance) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(instance.chords.size) { index ->
            val chord = instance.chords[index]
            ChordCard(chord)
        }
    }
}

@Composable
private fun ChordCard(chord: ProgressionChord) {
    Card(
        modifier = Modifier.width(140.dp),
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
                text = chord.romanNumeral.numeral,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = chord.voicing.fullName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "小节 ${chord.measureIndex + 1}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(6.dp))
            // 音名列表
            chord.voicing.noteNames.take(4).forEach { name ->
                Text(
                    text = name,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    audioReady: Boolean,
    durationMs: Long,
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
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "播放",
                    modifier = Modifier.size(28.dp)
                )
            }
            if (durationMs > 0) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatDuration(durationMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${if (seconds < 10) "0" else ""}$seconds"
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
private fun ProgressionTemplateSelector(
    templatesByGenre: Map<ProgressionGenre, List<ProgressionTemplate>>,
    selectedId: String?,
    onSelect: (ProgressionTemplate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        templatesByGenre.forEach { (genre, templates) ->
            Text(
                text = genre.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                templates.forEach { template ->
                    FilterChip(
                        selected = template.id == selectedId,
                        onClick = { onSelect(template) },
                        label = {
                            Column {
                                Text(template.displayName, fontSize = 11.sp)
                                Text(
                                    template.name,
                                    fontSize = 9.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = if (template.id == selectedId)
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
private fun ProgressionDescription(template: ProgressionTemplate) {
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
                    text = "关于此进行",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = template.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}
