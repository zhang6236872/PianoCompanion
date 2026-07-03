package com.pianocompanion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test: verify Paparazzi/Robolectric layoutlib emoji + CJK rendering.
 * If emoji render as tofu/missing-glyph boxes, the full screenshot path is unviable
 * for this emoji-heavy app and we fall back to a Chromium-based renderer.
 */
class EmojiSmokeTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = app.cash.paparazzi.DeviceConfig.PIXEL_5
    )

    @Test
    fun renderEmojiAndCjk() {
        paparazzi.snapshot {
            MaterialTheme {
                Surface(color = Color.White) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("🎼 乐谱库", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("🎡 五度圈 🎲 视奏 👂 听音 🥁 节奏")
                        Text("🎹 和弦 🎶 音阶 📐 音程 📁 乐谱")
                        Text("💡 提示 📊 统计 ✅ ❌ ⚠️")
                        Text("C大调 F♯ B♭ 𝄞 𝄢")
                    }
                }
            }
        }
    }
}
