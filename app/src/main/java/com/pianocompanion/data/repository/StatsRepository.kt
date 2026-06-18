package com.pianocompanion.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pianocompanion.data.model.AggregatedStats
import com.pianocompanion.data.model.SessionRecord

/**
 * Stores practice session records using SharedPreferences + Gson.
 * Lightweight persistence without Room database overhead.
 */
class StatsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "piano_companion_stats"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_STREAK = "current_streak"
        private const val KEY_LAST_PRACTICE_DAY = "last_practice_day"
    }

    fun saveSession(record: SessionRecord) {
        val sessions = getAllSessions().toMutableList()
        sessions.add(0, record)  // newest first

        // Keep only last 200 sessions
        if (sessions.size > 200) {
            sessions.subList(200, sessions.size).clear()
        }

        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()

        // Update streak
        updateStreak()
    }

    fun getAllSessions(): List<SessionRecord> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SessionRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRecentSessions(limit: Int = 20): List<SessionRecord> {
        return getAllSessions().take(limit)
    }

    fun getAggregatedStats(): AggregatedStats {
        val sessions = getAllSessions()
        if (sessions.isEmpty()) {
            return AggregatedStats(0, 0, 0, 0f, 0f, 0, null, emptyMap())
        }

        val totalTime = sessions.sumOf { it.durationMs }
        val totalNotes = sessions.sumOf { it.totalNotes }
        val avgAccuracy = sessions.map { it.accuracy }.average().toFloat()
        val bestAccuracy = sessions.maxOf { it.accuracy }

        // Most practiced score
        val scoreCounts = sessions.groupingBy { it.scoreTitle }.eachCount()
        val mostPracticed = scoreCounts.maxByOrNull { it.value }?.key

        // Sessions by day
        val byDay = sessions.groupBy { it.getFormattedDate().substring(0, 10) }
            .mapValues { it.value.size }

        return AggregatedStats(
            totalSessions = sessions.size,
            totalPracticeTimeMs = totalTime,
            totalNotesPlayed = totalNotes,
            averageAccuracy = avgAccuracy,
            bestAccuracy = bestAccuracy,
            currentStreak = prefs.getInt(KEY_STREAK, 0),
            mostPracticedScore = mostPracticed,
            sessionsByDay = byDay
        )
    }

    fun clearAllStats() {
        prefs.edit().clear().apply()
    }

    private fun updateStreak() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val lastDay = prefs.getString(KEY_LAST_PRACTICE_DAY, null)
        val currentStreak = prefs.getInt(KEY_STREAK, 0)

        if (lastDay == null) {
            // First ever practice
            prefs.edit().putInt(KEY_STREAK, 1).putString(KEY_LAST_PRACTICE_DAY, today).apply()
        } else if (lastDay != today) {
            // Check if yesterday
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(cal.time)

            val newStreak = if (lastDay == yesterday) currentStreak + 1 else 1
            prefs.edit()
                .putInt(KEY_STREAK, newStreak)
                .putString(KEY_LAST_PRACTICE_DAY, today)
                .apply()
        }
        // Same day, no streak update needed
    }
}
