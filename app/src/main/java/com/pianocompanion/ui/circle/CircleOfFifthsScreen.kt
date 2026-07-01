package com.pianocompanion.ui.circle

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pianocompanion.circle.*
import kotlin.math.*

/**
 * 五度圈交互工具页面。
 *
 * 功能：
 * - 可视化五度圈圆环（大调外环 + 小调内环），12 个调性按纯五度排列
 * - 点击圆环选择调性
 * - 选中调性的调号、音阶、调内顺阶三和弦（罗马数字分析）
 * - 近关系调展示
 * - 试听顺阶和弦序列 / 音阶
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleOfFifthsScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    viewModel: CircleOfFifthsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CircleOfFifthsViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val info = state.currentKeyInfo

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
            CircleOfFifthsWheel(
                majorKeys = state.majorKeys,
                minorKeys = state.minorKeys,
                selectedKey = state.selectedKey,
                onSelectKey = { viewModel.selectKey(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // ── 调式切换 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = state.selectedMode == CircleMode.MAJOR,
                    onClick = {
                        if (state.selectedMode != CircleMode.MAJOR) viewModel.toggleMode()
                    },
                    label = { Text("大调", fontWeight = FontWeight.Medium) }
                )
                Spacer(Modifier.width(12.dp))
                FilterChip(
                    selected = state.selectedMode == CircleMode.MINOR,
                    onClick = {
                        if (state.selectedMode != CircleMode.MINOR) viewModel.toggleMode()
                    },
                    label = { Text("小调", fontWeight = FontWeight.Medium) }
                )
            }

            // ── 选中调性信息卡 ──
            info?.let { SelectedKeyCard(it) }

            // ── 播放控制 ──
            PlaybackControls(
                isPlaying = state.isPlaying,
                playMode = state.playMode,
                audioReady = state.audioReady,
                onPlay = { viewModel.play() },
                onStop = { viewModel.stopPlayback() },
                onModeChange = { viewModel.selectPlayMode(it) }
            )

            // ── 调内顺阶和弦 ──
            if (state.diatonicChords.isNotEmpty()) {
                SectionTitle("调内顺阶三和弦")
                DiatonicChordsList(state.diatonicChords)
            }

            // ── 近关系调 ──
            if (state.closelyRelatedKeys.isNotEmpty()) {
                SectionTitle("近关系调")
                RelatedKeysRow(
                    keys = state.closelyRelatedKeys,
                    onSelect = { viewModel.selectKey(it) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  五度圈圆环（Canvas + 点击命中检测）
// ════════════════════════════════════════════════════════════

private const val WHEEL_SIZE_DP = 340
private const val WHEEL_HALF_DP = WHEEL_SIZE_DP / 2.0 // 170
// 大调标签半径（外环中点）与小调标签半径（内环中点），单位 dp
private const val MAJOR_LABEL_R_DP = 142.0
private const val MINOR_LABEL_R_DP = 93.0

@Composable
private fun CircleOfFifthsWheel(
    majorKeys: List<CircleKey>,
    minorKeys: List<CircleKey>,
    selectedKey: CircleKey,
    onSelectKey: (CircleKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

    val majorNames = remember(majorKeys) { majorKeys.associateWith { CircleOfFifthsEngine.tonicName(it) } }
    val minorNames = remember(minorKeys) { minorKeys.associateWith { CircleOfFifthsEngine.tonicName(it).lowercase() } }

    val density = LocalDensity.current
    val sizeDp = WHEEL_SIZE_DP.dp
    val sizePx = with(density) { sizeDp.toPx() }

    val selectedKeyParam = selectedKey // 稳定指针输入 key

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        // ── Canvas 绘制彩色扇区 + 分隔线 ──
        Canvas(
            modifier = Modifier
                .size(sizeDp)
                .pointerInput(selectedKeyParam.mode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            if (!change.pressed) continue
                            val x = change.position.x
                            val y = change.position.y
                            val cx = sizePx / 2f
                            val cy = sizePx / 2f
                            val dx = x - cx
                            val dy = y - cy
                            val dist = sqrt(dx * dx + dy * dy)
                            val outerR = sizePx / 2f
                            val innerR = sizePx / 2f * 0.42f
                            val midR = sizePx / 2f * 0.68f
                            if (dist <= outerR && dist >= innerR) {
                                val isMajorRing = dist >= midR
                                // 角度：0° 在顶部，顺时针
                                var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
                                if (angle < 0) angle += 360.0
                                val position = ((angle / 30.0).roundToInt()) % 12
                                val keys = if (isMajorRing) majorKeys else minorKeys
                                val target = keys.getOrNull(position)
                                if (target != null) {
                                    onSelectKey(target)
                                }
                            }
                        }
                    }
                }
        ) {
            drawCircleWedges(
                selectedKey = selectedKey,
                primary = primary,
                primaryContainer = primaryContainer,
                secondary = secondary,
                surfaceVariant = surfaceVariant,
                surface = surface
            )
        }

        // ── 覆盖层：调名文字标签 ──
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
                modifier = Modifier.offset(
                    x = (tx - 14).dp,
                    y = (ty - 10).dp
                )
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
                modifier = Modifier.offset(
                    x = (tx - 10).dp,
                    y = (ty - 8).dp
                )
            )
        }
    }
}

/**
 * 绘制五度圈的彩色扇区、分隔线与中心圆（不含文字，文字由 Compose 覆盖层提供）。
 */
private fun DrawScope.drawCircleWedges(
    selectedKey: CircleKey,
    primary: Color,
    primaryContainer: Color,
    secondary: Color,
    surfaceVariant: Color,
    surface: Color
) {
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

        drawArc(
            color = majorColor,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(cx - outerR, cy - outerR),
            size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
        )
        drawArc(
            color = minorColor,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(cx - majorInnerR, cy - majorInnerR),
            size = androidx.compose.ui.geometry.Size(majorInnerR * 2, majorInnerR * 2)
        )
        drawArc(
            color = surface,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(cx - minorInnerR, cy - minorInnerR),
            size = androidx.compose.ui.geometry.Size(minorInnerR * 2, minorInnerR * 2)
        )
    }

    // 分隔线
    for (pos in 0..11) {
        val angle = Math.toRadians((pos * 30.0 - 90.0 - 15.0))
        val x1 = cx + (centerR * cos(angle)).toFloat()
        val y1 = cy + (centerR * sin(angle)).toFloat()
        val x2 = cx + (outerR * cos(angle)).toFloat()
        val y2 = cy + (outerR * sin(angle)).toFloat()
        drawLine(
            color = surface,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 1.5f
        )
    }

    drawCircle(
        color = surfaceVariant,
        radius = centerR,
        center = Offset(cx, cy),
        style = Stroke(width = 2f)
    )

    // 选中扇区高亮描边
    val selPos = CircleOfFifthsEngine.positionOf(selectedKey)
    val isMajorSel = selectedKey.isMajor
    val highlightR = if (isMajorSel) outerR else majorInnerR
    val highlightInner = if (isMajorSel) majorInnerR else minorInnerR
    val selStart = selPos * 30f - 90f - 15f
    drawArc(
        color = if (isMajorSel) primary else secondary,
        startAngle = selStart,
        sweepAngle = 30f,
        useCenter = false,
        topLeft = Offset(cx - highlightR, cy - highlightR),
        size = androidx.compose.ui.geometry.Size(highlightR * 2, highlightR * 2),
        style = Stroke(width = 4f)
    )
    drawArc(
        color = if (isMajorSel) primary else secondary,
        startAngle = selStart,
        sweepAngle = 30f,
        useCenter = false,
        topLeft = Offset(cx - highlightInner, cy - highlightInner),
        size = androidx.compose.ui.geometry.Size(highlightInner * 2, highlightInner * 2),
        style = Stroke(width = 4f)
    )
}

// ════════════════════════════════════════════════════════════
//  选中调性信息卡 & 其他子组件
// ════════════════════════════════════════════════════════════

@Composable
private fun SelectedKeyCard(info: KeyInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = info.displayName,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            // 调号
            val sigText = when {
                info.signature.isNaturalKey -> "无升降号"
                info.signature.isSharpKey -> "${info.signature.sharpsCount} 个升号：${info.signature.displayString()}"
                else -> "${info.signature.flatsCount} 个降号：${info.signature.displayString()}"
            }
            Text(
                text = sigText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "关系调：${info.relativeDisplayName}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            // 音阶音名
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = "音阶",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = info.scaleNoteNames.joinToString("  "),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    playMode: CirclePlayMode,
    audioReady: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onModeChange: (CirclePlayMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = { if (isPlaying) onStop() else onPlay() },
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            enabled = audioReady
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "停止" else "播放",
                modifier = Modifier.size(28.dp)
            )
        }

        CirclePlayMode.entries.forEach { mode ->
            FilterChip(
                selected = playMode == mode,
                onClick = { onModeChange(mode) },
                label = { Text(mode.displayName, fontSize = 13.sp) }
            )
        }

        if (!audioReady) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun DiatonicChordsList(chords: List<DiatonicChord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chords.forEach { chord ->
                DiatonicChordRow(chord)
            }
        }
    }
}

@Composable
private fun DiatonicChordRow(chord: DiatonicChord) {
    val qualityColor = when (chord.quality) {
        com.pianocompanion.circle.ChordQuality.MAJOR -> MaterialTheme.colorScheme.primary
        com.pianocompanion.circle.ChordQuality.MINOR -> MaterialTheme.colorScheme.tertiary
        com.pianocompanion.circle.ChordQuality.DIMINISHED -> MaterialTheme.colorScheme.error
        com.pianocompanion.circle.ChordQuality.AUGMENTED -> Color(0xFFE91E63)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 罗马数字
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = qualityColor.copy(alpha = 0.12f),
            modifier = Modifier.size(width = 56.dp, height = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = chord.romanNumeral,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
            }
        }
        // 和弦名
        Text(
            text = chord.displayName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(56.dp)
        )
        // 音名
        Text(
            text = chord.noteNames.joinToString("-"),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RelatedKeysRow(
    keys: List<CircleKey>,
    onSelect: (CircleKey) -> Unit
) {
    FlowRowRelatedKeys(keys, onSelect)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowRelatedKeys(
    keys: List<CircleKey>,
    onSelect: (CircleKey) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { key ->
            val name = CircleOfFifthsEngine.tonicName(key)
            val display = if (key.isMajor) "${name}大调" else "${name.lowercase()}小调"
            AssistChip(
                onClick = { onSelect(key) },
                label = { Text(display, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        if (key.isMajor) Icons.Filled.MusicNote else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}
