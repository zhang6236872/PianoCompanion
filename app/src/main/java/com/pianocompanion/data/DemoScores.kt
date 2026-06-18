package com.pianocompanion.data

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils

/**
 * Built-in demo scores so the app works immediately without importing files.
 */
object DemoScores {

    fun getAll(): List<Score> = listOf(
        odeToJoy(),
        twinkleTwinkle(),
        jingleBells(),
        cMajorScale()
    )

    /** 欢乐颂 - Ode to Joy (贝多芬第九交响曲) */
    fun odeToJoy(): Score {
        // E E F G | G F E D | C C D E | E. D D
        val pattern = listOf(64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62)
        val notes = pattern.mapIndexed { idx, midi ->
            ScoreNote(
                midiNumber = midi,
                noteName = MusicUtils.midiToNoteName(midi),
                startTime = idx * 500L,
                duration = 450L,
                measureIndex = idx / 4,
                staff = Staff.TREBLE
            )
        }
        return Score(
            id = "ode_to_joy",
            title = "欢乐颂",
            composer = "贝多芬",
            notes = notes
        )
    }

    /** 小星星 - Twinkle Twinkle Little Star */
    fun twinkleTwinkle(): Score {
        // C C G G | A A G | F F E E | D D C
        val pattern = listOf(60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60)
        val notes = pattern.mapIndexed { idx, midi ->
            ScoreNote(
                midiNumber = midi,
                noteName = MusicUtils.midiToNoteName(midi),
                startTime = idx * 400L,
                duration = 350L,
                measureIndex = idx / 4,
                staff = Staff.TREBLE
            )
        }
        return Score(
            id = "twinkle",
            title = "小星星",
            composer = "法国民谣",
            notes = notes
        )
    }

    /** 铃儿响叮当 - Jingle Bells */
    fun jingleBells(): Score {
        // E E E | E E E | E G C D E
        val pattern = listOf(64, 64, 64, 64, 64, 64, 64, 67, 60, 62, 64)
        val notes = pattern.mapIndexed { idx, midi ->
            ScoreNote(
                midiNumber = midi,
                noteName = MusicUtils.midiToNoteName(midi),
                startTime = idx * 350L,
                duration = 300L,
                measureIndex = idx / 4,
                staff = Staff.TREBLE
            )
        }
        return Score(
            id = "jingle_bells",
            title = "铃儿响叮当",
            composer = "James Pierpont",
            notes = notes
        )
    }

    /** C大调音阶 - C Major Scale (练习用) */
    fun cMajorScale(): Score {
        val pattern = listOf(60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60)
        val notes = pattern.mapIndexed { idx, midi ->
            ScoreNote(
                midiNumber = midi,
                noteName = MusicUtils.midiToNoteName(midi),
                startTime = idx * 400L,
                duration = 350L,
                measureIndex = idx / 4,
                staff = Staff.TREBLE
            )
        }
        return Score(
            id = "c_major_scale",
            title = "C大调音阶",
            composer = "练习曲",
            notes = notes
        )
    }
}
