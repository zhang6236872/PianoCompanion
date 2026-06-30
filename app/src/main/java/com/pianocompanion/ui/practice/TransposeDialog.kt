package com.pianocompanion.ui.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pianocompanion.music.KeyInfo
import com.pianocompanion.music.Transposer

/**
 * 移调对话框。
 *
 * 显示当前乐谱检测到的调性，提供半音步进器（-12 ~ +12）和常用目标调快捷选择，
 * 用户确认后通过 [onTranspose] 回调应用移调。
 *
 * @param detectedKey 当前乐谱检测到的调性（可能为 null）。
 * @param onDismiss 关闭对话框回调。
 * @param onTranspose 确认移调回调，参数为半音数。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransposeDialog(
    detectedKey: KeyInfo?,
    onDismiss: () -> Unit,
    onTranspose: (semitones: Int) -> Unit
) {
    var semitones by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "移调",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 当前调性显示
                if (detectedKey != null) {
                    Text(
                        text = "当前调性: ${detectedKey.displayName}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 半音步进器
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 减少按钮
                    FilledIconButton(
                        onClick = { if (semitones > -12) semitones-- },
                        enabled = semitones > -12,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "降低")
                    }

                    // 当前半音数
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatSemitones(semitones),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (semitones == 0)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "半音",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    // 增加按钮
                    FilledIconButton(
                        onClick = { if (semitones < 12) semitones++ },
                        enabled = semitones < 12,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "升高")
                    }
                }

                // 八度快捷按钮
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { semitones = (semitones - 12).coerceAtLeast(-12) },
                        label = { Text("↓ 八度", fontSize = 12.sp) }
                    )
                    AssistChip(
                        onClick = { semitones = 0 },
                        label = { Text("重置", fontSize = 12.sp) }
                    )
                    AssistChip(
                        onClick = { semitones = (semitones + 12).coerceAtMost(12) },
                        label = { Text("↑ 八度", fontSize = 12.sp) }
                    )
                }

                // 常用目标调
                Text(
                    "常用调",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Transposer.COMMON_KEYS.forEach { key ->
                        FilterChip(
                            selected = semitones == key.semitoneOffset,
                            onClick = { semitones = key.semitoneOffset },
                            label = { Text(key.shortLabel, fontSize = 12.sp) }
                        )
                    }
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onTranspose(semitones)
                            onDismiss()
                        },
                        enabled = semitones != 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("移调")
                    }
                }
            }
        }
    }
}

/** 格式化半音数为显示文本。 */
private fun formatSemitones(semitones: Int): String {
    return if (semitones >= 0) "+$semitones" else "$semitones"
}
