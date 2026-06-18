package com.pianocompanion.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var autoPageTurn by remember { mutableStateOf(true) }
    var errorFeedback by remember { mutableStateOf(true) }
    var soundOnWrong by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableFloatStateOf(0.6f) }
    var tolerance by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("⚙️ 设置", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === 翻页设置 ===
            SectionHeader("📜 翻页")
            Card {
                Column {
                    SwitchItem(
                        title = "自动翻页",
                        subtitle = "根据演奏进度自动翻页",
                        checked = autoPageTurn,
                        onChange = { autoPageTurn = it }
                    )
                    HorizontalDivider()
                    SwitchItem(
                        title = "翻页提前量",
                        subtitle = "在末尾音符前提前翻页",
                        checked = true,
                        onChange = {}
                    )
                }
            }

            // === 识音设置 ===
            SectionHeader("🎵 识音")
            Card {
                Column {
                    SwitchItem(
                        title = "弹错音反馈",
                        subtitle = "弹错时立即提醒",
                        checked = errorFeedback,
                        onChange = { errorFeedback = it }
                    )
                    HorizontalDivider()
                    SwitchItem(
                        title = "错误音效",
                        subtitle = "弹错时播放提示音",
                        checked = soundOnWrong,
                        onChange = { soundOnWrong = it }
                    )
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("麦克风灵敏度", fontWeight = FontWeight.Medium)
                        Text("调整音符检测阈值", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it },
                            valueRange = 0.2f..1f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("低", fontSize = 11.sp)
                            Text("高", fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("音准容差", fontWeight = FontWeight.Medium)
                        Text("允许的音高偏差（半音）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = tolerance,
                            onValueChange = { tolerance = it },
                            valueRange = 0f..3f,
                            steps = 2
                        )
                        Text("\${tolerance.toInt()} 半音", fontSize = 11.sp)
                    }
                }
            }

            // === 关于 ===
            SectionHeader("ℹ️ 关于")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Piano Companion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("版本 1.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("智能钢琴练习助手 - 自动翻页 + 弹奏纠错", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
