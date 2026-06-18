package com.pianocompanion.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PracticeViewModelFactory(
    private val application: android.app.Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PracticeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
