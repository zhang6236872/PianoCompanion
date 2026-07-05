package com.pianocompanion.ui.metronome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import com.pianocompanion.audio.Subdivision
import com.pianocompanion.audio.ClickPatternGenerator
import com.pianocompanion.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: MetronomeViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MetronomeViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("⏱️ 节拍器", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // === Beat indicator dots ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(uiState.beatsPerMeasure) { index ->
                    val isActive = uiState.isPlaying && index == uiState.currentBeat
                    val isAccent = index == 0
                    val targetColor = if (isAccent) Color(0xFFEF5350) else Color(0xFF6750A4)
                    val color by animateColorAsState(
                        targetValue = if (isActive) targetColor else targetColor.copy(alpha = 0.2f),
                        animationSpec = tween(100),
                        label = "beat_$index"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isActive) 1.3f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "beat_scale_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size((if (isAccent) 36.dp else 30.dp) * scale)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // === BPM display ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${uiState.bpm}",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text("BPM", fontSize = 16.sp,
                         color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tempoName(uiState.bpm), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                         color = MaterialTheme.colorScheme.primary)
                }
            }

            // === BPM slider ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { viewModel.decreaseBpm() }) {
                    Icon(Icons.Filled.Remove, "减速")
                }
                Slider(
                    value = uiState.bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.toInt()) },
                    valueRange = 40f..240f,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.increaseBpm() }) {
                    Icon(Icons.Filled.Add, "加速")
                }
            }

            // === Time signature selector ===
            SectionHeader(title = "拍号", icon = Icons.Filled.MusicNote)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 3, 4, 6).forEach { beats ->
                    FilterChip(
                        selected = uiState.beatsPerMeasure == beats,
                        onClick = { viewModel.setBeatsPerMeasure(beats) },
                        label = { Text("$beats/4") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // === Subdivision selector ===
            SectionHeader(title = "细分模式", icon = Icons.Filled.GraphicEq)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Subdivision.entries.take(3).forEach { sub ->
                        SubdivisionChip(
                            subdivision = sub,
                            isSelected = uiState.subdivision == sub,
                            onClick = { viewModel.setSubdivision(sub) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Subdivision.entries.drop(3).forEach { sub ->
                        SubdivisionChip(
                            subdivision = sub,
                            isSelected = uiState.subdivision == sub,
                            onClick = { viewModel.setSubdivision(sub) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // 子拍点间隔提示
                val subMs = ClickPatternGenerator.subClickIntervalMs(uiState.bpm, uiState.subdivision)
                Text(
                    text = "每次点击间隔 ${subMs}ms · 每小节 ${Subdivision.totalClicks(uiState.beatsPerMeasure, uiState.subdivision)} 次点击",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // === Tempo presets ===
            SectionHeader(title = "速度预设", icon = Icons.Filled.Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TempoPreset("慢板", 60, viewModel, uiState.bpm)
                TempoPreset("行板", 80, viewModel, uiState.bpm)
                TempoPreset("中板", 100, viewModel, uiState.bpm)
                TempoPreset("快板", 140, viewModel, uiState.bpm)
                TempoPreset("急板", 180, viewModel, uiState.bpm)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // === Play/Stop button ===
            Box(
                modifier = Modifier.padding(bottom = 16.dp).shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FilledIconButton(
                    onClick = {
                        if (uiState.isPlaying) viewModel.stop() else viewModel.start()
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isPlaying)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        if (uiState.isPlaying) "停止" else "开始",
                        Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TempoPreset(
    name: String,
    bpm: Int,
    viewModel: MetronomeViewModel,
    currentBpm: Int
) {
    val selected = currentBpm == bpm
    FilterChip(
        selected = selected,
        onClick = { viewModel.setBpm(bpm) },
        label = { Text(name, fontSize = 11.sp) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun SubdivisionChip(
    subdivision: Subdivision,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(subdivision.displayName, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(subdivision.symbol, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = modifier
    )
}

private fun tempoName(bpm: Int): String = when {
    bpm < 60 -> "极慢板 (Largo)"
    bpm < 76 -> "慢板 (Adagio)"
    bpm < 108 -> "行板 (Andante)"
    bpm < 120 -> "中板 (Moderato)"
    bpm < 156 -> "快板 (Allegro)"
    bpm < 200 -> "很快板 (Vivace)"
    else -> "急板 (Presto)"
}
