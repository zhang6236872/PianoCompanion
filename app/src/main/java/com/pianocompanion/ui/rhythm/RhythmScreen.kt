package com.pianocompanion.ui.rhythm

import android.app.Application
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.rhythm.*
import com.pianocompanion.ui.theme.CorrectGreen
import com.pianocompanion.ui.theme.WrongRed

/**
 * 节奏训练页面。
 *
 * 四个阶段：
 * 1. SETUP：选择难度 → 开始
 * 2. LISTENING：播放参考音频（含预备拍），可重听
 * 3. TAPPING：用户敲击大按钮，跟随节奏
 * 4. RESULT：显示匹配结果，进入下一题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: RhythmViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RhythmViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🥁 节奏训练", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        when (state.phase) {
            RhythmPhase.SETUP -> SetupContent(state, viewModel, Modifier.padding(padding))
            RhythmPhase.LISTENING -> ListeningContent(state, viewModel, Modifier.padding(padding))
            RhythmPhase.TAPPING -> TappingContent(state, viewModel, Modifier.padding(padding))
            RhythmPhase.RESULT -> ResultContent(state, viewModel, Modifier.padding(padding))
        }
    }
}

// ── SETUP 阶段 ──────────────────────────────────────────

@Composable
private fun SetupContent(
    state: RhythmUiState,
    viewModel: RhythmViewModel,
    modifier: Modifier
) {
    var selectedDifficulty by remember { mutableStateOf(RhythmDifficulty.BEGINNER) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "🥁 节奏训练",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "听一段节奏，然后跟着节拍敲击屏幕把它模仿出来。" +
                        "这是训练节奏感和稳定拍速的核心练习。\n\n" +
                        "🔊 App 会先播放 4 拍预备拍（嗒嗒嗒嗒），" +
                        "然后播放节奏型。听完后按下「开始敲击」按钮，" +
                        "跟着节奏敲击大圆钮。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        // 难度选择
        SectionLabel("难度等级")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RhythmDifficulty.ALL.forEach { diff ->
                FilterChip(
                    selected = selectedDifficulty == diff,
                    onClick = {
                        selectedDifficulty = diff
                        viewModel.setDifficulty(diff)
                    },
                    label = { Text(diff.displayName, fontWeight = FontWeight.Medium) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 各难度说明
        val diffDesc = when (selectedDifficulty) {
            RhythmDifficulty.BEGINNER -> "四分音符和二分音符，适合入门"
            RhythmDifficulty.INTERMEDIATE -> "加入八分音符和附点四分，中等挑战"
            RhythmDifficulty.ADVANCED -> "加入十六分音符和休止符，高级挑战"
        }
        Text(
            diffDesc,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // 历史进度
        val progress = state.progress.getProgress(selectedDifficulty)
        if (progress.sessionCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📊 历史记录（${selectedDifficulty.displayName}）",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("${progress.sessionCount}", "练习次数")
                        StatItem("${"%.0f".format(progress.averageScore * 100)}%", "平均分")
                        StatItem("${progress.bestStreak}", "最长连击")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 开始按钮
        Button(
            onClick = {
                viewModel.setDifficulty(selectedDifficulty)
                viewModel.startSession()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("开始训练", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── LISTENING 阶段 ──────────────────────────────────────

@Composable
private fun ListeningContent(
    state: RhythmUiState,
    viewModel: RhythmViewModel,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 顶部统计
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${state.passedCount}/${state.answeredCount}", "通过")
            StatItem("${state.currentStreak}", "连击")
            DifficultyChip(state.difficulty.displayName)
        }

        Text(
            "🎧 仔细听这段节奏",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            "先有 4 拍预备拍，然后是节奏型",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 播放按钮
        val pulseScale by animateFloatAsState(
            targetValue = if (state.isPlaying) 1.1f else 1.0f,
            animationSpec = if (state.isPlaying) infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ) else tween(200),
            label = "pulse"
        )

        FilledIconButton(
            onClick = {
                if (state.isPlaying) viewModel.stopPlayback() else viewModel.playReference()
            },
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
                .clip(CircleShape),
            enabled = state.audioReady,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                contentDescription = if (state.isPlaying) "停止" else "播放",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 开始敲击按钮
        Button(
            onClick = { viewModel.startTapping() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = state.audioReady
        ) {
            Icon(Icons.Filled.TouchApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("准备好了，开始敲击", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // 结束按钮
        OutlinedButton(
            onClick = { viewModel.endSession() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("结束训练")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── TAPPING 阶段 ────────────────────────────────────────

@Composable
private fun TappingContent(
    state: RhythmUiState,
    viewModel: RhythmViewModel,
    modifier: Modifier
) {
    var tapAnimScale by remember { mutableStateOf(1f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "👆 跟着节奏敲击！",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            "已敲击 ${state.userTaps.size} 次",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        // 大敲击按钮
        val tapScale by animateFloatAsState(
            targetValue = tapAnimScale,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "tapAnim"
        )

        FilledIconButton(
            onClick = {
                viewModel.recordTap()
                // 弹跳动画反馈
                tapAnimScale = 0.85f
            },
            modifier = Modifier
                .size(200.dp)
                .scale(tapScale)
                .clip(CircleShape),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.TouchApp,
                    contentDescription = "敲击",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onTertiary
                )
                Text(
                    "TAP",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onTertiary
                )
            }
        }

        // 动画恢复
        LaunchedEffect(tapAnimScale) {
            if (tapAnimScale < 1f) {
                kotlinx.coroutines.delay(100)
                tapAnimScale = 1f
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 提交按钮
        Button(
            onClick = { viewModel.submitTaps() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("完成，查看结果", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // 重听按钮
        OutlinedButton(
            onClick = { viewModel.replayReference() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Replay, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("重听参考节奏")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── RESULT 阶段 ─────────────────────────────────────────

@Composable
private fun ResultContent(
    state: RhythmUiState,
    viewModel: RhythmViewModel,
    modifier: Modifier
) {
    val result = state.lastResult ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 评级卡片
        val gradeColor = when (result.grade) {
            TapGrade.PERFECT, TapGrade.GREAT -> CorrectGreen
            TapGrade.GOOD -> MaterialTheme.colorScheme.tertiary
            TapGrade.TRY_AGAIN -> WrongRed
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.8f)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = gradeColor.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        result.grade.emoji,
                        fontSize = 56.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        result.grade.displayName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = gradeColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        result.scorePercent,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor
                    )
                }
            }
        }

        // 详细统计
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${result.perfectHits}", "🎯 Perfect")
            StatItem("${result.goodHits}", "👍 Good")
            StatItem("${result.missedNotes}", "✗ 遗漏")
            StatItem("${result.extraTaps}", "+ 多余")
        }

        Spacer(modifier = Modifier.weight(1f))

        // 下一题 / 结束
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.endSession() },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("结束")
            }
            Button(
                onClick = { viewModel.nextQuestion() },
                modifier = Modifier.weight(1.5f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.NavigateNext, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("下一题", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── 公共组件 ────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DifficultyChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
