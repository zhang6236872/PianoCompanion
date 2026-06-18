package com.pianocompanion.ui.omr

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.pianocompanion.data.model.Score
import com.pianocompanion.omr.ImagePreprocessor
import com.pianocompanion.omr.OmrEngine
import com.pianocompanion.omr.OmrResult
import com.pianocompanion.omr.StubOmrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OmrViewModel(application: Application) : AndroidViewModel(application) {

    data class OmrUiState(
        val isLoading: Boolean = false,
        val capturedBitmap: Bitmap? = null,
        val processedBitmap: Bitmap? = null,
        val result: OmrResult? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(OmrUiState())
    val uiState: StateFlow<OmrUiState> = _uiState.asStateFlow()

    private val engine: OmrEngine = StubOmrEngine()

    fun processImage(uri: Uri) {
        val context = getApplication<Application>()
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(input).also { input?.close() }
                }

                if (bitmap == null) {
                    _uiState.update { it.copy(isLoading = false, error = "无法读取图片") }
                    return@launch
                }

                // Preprocess image
                val grayscale = ImagePreprocessor.toGrayscale(bitmap)
                val binary = ImagePreprocessor.binarize(grayscale)

                _uiState.update {
                    it.copy(capturedBitmap = bitmap, processedBitmap = binary)
                }

                // Run OMR
                val result = engine.recognize(bitmap)
                _uiState.update { it.copy(isLoading = false, result = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "处理失败: ${e.message}") }
            }
        }
    }

    fun getRecognizedScore(): Score? {
        return when (val r = _uiState.value.result) {
            is OmrResult.Success -> r.score
            is OmrResult.PartialSuccess -> r.score
            else -> null
        }
    }

    fun reset() {
        _uiState.value = OmrUiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmrScreen(
    onScoreRecognized: (Score) -> Unit,
    onBack: () -> Unit,
    viewModel: OmrViewModel = run {
        val context = LocalContext.current
        viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return OmrViewModel(context.applicationContext as Application) as T
                }
            }
        )
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.processImage(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📷 拍照识谱", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = uiState.processedBitmap ?: uiState.capturedBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "乐谱图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (uiState.isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("正在识别乐谱...", fontSize = 14.sp, color = Color.Gray)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 64.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("选择一张乐谱图片", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }

            // Action buttons
            if (!uiState.isLoading && uiState.result == null) {
                Button(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("选择乐谱图片")
                }
            }

            // Results
            uiState.result?.let { result ->
                when (result) {
                    is OmrResult.Success -> {
                        ResultCard(
                            title = "✅ 识别成功！",
                            color = Color(0xFF4CAF50),
                            score = result.score,
                            warnings = emptyList(),
                            onPractice = { onScoreRecognized(result.score) }
                        )
                    }
                    is OmrResult.PartialSuccess -> {
                        ResultCard(
                            title = "⚠️ 识别完成（部分结果）",
                            color = Color(0xFFFFA726),
                            score = result.score,
                            warnings = result.warnings,
                            onPractice = { onScoreRecognized(result.score) }
                        )
                    }
                    is OmrResult.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Text(
                                "❌ ${result.message}",
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFFEF5350)
                            )
                        }
                    }
                    OmrResult.Processing -> {}
                }

                TextButton(onClick = { viewModel.reset() }) {
                    Text("重新识别")
                }
            }

            // Error message
            uiState.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔬 OMR 功能说明", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前为占位引擎，返回示例乐谱。\n后续将集成 TFLite 模型实现真实拍照识谱。\n\n支持图片：JPG / PNG，建议高分辨率拍摄。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    color: Color,
    score: Score,
    warnings: List<String>,
    onPractice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("识别到 ${score.notes.size} 个音符", fontSize = 12.sp)
            warnings.forEach { warning ->
                Text("⚠️ $warning", fontSize = 11.sp, color = color.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPractice,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("开始练习")
            }
        }
    }
}
