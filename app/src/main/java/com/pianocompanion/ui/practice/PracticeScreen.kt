package com.pianocompanion.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel = viewModel(factory = PracticeViewModelFactory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startPractice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎹 练习模式", fontWeight = FontWeight.Bold) },
                actions = {
                    // Accuracy badge
                    if (uiState.isPracticing) {
                        Surface(
                            color = when {
                                uiState.accuracy >= 0.8f -> Color(0xFF4CAF50)
                                uiState.accuracy >= 0.5f -> Color(0xFFFFA726)
                                else -> Color(0xFFEF5350)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "\${(uiState.accuracy * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Score info card ===
            uiState.score?.let { score ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = score.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = score.composer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // === Current note display ===
            AnimatedContent(
                targetState = uiState.lastFeedback,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
                },
                label = "feedback"
            ) { feedback ->
                FeedbackCard(
                    feedback = feedback,
                    expectedNote = uiState.lastExpectedNote,
                    detectedNote = uiState.lastDetectedNote
                )
            }

            // === Progress bar ===
            uiState.score?.let { score ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("第 ${uiState.currentPage + 1} 页", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "音符 ${uiState.currentNoteIndex + 1}/${score.notes.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (score.notes.isNotEmpty())
                                (uiState.currentNoteIndex + 1f) / score.notes.size
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // === Piano Roll visualization ===
            if (uiState.isPracticing) {
                PianoRollView(
                    scoreNotes = uiState.score?.notes ?: emptyList(),
                    currentPosition = uiState.currentNoteIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // === Stats row ===
            if (uiState.isPracticing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip("✅", uiState.correctCount, Color(0xFF4CAF50))
                    StatChip("❌", uiState.wrongCount, Color(0xFFEF5350))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // === Control buttons ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!uiState.isPracticing) {
                    Button(
                        onClick = {
                            val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                viewModel.startPractice()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "开始", modifier = Modifier.size(36.dp))
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopPractice() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350)
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止", modifier = Modifier.size(36.dp))
                    }
                }

                // Manual page turn buttons
                IconButton(onClick = { /* manual prev page */ }) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = "上一页")
                }
                Text("手动翻页", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { /* manual next page */ }) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = "下一页")
                }
            }

            // Error message
            uiState.errorMessage?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    feedback: PracticeViewModel.FeedbackType,
    expectedNote: String,
    detectedNote: String
) {
    val (bgColor, icon, message) = when (feedback) {
        PracticeViewModel.FeedbackType.CORRECT ->
            Triple(Color(0xFF4CAF50), "✅", "正确！ \$expectedNote")
        PracticeViewModel.FeedbackType.WRONG_PITCH ->
            Triple(Color(0xFFEF5350), "❌", "错音！ 弹了 \$detectedNote 应弹 \$expectedNote")
        PracticeViewModel.FeedbackType.EXTRA_NOTE ->
            Triple(Color(0xFFFFA726), "➕", "多弹了 \$detectedNote")
        PracticeViewModel.FeedbackType.MISSING_NOTE ->
            Triple(Color(0xFFFFA726), "⚠️", "漏弹 \$expectedNote")
        PracticeViewModel.FeedbackType.NONE ->
            Triple(Color(0xFF42A5F5), "🎹", "等待弹奏...")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(2.dp, bgColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = icon, fontSize = 32.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = bgColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatChip(emoji: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// === Piano Roll mini visualization ===
@Composable
private fun PianoRollView(
    scoreNotes: List<com.pianocompanion.data.model.ScoreNote>,
    currentPosition: Int,
    modifier: Modifier = Modifier
) {
    if (scoreNotes.isEmpty()) return

    val midiNotes = scoreNotes.map { it.midiNumber }
    val minMidi = (midiNotes.min() ?: 48) - 2
    val maxMidi = (midiNotes.max() ?: 72) + 2
    val range = maxOf(1, maxMidi - minMidi)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        val w = size.width
        val h = size.height
        val noteWidth = w / maxOf(1, scoreNotes.size)
        val visibleRange = minOf(scoreNotes.size, 16)
        val startPos = maxOf(0, currentPosition - 4)

        // Draw grid lines
        for (octave in minMidi..maxMidi step 12) {
            val y = h - ((octave - minMidi).toFloat() / range) * h
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Draw notes
        for (i in startPos until minOf(startPos + visibleRange, scoreNotes.size)) {
            val note = scoreNotes[i]
            val x = (i - startPos) * noteWidth
            val noteHeight = h / range
            val y = h - ((note.midiNumber - minMidi).toFloat() / range) * h - noteHeight

            val isCurrent = i == currentPosition
            val isPast = i < currentPosition
            val color = when {
                isCurrent -> Color(0xFF4CAF50)
                isPast -> Color(0xFF666666)
                else -> Color(0xFF90CAF9)
            }

            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x + 2, y),
                size = androidx.compose.ui.geometry.Size(noteWidth - 4, noteHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
        }
    }
}

// Canvas is part of foundation

