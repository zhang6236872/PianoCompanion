package com.pianocompanion.ui.rhythmreading

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.rhythmreading.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 节奏视读训练主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择难度（初级/中级/高级）
 * 2. 开始练习后，屏幕显示一条节奏型（4 拍小节）
 * 3. 用户从 4 个视觉选项中找出与题目完全一致的节奏型
 * 4. 答题后显示对错，可播放节奏点击音轨验证
 * 5. 点击「下一题」继续
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmReadingScreen(
    viewModel: RhythmReadingViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RhythmReadingViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🥁 节奏视读训练", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!uiState.isSessionActive) {
                RhythmReadingSetupPanel(
                    difficulty = uiState.difficulty,
                    progress = uiState.progress,
                    onSelectDifficulty = { viewModel.selectDifficulty(it) },
                    onStart = { viewModel.startSession(it) }
                )
            } else {
                RhythmReadingPracticePanel(
                    uiState = uiState,
                    onPlayAudio = { viewModel.playAudio() },
                    onStopAudio = { viewModel.stopAudio() },
                    onSubmit = { viewModel.submitAnswer(it) },
                    onNext = { viewModel.nextQuestion() },
                    onEnd = { viewModel.endSession() }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── 配置面板 ──────────────────────────────────────────────

@Composable
private fun RhythmReadingSetupPanel(
    difficulty: RhythmReadingDifficulty,
    progress: RhythmReadingProgress,
    onSelectDifficulty: (RhythmReadingDifficulty) -> Unit,
    onStart: (RhythmReadingDifficulty) -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(difficulty) }

    // 难度选择
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("选择难度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            RhythmReadingDifficulty.ALL.forEach { diff ->
                val selected = diff == selectedDifficulty
                FilterChip(
                    selected = selected,
                    onClick = { selectedDifficulty = diff },
                    label = {
                        Text("${diff.displayName} · ${difficultyDesc(diff)}")
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }

    // 进度展示
    if (progress.totalAnswered > 0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("练习记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "累计答题 ${progress.totalAnswered} 题 · 正确 ${progress.totalCorrect} 题 · " +
                        "准确率 ${"%.0f".format(progress.overallAccuracy * 100)}%",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                RhythmReadingDifficulty.ALL.forEach { diff ->
                    val entry = progress.getProgress(diff)
                    if (entry.totalAnswered > 0) {
                        Text(
                            "${diff.displayName}：${entry.totalAnswered} 题 · " +
                                "准确率 ${"%.0f".format(entry.cumulativeAccuracy * 100)}% · " +
                                "最佳连击 ${entry.bestStreak}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // 知识说明
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("📖 训练说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "屏幕上方显示一条 4 拍节奏型（4/4 拍号的一个小节），" +
                    "请从下方 4 个节奏型选项中找出与题目完全一致的那个。\n" +
                    "可点击「试听节奏」听到节奏的律动。仔细辨认每个音符的时值！",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )
        }
    }

    // 开始按钮
    Button(
        onClick = { onStart(selectedDifficulty) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, "开始")
        Spacer(modifier = Modifier.width(8.dp))
        Text("开始训练", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun difficultyDesc(diff: RhythmReadingDifficulty): String = when (diff) {
    RhythmReadingDifficulty.BEGINNER -> "四分音符 + 八分音符"
    RhythmReadingDifficulty.INTERMEDIATE -> "加入二分音符 + 休止符"
    RhythmReadingDifficulty.ADVANCED -> "加入十六分音符 + 八分休止符"
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun RhythmReadingPracticePanel(
    uiState: RhythmReadingUiState,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit
) {
    val question = uiState.currentQuestion ?: return

    // 统计栏
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RhythmStatColumn("${uiState.correctCount}/${uiState.answeredCount}", "答题")
            RhythmStatColumn("${uiState.currentStreak}", "连击")
            RhythmStatColumn("${"%.0f".format(accuracy(uiState) * 100)}%", "准确率")
        }
    }

    // 节奏型题目卡片
    RhythmPatternCard(
        items = question.pattern,
        isAnswered = uiState.isAnswered,
        lastResultCorrect = uiState.lastResult?.isCorrect,
        title = "题目",
        modifier = Modifier.fillMaxWidth(),
        height = 140
    )

    // 试听按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (!uiState.audioReady) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("音频准备中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FilledTonalButton(
                onClick = { if (uiState.isPlaying) onStopAudio() else onPlayAudio() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                    "试听节奏",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("试听节奏")
            }
        }
    }

    // 选项区
    if (!uiState.isAnswered) {
        Text("选出完全相同的节奏型：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.answerOptions.forEach { option ->
                RhythmOptionCard(
                    items = option.items,
                    label = option.label,
                    onClick = { onSubmit(option.fingerprint) }
                )
            }
        }
    } else {
        // 答案反馈
        AnswerFeedbackCard(
            isCorrect = uiState.lastResult?.isCorrect == true,
            correctLabel = question.answerOptions.firstOrNull { it.fingerprint == question.correctAnswer }?.label
                ?: ""
        )
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.NavigateNext, "下一题")
            Spacer(modifier = Modifier.width(8.dp))
            Text("下一题", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    // 结束会话按钮
    TextButton(
        onClick = onEnd,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("结束训练")
    }
}

private fun accuracy(uiState: RhythmReadingUiState): Double =
    if (uiState.answeredCount == 0) 0.0 else uiState.correctCount.toDouble() / uiState.answeredCount

@Composable
private fun RhythmStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun AnswerFeedbackCard(isCorrect: Boolean, correctLabel: String) {
    val containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val contentColor = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (isCorrect) "✅ 正确！" else "❌ 不正确",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (!isCorrect) {
                Text(
                    "正确节奏型：$correctLabel",
                    fontSize = 14.sp,
                    color = contentColor
                )
            }
        }
    }
}

// ── 节奏型 Canvas 渲染 ───────────────────────────────────

/**
 * 节奏型显示卡片。在单线谱上绘制节奏序列。
 */
@Composable
private fun RhythmPatternCard(
    items: List<RhythmItem>,
    isAnswered: Boolean,
    lastResultCorrect: Boolean?,
    title: String,
    modifier: Modifier = Modifier,
    height: Int = 140
) {
    val noteColor = if (isAnswered) {
        if (lastResultCorrect == true) Color(0xFF2E7D32) else Color(0xFFC62828)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier.height(height.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRhythmPattern(items, noteColor)
            }
        }
    }
}

/**
 * 选项卡片：小尺寸节奏型 Canvas + 标签。
 */
@Composable
private fun RhythmOptionCard(
    items: List<RhythmItem>,
    label: String,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = 1f, label = "opt_scale")
    val noteColor = MaterialTheme.colorScheme.onSurface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRhythmPattern(items, noteColor)
                }
            }
        }
    }
}

// ── DrawScope 扩展：节奏型绘制核心 ──────────────────────

/**
 * 在单线谱上绘制一条节奏型。
 *
 * 布局：
 * - 一条水平线在垂直中心
 * - 每个音符/休止符占据与其时值成正比的水平段
 * - 音符：实心/空心椭圆符头 + 向上符干 + 横梁（连续八/十六分）/符尾（孤立）
 * - 休止符：几何图形（四分休止=竖锯齿形、八分休止=旗形）
 *
 * @param items 节奏序列
 * @param color 绘制颜色
 */
private fun DrawScope.drawRhythmPattern(
    items: List<RhythmItem>,
    color: Color
) {
    if (items.isEmpty()) return

    val w = size.width
    val h = size.height
    val lineY = h * 0.5f
    val padX = w * 0.06f
    val usableW = w - 2 * padX

    // 节奏线
    drawLine(
        color = color.copy(alpha = 0.35f),
        start = Offset(padX * 0.5f, lineY),
        end = Offset(w - padX * 0.5f, lineY),
        strokeWidth = 2f
    )

    val totalBeats = items.sumOf { it.beats }
    if (totalBeats <= 0) return

    // 计算每个元素的布局
    val unit = usableW / totalBeats.toFloat()
    data class Slot(val item: RhythmItem, val cx: Float, val segStart: Float, val segEnd: Float, val index: Int)
    val slots = mutableListOf<Slot>()
    var cursor = padX
    items.forEachIndexed { idx, item ->
        val segW = (item.beats * unit).toFloat()
        slots.add(Slot(item, cursor + segW / 2f, cursor, cursor + segW, idx))
        cursor += segW
    }

    val noteheadW = (unit * 0.5f).coerceIn(8f, 24f)
    val noteheadH = noteheadW * 0.7f
    val stemLen = h * 0.32f
    val stemXOffset = noteheadW * 0.45f

    // 第一遍：绘制符头 + 符干 + 休止符
    slots.forEach { slot ->
        val item = slot.item
        val cx = slot.cx
        if (item.isRest) {
            drawRest(item.duration, cx, lineY, noteheadW, color)
        } else {
            // 符头
            if (item.duration.isFilled) {
                drawOval(
                    color = color,
                    topLeft = Offset(cx - noteheadW / 2f, lineY - noteheadH / 2f),
                    size = Size(noteheadW, noteheadH)
                )
            } else {
                // 空心符头
                drawOval(
                    color = color,
                    topLeft = Offset(cx - noteheadW / 2f, lineY - noteheadH / 2f),
                    size = Size(noteheadW, noteheadH),
                    style = Stroke(width = 2.5f)
                )
            }
            // 符干（全音符无符干）
            if (item.duration.hasStem) {
                drawLine(
                    color = color,
                    start = Offset(cx + stemXOffset, lineY),
                    end = Offset(cx + stemXOffset, lineY - stemLen),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    // 第二遍：横梁 / 符尾（基于符干顶端 Y）
    val stemTopY = lineY - stemLen
    val beamThickness = 3.5f

    // 检测连续可加横梁的音符组
    var i = 0
    while (i < slots.size) {
        val item = slots[i].item
        if (item.duration.isBeamed) {
            // 收集连续的 beamed 音符
            val groupStart = i
            while (i < slots.size && slots[i].item.duration.isBeamed) {
                i++
            }
            val group = slots.subList(groupStart, i)
            if (group.size >= 2) {
                // 绘制横梁连接组内相邻音符
                val maxFlags = group.maxOf { it.item.duration.flagCount }
                for (layer in 0 until maxFlags) {
                    val yOffset = layer * (beamThickness + 3f)
                    for (j in 0 until group.size - 1) {
                        val a = group[j]
                        val b = group[j + 1]
                        drawLine(
                            color = color,
                            start = Offset(a.cx + stemXOffset, stemTopY + yOffset),
                            end = Offset(b.cx + stemXOffset, stemTopY + yOffset),
                            strokeWidth = beamThickness,
                            cap = StrokeCap.Round
                        )
                    }
                }
            } else {
                // 单个 beamed 音符 → 绘制符尾
                drawFlags(group[0].cx, stemTopY, group[0].item.duration.flagCount, noteheadW, color)
            }
        } else {
            i++
        }
    }
}

/**
 * 绘制符尾（孤立八分/十六分音符）。
 * 在符干顶端绘制 flagCount 个弯曲小钩。
 */
private fun DrawScope.drawFlags(
    stemX: Float,
    stemTopY: Float,
    flagCount: Int,
    noteheadW: Float,
    color: Color
) {
    val flagLen = noteheadW * 0.9f
    for (layer in 0 until flagCount) {
        val yOffset = layer * 7f
        // 绘制一条短斜线作为符尾钩
        drawLine(
            color = color,
            start = Offset(stemX, stemTopY + yOffset),
            end = Offset(stemX + flagLen, stemTopY + yOffset + flagLen * 0.6f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 绘制休止符图形。
 * - 四分休止符：竖直锯齿形（Z 形）
 * - 八分休止符：旗形（顶部横钩 + 下部斜钩）
 */
private fun DrawScope.drawRest(
    duration: RhythmDuration,
    cx: Float,
    lineY: Float,
    noteheadW: Float,
    color: Color
) {
    val s = noteheadW * 0.5f // 基准尺寸
    when (duration) {
        RhythmDuration.QUARTER_REST -> {
            // 锯齿形四分休止符
            val top = lineY - s * 1.8f
            val bot = lineY + s * 1.8f
            drawLine(color, Offset(cx - s, top), Offset(cx + s, top + s * 0.8f), 3f, StrokeCap.Round)
            drawLine(color, Offset(cx + s, top + s * 0.8f), Offset(cx - s * 0.6f, lineY), 3f, StrokeCap.Round)
            drawLine(color, Offset(cx - s * 0.6f, lineY), Offset(cx + s * 0.6f, lineY + s * 0.4f), 3f, StrokeCap.Round)
            drawLine(color, Offset(cx + s * 0.6f, lineY + s * 0.4f), Offset(cx - s, bot), 3f, StrokeCap.Round)
        }
        RhythmDuration.EIGHTH_REST -> {
            // 旗形八分休止符：一个圆点 + 两条斜钩
            val top = lineY - s * 1.4f
            drawCircle(color, radius = s * 0.32f, center = Offset(cx, top))
            drawLine(color, Offset(cx, top), Offset(cx + s, lineY + s * 0.6f), 3f, StrokeCap.Round)
            drawLine(color, Offset(cx, top + s * 0.3f), Offset(cx + s * 0.7f, lineY + s), 2.5f, StrokeCap.Round)
        }
        else -> {
            // 兜底：小矩形
            drawRect(
                color = color,
                topLeft = Offset(cx - s * 0.3f, lineY - s),
                size = Size(s * 0.6f, s * 2f)
            )
        }
    }
}
