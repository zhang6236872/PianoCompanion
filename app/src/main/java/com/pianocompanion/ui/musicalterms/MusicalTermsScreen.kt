package com.pianocompanion.ui.musicalterms

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.musicalterms.*

/**
 * 音乐表情术语训练主界面（Material 3 Compose）。
 *
 * 功能流程：
 * 1. 选择类别（速度/力度/演奏法/表情/修饰词/混合）和难度（初级/中级/高级）
 * 2. 选择出题方向（术语→含义 或 含义→术语 或 随机）
 * 3. 开始练习后，屏幕显示一个音乐术语或其含义
 * 4. 用户从选项中选择正确的对应项
 * 5. 答题后显示对错 + 术语详情（缩写、速度范围、示例）
 * 6. 点击「下一题」继续
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicalTermsScreen(
    viewModel: MusicalTermsViewModel = run {
        val context = LocalContext.current
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MusicalTermsViewModel(context.applicationContext as android.app.Application) as T
            }
        })
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📖 音乐术语训练", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
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
                TermSetupPanel(
                    difficulty = uiState.difficulty,
                    category = uiState.category,
                    direction = uiState.direction,
                    progress = uiState.progress,
                    onStart = { selDifficulty, selCategory, selDirection ->
                        viewModel.startSession(selDifficulty, selCategory, selDirection)
                    }
                )
            } else {
                TermPracticePanel(
                    uiState = uiState,
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
private fun TermSetupPanel(
    difficulty: TermDifficulty,
    category: TermCategory?,
    direction: QuizDirection?,
    progress: MusicalTermsProgress,
    onStart: (TermDifficulty, TermCategory?, QuizDirection?) -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(difficulty) }
    var selectedCategory by remember { mutableStateOf<TermCategory?>(category) }
    var selectedDirection by remember { mutableStateOf<QuizDirection?>(direction) }

    // 类别选择（含「混合」选项）
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择类别", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // 第一行：混合 + 速度 + 力度
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("混合全部") }
                )
                FilterChip(
                    selected = selectedCategory == TermCategory.TEMPO,
                    onClick = { selectedCategory = TermCategory.TEMPO },
                    label = { Text(TermCategory.TEMPO.displayName) }
                )
                FilterChip(
                    selected = selectedCategory == TermCategory.DYNAMICS,
                    onClick = { selectedCategory = TermCategory.DYNAMICS },
                    label = { Text(TermCategory.DYNAMICS.displayName) }
                )
            }
            // 第二行：演奏法 + 表情 + 修饰词
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedCategory == TermCategory.ARTICULATION,
                    onClick = { selectedCategory = TermCategory.ARTICULATION },
                    label = { Text(TermCategory.ARTICULATION.displayName) }
                )
                FilterChip(
                    selected = selectedCategory == TermCategory.EXPRESSION,
                    onClick = { selectedCategory = TermCategory.EXPRESSION },
                    label = { Text(TermCategory.EXPRESSION.displayName) }
                )
                FilterChip(
                    selected = selectedCategory == TermCategory.MODIFIER,
                    onClick = { selectedCategory = TermCategory.MODIFIER },
                    label = { Text(TermCategory.MODIFIER.displayName) }
                )
            }
            // 类别说明
            val catDesc = if (selectedCategory == null) {
                "混合所有类别的术语"
            } else {
                selectedCategory!!.description
            }
            val availableCount = MusicalTermsLibrary.filter(selectedCategory, selectedDifficulty).size
            Text(
                "$catDesc · 当前难度共 $availableCount 个术语",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 难度选择
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择难度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermDifficulty.ALL.forEach { d ->
                    FilterChip(
                        selected = selectedDifficulty == d,
                        onClick = { selectedDifficulty = d },
                        label = { Text(d.displayName) }
                    )
                }
            }
            Text(
                when (selectedDifficulty) {
                    TermDifficulty.BEGINNER -> "常见基础术语 · 3 选项"
                    TermDifficulty.INTERMEDIATE -> "含次常见术语 · 4 选项"
                    TermDifficulty.ADVANCED -> "全部 ${MusicalTermsLibrary.size}+ 术语 · 5 选项"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 出题方向选择
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("出题方向", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedDirection == null,
                    onClick = { selectedDirection = null },
                    label = { Text("随机") }
                )
                FilterChip(
                    selected = selectedDirection == QuizDirection.TERM_TO_MEANING,
                    onClick = { selectedDirection = QuizDirection.TERM_TO_MEANING },
                    label = { Text(QuizDirection.TERM_TO_MEANING.displayName) }
                )
                FilterChip(
                    selected = selectedDirection == QuizDirection.MEANING_TO_TERM,
                    onClick = { selectedDirection = QuizDirection.MEANING_TO_TERM },
                    label = { Text(QuizDirection.MEANING_TO_TERM.displayName) }
                )
            }
        }
    }

    // 进度展示
    if (progress.totalAnswered > 0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📊 练习记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TermStatColumn("总答题", "${progress.totalAnswered}")
                    TermStatColumn("正确", "${progress.totalCorrect}")
                    TermStatColumn("准确率", "${"%.0f".format(progress.overallAccuracy * 100)}%")
                    TermStatColumn("最长连击", "${progress.overallBestStreak}")
                }
            }
        }
    }

    // 开始按钮
    Button(
        onClick = { onStart(selectedDifficulty, selectedCategory, selectedDirection) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, "开始")
        Spacer(modifier = Modifier.width(8.dp))
        Text("开始练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }

    // 说明卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💡 如何练习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("1. 屏幕显示一个音乐术语（如 Allegro）或其含义", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2. 从选项中选择正确的对应项", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("3. 答题后可查看术语详情（缩写、速度范围、使用场景）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4. 连续答对可增加连击数，挑战自己！", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 练习面板 ──────────────────────────────────────────────

@Composable
private fun TermPracticePanel(
    uiState: MusicalTermsUiState,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit
) {
    val question = uiState.currentQuestion ?: return

    // 统计栏
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TermStatCard("${uiState.correctCount}/${uiState.answeredCount}", "答题", 1f)
        TermStatCard("${uiState.currentStreak}", "连击", 1f)
        TermStatCard(
            if (uiState.answeredCount > 0)
                "${"%.0f".format(uiState.correctCount.toDouble() / uiState.answeredCount * 100)}%"
            else "—",
            "准确率",
            1f
        )
    }

    // 配置标签 + 结束按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "${question.term.category.displayName} · ${uiState.difficulty.displayName} · ${question.direction.displayName}",
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onEnd) {
            Text("结束", color = MaterialTheme.colorScheme.error)
        }
    }

    // 题目卡片
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.isAnswered && uiState.lastResult?.isCorrect == true)
                MaterialTheme.colorScheme.primaryContainer
            else if (uiState.isAnswered && uiState.lastResult?.isCorrect == false)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                question.promptLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                question.prompt,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 速度术语显示 BPM 范围
            if (question.term.bpmRange != null && uiState.isAnswered) {
                Text(
                    "♩ = ${question.term.bpmRange} BPM",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // 答题反馈（答题后显示术语详情）
    AnimatedVisibility(
        visible = uiState.isAnswered,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val result = uiState.lastResult
        val term = question.term
        if (result != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isCorrect)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (result.isCorrect) "✅ 正确！" else "❌ 答错了",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${term.term} = ${term.meaning}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (term.abbreviation != null) {
                        Text(
                            "缩写：${term.abbreviation}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (term.bpmRange != null) {
                        Text(
                            "速度范围：${term.bpmRange} BPM",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (term.example != null) {
                        Text(
                            "例：${term.example}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 答案选项
    if (!uiState.isAnswered) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.answerChoices.forEach { choice ->
                TermAnswerButton(
                    text = choice,
                    onClick = { onSubmit(choice) }
                )
            }
        }
    } else {
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
}

// ── 可复用组件 ────────────────────────────────────────────

@Composable
private fun TermAnswerButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RowScope.TermStatCard(value: String, label: String, @Suppress("UNUSED_PARAMETER") weight: Float) {
    Card(
        modifier = Modifier.weight(weight),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TermStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
