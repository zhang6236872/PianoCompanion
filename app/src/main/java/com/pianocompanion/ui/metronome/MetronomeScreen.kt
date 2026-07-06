package com.pianocompanion.ui.metronome

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import com.pianocompanion.audio.Subdivision
import com.pianocompanion.audio.ClickPatternGenerator
import com.pianocompanion.audio.MetronomePreset
import com.pianocompanion.audio.AutoStopPreset
import com.pianocompanion.audio.AutoStopState
import com.pianocompanion.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: MetronomeViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MetronomeViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("⏱️ 节拍器", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // === Beat indicator dots ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(uiState.beatsPerMeasure) { index ->
                    val isActive = uiState.isPlaying && index == uiState.currentBeat
                    val isAccent = index == 0
                    val targetColor = if (isAccent) Color(0xFFEF5350) else Color(0xFF6750A4)
                    val color by animateColorAsState(
                        targetValue = if (isActive) targetColor else targetColor.copy(alpha = 0.2f),
                        animationSpec = tween(100),
                        label = "beat_$index"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isActive) 1.3f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "beat_scale_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size((if (isAccent) 36.dp else 30.dp) * scale)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // === BPM display ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${uiState.bpm}",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text("BPM", fontSize = 16.sp,
                         color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tempoName(uiState.bpm), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                         color = MaterialTheme.colorScheme.primary)
                }
            }

            // === BPM slider ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { viewModel.decreaseBpm() }) {
                    Icon(Icons.Filled.Remove, "减速")
                }
                Slider(
                    value = uiState.bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.toInt()) },
                    valueRange = 40f..240f,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.increaseBpm() }) {
                    Icon(Icons.Filled.Add, "加速")
                }
            }

            // === Time signature selector ===
            SectionHeader(title = "拍号", icon = Icons.Filled.MusicNote)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 3, 4, 6).forEach { beats ->
                    FilterChip(
                        selected = uiState.beatsPerMeasure == beats,
                        onClick = { viewModel.setBeatsPerMeasure(beats) },
                        label = { Text("$beats/4") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // === Subdivision selector ===
            SectionHeader(title = "细分模式", icon = Icons.Filled.GraphicEq)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Subdivision.entries.take(3).forEach { sub ->
                        SubdivisionChip(
                            subdivision = sub,
                            isSelected = uiState.subdivision == sub,
                            onClick = { viewModel.setSubdivision(sub) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Subdivision.entries.drop(3).forEach { sub ->
                        SubdivisionChip(
                            subdivision = sub,
                            isSelected = uiState.subdivision == sub,
                            onClick = { viewModel.setSubdivision(sub) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // 子拍点间隔提示
                val subMs = ClickPatternGenerator.subClickIntervalMs(uiState.bpm, uiState.subdivision)
                Text(
                    text = "每次点击间隔 ${subMs}ms · 每小节 ${Subdivision.totalClicks(uiState.beatsPerMeasure, uiState.subdivision)} 次点击",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // === Auto-stop timer ===
            MetronomeAutoStopSection(
                preset = uiState.autoStopPreset,
                autoStopState = uiState.autoStopState,
                remaining = uiState.autoStopRemaining,
                progress = uiState.autoStopProgress,
                message = uiState.autoStopMessage,
                onSelectPreset = { viewModel.setAutoStopPreset(it) },
                onConsumeMessage = { viewModel.consumeAutoStopMessage() },
            )

            // === Tempo presets ===
            SectionHeader(title = "速度预设", icon = Icons.Filled.Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TempoPreset("慢板", 60, viewModel, uiState.bpm)
                TempoPreset("行板", 80, viewModel, uiState.bpm)
                TempoPreset("中板", 100, viewModel, uiState.bpm)
                TempoPreset("快板", 140, viewModel, uiState.bpm)
                TempoPreset("急板", 180, viewModel, uiState.bpm)
            }

            // === Saved presets ===
            MetronomePresetsSection(
                presets = uiState.presets,
                activePresetName = uiState.activePresetName,
                currentBpm = uiState.bpm,
                currentBeats = uiState.beatsPerMeasure,
                currentSubdivision = uiState.subdivision,
                onLoadPreset = { viewModel.loadPreset(it) },
                onSaveCurrent = { name -> viewModel.saveCurrentAsPreset(name) },
                onDeletePreset = { viewModel.deletePreset(it) },
                onRenamePreset = { old, new -> viewModel.renamePreset(old, new) },
            )

            // 预设操作消息提示
            uiState.presetMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.consumePresetMessage()
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // === Play/Stop button ===
            Box(
                modifier = Modifier.padding(bottom = 16.dp).shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                FilledIconButton(
                    onClick = {
                        if (uiState.isPlaying) viewModel.stop() else viewModel.start()
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isPlaying)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        if (uiState.isPlaying) "停止" else "开始",
                        Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TempoPreset(
    name: String,
    bpm: Int,
    viewModel: MetronomeViewModel,
    currentBpm: Int
) {
    val selected = currentBpm == bpm
    FilterChip(
        selected = selected,
        onClick = { viewModel.setBpm(bpm) },
        label = { Text(name, fontSize = 11.sp) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun SubdivisionChip(
    subdivision: Subdivision,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(subdivision.displayName, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(subdivision.symbol, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = modifier
    )
}

private fun tempoName(bpm: Int): String = when {
    bpm < 60 -> "极慢板 (Largo)"
    bpm < 76 -> "慢板 (Adagio)"
    bpm < 108 -> "行板 (Andante)"
    bpm < 120 -> "中板 (Moderato)"
    bpm < 156 -> "快板 (Allegro)"
    bpm < 200 -> "很快板 (Vivace)"
    else -> "急板 (Presto)"
}

// ═══════════════════════ 节拍器预设 UI ═══════════════════════

/**
 * 节拍器预设管理区域：保存当前配置为预设 + 预设列表（点击加载 / 删除）。
 */
@Composable
private fun MetronomePresetsSection(
    presets: List<MetronomePreset>,
    activePresetName: String?,
    currentBpm: Int,
    currentBeats: Int,
    currentSubdivision: Subdivision,
    onLoadPreset: (MetronomePreset) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
) {
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    SectionHeader(title = "我的预设", icon = Icons.Filled.Bookmark)

    // 保存当前设置按钮
    OutlinedButton(
        onClick = { showSaveDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("保存当前设置为预设  ($currentBpm · $currentBeats/4 · ${currentSubdivision.displayName})")
    }

    Spacer(modifier = Modifier.height(4.dp))

    if (presets.isEmpty()) {
        Text(
            text = "暂无保存的预设，点击上方按钮创建",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isActive = preset.name == activePresetName,
                    onLoad = { onLoadPreset(preset) },
                    onRename = { renameTarget = preset.name },
                    onDelete = { deleteTarget = preset.name },
                )
            }
        }
    }

    // ── 保存对话框 ──
    if (showSaveDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存预设") },
            text = {
                Column {
                    Text(
                        "当前配置：$currentBpm BPM · $currentBeats/4 · ${currentSubdivision.displayName}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("预设名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSaveCurrent(name.trim())
                        }
                        showSaveDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            },
        )
    }

    // ── 重命名对话框 ──
    renameTarget?.let { target ->
        var newName by rememberSaveable(target) { mutableStateOf(target) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名预设") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName.trim() != target) {
                            onRenamePreset(target, newName.trim())
                        }
                        renameTarget = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    // ── 删除确认对话框 ──
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除预设") },
            text = { Text("确定删除预设「$target」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(target)
                        deleteTarget = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 单个预设卡片：点击加载，右侧操作按钮。
 */
@Composable
private fun PresetCard(
    preset: MetronomePreset,
    isActive: Boolean,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp),
        onClick = onLoad,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = preset.summary,
                    fontSize = 12.sp,
                    color = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 重命名按钮
            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "重命名",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 删除按钮
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════ 自动停止定时器 UI ═══════════════════════

/**
 * 自动停止定时器区域：预设时长选择 + 倒计时显示 + 到期提示。
 */
@Composable
private fun MetronomeAutoStopSection(
    preset: AutoStopPreset,
    autoStopState: AutoStopState,
    remaining: String,
    progress: Float,
    message: String?,
    onSelectPreset: (AutoStopPreset) -> Unit,
    onConsumeMessage: () -> Unit,
) {
    SectionHeader(title = "自动停止", icon = Icons.Filled.Timer)

    // 预设时长选择（两行）
    val chips = AutoStopPreset.entries.toList()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.take(4).forEach { p ->
                FilterChip(
                    selected = preset == p,
                    onClick = { onSelectPreset(p) },
                    label = { Text(p.displayLabel, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.drop(4).forEach { p ->
                FilterChip(
                    selected = preset == p,
                    onClick = { onSelectPreset(p) },
                    label = { Text(p.displayLabel, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 倒计时显示（仅 Running 时）
    if (autoStopState is AutoStopState.Running) {
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = remaining,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    trackColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${preset.displayLabel}后自动停止",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
    }

    // 到期提示（3 秒后自动消失）
    message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            onConsumeMessage()
        }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
