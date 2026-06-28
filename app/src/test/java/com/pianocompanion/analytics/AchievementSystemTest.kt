package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementSystemTest {

    // ──────────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────────

    companion object {
        private const val DAY_MS = 86_400_000L

        /** 创建一个测试用的 SessionRecord。 */
        private fun session(
            scoreTitle: String = "欢乐颂",
            startTime: Long = System.currentTimeMillis(),
            durationMs: Long = 300_000L, // 5 min
            totalNotes: Int = 50,
            correctNotes: Int = 45,
            accuracy: Float = 0.90f
        ): SessionRecord = SessionRecord(
            scoreTitle = scoreTitle,
            startTime = startTime,
            durationMs = durationMs,
            totalNotes = totalNotes,
            correctNotes = correctNotes,
            wrongNotes = (totalNotes - correctNotes) / 3,
            missedNotes = (totalNotes - correctNotes) / 3,
            extraNotes = (totalNotes - correctNotes) - (totalNotes - correctNotes) / 3 * 2,
            accuracy = accuracy
        )

        /** 创建连续 N 天的练习记录。 */
        private fun consecutiveDaySessions(
            days: Int,
            startOffsetDaysAgo: Int = 0,
            scoreTitle: String = "欢乐颂"
        ): List<SessionRecord> {
            val today = System.currentTimeMillis() / DAY_MS * DAY_MS
            return (0 until days).map { i ->
                val dayOffset = startOffsetDaysAgo + i
                session(
                    scoreTitle = scoreTitle,
                    startTime = today - dayOffset * DAY_MS
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  PracticeProfileBuilder
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `empty sessions returns empty profile`() {
        val profile = PracticeProfileBuilder.fromSessions(emptyList())
        assertEquals(0, profile.totalSessions)
        assertEquals(0, profile.bestStreakDays)
        assertEquals(0, profile.currentStreakDays)
        assertEquals(0L, profile.totalPracticeMs)
        assertEquals(0, profile.totalNotesPlayed)
        assertEquals(0f, profile.bestAccuracy, 0.001f)
        assertEquals(0, profile.distinctScores)
    }

    @Test
    fun `single session aggregates correctly`() {
        val s = session(totalNotes = 50, accuracy = 0.92f, durationMs = 600_000L)
        val profile = PracticeProfileBuilder.fromSessions(listOf(s))
        assertEquals(1, profile.totalSessions)
        assertEquals(600_000L, profile.totalPracticeMs)
        assertEquals(50, profile.totalNotesPlayed)
        assertEquals(0.92f, profile.bestAccuracy, 0.001f)
        assertEquals(1, profile.distinctScores)
    }

    @Test
    fun `multiple sessions aggregate totals`() {
        val sessions = listOf(
            session(scoreTitle = "A", totalNotes = 10, accuracy = 0.80f, durationMs = 60_000L),
            session(scoreTitle = "B", totalNotes = 20, accuracy = 0.90f, durationMs = 120_000L),
            session(scoreTitle = "A", totalNotes = 30, accuracy = 0.95f, durationMs = 180_000L)
        )
        val profile = PracticeProfileBuilder.fromSessions(sessions)
        assertEquals(3, profile.totalSessions)
        assertEquals(360_000L, profile.totalPracticeMs)
        assertEquals(60, profile.totalNotesPlayed)
        assertEquals(0.95f, profile.bestAccuracy, 0.001f)
        assertEquals(2, profile.distinctScores) // A, B
    }

    @Test
    fun `high accuracy counts at thresholds`() {
        val sessions = listOf(
            session(accuracy = 0.89f),  // not 90%
            session(accuracy = 0.90f),  // >= 90%
            session(accuracy = 0.91f),  // >= 90%
            session(accuracy = 0.94f),  // >= 90%, not 95%
            session(accuracy = 0.95f),  // >= 90%, >= 95%
            session(accuracy = 0.99f)   // >= 90%, >= 95%
        )
        val profile = PracticeProfileBuilder.fromSessions(sessions)
        assertEquals(5, profile.highAccuracyCount90)
        assertEquals(2, profile.highAccuracyCount95)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Streak calculations
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `best streak with consecutive days`() {
        val sessions = consecutiveDaySessions(5, startOffsetDaysAgo = 0)
        val best = PracticeProfileBuilder.computeBestStreak(sessions)
        assertEquals(5, best)
    }

    @Test
    fun `best streak with gap`() {
        val today = System.currentTimeMillis() / DAY_MS * DAY_MS
        val sessions = listOf(
            session(startTime = today),                    // day 0 (today)
            session(startTime = today - DAY_MS),           // day 1
            session(startTime = today - 2 * DAY_MS),       // day 2
            // gap on day 3, 4
            session(startTime = today - 5 * DAY_MS),       // day 5
            session(startTime = today - 6 * DAY_MS)        // day 6
        )
        val best = PracticeProfileBuilder.computeBestStreak(sessions)
        assertEquals(3, best)
    }

    @Test
    fun `best streak single day`() {
        val sessions = listOf(session())
        assertEquals(1, PracticeProfileBuilder.computeBestStreak(sessions))
    }

    @Test
    fun `best streak same day multiple sessions counts as 1`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            session(startTime = now),
            session(startTime = now + 1000),
            session(startTime = now + 2000)
        )
        assertEquals(1, PracticeProfileBuilder.computeBestStreak(sessions))
    }

    @Test
    fun `best streak long run`() {
        val sessions = consecutiveDaySessions(30, startOffsetDaysAgo = 0)
        assertEquals(30, PracticeProfileBuilder.computeBestStreak(sessions))
    }

    @Test
    fun `best streak empty returns 0`() {
        assertEquals(0, PracticeProfileBuilder.computeBestStreak(emptyList()))
    }

    @Test
    fun `current streak counts back from today`() {
        val sessions = consecutiveDaySessions(5, startOffsetDaysAgo = 0)
        val current = PracticeProfileBuilder.computeCurrentStreak(sessions)
        assertEquals(5, current)
    }

    @Test
    fun `current streak breaks when today missing`() {
        val today = System.currentTimeMillis() / DAY_MS * DAY_MS
        // practiced yesterday and day before, but not today
        val sessions = listOf(
            session(startTime = today - DAY_MS),
            session(startTime = today - 2 * DAY_MS)
        )
        val current = PracticeProfileBuilder.computeCurrentStreak(sessions)
        assertEquals(0, current)
    }

    @Test
    fun `current streak less than best streak`() {
        // 10 consecutive days ending 5 days ago → best=10, current=0
        val sessions = consecutiveDaySessions(10, startOffsetDaysAgo = 5)
        val profile = PracticeProfileBuilder.fromSessions(sessions)
        assertEquals(10, profile.bestStreakDays)
        assertEquals(0, profile.currentStreakDays)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementProgress
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `progress unlocked when current exceeds target`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "FIRST_STEPS" }
        val progress = AchievementProgress(def, 3.0)
        assertTrue(progress.isUnlocked)
        assertEquals(1f, progress.progressRatio, 0.001f)
    }

    @Test
    fun `progress locked when current below target`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "CENTURY" }
        val progress = AchievementProgress(def, 50.0)
        assertFalse(progress.isUnlocked)
        assertEquals(0.5f, progress.progressRatio, 0.001f)
    }

    @Test
    fun `progress ratio clamped to 0`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "CENTURY" }
        val progress = AchievementProgress(def, 0.0)
        assertEquals(0f, progress.progressRatio, 0.001f)
    }

    @Test
    fun `progress ratio clamped to 1`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "DAILY_HABIT" }
        val progress = AchievementProgress(def, 100.0)
        assertEquals(1f, progress.progressRatio, 0.001f)
    }

    @Test
    fun `format target for time category shows minutes`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "HALF_HOUR" }
        assertEquals("30分钟", def.formatTarget())
    }

    @Test
    fun `format target for time category shows hours`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "MARATHON" }
        assertEquals("5小时", def.formatTarget())
    }

    @Test
    fun `format target for accuracy shows count`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "CONSISTENT" }
        assertEquals("10", def.formatTarget())
    }

    @Test
    fun `format metric value for pitch perfect shows percentage`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "PITCH_PERFECT" }
        assertEquals("100%", def.formatTarget())
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementEngine — definition integrity
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `all achievement ids are unique`() {
        val ids = AchievementEngine.DEFINITIONS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `has 22 achievements`() {
        assertEquals(22, AchievementEngine.DEFINITIONS.size)
    }

    @Test
    fun `all categories are represented`() {
        val categories = AchievementEngine.DEFINITIONS.map { it.category }.toSet()
        assertEquals(AchievementCategory.values().toSet(), categories)
    }

    @Test
    fun `all targets are positive`() {
        AchievementEngine.DEFINITIONS.forEach { def ->
            assertTrue("Target for ${def.id} should be positive", def.target > 0)
        }
    }

    @Test
    fun `all ids in ALL_IDS`() {
        assertEquals(
            AchievementEngine.DEFINITIONS.map { it.id }.toSet(),
            AchievementEngine.ALL_IDS
        )
    }

    @Test
    fun `all definitions have non-empty titles and descriptions`() {
        AchievementEngine.DEFINITIONS.forEach { def ->
            assertTrue(def.title.isNotEmpty())
            assertTrue(def.description.isNotEmpty())
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementEngine — evaluate with empty profile
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `empty profile unlocks nothing`() {
        val summary = AchievementEngine.evaluate(PracticeProfile())
        assertEquals(0, summary.unlockedCount)
        assertEquals(22, summary.totalCount)
        assertEquals(0f, summary.completionRatio, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementEngine — individual category unlocking
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `first session unlocks FIRST_STEPS`() {
        val profile = PracticeProfile(totalSessions = 1)
        val summary = AchievementEngine.evaluate(profile)
        val firstSteps = summary.all.first { it.definition.id == "FIRST_STEPS" }
        assertTrue(firstSteps.isUnlocked)
    }

    @Test
    fun `streak achievements unlock at thresholds`() {
        // 7-day streak → DAILY_HABIT unlocked, FORTNIGHT still locked
        val profile = PracticeProfile(bestStreakDays = 7)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "DAILY_HABIT" }.isUnlocked)
        assertFalse(summary.all.first { it.definition.id == "FORTNIGHT" }.isUnlocked)
    }

    @Test
    fun `30-day streak unlocks MONTHLY_DEVOTION but not IRON_WILL`() {
        val profile = PracticeProfile(bestStreakDays = 30)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "MONTHLY_DEVOTION" }.isUnlocked)
        assertFalse(summary.all.first { it.definition.id == "IRON_WILL" }.isUnlocked)
    }

    @Test
    fun `100-day streak unlocks all streak achievements`() {
        val profile = PracticeProfile(bestStreakDays = 100, totalSessions = 100)
        val summary = AchievementEngine.evaluate(profile)
        val streakAchievements = summary.all.filter {
            it.definition.category == AchievementCategory.STREAK
        }
        assertTrue(streakAchievements.all { it.isUnlocked })
    }

    @Test
    fun `100 sessions unlocks CENTURY`() {
        val profile = PracticeProfile(totalSessions = 100)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "CENTURY" }.isUnlocked)
        assertFalse(summary.all.first { it.definition.id == "FIVE_HUNDRED" }.isUnlocked)
    }

    @Test
    fun `perfect accuracy unlocks PITCH_PERFECT`() {
        val profile = PracticeProfile(bestAccuracy = 1.0f)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "PITCH_PERFECT" }.isUnlocked)
    }

    @Test
    fun `near-perfect but not perfect does not unlock PITCH_PERFECT`() {
        val profile = PracticeProfile(bestAccuracy = 0.999f)
        val summary = AchievementEngine.evaluate(profile)
        assertFalse(summary.all.first { it.definition.id == "PITCH_PERFECT" }.isUnlocked)
    }

    @Test
    fun `10 sessions at 90 percent unlocks CONSISTENT`() {
        val profile = PracticeProfile(highAccuracyCount90 = 10)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "CONSISTENT" }.isUnlocked)
    }

    @Test
    fun `20 sessions at 95 percent unlocks VIRTUOSO_ACCURACY`() {
        val profile = PracticeProfile(highAccuracyCount95 = 20)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "VIRTUOSO_ACCURACY" }.isUnlocked)
    }

    @Test
    fun `1000 notes unlocks THOUSAND_NOTES not TEN_THOUSAND`() {
        val profile = PracticeProfile(totalNotesPlayed = 1000)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "THOUSAND_NOTES" }.isUnlocked)
        assertFalse(summary.all.first { it.definition.id == "TEN_THOUSAND_NOTES" }.isUnlocked)
    }

    @Test
    fun `10 distinct scores unlocks COLLECTOR`() {
        val profile = PracticeProfile(distinctScores = 10)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "COLLECTOR" }.isUnlocked)
        assertFalse(summary.all.first { it.definition.id == "REPERTOIRE_MASTER" }.isUnlocked)
    }

    @Test
    fun `30 minutes unlocks HALF_HOUR`() {
        val profile = PracticeProfile(totalPracticeMs = 1_800_000L)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "HALF_HOUR" }.isUnlocked)
    }

    @Test
    fun `5 hours unlocks MARATHON`() {
        val profile = PracticeProfile(totalPracticeMs = 18_000_000L)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "MARATHON" }.isUnlocked)
    }

    @Test
    fun `24 hours unlocks DEDICATED`() {
        val profile = PracticeProfile(totalPracticeMs = 86_400_000L)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "DEDICATED" }.isUnlocked)
    }

    @Test
    fun `1 completed tempo session unlocks SPEED_DEMON`() {
        val profile = PracticeProfile(tempoSessionsCompleted = 1)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "SPEED_DEMON" }.isUnlocked)
        assertFalse(summary.all.first { it.definition.id == "TEMPO_MASTER" }.isUnlocked)
    }

    @Test
    fun `10 completed tempo sessions unlocks TEMPO_MASTER`() {
        val profile = PracticeProfile(tempoSessionsCompleted = 10)
        val summary = AchievementEngine.evaluate(profile)
        assertTrue(summary.all.first { it.definition.id == "TEMPO_MASTER" }.isUnlocked)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementEngine — evaluateOne
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `evaluateOne returns correct progress`() {
        val def = AchievementEngine.DEFINITIONS.first { it.id == "DAILY_HABIT" }
        val profile = PracticeProfile(bestStreakDays = 5)
        val progress = AchievementEngine.evaluateOne(def, profile)
        assertEquals(5.0, progress.currentValue, 0.001)
        assertFalse(progress.isUnlocked)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementEngine — sorting & structure
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `unlocked achievements come first in summary`() {
        val profile = PracticeProfile(
            totalSessions = 1,  // unlocks FIRST_STEPS only
            bestStreakDays = 0,
            totalNotesPlayed = 0
        )
        val summary = AchievementEngine.evaluate(profile)
        // First item should be unlocked
        assertTrue(summary.all.first().isUnlocked)
    }

    @Test
    fun `summary completionRatio is fraction`() {
        val profile = PracticeProfile(totalSessions = 1) // only FIRST_STEPS unlocked
        val summary = AchievementEngine.evaluate(profile)
        assertEquals(1f / 22f, summary.completionRatio, 0.001f)
    }

    @Test
    fun `byCategory groups correctly`() {
        val profile = PracticeProfile(totalSessions = 1)
        val summary = AchievementEngine.evaluate(profile)
        val byCat = summary.byCategory()
        assertEquals(7, byCat.size)
        // STREAK category has 5 achievements
        assertEquals(5, byCat[AchievementCategory.STREAK]!!.size)
        assertEquals(3, byCat[AchievementCategory.VOLUME]!!.size)
        assertEquals(3, byCat[AchievementCategory.ACCURACY]!!.size)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AchievementEngine — newlyUnlocked
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `newlyUnlocked returns achievements not in previous set`() {
        val profile = PracticeProfile(totalSessions = 10, bestStreakDays = 7)
        val summary = AchievementEngine.evaluate(profile)
        // Previous: only FIRST_STEPS was unlocked
        val newlyUnlocked = summary.newlyUnlocked(setOf("FIRST_STEPS"))
        val newlyIds = newlyUnlocked.map { it.definition.id }
        assertTrue("DAILY_HABIT" in newlyIds)
        assertTrue("GETTING_WARM" in newlyIds)
        assertFalse("FIRST_STEPS" in newlyIds)
    }

    @Test
    fun `newlyUnlocked empty when nothing new`() {
        val profile = PracticeProfile(totalSessions = 1)
        val summary = AchievementEngine.evaluate(profile)
        val unlockedIds = summary.unlocked.map { it.definition.id }.toSet()
        assertEquals(0, summary.newlyUnlocked(unlockedIds).size)
    }

    @Test
    fun `newlyUnlocked returns all when previous is empty`() {
        val profile = PracticeProfile(
            totalSessions = 10,
            bestStreakDays = 7,
            totalNotesPlayed = 100
        )
        val summary = AchievementEngine.evaluate(profile)
        val newlyUnlocked = summary.newlyUnlocked(emptySet())
        // Should include all currently unlocked achievements
        assertEquals(summary.unlockedCount, newlyUnlocked.size)
    }

    @Test
    fun `evaluateNewlyUnlocked delegates correctly`() {
        val profile = PracticeProfile(totalSessions = 1)
        val newlyUnlocked = AchievementEngine.evaluateNewlyUnlocked(profile, emptySet())
        assertEquals(1, newlyUnlocked.size) // only FIRST_STEPS
        assertEquals("FIRST_STEPS", newlyUnlocked.first().definition.id)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  End-to-end: full profile integration
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `comprehensive profile unlocks many achievements`() {
        val sessions = (1..20).flatMap { day ->
            val today = System.currentTimeMillis() / DAY_MS * DAY_MS
            listOf(
                session(
                    scoreTitle = "Piece$day",
                    startTime = today - (20 - day) * DAY_MS,
                    totalNotes = 100,
                    accuracy = 0.96f,
                    durationMs = 1_800_000L // 30 min each
                )
            )
        }
        val tempoRecords = (1..10).map {
            TempoProgressRecord(
                scoreTitle = "Piece$it",
                startMeasure = 0,
                endMeasure = 4,
                startBpm = 60,
                peakBpm = 120,
                targetBpm = 120,
                completed = true,
                durationMs = 300_000L
            )
        }

        val profile = PracticeProfileBuilder.fromSessions(sessions, tempoRecords)

        // Verify aggregate metrics
        assertEquals(20, profile.totalSessions)
        assertEquals(20, profile.bestStreakDays)     // 20 consecutive days
        assertEquals(20, profile.currentStreakDays)
        assertEquals(20, profile.distinctScores)
        assertEquals(2000, profile.totalNotesPlayed) // 20 * 100
        assertEquals(0.96f, profile.bestAccuracy, 0.001f)
        assertEquals(20, profile.highAccuracyCount90) // all >= 90%
        assertEquals(20, profile.highAccuracyCount95) // all >= 95%
        assertEquals(10, profile.tempoSessionsCompleted)

        val summary = AchievementEngine.evaluate(profile)

        // Should unlock a large number of achievements
        val unlockedIds = summary.unlocked.map { it.definition.id }.toSet()
        assertTrue("FIRST_STEPS" in unlockedIds)
        assertTrue("DAILY_HABIT" in unlockedIds)
        assertTrue("FORTNIGHT" in unlockedIds)         // 20-day streak >= 14
        assertTrue("GETTING_WARM" in unlockedIds)      // 10 sessions
        assertTrue("CONSISTENT" in unlockedIds)        // 10 at 90%
        assertTrue("VIRTUOSO_ACCURACY" in unlockedIds) // 20 at 95%
        assertTrue("CENTURY_NOTES" in unlockedIds)
        assertTrue("THOUSAND_NOTES" in unlockedIds)    // 2000 notes
        assertTrue("EXPLORER" in unlockedIds)          // 5 distinct
        assertTrue("COLLECTOR" in unlockedIds)         // 10 distinct
        assertTrue("REPERTOIRE_MASTER" in unlockedIds) // 20 distinct
        assertTrue("HALF_HOUR" in unlockedIds)
        assertTrue("MARATHON" in unlockedIds)          // 20*30min = 600 min = 10 hrs
        assertTrue("SPEED_DEMON" in unlockedIds)
        assertTrue("TEMPO_MASTER" in unlockedIds)

        // Should NOT unlock (yet)
        assertFalse("CENTURY" in unlockedIds)          // only 20 sessions
        assertFalse("FIVE_HUNDRED" in unlockedIds)
        assertFalse("MONTHLY_DEVOTION" in unlockedIds) // only 20-day streak
        assertFalse("IRON_WILL" in unlockedIds)
        assertFalse("PITCH_PERFECT" in unlockedIds)    // best 96%, not 100%
        assertFalse("TEN_THOUSAND_NOTES" in unlockedIds) // only 2000
        assertFalse("DEDICATED" in unlockedIds)        // only 10 hrs, not 24
    }

    @Test
    fun `profile from tempo records only`() {
        val tempoRecords = listOf(
            TempoProgressRecord("A", 0, 4, 60, 120, 120, completed = true),
            TempoProgressRecord("B", 0, 8, 70, 100, 140, completed = false)
        )
        val profile = PracticeProfileBuilder.fromSessions(emptyList(), tempoRecords)
        assertEquals(1, profile.tempoSessionsCompleted) // only 1 completed
    }

    @Test
    fun `end-to-end newly unlocked detection after improvement`() {
        // Before: 5 sessions
        val beforeProfile = PracticeProfile(totalSessions = 5)
        val beforeSummary = AchievementEngine.evaluate(beforeProfile)
        val beforeIds = beforeSummary.unlocked.map { it.definition.id }.toSet()

        // After: 10 sessions (unlocks GETTING_WARM)
        val afterProfile = PracticeProfile(totalSessions = 10)
        val newly = AchievementEngine.evaluateNewlyUnlocked(afterProfile, beforeIds)
        val newlyIds = newly.map { it.definition.id }
        assertTrue("GETTING_WARM" in newlyIds)
    }
}
