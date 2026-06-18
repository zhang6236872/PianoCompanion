package com.pianocompanion.ui.practice

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianocompanion.ui.theme.Correct
import com.pianocompanion.ui.theme.Error
import com.pianocompanion.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen() {
    var isPracticing by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(3) }
    var currentMeasure by remember { mutableStateOf(1) }
    var lastNoteStatus by remember { mutableStateOf<String?>(null) }
    var lastNoteColor by remember { mutableStateOf(Color.LightGray) }
    var correctCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var accuracy by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("练习模式") },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Score Display Area (WebView placeholder)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFFEF7)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Piano,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "乐谱显示区域",
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "第 $currentPage / $totalPages 页",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "小节: $currentMeasure",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-time feedback
            AnimatedContent(
                targetState = lastNoteStatus,
                label = "feedback"
            ) { status ->
                if (status != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(lastNoteColor.copy(alpha = 0.2f))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = status,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = lastNoteColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Statistics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("正确", correctCount.toString(), Correct)
                StatCard("错误", wrongCount.toString(), Error)
                StatCard("准确率", "${(accuracy * 100).toInt()}%", if (accuracy > 0.8f) Correct else Warning)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (currentPage > 1) currentPage--
                    },
                    enabled = !isPracticing && currentPage > 1
                ) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = null)
                    Text("上一页")
                }

                Button(
                    onClick = {
                        isPracticing = !isPracticing
                        if (isPracticing) {
                            // Start score following
                            correctCount = 0
                            wrongCount = 0
                            accuracy = 0f
                        }
                    },
                    colors = if (isPracticing) {
                        ButtonDefaults.buttonColors(containerColor = Error)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = Correct)
                    },
                    modifier = Modifier.size(width = 140.dp, height = 56.dp)
                ) {
                    Icon(
                        if (isPracticing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Text(if (isPracticing) "停止" else "开始", fontSize = 18.sp)
                }

                Button(
                    onClick = {
                        if (currentPage < totalPages) currentPage++
                    },
                    enabled = !isPracticing && currentPage < totalPages
                ) {
                    Text("下一页")
                    Icon(Icons.Default.NavigateNext, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        modifier = Modifier.size(width = 100.dp, height = 80.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
