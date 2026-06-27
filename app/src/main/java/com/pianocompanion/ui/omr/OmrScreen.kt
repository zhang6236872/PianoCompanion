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
import com.pianocompanion.omr.RealOmrEngine
import com.pianocompanion.omr.image.ConfidenceLevel
import com.pianocompanion.omr.image.RecognitionQuality
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
        val error: String? = null,
        val exportStatus: String? = null
    )

    private val _uiState = MutableStateFlow(OmrUiState())
    val uiState: StateFlow<OmrUiState> = _uiState.asStateFlow()

    private val engine: OmrEngine = RealOmrEngine()
    private val exporter = com.pianocompanion.data.parser.MusicXmlExporter()
    private val midiExporter = com.pianocompanion.data.parser.MidiExporter()

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

    /**
     * 将当前识别到的乐谱导出为 MusicXML，写入 SAF 返回的 [uri]。
     * 在 IO 线程执行序列化与文件写入，更新 [OmrUiState.exportStatus]。
     */
    fun exportRecognizedScore(uri: Uri) {
        val score = getRecognizedScore()
        if (score == null) {
            _uiState.update { it.copy(exportStatus = "没有可导出的乐谱") }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val xml = exporter.export(score)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(xml.toByteArray(Charsets.UTF_8))
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            _uiState.update {
                it.copy(exportStatus = if (ok) "✅ 已导出 MusicXML" else "❌ 导出失败")
            }
        }
    }

    /**
     * 将当前识别到的乐谱导出为标准 MIDI 文件 (.mid)，写入 SAF 返回的 [uri]。
     * 在 IO 线程执行序列化与文件写入，更新 [OmrUiState.exportStatus]。
     * 导出的 MIDI 可在任意 DAW / 媒体播放器 / 数码钢琴中播放，或作为练习伴奏。
     */
    fun exportRecognizedScoreToMidi(uri: Uri) {
        val score = getRecognizedScore()
        if (score == null) {
            _uiState.update { it.copy(exportStatus = "没有可导出的乐谱") }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bytes = midiExporter.export(score)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            _uiState.update {
                it.copy(exportStatus = if (ok) "✅ 已导出 MIDI" else "❌ 导出失败")
            }
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

    // SAF 创建文档：用于导出 MusicXML
    val createXmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        if (uri != null) viewModel.exportRecognizedScore(uri)
    }

    // SAF 创建文档：用于导出 MIDI
    val createMidiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/midi")
    ) { uri ->
        if (uri != null) viewModel.exportRecognizedScoreToMidi(uri)
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
                val exportAction: (() -> Unit)? = when (result) {
                    is OmrResult.Success, is OmrResult.PartialSuccess -> {
                        {
                            val score = viewModel.getRecognizedScore()
                            val name = (score?.title?.takeIf { it.isNotBlank() } ?: "score") + ".xml"
                            createXmlLauncher.launch(name)
                        }
                    }
                    else -> null
                }
                val exportMidiAction: (() -> Unit)? = when (result) {
                    is OmrResult.Success, is OmrResult.PartialSuccess -> {
                        {
                            val score = viewModel.getRecognizedScore()
                            val name = (score?.title?.takeIf { it.isNotBlank() } ?: "score") + ".mid"
                            createMidiLauncher.launch(name)
                        }
                    }
                    else -> null
                }
                when (result) {
                    is OmrResult.Success -> {
                        ResultCard(
                            title = "✅ 识别成功！",
                            color = Color(0xFF4CAF50),
                            score = result.score,
                            warnings = emptyList(),
                            quality = result.quality,
                            onPractice = { onScoreRecognized(result.score) },
                            onExport = exportAction,
                            onExportMidi = exportMidiAction
                        )
                    }
                    is OmrResult.PartialSuccess -> {
                        ResultCard(
                            title = "⚠️ 识别完成（部分结果）",
                            color = Color(0xFFFFA726),
                            score = result.score,
                            warnings = result.warnings,
                            quality = result.quality,
                            onPractice = { onScoreRecognized(result.score) },
                            onExport = exportAction,
                            onExportMidi = exportMidiAction
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

                // 导出状态提示
                uiState.exportStatus?.let { status ->
                    Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
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
                    val infoLines = remember {
                        listOf(
                            "真实本地识谱引擎：Otsu 二值化 → 五线谱检测/去除",
                            "→ 音符定位 → 音高映射 → 符干/横梁节奏分析（离线）",
                            "建议拍摄端正、高对比度、高分辨率的乐谱图片。",
                            "节奏已支持全/二/四/八/十六分音符估算，复杂节奏需人工校对。",
                            "支持图片：JPG / PNG。"
                        )
                    }
                    infoLines.forEach { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
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
    quality: RecognitionQuality? = null,
    onPractice: () -> Unit,
    onExport: (() -> Unit)? = null,
    onExportMidi: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)

            // 识别置信度卡片
            quality?.let { q ->
                Spacer(Modifier.height(8.dp))
                val qualityColor = when (q.level) {
                    ConfidenceLevel.HIGH -> Color(0xFF4CAF50)
                    ConfidenceLevel.MEDIUM -> Color(0xFFFFA726)
                    ConfidenceLevel.LOW -> Color(0xFFEF5350)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = qualityColor.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "🎯 识别置信度：${q.percentString}（${q.level.displayName}）",
                            fontWeight = FontWeight.Bold,
                            color = qualityColor,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(q.summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // 展示关键评估因子明细
                        q.factors.take(3).forEach { factor ->
                            Text(
                                "• ${factor.name}：${factor.detail}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

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
            // 导出 MusicXML
            if (onExport != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出 MusicXML")
                }
            }
            // 导出 MIDI
            if (onExportMidi != null) {
                OutlinedButton(
                    onClick = onExportMidi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出 MIDI")
                }
            }
        }
    }
}
