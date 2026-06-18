package com.pianocompanion.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pianocompanion.ui.metronome.MetronomeViewModel
import com.pianocompanion.ui.stats.StatsViewModel

class AppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(MetronomeViewModel::class.java) ->
                MetronomeViewModel(application) as T
            modelClass.isAssignableFrom(StatsViewModel::class.java) ->
                StatsViewModel(application) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
