package com.pianocompanion.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var autoPageTurn by remember { mutableStateOf(true) }
    var errorDetection by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var latencyMode by remember { mutableStateOf("标准") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(16.dp)
        ) {
            Text("练习设置", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            ListItem(
                headlineContent = { Text("自动翻页") },
                supportingContent = { Text("根据弹奏进度自动翻页") },
                trailingContent = {
                    Switch(checked = autoPageTurn, onCheckedChange = { autoPageTurn = it })
                }
            )

            ListItem(
                headlineContent = { Text("弹奏纠错") },
                supportingContent = { Text("实时检测弹错的音") },
                trailingContent = {
                    Switch(checked = errorDetection, onCheckedChange = { errorDetection = it })
                }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("音频设置", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Text("麦克风灵敏度: ${(sensitivity * 100).toInt()}%")
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0f..1f
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("延迟模式")
            Row {
                listOf("低延迟", "标准", "高精度").forEach { mode ->
                    FilterChip(
                        selected = latencyMode == mode,
                        onClick = { latencyMode = mode },
                        label = { Text(mode) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Piano Companion v1.0.0-alpha\n基于 YIN 音高检测 + 在线 DTW 琴谱跟踪\n完全离线运行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
