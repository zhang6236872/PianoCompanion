package com.pianocompanion.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Complete practice session record for statistics tracking.
 */
data class SessionRecord(
    val id: Long = System.currentTimeMillis(),
    val scoreTitle: String,
    val startTime: Long,
    val durationMs: Long,
    val totalNotes: Int,
    val correctNotes: Int,
    val wrongNotes: Int,
    val missedNotes: Int,
    val extraNotes: Int,
    val accuracy: Float,
    val errorPositions: List<ErrorPosition> = emptyList()
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(startTime))
    }

    fun getFormattedDuration(): String {
        val minutes = durationMs / 60000
        val seconds = (durationMs % 60000) / 1000
        return if (minutes > 0) "\${minutes}分\${seconds}秒" else "\${seconds}秒"
    }

    fun getRating(): PracticeRating {
        return when {
            accuracy >= 0.95f -> PracticeRating.PERFECT
            accuracy >= 0.85f -> PracticeRating.EXCELLENT
            accuracy >= 0.70f -> PracticeRating.GOOD
            accuracy >= 0.50f -> PracticeRating.FAIR
            else -> PracticeRating.NEEDS_PRACTICE
        }
    }
}

enum class PracticeRating(val emoji: String, val label: String) {
    PERFECT("🌟", "完美"),
    EXCELLENT("🎉", "优秀"),
    GOOD("👍", "良好"),
    FAIR("💪", "继续努力"),
    NEEDS_PRACTICE("📖", "需要练习")
}

/**
 * Aggregated statistics across all practice sessions.
 */
data class AggregatedStats(
    val totalSessions: Int,
    val totalPracticeTimeMs: Long,
    val totalNotesPlayed: Int,
    val averageAccuracy: Float,
    val bestAccuracy: Float,
    val currentStreak: Int,
    val mostPracticedScore: String?,
    val sessionsByDay: Map<String, Int>  // date string -> session count
) {
    fun getFormattedTotalTime(): String {
        val hours = totalPracticeTimeMs / 3600000
        val minutes = (totalPracticeTimeMs % 3600000) / 60000
        return if (hours > 0) "\${hours}小时\${minutes}分钟" else "\${minutes}分钟"
    }
}
