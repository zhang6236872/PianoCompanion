package com.pianocompanion.data.model

data class PracticeSession(
    val id: String,
    val scoreId: String,
    val scoreTitle: String,
    val startTime: Long,
    val durationMs: Long = 0,
    val totalNotes: Int = 0,
    val correctNotes: Int = 0,
    val wrongNotes: Int = 0,
    val missedNotes: Int = 0,
    val extraNotes: Int = 0,
    val errorPositions: List<ErrorPosition> = emptyList()
) {
    val accuracy: Float
        get() = if (totalNotes > 0) correctNotes.toFloat() / totalNotes else 0f
}

data class ErrorPosition(
    val measureIndex: Int,
    val expectedNote: String,
    val detectedNote: String,
    val errorType: MatchStatus,
    val timestamp: Long
)
