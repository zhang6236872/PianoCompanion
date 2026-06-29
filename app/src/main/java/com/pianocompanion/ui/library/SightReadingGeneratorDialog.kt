package com.pianocompanion.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pianocompanion.data.model.Staff
import com.pianocompanion.generator.SightReadingDifficulty
import com.pianocompanion.generator.SightReadingGenerator
import com.pianocompanion.omr.image.KeySignature

/**
 * 视奏练习生成器对话框。
 *
 * 让用户选择调号、难度、小节数、拍号、速度、谱号，然后调用
 * [SightReadingGenerator] 生成一份练习乐谱，通过 [onGenerate] 回调交回
 * 调用方（乐谱库页面会将其存入 [ScoreSelectionHolder] 并导航到练习页）。
 *
 * 生成器引擎（[SightReadingGenerator]）已完整覆盖单元测试，本对话框仅负责
 * 选项收集与 UI 展示。
 *
 * @param onGenerate 生成乐谱后的回调，参数为新生成的 [com.pianocompanion.data.model.Score]
 * @param onDismiss 关闭对话框（取消 / 生成后）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SightReadingGeneratorDialog(
    onGenerate: (com.pianocompanion.data.model.Score) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf(SightReadingDialogState()) }
    val generator = remember { SightReadingGenerator() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // === 标题 ===
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎹", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "视奏练习生成器",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "选择参数，自动生成练习乐谱",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider()

                // === 调号 ===
                LabeledDropdown(
                    label = "调号",
                    selectedLabel = state.keySignature.label,
                    options = SightReadingKeys.common.map { it.label },
                    onSelected = { label ->
                        state = state.copy(
                            keySignature = SightReadingKeys.common.first { it.label == label }
                        )
                    }
                )

                // === 拍号 ===
                LabeledDropdown(
                    label = "拍号",
                    selectedLabel = state.timeSignature,
                    options = SightReadingKeys.timeSignatures,
                    onSelected = { state = state.copy(timeSignature = it) }
                )

                // === 难度（选择按钮组） ===
                Text("难度", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SightReadingDifficulty.entries.forEach { diff ->
                        val selected = state.difficulty == diff
                        FilterChip(
                            selected = selected,
                            onClick = { state = state.copy(difficulty = diff) },
                            label = { Text(diff.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    "${state.difficulty.rangeSemitones} 半音音域 · 最大跳进 ${state.difficulty.maxLeap} 级",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )

                // === 小节数 ===
                StepperRow(
                    label = "小节数",
                    value = state.measures,
                    options = SightReadingKeys.measures,
                    onValueChange = { state = state.copy(measures = it) }
                )

                // === 谱号（高音/低音） ===
                Text("谱号", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.staff == Staff.TREBLE,
                        onClick = { state = state.copy(staff = Staff.TREBLE) },
                        label = { Text("高音谱号") }
                    )
                    FilterChip(
                        selected = state.staff == Staff.BASS,
                        onClick = { state = state.copy(staff = Staff.BASS) },
                        label = { Text("低音谱号") }
                    )
                }

                // === 速度 ===
                Text(
                    "速度  ${state.tempo} BPM",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = state.tempo.toFloat(),
                    onValueChange = { state = state.copy(tempo = it.toInt()) },
                    valueRange = 40f..200f,
                    steps = 0
                )

                HorizontalDivider()

                // === 操作按钮 ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            val score = generator.generate(state.toOptions())
                            onGenerate(score)
                        },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("生成并练习")
                    }
                }
            }
        }
    }
}

// ── 内部可复用小组件 ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp)
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onSelected(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit
) {
    val idx = options.indexOf(value).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp)
        )
        IconButton(onClick = { if (idx > 0) onValueChange(options[idx - 1]) }) {
            Text("−", fontSize = 22.sp)
        }
        Text(
            "$value",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = { if (idx < options.lastIndex) onValueChange(options[idx + 1]) }) {
            Text("+", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("小节", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
