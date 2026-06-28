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
                            navController.navigate(Screen.Practice.route) {
                                launchSingleTop = true
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
