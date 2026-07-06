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
import com.pianocompanion.data.model.PracticeMode
import com.pianocompanion.ui.components.AccuracyRing
import com.pianocompanion.ui.components.InfoChip
import com.pianocompanion.ui.components.PulseIndicator
import com.pianocompanion.ui.score.AutoScrollScoreRenderer

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
    var showTransposeDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

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
                        TextButton(onClick = { showTransposeDialog = true }) {
                            Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("移调", fontSize = 13.sp)
                        }
                        TextButton(onClick = { showScorePicker = true }) {
                            Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("换一首", fontSize = 13.sp)
                        }
                    }
                }

                // === 参考音频回放控制 ===
                if (uiState.referenceDurationMs > 0L) {
                    ReferencePlaybackBar(
                        isPlaying = uiState.isReferencePlaying,
                        currentMs = uiState.referencePlaybackMs,
                        totalMs = uiState.referenceDurationMs,
                        onToggle = { viewModel.toggleReferencePlayback() },
                        onStop = { viewModel.stopReferencePlayback() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // === Settings summary bar (compact, tap to open settings) ===
            uiState.score?.let {
                SettingsSummaryBar(
                    practiceMode = uiState.practiceMode,
                    metronomeEnabled = uiState.metronomeEnabled,
                    metronomeBpm = uiState.metronomeBpm,
                    loopEnabled = uiState.loopEnabled,
                    tempoRampEnabled = uiState.tempoRampEnabled,
                    onOpenSettings = { showSettingsSheet = true }
                )
            }

            // === Tempo ramp-up live progress (练习中显示) ===
            if (uiState.isPracticing && uiState.tempoRampEnabled) {
                TempoRampProgressCard(
                    currentBpm = uiState.tempoRampCurrentBpm,
                    targetBpm = uiState.tempoRampTargetBpm,
                    currentStep = uiState.tempoRampCurrentStep,
                    totalSteps = uiState.tempoRampTotalSteps,
                    loopsAtCurrentStep = uiState.tempoRampLoopsAtCurrentStep,
                    loopsPerStep = uiState.tempoRampLoopsPerStep,
                    completed = uiState.tempoRampCompleted
                )
            }

            // === Staff notation — 大尺寸，占据屏幕主要空间 ===
            uiState.score?.let { score ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AutoScrollScoreRenderer(
                        notes = score.notes,
                        currentPosition = uiState.currentNoteIndex,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // === Hand separation stats ===
            if (uiState.isPracticing && (uiState.rightHandCorrect + uiState.leftHandCorrect > 0)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HandStatCard(
                        title = "✋ 右手",
                        accuracy = uiState.rightHandAccuracy,
                        correct = uiState.rightHandCorrect,
                        color = Color(0xFF42A5F5),
                        modifier = Modifier.weight(1f)
                    )
                    HandStatCard(
                        title = "✋ 左手",
                        accuracy = uiState.leftHandAccuracy,
                        correct = uiState.leftHandCorrect,
                        color = Color(0xFFEF5350),
                        modifier = Modifier.weight(1f)
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
                    if (uiState.loopEnabled && uiState.loopCount > 0) {
                        InfoChip("🔁", "第${uiState.loopCount + 1}遍",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary)
                    }
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

    // === Practice settings bottom sheet ===
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚙️ 练习设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                // --- Practice mode ---
                Text("练习模式", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PracticeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.practiceMode == mode,
                            onClick = { viewModel.setPracticeMode(mode) },
                            label = { Text(mode.displayName, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(uiState.practiceMode.description, fontSize = 12.sp,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

                HorizontalDivider()

                // --- Section loop ---
                uiState.score?.let {
                    if (uiState.maxMeasure > 0) {
                        SectionLoopControl(
                            enabled = uiState.loopEnabled,
                            startMeasure = uiState.loopStartMeasure,
                            endMeasure = uiState.loopEndMeasure,
                            maxMeasure = uiState.maxMeasure,
                            onToggle = { viewModel.setLoopEnabled(it) },
                            onRangeChange = { s, e -> viewModel.setLoopRange(s, e) }
                        )
                    }
                }

                // --- Tempo ramp (only if loop enabled) ---
                uiState.score?.let {
                    if (uiState.loopEnabled) {
                        TempoRampUpControl(
                            enabled = uiState.tempoRampEnabled,
                            startBpm = uiState.tempoRampStartBpm,
                            targetBpm = uiState.tempoRampTargetBpm,
                            increment = uiState.tempoRampIncrement,
                            loopsPerStep = uiState.tempoRampLoopsPerStep,
                            onToggle = { viewModel.setTempoRampEnabled(it) },
                            onConfigChange = { s, t, i, l ->
                                viewModel.setTempoRampConfig(s, t, i, l)
                            }
                        )
                    }
                }

                // --- Tempo progress history ---
                uiState.tempoProgressSummary?.let { summary ->
                    if (uiState.loopEnabled) {
                        TempoProgressSummaryCard(summary = summary)
                    }
                }

                HorizontalDivider()

                // --- Metronome ---
                MetronomeControlBar(
                    enabled = uiState.metronomeEnabled,
                    bpm = uiState.metronomeBpm,
                    currentBeat = uiState.metronomeBeat,
                    onToggle = { viewModel.toggleMetronome() },
                    onBpmChange = { viewModel.setMetronomeBpm(it) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Done button
                FilledTonalButton(
                    onClick = { showSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成", fontWeight = FontWeight.Medium)
                }
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

    // === 移调对话框 ===
    if (showTransposeDialog) {
        TransposeDialog(
            detectedKey = viewModel.detectCurrentKey(),
            onDismiss = { showTransposeDialog = false },
            onTranspose = { semitones -> viewModel.transposeScore(semitones) }
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

@Composable
private fun MetronomeControlBar(
    enabled: Boolean,
    bpm: Int,
    currentBeat: Int,
    onToggle: () -> Unit,
    onBpmChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle button
            FilterChip(
                selected = enabled,
                onClick = onToggle,
                label = { Text("🎵 节拍器", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        if (enabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        Modifier.size(16.dp)
                    )
                }
            )
            Spacer(modifier = Modifier.width(8.dp))

            // BPM display + steppers
            if (enabled) {
                // Beat indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == currentBeat) 10.dp else 7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (i == currentBeat) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // BPM steppers
            IconButton(onClick = { onBpmChange(bpm - 5) }, modifier = Modifier.size(32.dp)) {
                Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "$bpm",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onBpmChange(bpm + 5) }, modifier = Modifier.size(32.dp)) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HandStatCard(
    title: String,
    accuracy: Float,
    correct: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${(accuracy * 100).toInt()}%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text("$correct 正确", fontSize = 10.sp, color = color.copy(alpha = 0.7f))
        }
    }
}

/**
 * 段落循环练习控制器：选择小节范围反复练习指定段落（攻克薄弱环节）。
 */
@Composable
private fun SectionLoopControl(
    enabled: Boolean,
    startMeasure: Int,
    endMeasure: Int,
    maxMeasure: Int,
    onToggle: (Boolean) -> Unit,
    onRangeChange: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔁", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "段落循环",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "选择反复练习的小节范围（第 ${startMeasure + 1} ~ ${endMeasure + 1} 小节）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    MeasureStepper(
                        label = "起始小节",
                        value = startMeasure + 1,
                        minValue = 1,
                        maxValue = maxMeasure + 1,
                        onDecrement = {
                            if (startMeasure > 0) onRangeChange(startMeasure - 1, endMeasure)
                        },
                        onIncrement = {
                            if (startMeasure < endMeasure) onRangeChange(startMeasure + 1, endMeasure)
                        }
                    )
                    MeasureStepper(
                        label = "结束小节",
                        value = endMeasure + 1,
                        minValue = 1,
                        maxValue = maxMeasure + 1,
                        onDecrement = {
                            if (endMeasure > startMeasure) onRangeChange(startMeasure, endMeasure - 1)
                        },
                        onIncrement = {
                            if (endMeasure < maxMeasure) onRangeChange(startMeasure, endMeasure + 1)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasureStepper(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
            Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "$value",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 渐速练习配置面板：设置起始速度、目标速度、提速量、每步循环次数。
 * 与段落循环配合——每完成 loopsPerStep 次循环后自动提速。
 */
@Composable
private fun TempoRampUpControl(
    enabled: Boolean,
    startBpm: Int,
    targetBpm: Int,
    increment: Int,
    loopsPerStep: Int,
    onToggle: (Boolean) -> Unit,
    onConfigChange: (startBpm: Int, targetBpm: Int, increment: Int, loopsPerStep: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "渐速练习",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "每完成 $loopsPerStep 遍循环后提速 $increment BPM",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "从慢速开始，逐步加速到目标速度（钢琴练习核心技巧）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    MeasureStepper(
                        label = "起始 BPM",
                        value = startBpm,
                        minValue = 40,
                        maxValue = targetBpm,
                        onDecrement = { if (startBpm > 40) onConfigChange(startBpm - 5, targetBpm, increment, loopsPerStep) },
                        onIncrement = { if (startBpm < targetBpm) onConfigChange(startBpm + 5, targetBpm, increment, loopsPerStep) }
                    )
                    MeasureStepper(
                        label = "目标 BPM",
                        value = targetBpm,
                        minValue = startBpm,
                        maxValue = 240,
                        onDecrement = { if (targetBpm > startBpm) onConfigChange(startBpm, targetBpm - 5, increment, loopsPerStep) },
                        onIncrement = { if (targetBpm < 240) onConfigChange(startBpm, targetBpm + 5, increment, loopsPerStep) }
                    )
                    MeasureStepper(
                        label = "每次提速",
                        value = increment,
                        minValue = 1,
                        maxValue = 20,
                        onDecrement = { if (increment > 1) onConfigChange(startBpm, targetBpm, increment - 1, loopsPerStep) },
                        onIncrement = { if (increment < 20) onConfigChange(startBpm, targetBpm, increment + 1, loopsPerStep) }
                    )
                    MeasureStepper(
                        label = "每步循环遍数",
                        value = loopsPerStep,
                        minValue = 1,
                        maxValue = 10,
                        onDecrement = { if (loopsPerStep > 1) onConfigChange(startBpm, targetBpm, increment, loopsPerStep - 1) },
                        onIncrement = { if (loopsPerStep < 10) onConfigChange(startBpm, targetBpm, increment, loopsPerStep + 1) }
                    )
                }
            }
        }
    }
}

/**
 * 练习中渐速进度卡片：显示当前 BPM、目标 BPM、进度条、当前步循环计数。
 */
@Composable
private fun TempoRampProgressCard(
    currentBpm: Int,
    targetBpm: Int,
    currentStep: Int,
    totalSteps: Int,
    loopsAtCurrentStep: Int,
    loopsPerStep: Int,
    completed: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = if (completed) {
            CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                if (completed) {
                    Text(
                        "🎉 已达到目标速度 $targetBpm BPM！",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$currentBpm",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                " → $targetBpm BPM",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                        }
                        Text(
                            "第 ${currentStep + 1}/$totalSteps 步 · 本遍 $loopsAtCurrentStep/$loopsPerStep",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            if (!completed && totalSteps > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

/**
 * 参考音频回放控制条 — 播放/暂停/停止合成乐谱音色，附带进度条和时间标签。
 *
 * 让用户在练习前先听一遍乐谱应该怎么弹（「听 → 模仿 → 练习」学习闭环）。
 * 播放参考音频时会自动停止正在进行的练习（麦克风与扬声器不能同时使用）。
 */
@Composable
private fun ReferencePlaybackBar(
    isPlaying: Boolean,
    currentMs: Long,
    totalMs: Long,
    onToggle: () -> Unit,
    onStop: () -> Unit
) {
    val progress = if (totalMs > 0) (currentMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放 / 暂停按钮
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(40.dp),
                shape = CircleShape
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停参考音频" else "播放参考音频",
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))

            // 进度条 + 时间标签
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🔊 参考音频",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatMs(currentMs)} / ${formatMs(totalMs)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(4.dp))

            // 停止按钮
            IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = "停止参考音频", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 将毫秒格式化为 m:ss 文本。 */
private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * 渐速进度历史卡片 — 展示当前段落的速度进步趋势和历史记录。
 */
@Composable
private fun TempoProgressSummaryCard(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "📈",
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                summary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 紧凑的设置摘要条 — 展示当前关键设置状态，点击打开完整设置面板。
 */
@Composable
private fun SettingsSummaryBar(
    practiceMode: PracticeMode,
    metronomeEnabled: Boolean,
    metronomeBpm: Int,
    loopEnabled: Boolean,
    tempoRampEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onOpenSettings
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SettingsMiniChip(icon = "🎹", label = practiceMode.displayName)
            if (metronomeEnabled) {
                SettingsMiniChip(icon = "🎵", label = "$metronomeBpm BPM")
            }
            if (loopEnabled) {
                SettingsMiniChip(icon = "🔁", label = "循环")
            }
            if (tempoRampEnabled) {
                SettingsMiniChip(icon = "⚡", label = "渐速")
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Settings,
                contentDescription = "练习设置",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SettingsMiniChip(icon: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(icon, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
