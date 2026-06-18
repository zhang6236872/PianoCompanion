package com.pianocompanion.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.pianocompanion.following.DtwConfig
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: android.content.Context = LocalContext.current,
    viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(context.applicationContext as Application) as T
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val dtw = uiState.dtwConfig

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("⚙️ 设置", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // === 翻页设置 ===
            SettingsSection("📜 翻页") {
                SwitchItem("自动翻页", "根据演奏进度自动翻页", uiState.autoPageTurn) { viewModel.setAutoPageTurn(it) }
                HorizontalDivider()
                SwitchItem("翻页提前量", "在末尾音符前提前翻页", true) {}
            }

            // === 识音设置 ===
            SettingsSection("🎵 识音") {
                SwitchItem("弹错音反馈", "弹错时立即提醒", uiState.errorFeedback) { viewModel.setErrorFeedback(it) }
                HorizontalDivider()
                SwitchItem("错误音效", "弹错时播放提示音", uiState.soundOnWrong) { viewModel.setSoundOnWrong(it) }
                HorizontalDivider()
                SwitchItem("振动反馈", "弹错时手机振动", uiState.hapticFeedback) { viewModel.setHapticFeedback(it) }
                HorizontalDivider()
                SliderItem(
                    title = "麦克风灵敏度",
                    subtitle = "调整音符检测阈值",
                    value = uiState.micSensitivity,
                    range = 0.2f..1f,
                    suffix = "",
                    format = { "%.0f%%" }
                ) { viewModel.setMicSensitivity(it) }
            }

            // === DTW 高级参数 ===
            SettingsSection("🔧 对齐引擎 (DTW)") {
                // Preset chips
                Text("  预设模式", fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(start=4.dp, top=8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip("入门", DtwConfig.RELAXED == dtw) { viewModel.applyPreset(DtwConfig.RELAXED) }
                    PresetChip("标准", DtwConfig.DEFAULT == dtw) { viewModel.applyPreset(DtwConfig.DEFAULT) }
                    PresetChip("严格", DtwConfig.STRICT == dtw) { viewModel.applyPreset(DtwConfig.STRICT) }
                }
                HorizontalDivider()
                SliderItem(
                    title = "搜索窗口",
                    subtitle = "越大越容错，但更慢",
                    value = dtw.searchWindow.toFloat(),
                    range = 10f..100f,
                    suffix = "",
                    format = { "%.0f" }
                ) { viewModel.updateDtwConfig { c -> c.copy(searchWindow = it.toInt()) } }
                HorizontalDivider()
                SliderItem(
                    title = "音准容差",
                    subtitle = "允许的半音偏差",
                    value = dtw.pitchTolerance.toFloat(),
                    range = 0f..3f,
                    steps = 2,
                    suffix = "半音"
                ) { viewModel.updateDtwConfig { c -> c.copy(pitchTolerance = it.toInt()) } }
                HorizontalDivider()
                SliderItem(
                    title = "多弹惩罚",
                    subtitle = "多弹音的代价（越高越严格）",
                    value = dtw.insertCost,
                    range = 0.5f..2f,
                    suffix = "",
                    format = { "%.1f" }
                ) { viewModel.updateDtwConfig { c -> c.copy(insertCost = it) } }
                HorizontalDivider()
                SliderItem(
                    title = "漏弹惩罚",
                    subtitle = "漏弹音的代价（越高越严格）",
                    value = dtw.deleteCost,
                    range = 0.5f..2f,
                    suffix = "",
                    format = { "%.1f" }
                ) { viewModel.updateDtwConfig { c -> c.copy(deleteCost = it) } }
            }

            // === 关于 ===
            SettingsSection("ℹ️ 关于") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Piano Companion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("版本 1.3.0-dev", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("智能钢琴练习助手 — 自动翻页 + 弹奏纠错", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
    Card { Column(content = content) }
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderItem(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    suffix: String = "",
    format: (Float) -> String = { "%.0f" },
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            Text("${format(value)}$suffix", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun RowScope.PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
