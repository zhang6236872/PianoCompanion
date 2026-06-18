package com.pianocompanion.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.pianocompanion.following.DtwConfig

/**
 * Persists user settings including DTW configuration, audio sensitivity,
 * and practice preferences via SharedPreferences.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("piano_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_DTW_CONFIG = "dtw_config"
        private const val KEY_AUTO_PAGE_TURN = "auto_page_turn"
        private const val KEY_ERROR_FEEDBACK = "error_feedback"
        private const val KEY_SOUND_ON_WRONG = "sound_on_wrong"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_PRACTICE_MODE = "practice_mode"
    }

    // === DTW Config ===
    fun getDtwConfig(): DtwConfig {
        val json = prefs.getString(KEY_DTW_CONFIG, null) ?: return DtwConfig.DEFAULT
        return try {
            gson.fromJson(json, DtwConfig::class.java) ?: DtwConfig.DEFAULT
        } catch (e: Exception) {
            DtwConfig.DEFAULT
        }
    }

    fun saveDtwConfig(config: DtwConfig) {
        prefs.edit().putString(KEY_DTW_CONFIG, gson.toJson(config)).apply()
    }

    // === Booleans ===
    var autoPageTurn: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PAGE_TURN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PAGE_TURN, value).apply()

    var errorFeedback: Boolean
        get() = prefs.getBoolean(KEY_ERROR_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_ERROR_FEEDBACK, value).apply()

    var soundOnWrong: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ON_WRONG, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ON_WRONG, value).apply()

    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    // === Floats ===
    var micSensitivity: Float
        get() = prefs.getFloat(KEY_MIC_SENSITIVITY, 0.6f)
        set(value) = prefs.edit().putFloat(KEY_MIC_SENSITIVITY, value).apply()

    // === Practice Mode ===
    var practiceMode: String
        get() = prefs.getString(KEY_PRACTICE_MODE, "NORMAL") ?: "NORMAL"
        set(value) = prefs.edit().putString(KEY_PRACTICE_MODE, value).apply()
}
