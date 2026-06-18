package com.pianocompanion.wear

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lightweight metronome optimized for Wear OS round displays.
 * Large tap targets, vibration on each beat, minimal UI.
 * Uses standard Material3 (no Wear-specific dependency needed).
 */
class WearMetronomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearMetronomeScreen()
        }
    }
}

@Composable
fun WearMetronomeScreen() {
    var isRunning by remember { mutableStateOf(false) }
    var bpm by remember { mutableStateOf(120) }
    var currentBeat by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var metronomeJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Beat indicator circle
            val beatColor = when {
                currentBeat == 0 && isRunning -> Color(0xFF6750A4)
                isRunning -> Color.White.copy(alpha = 0.15f)
                else -> Color.White.copy(alpha = 0.08f)
            }

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(beatColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRunning) "${currentBeat + 1}" else "♪",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning && currentBeat == 0) Color.White else Color(0xFFB0B0FF),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(10.dp))

            // BPM display
            Text(
                text = "$bpm BPM",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Spacer(Modifier.height(12.dp))

            // BPM controls row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decrease BPM
                FilledIconButton(
                    onClick = { bpm = (bpm - 5).coerceIn(40, 240) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape
                ) {
                    Text("−", fontSize = 20.sp)
                }

                // Start/Stop
                FilledIconButton(
                    onClick = {
                        isRunning = !isRunning
                        if (isRunning) {
                            metronomeJob = scope.launch(Dispatchers.Default) {
                                while (true) {
                                    for (beat in 0 until 4) {
                                        currentBeat = beat
                                        if (beat == 0) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            vibrator.vibrate(VibrationEffect.createOneShot(20, 80))
                                        }
                                        delay((60000.0 / bpm).roundToInt().toLong())
                                    }
                                }
                            }
                        } else {
                            metronomeJob?.cancel()
                            currentBeat = 0
                        }
                    },
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRunning) Color(0xFFE53935) else Color(0xFF6750A4)
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Stop" else "Start",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Increase BPM
                FilledIconButton(
                    onClick = { bpm = (bpm + 5).coerceIn(40, 240) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape
                ) {
                    Text("+", fontSize = 20.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Piano Companion",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
