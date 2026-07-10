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
import com.pianocompanion.data.FavoriteStore
import com.pianocompanion.data.model.Score
import com.pianocompanion.musicalterms.MusicalTermsLibrary
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

    // Filter scores by search query, then apply favorites sorting / filter.
    val favorites = uiState.favorites
    val showFavoritesOnly = uiState.showFavoritesOnly

    val filteredBuiltIn = remember(searchQuery, favorites, showFavoritesOnly) {
        var result = if (searchQuery.isBlank()) builtInScores
        else builtInScores.filter {
            it.title.contains(searchQuery, true) || it.composer.contains(searchQuery, true)
        }
        if (showFavoritesOnly) {
            result = result.filter { favorites.contains(it.id) }
        } else {
            // 收藏置顶（稳定排序）
            result = result.sortedWith(
                compareBy<Score> { !favorites.contains(it.id) }.thenBy { builtInScores.indexOf(it) }
            )
        }
        result
    }
    val filteredImported = remember(searchQuery, uiState.importedScores, favorites, showFavoritesOnly) {
        var result = if (searchQuery.isBlank()) uiState.importedScores
        else uiState.importedScores.filter {
            it.title.contains(searchQuery, true) || it.composer.contains(searchQuery, true)
        }
        if (showFavoritesOnly) {
            result = result.filter { favorites.contains(FavoriteStore.keyForImported(it.fileName)) }
        } else {
            result = result.sortedWith(
                compareBy<ScoreItem> {
                    !favorites.contains(FavoriteStore.keyForImported(it.fileName))
                }.thenBy { uiState.importedScores.indexOf(it) }
            )
        }
        result
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

            // === Favorites filter chip ===
            item {
                FavoritesFilterRow(
                    favoriteCount = favorites.size,
                    showFavoritesOnly = showFavoritesOnly,
                    onToggle = { viewModel.toggleFavoritesOnly() }
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

            // === Key signature trainer entry ===
            item {
                KeySignatureTrainerEntryCard(onClick = {
                    navController.navigate(Screen.KeySignature.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Rhythm reading trainer entry ===
            item {
                RhythmReadingTrainerEntryCard(onClick = {
                    navController.navigate(Screen.RhythmReading.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Training summary dashboard entry ===
            item {
                TrainingSummaryEntryCard(onClick = {
                    navController.navigate(Screen.TrainingSummary.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Mixed practice entry ===
            item {
                MixedPracticeEntryCard(onClick = {
                    navController.navigate(Screen.MixedPractice.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Musical terms trainer entry ===
            item {
                MusicalTermsEntryCard(onClick = {
                    navController.navigate(Screen.MusicalTerms.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Mode recognition trainer entry ===
            item {
                ModeRecognitionEntryCard(onClick = {
                    navController.navigate(Screen.ModeRecognition.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Chord ear training entry ===
            item {
                ChordTrainingEntryCard(onClick = {
                    navController.navigate(Screen.ChordTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Rhythm pattern ear training entry ===
            item {
                RhythmPatternEntryCard(onClick = {
                    navController.navigate(Screen.RhythmPattern.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Melody memory training entry ===
            item {
                MelodyMemoryEntryCard(onClick = {
                    navController.navigate(Screen.MelodyMemory.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Interval ear training entry ===
            item {
                IntervalTrainingEntryCard(onClick = {
                    navController.navigate(Screen.IntervalTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Pitch (absolute) training entry ===
            item {
                PitchTrainingEntryCard(onClick = {
                    navController.navigate(Screen.PitchTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Cadence training entry ===
            item {
                CadenceTrainingEntryCard(onClick = {
                    navController.navigate(Screen.CadenceTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Scale training entry ===
            item {
                ScaleTrainingEntryCard(onClick = {
                    navController.navigate(Screen.ScaleTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Inversion training entry ===
            item {
                InversionTrainingEntryCard(onClick = {
                    navController.navigate(Screen.InversionTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Progression training entry ===
            item {
                ProgressionTrainingEntryCard(onClick = {
                    navController.navigate(Screen.ProgressionTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Key identification training entry ===
            item {
                KeyIdentificationTrainingEntryCard(onClick = {
                    navController.navigate(Screen.KeyIdentificationTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Seventh chord training entry ===
            item {
                SeventhChordTrainingEntryCard(onClick = {
                    navController.navigate(Screen.SeventhChordTraining.route) {
                        launchSingleTop = true
                    }
                })
            }

            // === Suspended chord training entry ===
            item {
                SuspendedChordTrainingEntryCard(onClick = {
                    navController.navigate(Screen.SuspendedChordTraining.route) {
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
                    isFavorite = favorites.contains(score.id),
                    onToggleFavorite = { viewModel.toggleBuiltInFavorite(score.id) },
                    onClick = {
                        ScoreSelectionHolder.set(score)
                        navController.navigate(Screen.Practice.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // === Imported scores ===
            // 在收藏筛选模式下，仅在确有收藏乐谱或无搜索时显示标题；
            // 否则隐藏标题让空状态占满视觉焦点。
            val showImportedHeader = filteredImported.isNotEmpty() ||
                (searchQuery.isBlank() && !showFavoritesOnly)
            if (showImportedHeader) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = "我的乐谱",
                        icon = Icons.Filled.FileUpload
                    )
                }
            }

            if (filteredBuiltIn.isEmpty() && filteredImported.isEmpty() && showFavoritesOnly) {
                item {
                    EmptyState(
                        emoji = "⭐",
                        title = "还没有收藏的乐谱",
                        subtitle = "点击乐谱卡片上的 ☆ 星标即可收藏，收藏的乐谱会置顶显示",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (filteredImported.isEmpty() && searchQuery.isBlank() && !showFavoritesOnly) {
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
                        isFavorite = favorites.contains(FavoriteStore.keyForImported(item.fileName)),
                        onToggleFavorite = { viewModel.toggleImportedFavorite(item.fileName) },
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
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
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
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
            FavoriteIconButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite
            )
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
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
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
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
            FavoriteIconButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite
            )
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

/**
 * 调号识别训练入口卡片。
 */
@Composable
private fun KeySignatureTrainerEntryCard(onClick: () -> Unit) {
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
                    "调号识别训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "看五线谱调号判断调性 · 五度圈 · 大调/小调",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "调号训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 节奏视读训练入口卡片。
 */
@Composable
private fun RhythmReadingTrainerEntryCard(onClick: () -> Unit) {
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
                    "节奏视读训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "看节奏型辨认音符时值 · 四分/八分/十六分 · 休止符",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "节奏视读训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 训练数据汇总统计入口卡片。
 * 引导用户进入训练汇总仪表盘：汇总所有视唱练耳/听觉训练模块的进度，
 * 展示综合统计、技能等级、改进建议和各模块明细。
 */
@Composable
private fun TrainingSummaryEntryCard(onClick: () -> Unit) {
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
            Text("📊", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "训练汇总",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "汇总所有训练模块进度 · 技能等级 · 智能改进建议",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "训练汇总",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 综合练习入口卡片。
 *
 * 引导用户进入综合练习：将 5 种视唱练耳训练混合在一起随机出题。
 */
@Composable
private fun MixedPracticeEntryCard(onClick: () -> Unit) {
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
            Text("🎯", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "综合练习",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "识谱 · 音程 · 和弦 · 调号 · 节奏 混合随机出题",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "综合练习",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 音乐表情术语训练入口卡片。
 *
 * 引导用户进入音乐术语训练：学习乐谱中常见的速度、力度、演奏法、表情等术语。
 */
@Composable
private fun MusicalTermsEntryCard(onClick: () -> Unit) {
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
            Text("📖", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "音乐术语训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "速度 · 力度 · 演奏法 · 表情 ${MusicalTermsLibrary.size}+ 术语",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "音乐术语训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 调式识别训练入口卡片。
 *
 * 引导用户进入调式听辨训练：通过聆听音阶判断调式类型
 * （大调/小调/多利亚/混合利底亚…）。
 */
@Composable
private fun ModeRecognitionEntryCard(onClick: () -> Unit) {
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
            Text("🎵", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "调式识别训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "听辨音阶 · 判断大调/小调/教会调式 · 8 种调式",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "调式识别训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 和弦听辨训练入口卡片。
 *
 * 引导用户进入和弦听辨训练：通过聆听和弦判断和弦类型
 * （大三/小三/减三/增三/七和弦…）。
 */
@Composable
private fun ChordTrainingEntryCard(onClick: () -> Unit) {
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
                    "和弦听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "听辨和弦 · 判断大三/小三/增减/七和弦 · 8 种类型",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "和弦听辨训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 节奏型听辨训练入口卡片。
 *
 * 引导用户进入节奏型听辨训练：通过聆听由等高"哒"声组成的节奏序列
 * 判断节奏型类型（四分/八分/附点/切分/三连音…）。
 */
@Composable
private fun RhythmPatternEntryCard(onClick: () -> Unit) {
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
                    "节奏型听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "听辨节奏 · 判断四分/八分/附点/切分/三连音 · 8 种节奏型",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "节奏型听辨训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 旋律记忆训练入口卡片。
 *
 * 引导用户进入旋律记忆训练：通过聆听短旋律判断旋律走向
 * （上行 ↑ / 下行 ↓ / 同音 →），训练旋律轮廓听觉识别能力。
 */
@Composable
private fun MelodyMemoryEntryCard(onClick: () -> Unit) {
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
            Text("🎵", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "旋律记忆训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "听辨旋律走向 · 判断上行/下行/同音 · 3 难度 × 2 速度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "旋律记忆训练",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 音程听辨训练入口卡片。
 *
 * 引导用户进入音程听辨训练：通过聆听两个音判断音程名称
 * （小二度/大二度/小三度/大三度/纯四度/纯五度等），训练音程距离听觉识别能力。
 */
@Composable
private fun IntervalTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎼", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "音程听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "听辨两音距离 · 上行/下行/和声 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "音程听辨训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 绝对音高训练入口卡片。
 *
 * 引导用户进入绝对音高训练：聆听一个音符后判断其音名（C/C#/D…B），
 * 训练「频率→音名」的绝对映射能力（perfect pitch）。
 */
@Composable
private fun PitchTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎯", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "绝对音高训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "听单音辨音名 · 白键/全音/跨八度 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "绝对音高训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 终止式听辨训练入口卡片。
 *
 * 引导用户进入终止式听辨训练：聆听两个和弦的进行后判断终止式类型
 * （完全正格/变格/半终止/伪终止），训练对音乐「标点符号」的感知能力。
 */
@Composable
private fun CadenceTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎼", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "终止式听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "听和弦进行辨终止式 · 正格/变格/半终止/伪终止 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "终止式听辨训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 音阶听辨训练入口卡片。
 */
@Composable
private fun ScaleTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎼", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "音阶听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "听音阶辨色彩 · 大调/小调/和声小调/五声调 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "音阶听辨训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun InversionTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎹", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "和弦转位听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "听和弦辨转位 · 原位/第一/第二转位 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "和弦转位听辨训练",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ProgressionTrainingEntryCard(onClick: () -> Unit) {
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
                    "和弦进行听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "听和声运动辨进行 · 流行/古典/爵士 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "和弦进行听辨训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 调性中心辨识训练入口卡片。
 */
@Composable
private fun KeyIdentificationTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎼", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "调性中心辨识训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "听旋律辨调性 · 大调/小调 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "调性中心辨识训练",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun SeventhChordTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎹", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "七和弦品质听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "听和弦辨品质 · 大七/属七/小七/半减七/减七 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "七和弦品质听辨训练",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SuspendedChordTrainingEntryCard(onClick: () -> Unit) {
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
            Text("🎶", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "挂留和弦品质听辨训练",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "听和弦辨类型 · 大三/小三/挂二/挂四/双挂 · 3 难度",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                "挂留和弦品质听辨训练",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 收藏星标按钮。点击切换收藏状态。
 *
 * 已收藏时使用 [Icons.Filled.Star]（实心，amber 强调色），
 * 未收藏时使用 [Icons.Filled.StarBorder]（描边，弱化色）。
 * 点击区域足够大以保证可操作性（48dp 目标）。
 */
@Composable
private fun FavoriteIconButton(
    isFavorite: Boolean,
    onToggle: () -> Unit
) {
    IconButton(onClick = onToggle) {
        if (isFavorite) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "取消收藏",
                tint = Color(0xFFFFC107) // amber
            )
        } else {
            Icon(
                Icons.Filled.StarBorder,
                contentDescription = "添加收藏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 收藏筛选行：显示「只看收藏」FilterChip 与收藏计数。
 *
 * 当没有任何收藏时，筛选 chip 仍然可见但弱化（不可点击也无视觉效果，
 * 因为 toggle 后列表只会变空），帮助引导用户先收藏乐谱。
 */
@Composable
private fun FavoritesFilterRow(
    favoriteCount: Int,
    showFavoritesOnly: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = showFavoritesOnly,
            onClick = onToggle,
            label = { Text("只看收藏", fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    if (showFavoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        if (favoriteCount > 0) {
            Text(
                "★ $favoriteCount 首已收藏",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
