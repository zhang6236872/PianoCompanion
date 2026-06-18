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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.ui.score.ScoreRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel = viewModel(factory = PracticeViewModelFactory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showScorePicker by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startPractice()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎹 练习模式", fontWeight = FontWeight.Bold) },
                actions = {
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
                                text = "${(uiState.accuracy * 100).toInt()}%",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Score selector ===
            if (uiState.score == null) {
                // No score selected — show picker prompt
                Spacer(modifier = Modifier.weight(1f))
                Text("🎵", fontSize = 64.sp)
                Text("选择一首乐谱开始练习", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                viewModel.availableScores.forEach { score ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setScore(score)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(score.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${score.composer} · ${score.notes.size}个音",
                                     fontSize = 12.sp, color = Color.Gray)
                            }
                            Icon(Icons.Filled.PlayArrow, contentDescription = "选择",
                                 tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                return@Column
            }

            // === Score info ===
            uiState.score?.let { score ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(score.title, fontWeight = FontWeight.Bold,
                                 style = MaterialTheme.typography.titleMedium)
                            Text(score.composer, fontSize = 12.sp,
                                 color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = { showScorePicker = true }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "换一首")
                        }
                    }
                }
            }

            // === Staff notation rendering ===
            uiState.score?.let { score ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    ScoreRenderer(
                        notes = score.notes,
                        currentPosition = uiState.currentNoteIndex,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // === Feedback card ===
            AnimatedContent(
                targetState = uiState.lastFeedback,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "feedback"
            ) { feedback ->
                FeedbackCard(feedback, uiState.lastExpectedNote, uiState.lastDetectedNote)
            }

            // === Progress ===
            uiState.score?.let { score ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("进度", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${uiState.currentNoteIndex + 1}/${score.notes.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
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
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // === Stats ===
            if (uiState.isPracticing || uiState.correctCount + uiState.wrongCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip("✅", uiState.correctCount, Color(0xFF4CAF50))
                    StatChip("❌", uiState.wrongCount, Color(0xFFEF5350))
                }
            }

            // Session saved notification
            if (uiState.sessionSaved) {
                Text("💾 练习记录已保存", color = Color(0xFF4CAF50), fontSize = 13.sp,
                     fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.weight(1f))

            // === Controls ===
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
                            if (hasPermission) viewModel.startPractice()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.PlayArrow, "开始", Modifier.size(36.dp))
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopPractice() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                    ) {
                        Icon(Icons.Filled.Stop, "停止", Modifier.size(36.dp))
                    }
                }
            }

            uiState.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }

    // Score picker dialog
    if (showScorePicker) {
        AlertDialog(
            onDismissRequest = { showScorePicker = false },
            title = { Text("选择乐谱") },
            text = {
                Column {
                    viewModel.availableScores.forEach { score ->
                        Text(
                            text = "${score.title} (${score.notes.size}个音)",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setScore(score)
                                    showScorePicker = false
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScorePicker = false }) { Text("取消") }
            }
        )
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
            Triple(Color(0xFF4CAF50), "✅", "正确！$expectedNote")
        PracticeViewModel.FeedbackType.WRONG_PITCH ->
            Triple(Color(0xFFEF5350), "❌", "错音！弹了$detectedNote 应弹$expectedNote")
        PracticeViewModel.FeedbackType.EXTRA_NOTE ->
            Triple(Color(0xFFFFA726), "➕", "多弹了$detectedNote")
        PracticeViewModel.FeedbackType.MISSING_NOTE ->
            Triple(Color(0xFFFFA726), "⚠️", "漏弹$expectedNote")
        PracticeViewModel.FeedbackType.NONE ->
            Triple(Color(0xFF42A5F5), "🎹", "等待弹奏...")
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, bgColor.copy(alpha = 0.5f))
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(icon, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = bgColor)
            }
        }
    }
}

@Composable
private fun StatChip(emoji: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
