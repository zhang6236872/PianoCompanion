package com.pianocompanion.ui.metronome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianocompanion.audio.Metronome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen() {
    val metronome = remember { Metronome() }
    var bpm by remember { mutableStateOf(120) }
    var beatsPerMeasure by remember { mutableStateOf(4) }
    var currentBeat by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(metronome) {
        metronome.onBeat = { beat ->
            currentBeat = beat
        }
    }

    // Pulse animation for current beat
    val pulseScale by animateFloatAsState(
        targetValue = if (currentBeat >= 0 && isPlaying) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⏱️ 节拍器", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // === Beat indicator dots ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in 0 until beatsPerMeasure) {
                    val isActive = i == currentBeat && isPlaying
                    val color = if (i == 0) Color(0xFFEF5350) else Color(0xFF42A5F5)
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 28.dp else 20.dp)
                            .clip(CircleShape)
                            .background(if (isActive) color else color.copy(alpha = 0.2f))
                            .animateContentSize()
                    )
                }
            }

            // === BPM display ===
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = bpm.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("BPM", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }

            // === Tempo markings ===
            Text(
                text = getTempoMarking(bpm),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )

            // === BPM slider ===
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("40", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("240", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = {
                        bpm = it.toInt()
                        metronome.setBpm(bpm)
                    },
                    valueRange = 40f..240f,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(60 to "慢板", 90 to "行板", 120 to "中板", 144 to "快板", 180 to "急板").forEach { (presetBpm, label) ->
                        FilterChip(
                            selected = bpm == presetBpm,
                            onClick = {
                                bpm = presetBpm
                                metronome.setBpm(bpm)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // === Time signature selector ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("拍号:", style = MaterialTheme.typography.bodyMedium)
                listOf(2, 3, 4, 6).forEach { beats ->
                    FilterChip(
                        selected = beatsPerMeasure == beats,
                        onClick = {
                            beatsPerMeasure = beats
                            metronome.setBeatsPerMeasure(beats)
                        },
                        label = { Text("\$beats/4") }
                    )
                }
            }

            // === Play/Stop button ===
            Button(
                onClick = {
                    if (isPlaying) {
                        metronome.stop()
                        isPlaying = false
                        currentBeat = -1
                    } else {
                        metronome.setBpm(bpm)
                        metronome.setBeatsPerMeasure(beatsPerMeasure)
                        metronome.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) Color(0xFFEF5350) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "开始",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

private fun getTempoMarking(bpm: Int): String {
    return when {
        bpm < 60 -> " Grave (庄板)"
        bpm < 72 -> " Largo (广板)"
        bpm < 76 -> " Lento (慢板)"
        bpm < 90 -> " Andante (行板)"
        bpm < 110 -> " Moderato (中板)"
        bpm < 132 -> " Allegro (快板)"
        bpm < 160 -> " Vivace (活泼)"
        bpm < 200 -> " Presto (急板)"
        else -> " Prestissimo (最急板)"
    }
}
