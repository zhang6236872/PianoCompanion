package com.pianocompanion.ui.library

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pianocompanion.analytics.DifficultyEstimator
import com.pianocompanion.analytics.DifficultyLevel
import com.pianocompanion.data.DemoScores
import com.pianocompanion.data.model.Score
import com.pianocompanion.ui.components.EmptyState
import com.pianocompanion.ui.components.SectionHeader
import com.pianocompanion.ui.navigation.Screen
import com.pianocompanion.ui.practice.ScoreSelectionHolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as Application) as T
                }
            }
        )
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val builtInScores = remember { DemoScores.getAll() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showSightReadingDialog by remember { mutableStateOf(false) }

    // SAF document picker — MusicXML (.xml/.musicxml) and MIDI (.mid/.midi).
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importScore(uri)
        }
    }

    // Surface import/delete messages via a snackbar.
    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    fun launchImportPicker() {
        pickFileLauncher.launch(arrayOf("application/xml", "text/xml", "audio/midi", "audio/x-midi", "*/*"))
    }

    // Filter scores by search query
    val filteredBuiltIn = remember(searchQuery) {
        if (searchQuery.isBlank()) builtInScores
        else builtInScores.filter {
            it.title.contains(searchQuery, true) || it.composer.contains(searchQuery, true)
        }
    }
    val filteredImported = remember(searchQuery, uiState.importedScores) {
        if (searchQuery.isBlank()) uiState.importedScores
        else uiState.importedScores.filter {
            it.title.contains(searchQuery, true) || it.composer.contains(searchQuery, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎼 乐谱库", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        navController.navigate(Screen.Omr.route)
                    },
                    icon = { Icon(Icons.Filled.PhotoCamera, "拍照识谱") },
                    text = { Text("拍照识谱") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                ExtendedFloatingActionButton(
                    onClick = { launchImportPicker() },
                icon = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Filled.FileUpload, "导入乐谱")
                    }
                },
                text = { Text(if (uiState.isLoading) "导入中…" else "导入乐谱") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // === Search bar ===
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索乐谱名称或作曲家…", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, "搜索", modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, "清除", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // === Sight-reading generator entry ===
            item {
                SightReadingEntryCard(onClick = { showSightReadingDialog = true })
            }

            // === Ear training entry ===
            item {
                EarTrainingEntryCard(onClick = {
                    navController.navigate(Screen.EarTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Rhythm training entry ===
            item {
                RhythmTrainingEntryCard(onClick = {
                    navController.navigate(Screen.RhythmTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Chord dictionary entry ===
            item {
                ChordDictionaryEntryCard(onClick = {
                    navController.navigate(Screen.ChordDictionary.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Scale library entry ===
            item {
                ScaleLibraryEntryCard(onClick = {
                    navController.navigate(Screen.ScaleLibrary.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Chord progression entry ===
            item {
                ChordProgressionEntryCard(onClick = {
                    navController.navigate(Screen.ChordProgression.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Circle of Fifths entry ===
            item {
                CircleOfFifthsEntryCard(onClick = {
                    navController.navigate(Screen.CircleOfFifths.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Cadence library entry ===
            item {
                CadenceLibraryEntryCard(onClick = {
                    navController.navigate(Screen.CadenceLibrary.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Note reading trainer entry ===
            item {
                NoteReadingEntryCard(onClick = {
                    navController.navigate(Screen.NoteReading.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Interval trainer entry ===
            item {
                IntervalTrainerEntryCard(onClick = {
                    navController.navigate(Screen.IntervalTrainer.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Chord reading trainer entry ===
            item {
                ChordReadingTrainerEntryCard(onClick = {
                    navController.navigate(Screen.ChordReading.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Built-in scores ===
            if (filteredBuiltIn.isNotEmpty()) {
                item {
                    SectionHeader(title = "内置乐谱", icon = Icons.Filled.MusicNote)
                }
            }

            items(filteredBuiltIn) { score ->
                EnhancedScoreCard(
                    score = score,
                    onClick = {
                        ScoreSelectionHolder.set(score)
                        navController.navigate(Screen.Practice.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // === Imported scores ===
            if (filteredImported.isNotEmpty() || searchQuery.isBlank()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = "我的乐谱",
                        icon = Icons.Filled.FileUpload
                    )
                }
            }

            if (filteredImported.isEmpty() && searchQuery.isBlank()) {
                item {
                    EmptyState(
                        emoji = "📁",
                        title = "还没有导入的乐谱",
                        subtitle = "点击右下角「导入乐谱」按钮，选择 MusicXML 或 MIDI 文件",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (filteredImported.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    EmptyState(
                        emoji = "🔍",
                        title = "没有找到匹配的乐谱",
                        subtitle = "试试其他关键词",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(filteredImported, key = { it.fileName }) { item ->
                    var showDeleteDialog by remember { mutableStateOf(false) }
                    ImportedScoreCard(
                        item = item,
                        onClick = {
                            // 加载导入的乐谱并传递到练习页（解析失败的乐谱提示用户）。
                            val result = viewModel.loadScore(item.fileName)
                            val loaded = result.getOrNull()
                            if (loaded != null) {
                                ScoreSelectionHolder.set(loaded)
                                navController.navigate(Screen.Practice.route) {
                                    launchSingleTop = true
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        result.exceptionOrNull()?.message ?: "无法加载乐谱，请检查文件格式"
                                    )
                                }
                            }
                        },
                        onLongClick = { showDeleteDialog = true }
                    )
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("删除乐谱") },
                            text = { Text("确定要删除「${item.title}」吗？此操作无法撤销。") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteScore(item.fileName)
                                        showDeleteDialog = false
                                    }
                                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                HelpCard()
            }
        }

        // === 视奏练习生成器对话框 ===
        if (showSightReadingDialog) {
            SightReadingGeneratorDialog(
                onGenerate = { score ->
                    showSightReadingDialog = false
                    ScoreSelectionHolder.set(score)
                    navController.navigate(Screen.Practice.route) {
                        launchSingleTop = true
                    }
                },
                onDismiss = { showSightReadingDialog = false }
            )
        }
    }
}

@Composable
private fun EnhancedScoreCard(
    score: Score,
    onClick: () -> Unit
) {
    val difficultyResult = remember(score) { DifficultyEstimator.estimate(score) }
    val difficulty = difficultyLevelVisual(difficultyResult.level)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(difficulty.first, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(score.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(score.composer, fontSize = 12.sp,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = difficulty.third.copy(alpha = 0.12f)
                    ) {
                        Text(
                            difficulty.second,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = difficulty.third,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("难度 ${difficultyResult.totalScore} · ${score.notes.size} 个音符", fontSize = 11.sp,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
            Icon(Icons.Filled.ChevronRight, "练习", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 将 [DifficultyLevel] 映射为视觉三元组 (星级 emoji, 等级名称, 主题色)。
 * 由 [DifficultyEstimator] 的真实分析驱动，替代原先仅凭音符数量的粗略判定。
 */
private fun difficultyLevelVisual(level: DifficultyLevel): Triple<String, String, Color> = when (level) {
    DifficultyLevel.BEGINNER -> Triple(level.stars, level.label, Color(0xFF4CAF50))
    DifficultyLevel.EASY -> Triple(level.stars, level.label, Color(0xFF8BC34A))
    DifficultyLevel.INTERMEDIATE -> Triple(level.stars, level.label, Color(0xFFFFA726))
    DifficultyLevel.ADVANCED -> Triple(level.stars, level.label, Color(0xFFEF5350))
    DifficultyLevel.EXPERT -> Triple(level.stars, level.label, Color(0xFFAB47BC))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedScoreCard(
    item: ScoreItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 解析成功的导入乐谱使用与内置乐谱一致的难度视觉（星级 emoji + 等级徽章 + 总分）；
    // 解析失败的乐谱回退到普通文件图标，保持原有错误提示。
    val difficultyVisual = if (!item.parseFailed) difficultyLevelVisual(item.difficultyLevel) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(difficultyVisual?.first ?: "📄", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.parseFailed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(item.composer, fontSize = 12.sp,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(4.dp))
                if (item.parseFailed) {
                    Text("⚠️ 解析失败，请检查文件格式", fontSize = 11.sp,
                         color = MaterialTheme.colorScheme.error)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (difficultyVisual != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = difficultyVisual.third.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    difficultyVisual.second,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = difficultyVisual.third,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                item.source,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        val metaText = if (difficultyVisual != null) {
                            "难度 ${item.difficultyScore} · ${item.noteCount} 个音符"
                        } else {
                            "${item.noteCount} 个音符 · 长按可删除"
                        }
                        Text(metaText, fontSize = 11.sp,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, "练习", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("支持导入 MusicXML 和 MIDI 文件", style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Medium)
            Text("用 MuseScore / Finale 导出 .xml，或任何 .mid/.midi 文件，通过 SAF 选择即可导入",
                 fontSize = 12.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/**
 * 视奏练习生成器入口卡片。
 *
 * 放置在乐谱库顶部（搜索栏之后、内置乐谱之前），以渐变强调色突出显示，
 * 引导用户进入视奏练习生成器。
 */
@Composable
private fun SightReadingEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎲", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "视奏练习生成器",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "自动生成调性/节奏/难度可控的练习旋律",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "生成视奏练习",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 听音训练入口卡片。
 *
 * 引导用户进入听音训练（音程/和弦/音阶识别），训练音乐听觉素养。
 */
@Composable
private fun EarTrainingEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("👂", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "听音训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "音程 · 和弦 · 音阶识别，训练你的耳朵",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "听音训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 节奏训练入口卡片。
 * 引导用户进入节奏训练（听节奏 → 敲击模仿 → 评分），训练节奏感和拍速稳定性。
 */
@Composable
private fun RhythmTrainingEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🥁", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "节奏训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "听节奏 · 跟着拍速敲击 · 训练节奏感",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "节奏训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ChordDictionaryEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎹", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "和弦词典",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "浏览和弦 · 可视化键盘 · 试听柱式/琶音",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "和弦词典",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ScaleLibraryEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎼", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "音阶词典",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "15种音阶 · 上行下行 · 可视化键盘 · 指法参考",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "音阶词典",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 和弦进行词典入口卡片。
 * 引导用户浏览和学习常见和弦进行模式（罗马数字分析、调性移调、试听）。
 */
@Composable
private fun ChordProgressionEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎶", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "和弦进行词典",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "流行万能进行 · 爵士ii-V-I · 蓝调 · 古典终止式",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "和弦进行词典",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 五度圈入口卡片。
 * 引导用户进入交互式五度圈工具，学习调性关系、调号与顺阶和弦。
 */
@Composable
private fun CircleOfFifthsEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎡", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "五度圈工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "调性关系 · 调号 · 顺阶和弦 · 罗马数字分析",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "五度圈工具",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 终止式参考库入口卡片。
 * 引导用户进入终止式学习工具，了解完全终止/变格终止/阻碍终止/半终止等
 * 乐句结尾和弦进行，含音频试听和罗马数字分析。
 */
@Composable
private fun CadenceLibraryEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎼", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "终止式参考库",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "完全终止 · 变格终止 · 阻碍终止 · 半终止 · 弗里几亚半终止",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "终止式参考库",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 识谱训练入口卡片。
 * 引导用户进入五线谱识谱训练工具：在五线谱上显示音符，
 * 测试识谱能力，可选不同难度与谱号，并提供音频反馈与进度跟踪。
 */
@Composable
private fun NoteReadingEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎹", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "识谱训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "五线谱识谱练习 · 高音/低音谱号 · 多级难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "识谱训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 音程识别训练入口卡片。
 * 引导用户进入音程识别训练工具：在五线谱上显示两个音符，
 * 测试用户判断音程（度数/性质）的能力，可选不同难度与谱号。
 */
@Composable
private fun IntervalTrainerEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📐", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "音程识别训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "看五线谱判断音程 · 大小/纯/增减 · 多级难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "音程训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 和弦识别训练入口卡片。
 * 引导用户进入和弦识别训练工具：在五线谱上显示叠置的和弦，
 * 测试用户判断和弦类型（大三/小三/减三/增三/七和弦）的能力。
 */
@Composable
private fun ChordReadingTrainerEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎸", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "和弦识别训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "看五线谱判断和弦 · 三和弦/七和弦 · 多级难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "和弦训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
