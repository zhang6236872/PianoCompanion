package com.pianocompanion.ui.metronome

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.pianocompanion.audio.Metronome
import com.pianocompanion.audio.MetronomePreset
import com.pianocompanion.audio.MetronomePresetStore
import com.pianocompanion.audio.PresetValidationResult
import com.pianocompanion.audio.Subdivision
import kotlinx.coroutines.flow.*

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    data class MetronomeUiState(
        val isPlaying: Boolean = false,
        val bpm: Int = 120,
        val beatsPerMeasure: Int = 4,
        val currentBeat: Int = -1,
        val subdivision: Subdivision = Subdivision.QUARTER,
        /** 当前子拍点索引（用于细分可视化），未播放时为 -1。 */
        val currentSubClick: Int = -1,
        /** 已保存的节拍器预设列表（按名称排序）。 */
        val presets: List<MetronomePreset> = emptyList(),
        /** 最近一次预设操作的结果提示（用于 Snackbar），null 表示无。 */
        val presetMessage: String? = null,
        /** 当前配置是否与某个已保存预设完全匹配（用于高亮）。 */
        val activePresetName: String? = null,
    )

    private val _uiState = MutableStateFlow(MetronomeUiState())
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private var metronome: Metronome? = null

    private val presetStore = MetronomePresetStore()

    init {
        metronome = Metronome()
        metronome?.onBeat = { beat ->
            _uiState.update { it.copy(currentBeat = beat) }
        }
        loadPresets()
    }

    // ───────────────── 播放控制 ─────────────────

    fun start() {
        metronome?.let { m ->
            m.setBpm(_uiState.value.bpm)
            m.setBeatsPerMeasure(_uiState.value.beatsPerMeasure)
            m.setSubdivision(_uiState.value.subdivision)
            m.start()
            _uiState.update { it.copy(isPlaying = true, currentBeat = -1) }
        }
    }

    fun stop() {
        metronome?.stop()
        _uiState.update { it.copy(isPlaying = false, currentBeat = -1) }
    }

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(40, 240)
        metronome?.setBpm(clamped)
        _uiState.update { it.copy(bpm = clamped, currentBeat = -1) }
        refreshActivePreset()
    }

    fun increaseBpm() = setBpm(_uiState.value.bpm + 1)
    fun decreaseBpm() = setBpm(_uiState.value.bpm - 1)

    fun setBeatsPerMeasure(beats: Int) {
        metronome?.setBeatsPerMeasure(beats)
        _uiState.update { it.copy(beatsPerMeasure = beats, currentBeat = -1) }
        refreshActivePreset()
    }

    /**
     * 设置细分模式。播放中切换时由 [Metronome] 在下一个子拍点生效，
     * UI 立即更新以反映新选择。
     */
    fun setSubdivision(sub: Subdivision) {
        metronome?.setSubdivision(sub)
        _uiState.update { it.copy(subdivision = sub, currentBeat = -1) }
        refreshActivePreset()
    }

    // ───────────────── 预设管理 ─────────────────

    /**
     * 将当前 BPM / 拍号 / 细分保存为命名预设。
     * @return 保存成功返回 true；名称冲突/无效返回 false。
     */
    fun saveCurrentAsPreset(name: String): Boolean {
        val trimmed = name.trim()
        val s = _uiState.value
        val result = presetStore.validate(trimmed, s.bpm, s.beatsPerMeasure)
        if (result !is PresetValidationResult.Ok) {
            _uiState.update { it.copy(presetMessage = validationMessage(result)) }
            return false
        }
        presetStore.save(MetronomePreset(trimmed, s.bpm, s.beatsPerMeasure, s.subdivision))
        persistPresets()
        _uiState.update {
            it.copy(
                presets = presetStore.list(),
                presetMessage = "已保存预设「$trimmed」",
                activePresetName = trimmed,
            )
        }
        return true
    }

    /**
     * 应用一个已保存的预设到当前节拍器。
     */
    fun loadPreset(preset: MetronomePreset) {
        metronome?.setBpm(preset.bpm)
        metronome?.setBeatsPerMeasure(preset.beatsPerMeasure)
        metronome?.setSubdivision(preset.subdivision)
        _uiState.update {
            it.copy(
                bpm = preset.bpm,
                beatsPerMeasure = preset.beatsPerMeasure,
                subdivision = preset.subdivision,
                currentBeat = -1,
                activePresetName = preset.name,
                presetMessage = "已加载预设「${preset.name}」",
            )
        }
    }

    /**
     * 删除一个预设。
     */
    fun deletePreset(name: String) {
        if (presetStore.delete(name)) {
            persistPresets()
            val active = _uiState.value.activePresetName
            _uiState.update {
                it.copy(
                    presets = presetStore.list(),
                    activePresetName = if (active == name) null else active,
                    presetMessage = "已删除预设「$name」",
                )
            }
        }
    }

    /**
     * 重命名一个预设。
     */
    fun renamePreset(oldName: String, newName: String): Boolean {
        val result = presetStore.rename(oldName, newName.trim())
        if (result is PresetValidationResult.Ok) {
            persistPresets()
            _uiState.update {
                it.copy(
                    presets = presetStore.list(),
                    activePresetName = if (it.activePresetName == oldName) newName.trim() else it.activePresetName,
                    presetMessage = "已重命名为「${newName.trim()}」",
                )
            }
            return true
        }
        _uiState.update { it.copy(presetMessage = validationMessage(result)) }
        return false
    }

    /**
     * 消费（清除）当前预设提示消息。
     */
    fun consumePresetMessage() {
        _uiState.update { it.copy(presetMessage = null) }
    }

    /**
     * 从持久化加载预设；首次启动若为空则写入内置默认预设。
     */
    private fun loadPresets() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PRESETS_KEY, null)
        val count = presetStore.fromJson(json)
        if (count == 0 && presetStore.size == 0) {
            // 首次启动：写入默认预设
            presetStore.replaceAll(MetronomePreset.defaults())
            persistPresetsInternal(prefs)
        }
        _uiState.update { it.copy(presets = presetStore.list()) }
        refreshActivePreset()
    }

    private fun persistPresets() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        persistPresetsInternal(prefs)
    }

    private fun persistPresetsInternal(prefs: android.content.SharedPreferences) {
        prefs.edit().putString(PRESETS_KEY, presetStore.toJson()).apply()
    }

    /**
     * 检测当前配置是否与某个已保存预设完全匹配。
     */
    private fun refreshActivePreset() {
        val s = _uiState.value
        val match = presetStore.list().find { p ->
            p.bpm == s.bpm && p.beatsPerMeasure == s.beatsPerMeasure && p.subdivision == s.subdivision
        }
        _uiState.update { it.copy(activePresetName = match?.name, presets = presetStore.list()) }
    }

    private fun validationMessage(result: PresetValidationResult): String = when (result) {
        is PresetValidationResult.NameTaken -> "名称「${result.existingName}」已被占用"
        PresetValidationResult.NameBlank -> "预设名称不能为空"
        PresetValidationResult.NameTooLong -> "名称过长（最多 ${MetronomePreset.MAX_NAME_LENGTH} 字符）"
        PresetValidationResult.BpmOutOfRange -> "BPM 超出范围"
        PresetValidationResult.BeatsOutOfRange -> "拍号超出范围"
        PresetValidationResult.Ok -> ""
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    companion object {
        private const val PREFS_NAME = "metronome_presets"
        private const val PRESETS_KEY = "presets_json"
    }
}
