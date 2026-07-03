@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.pianocompanion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianocompanion.circle.*
import com.pianocompanion.notation.*
import com.pianocompanion.interval.*
import kotlin.math.*

// ════════════════════════════════════════════════════════════
//  1. 曲库主界面 (Library)
// ════════════════════════════════════════════════════════════

@Composable
fun LibraryScreenPreviewContent() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎼 乐谱库", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 功能入口卡片
            item { PreviewEntryCard("🎹", "视奏练习", "实时跟弹评分 · 智能曲谱", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            item { PreviewEntryCard("👂", "听音训练", "音程/和弦/旋律听辨练习", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            item { PreviewEntryCard("🥁", "节奏训练", "节奏型识别 · 打击练习", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
            item { PreviewEntryCard("🎵", "和弦词典", "三和弦/七和弦/扩展和弦查询", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            item { PreviewEntryCard("🎶", "音阶库", "大调/小调/调式音阶速查", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            item { PreviewEntryCard("🎼", "和弦进行", "流行/爵士经典进行参考", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
            item { PreviewEntryCard("🎡", "五度圈", "交互式调性圆环 · 顺阶和弦", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            item { PreviewEntryCard("📚", "终止式参考库", "6种终止式 × 12调性 · 罗马数字分析", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            item { PreviewEntryCard("📖", "识谱训练", "五线谱音符快速识别 · 三档难度", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
            item { PreviewEntryCard("📐", "音程识别训练", "看五线谱判断音程 · 大小/纯/增减", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }

            // 内置乐谱区
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("内置乐谱", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 示例乐谱卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("小星星变奏曲", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("C大调 · 初级 · 4/4拍", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Mozart · Ah! vous dirai-je, maman", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Text("  初级  ", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("月光奏鸣曲（简化版）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("c♯小调 · 中级 · 3/4拍", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Beethoven · Op. 27 No. 2", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFA726).copy(alpha = 0.15f)
                        ) {
                            Text("  中级  ", fontSize = 12.sp, color = Color(0xFFFFA726), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewEntryCard(
    emoji: String,
    title: String,
    description: String,
    bgColor: Color,
    textColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                Text(description, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = title, tint = textColor)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  2. 五度圈 (Circle of Fifths)
// ════════════════════════════════════════════════════════════

private const val WHEEL_SIZE_DP = 340
private const val WHEEL_HALF_DP = WHEEL_SIZE_DP / 2.0
private const val MAJOR_LABEL_R_DP = 142.0
private const val MINOR_LABEL_R_DP = 93.0

@Composable
fun CircleOfFifthsPreviewContent() {
    val majorKeys = remember { CircleOfFifthsEngine.allKeys(CircleMode.MAJOR) }
    val minorKeys = remember { CircleOfFifthsEngine.allKeys(CircleMode.MINOR) }
    var selectedKey by remember { mutableStateOf(CircleKey(0, CircleMode.MAJOR)) }
    var selectedMode by remember { mutableStateOf(CircleMode.MAJOR) }

    val info = remember(selectedKey) { CircleOfFifthsEngine.keyInfo(selectedKey) }
    val diatonicChords = remember(selectedKey) { CircleOfFifthsEngine.diatonicChords(selectedKey) }
    val relatedKeys = remember(selectedKey) { CircleOfFifthsEngine.closelyRelatedKeys(selectedKey) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎡 五度圈", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 五度圈圆环 ──
            PreviewCircleWheel(
                majorKeys = majorKeys,
                minorKeys = minorKeys,
                selectedKey = selectedKey,
                modifier = Modifier.fillMaxWidth()
            )

            // ── 调式切换 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = selectedMode == CircleMode.MAJOR,
                    onClick = { selectedMode = CircleMode.MAJOR },
                    label = { Text("大调", fontWeight = FontWeight.Medium) }
                )
                Spacer(Modifier.width(12.dp))
                FilterChip(
                    selected = selectedMode == CircleMode.MINOR,
                    onClick = { selectedMode = CircleMode.MINOR },
                    label = { Text("小调", fontWeight = FontWeight.Medium) }
                )
            }

            // ── 选中调性信息卡 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(info.displayName, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(6.dp))
                    val sig = CircleOfFifthsEngine.keySignature(selectedKey)
                    val sigText = when {
                        sig.isNaturalKey -> "无升降号"
                        sig.isSharpKey -> "${sig.sharpsCount} 个升号"
                        else -> "${sig.flatsCount} 个降号"
                    }
                    Text(sigText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.dp))
                    val relKey = CircleOfFifthsEngine.relativeKey(selectedKey)
                    Text("关系调：${CircleOfFifthsEngine.tonicName(relKey)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))
                    Text("音阶", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CircleOfFifthsEngine.scaleNoteNames(selectedKey).joinToString("  "),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ── 播放控制 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {},
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放", modifier = Modifier.size(28.dp))
                }
                FilterChip(selected = true, onClick = {}, label = { Text("顺阶和弦") })
                FilterChip(selected = false, onClick = {}, label = { Text("音阶") })
            }

            // ── 调内顺阶三和弦 ──
            Text("调内顺阶三和弦", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    diatonicChords.forEach { chord ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(chord.romanNumeral, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(chord.displayName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(chord.noteNames.joinToString("·"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 近关系调 ──
            Text("近关系调", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                relatedKeys.forEach { key ->
                    SuggestionChip(
                        onClick = { selectedKey = key },
                        label = { Text(CircleOfFifthsEngine.tonicName(key)) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewCircleWheel(
    majorKeys: List<CircleKey>,
    minorKeys: List<CircleKey>,
    selectedKey: CircleKey,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    val majorNames = remember(majorKeys) { majorKeys.associateWith { CircleOfFifthsEngine.tonicName(it) } }
    val minorNames = remember(minorKeys) { minorKeys.associateWith { CircleOfFifthsEngine.tonicName(it).lowercase() } }

    val sizeDp = WHEEL_SIZE_DP.dp

    Box(
        modifier = modifier.fillMaxWidth().height(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = min(cx, cy)
            val majorInnerR = outerR * 0.68f
            val minorInnerR = outerR * 0.42f
            val centerR = minorInnerR

            for (pos in 0..11) {
                val startAngle = pos * 30f - 90f - 15f
                val sweep = 30f
                val majorColor = when (pos) {
                    0 -> primaryContainer
                    in 1..6 -> primaryContainer.copy(alpha = 0.6f - pos * 0.04f)
                    else -> surfaceVariant.copy(alpha = 0.65f)
                }
                val minorColor = when (pos) {
                    0 -> secondary.copy(alpha = 0.35f)
                    in 1..6 -> secondary.copy(alpha = 0.25f - pos * 0.02f)
                    else -> surfaceVariant.copy(alpha = 0.45f)
                }
                drawArc(color = majorColor, startAngle = startAngle, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - outerR, cy - outerR), size = GeometrySize(outerR * 2, outerR * 2))
                drawArc(color = minorColor, startAngle = startAngle, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - majorInnerR, cy - majorInnerR), size = GeometrySize(majorInnerR * 2, majorInnerR * 2))
                drawArc(color = surface, startAngle = startAngle, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(cx - minorInnerR, cy - minorInnerR), size = GeometrySize(minorInnerR * 2, minorInnerR * 2))
            }

            // 分隔线
            for (pos in 0..11) {
                val angle = Math.toRadians((pos * 30.0 - 90.0 - 15.0))
                val x1 = cx + (centerR * cos(angle)).toFloat()
                val y1 = cy + (centerR * sin(angle)).toFloat()
                val x2 = cx + (outerR * cos(angle)).toFloat()
                val y2 = cy + (outerR * sin(angle)).toFloat()
                drawLine(color = surface, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 1.5f)
            }

            drawCircle(color = surfaceVariant, radius = centerR, center = Offset(cx, cy), style = Stroke(width = 2f))

            // 选中扇区高亮描边
            val selPos = CircleOfFifthsEngine.positionOf(selectedKey)
            val isMajorSel = selectedKey.isMajor
            val highlightR = if (isMajorSel) outerR else majorInnerR
            val highlightInner = if (isMajorSel) majorInnerR else minorInnerR
            val selStart = selPos * 30f - 90f - 15f
            drawArc(color = if (isMajorSel) primary else secondary, startAngle = selStart, sweepAngle = 30f, useCenter = false,
                topLeft = Offset(cx - highlightR, cy - highlightR), size = GeometrySize(highlightR * 2, highlightR * 2), style = Stroke(width = 4f))
            drawArc(color = if (isMajorSel) primary else secondary, startAngle = selStart, sweepAngle = 30f, useCenter = false,
                topLeft = Offset(cx - highlightInner, cy - highlightInner), size = GeometrySize(highlightInner * 2, highlightInner * 2), style = Stroke(width = 4f))
        }

        // 大调标签
        for (pos in 0..11) {
            val majorKey = majorKeys.getOrNull(pos) ?: continue
            val name = majorNames[majorKey] ?: continue
            val isSel = majorKey == selectedKey
            val angleRad = Math.toRadians((pos * 30.0))
            val tx = WHEEL_HALF_DP + MAJOR_LABEL_R_DP * sin(angleRad)
            val ty = WHEEL_HALF_DP - MAJOR_LABEL_R_DP * cos(angleRad)
            Text(
                text = name,
                fontSize = if (isSel) 16.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSel) surface else onSurface,
                modifier = Modifier.offset(x = (tx - 14).dp, y = (ty - 10).dp)
            )
        }
        // 小调标签
        for (pos in 0..11) {
            val minorKey = minorKeys.getOrNull(pos) ?: continue
            val name = minorNames[minorKey] ?: continue
            val isSel = minorKey == selectedKey
            val angleRad = Math.toRadians((pos * 30.0))
            val tx = WHEEL_HALF_DP + MINOR_LABEL_R_DP * sin(angleRad)
            val ty = WHEEL_HALF_DP - MINOR_LABEL_R_DP * cos(angleRad)
            Text(
                text = name,
                fontSize = if (isSel) 13.sp else 11.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                color = if (isSel) surface else onSurface.copy(alpha = 0.7f),
                modifier = Modifier.offset(x = (tx - 10).dp, y = (ty - 8).dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  3. 识谱训练 (Note Reading Trainer) - 设置面板
// ════════════════════════════════════════════════════════════

@Composable
fun NoteReadingPreviewContent() {
    var clef by remember { mutableStateOf(NoteReadingClef.TREBLE) }
    var difficulty by remember { mutableStateOf(NoteReadingDifficulty.BEGINNER) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📖 识谱训练", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 选择谱号 ──
            Text("选择谱号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = clef == NoteReadingClef.TREBLE, onClick = { clef = NoteReadingClef.TREBLE },
                    label = { Text("𝄞 高音谱号") })
                FilterChip(selected = clef == NoteReadingClef.BASS, onClick = { clef = NoteReadingClef.BASS },
                    label = { Text("𝄢 低音谱号") })
            }

            // ── 选择难度 ──
            Text("选择难度", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = difficulty == NoteReadingDifficulty.BEGINNER, onClick = { difficulty = NoteReadingDifficulty.BEGINNER },
                    label = { Text("初级") })
                FilterChip(selected = difficulty == NoteReadingDifficulty.INTERMEDIATE, onClick = { difficulty = NoteReadingDifficulty.INTERMEDIATE },
                    label = { Text("中级") })
                FilterChip(selected = difficulty == NoteReadingDifficulty.ADVANCED, onClick = { difficulty = NoteReadingDifficulty.ADVANCED },
                    label = { Text("高级") })
            }

            // ── 五线谱预览 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEF7))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PreviewStaff(
                        clef = clef,
                        noteSteps = listOf(2), // 中央 C 在高音谱号的位置
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                }
            }

            // ── 开始练习按钮 ──
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("开始练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // ── 说明卡片 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 使用说明", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• 选择谱号和难度后点击「开始练习」", fontSize = 13.sp)
                    Text("• 五线谱上会显示一个音符，从4个选项中选择正确的音名", fontSize = 13.sp)
                    Text("• 答题后可点击播放按钮听音符验证", fontSize = 13.sp)
                    Text("• 进度自动保存，支持跨会话练习", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  4. 音程训练 (Interval Trainer) - 设置面板
// ════════════════════════════════════════════════════════════

@Composable
fun IntervalTrainerPreviewContent() {
    var clef by remember { mutableStateOf(IntervalClef.TREBLE) }
    var difficulty by remember { mutableStateOf(IntervalDifficulty.BEGINNER) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("📐 音程训练", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 选择谱号 ──
            Text("选择谱号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = clef == IntervalClef.TREBLE, onClick = { clef = IntervalClef.TREBLE },
                    label = { Text("𝄞 高音谱号") })
                FilterChip(selected = clef == IntervalClef.BASS, onClick = { clef = IntervalClef.BASS },
                    label = { Text("𝄢 低音谱号") })
            }

            // ── 选择难度 ──
            Text("选择难度", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = difficulty == IntervalDifficulty.BEGINNER, onClick = { difficulty = IntervalDifficulty.BEGINNER },
                    label = { Text("初级") })
                FilterChip(selected = difficulty == IntervalDifficulty.INTERMEDIATE, onClick = { difficulty = IntervalDifficulty.INTERMEDIATE },
                    label = { Text("中级") })
                FilterChip(selected = difficulty == IntervalDifficulty.ADVANCED, onClick = { difficulty = IntervalDifficulty.ADVANCED },
                    label = { Text("高级") })
            }

            // ── 五线谱预览（双音符） ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEF7))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PreviewStaff(
                        clef = if (clef == IntervalClef.TREBLE) NoteReadingClef.TREBLE else NoteReadingClef.BASS,
                        noteSteps = listOf(2, 5), // 两个音符
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            }

            // ── 开始练习按钮 ──
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("开始练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // ── 说明卡片 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 使用说明", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("• 选择谱号和难度后点击「开始练习」", fontSize = 13.sp)
                    Text("• 五线谱上会显示两个音符，判断它们之间的音程", fontSize = 13.sp)
                    Text("• 初级仅判断度数；中/高级还需判断性质（大/小/纯/增/减）", fontSize = 13.sp)
                    Text("• 旋律性播放：先播低音再播高音", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  五线谱绘制组件
// ════════════════════════════════════════════════════════════

@Composable
private fun PreviewStaff(
    clef: NoteReadingClef,
    noteSteps: List<Int>,
    modifier: Modifier = Modifier
) {
    val staffLineColor = Color(0xFF333333)
    val noteColor = Color(0xFF1A1A1A)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val lineSpacing = h / 12f // 五线谱线间距
        val staffTop = centerY - 2 * lineSpacing
        val staffBottom = centerY + 2 * lineSpacing

        // 5条线
        for (i in 0..4) {
            val y = staffTop + i * lineSpacing
            drawLine(
                color = staffLineColor,
                start = Offset(w * 0.1f, y),
                end = Offset(w * 0.9f, y),
                strokeWidth = 1.5f
            )
        }

        // 谱号符号
        val clefSymbol = if (clef == NoteReadingClef.TREBLE) "𝄞" else "𝄢"
        drawContext.canvas.nativeCanvas.drawText(
            clefSymbol,
            w * 0.15f,
            staffBottom + lineSpacing * 0.3f,
            android.graphics.Paint().apply {
                textSize = lineSpacing * 4.5f
                color = android.graphics.Color.argb(200, 26, 26, 26)
                textAlign = android.graphics.Paint.Align.LEFT
            }
        )

        // 音符
        val noteAreaStart = w * 0.45f
        val noteAreaWidth = w * 0.4f
        val noteSpacing = if (noteSteps.size > 1) noteAreaWidth / (noteSteps.size + 1) else noteAreaWidth / 2

        noteSteps.forEachIndexed { index, step ->
            val noteX = noteAreaStart + noteSpacing * (index + 1)
            // step=0 is middle line (staff line index 2), higher step = higher on staff
            val noteY = centerY - step * lineSpacing / 2f

            // 加线 (if outside staff)
            if (noteY < staffTop - lineSpacing * 0.5f) {
                var lineY = staffTop - lineSpacing
                while (lineY >= noteY - lineSpacing * 0.5f) {
                    drawLine(
                        color = staffLineColor,
                        start = Offset(noteX - lineSpacing * 0.7f, lineY),
                        end = Offset(noteX + lineSpacing * 0.7f, lineY),
                        strokeWidth = 1.5f
                    )
                    lineY -= lineSpacing
                }
            }
            if (noteY > staffBottom + lineSpacing * 0.5f) {
                var lineY = staffBottom + lineSpacing
                while (lineY <= noteY + lineSpacing * 0.5f) {
                    drawLine(
                        color = staffLineColor,
                        start = Offset(noteX - lineSpacing * 0.7f, lineY),
                        end = Offset(noteX + lineSpacing * 0.7f, lineY),
                        strokeWidth = 1.5f
                    )
                    lineY += lineSpacing
                }
            }

            // 音符头 (椭圆)
            val noteWidth = lineSpacing * 1.1f
            val noteHeight = lineSpacing * 0.8f
            drawOval(
                color = noteColor,
                topLeft = Offset(noteX - noteWidth / 2, noteY - noteHeight / 2),
                size = GeometrySize(noteWidth, noteHeight)
            )

            // 符干
            val stemX = noteX + noteWidth / 2
            drawLine(
                color = noteColor,
                start = Offset(stemX, noteY),
                end = Offset(stemX, noteY - lineSpacing * 3f),
                strokeWidth = 2f
            )
        }
    }
}


