package com.pianocompanion.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Captures audio from the Android microphone and delivers PCM samples
 * to a callback for real-time processing.
 *
 * @param sampleRate Sampling rate in Hz (44100 recommended)
 * @param onSamples Callback receiving mono float samples (-1.0 to 1.0)
 */
class AudioRecorder(
    private val sampleRate: Int = 44100,
    private val onSamples: (FloatArray) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    // Buffer size: ~46ms at 44.1kHz (enough for YIN pitch detection with 2048 samples)
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRecording) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingJob = CoroutineScope(Dispatchers.Default).launch {
            val shortBuffer = ShortArray(bufferSize)
            val floatBuffer = FloatArray(bufferSize)

            while (isActive && isRecording) {
                val readCount = audioRecord?.read(shortBuffer, 0, bufferSize) ?: -1
                if (readCount > 0) {
                    // Convert 16-bit PCM to float [-1.0, 1.0]
                    for (i in 0 until readCount) {
                        floatBuffer[i] = shortBuffer[i] / Short.MAX_VALUE.toFloat()
                    }
                    onSamples(floatBuffer.copyOf(readCount))
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun isRecording(): Boolean = isRecording
}
