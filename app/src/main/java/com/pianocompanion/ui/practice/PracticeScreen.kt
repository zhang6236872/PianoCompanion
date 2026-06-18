package com.pianocompanion.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.ui.components.AccuracyRing
import com.pianocompanion.ui.components.InfoChip
import com.pianocompanion.ui.components.PulseIndicator
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎹 练习模式", fontWeight = FontWeight.Bold)
                        if (uiState.isPracticing) {
                            Spacer(modifier = Modifier.width(8.dp))
                            PulseIndicator(color = MaterialTheme.colorScheme.error, size = 8.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("录制中", fontSize = 11.sp,
                                 color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        }
                    }
                },
                actions = {
                    if (uiState.isPracticing) {
                        AccuracyRing(
                            accuracy = uiState.accuracy,
                            size = 44.dp,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // === No score selected — show picker ===
            if (uiState.score == null) {
                Spacer(modifier = Modifier.weight(1f))
                Text("🎵", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("选择乐谱开始练习", style = MaterialTheme.typography.titleMedium,
                     fontWeight = FontWeight.Bold)
                Text("从下方选择一首内置乐谱", fontSize = 13.sp,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(20.dp))
                viewModel.availableScores.forEach { score ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setScore(score) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when {
                                    score.notes.size <= 10 -> "⭐"
                                    score.notes.size <= 15 -> "⭐⭐"
                                    else -> "⭐⭐⭐"
                                },
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(score.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${score.composer} · ${score.notes.size}个音",
                                     fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Icon(Icons.Filled.PlayArrow, "选择",
                                 tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                return@Column
            }

            // === Score info bar ===
            uiState.score?.let { score ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(score.title, fontWeight = FontWeight.Bold,
                                 style = MaterialTheme.typography.titleMedium,
                                 color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(score.composer, fontSize = 12.sp,
                                 color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        }
                        TextButton(onClick = { showScorePicker = true }) {
                            Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("换一首", fontSize = 13.sp)
                        }
                    }
                }
            }

            // === Staff notation ===
            uiState.score?.let { score ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    shape = RoundedCornerShape(12.dp)
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
                targetState = Triple(uiState.lastFeedback, uiState.lastExpectedNote, uiState.lastDetectedNote),
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically { it / 4 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically { -it / 4 })
                },
                label = "feedback",
                modifier = Modifier.fillMaxWidth()
            ) { (feedback, expected, detected) ->
                FeedbackCard(feedback, expected, detected)
            }

            // === Progress ===
            uiState.score?.let { score ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("进度", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(
                            "${uiState.currentNoteIndex + 1}/${score.notes.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // === Stats chips ===
            if (uiState.isPracticing || uiState.correctCount + uiState.wrongCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    InfoChip("✅", "${uiState.correctCount}",
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        contentColor = Color(0xFF2E7D32))
                    InfoChip("❌", "${uiState.wrongCount}",
                        containerColor = Color(0xFFEF5350).copy(alpha = 0.15f),
                        contentColor = Color(0xFFC62828))
                }
            }

            if (uiState.sessionSaved) {
                Text("💾 练习记录已保存到统计",
                     color = MaterialTheme.colorScheme.primary,
                     fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.weight(1f))

            // === Main control button ===
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!uiState.isPracticing) {
                    FilledIconButton(
                        onClick = {
                            val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (hasPermission) viewModel.startPractice()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.Mic, "开始练习", Modifier.size(36.dp))
                    }
                } else {
                    FilledIconButton(
                        onClick = { viewModel.stopPractice() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
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
            title = { Text("选择乐谱", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    viewModel.availableScores.forEach { score ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setScore(score)
                                    showScorePicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(score.title, style = MaterialTheme.typography.bodyLarge,
                                 modifier = Modifier.weight(1f))
                            Text("${score.notes.size}音", fontSize = 12.sp,
                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        HorizontalDivider()
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
            Triple(Color(0xFF4CAF50), "✅", "正确！$expectedNote 🎉")
        PracticeViewModel.FeedbackType.WRONG_PITCH ->
            Triple(Color(0xFFEF5350), "❌", "错音：弹了 $detectedNote，应为 $expectedNote")
        PracticeViewModel.FeedbackType.EXTRA_NOTE ->
            Triple(Color(0xFFFFA726), "➕", "多弹了 $detectedNote")
        PracticeViewModel.FeedbackType.MISSING_NOTE ->
            Triple(Color(0xFFFFA726), "⚠️", "漏弹 $expectedNote")
        PracticeViewModel.FeedbackType.NONE ->
            Triple(Color(0xFF42A5F5), "🎹", "准备就绪，开始弹奏...")
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, bgColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(message, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = bgColor)
            }
        }
    }
}
