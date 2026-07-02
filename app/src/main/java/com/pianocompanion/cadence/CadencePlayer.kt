package com.pianocompanion.cadence

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.pianocompanion.audio.ScorePlayer

/**
 * 终止式音频播放器（Android 层）。
 *
 * 使用 [AudioTrack]（MODE_STATIC）播放 [CadenceAudioBuilder] 渲染的 PCM 缓冲区。
 * 支持播放、停止、重播。资源使用完毕必须调用 [release]。
 */
class CadencePlayer(
    private val sampleRate: Int = CadenceAudioBuilder.SAMPLE_RATE
) {
    private var audioTrack: AudioTrack? = null
    private var pcmBuffer: ShortArray? = null
    private var isPrepared = false
    private var isCurrentlyPlaying = false

    /** 播放完成回调。 */
    var onComplete: (() -> Unit)? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val completionRunnable = object : Runnable {
        override fun run() {
            if (!isCurrentlyPlaying) return
            val track = audioTrack ?: return
            val headPos = track.playbackHeadPosition
            val totalSamples = pcmBuffer?.size ?: 0
            if (headPos >= totalSamples - COMPLETION_TOLERANCE_SAMPLES) {
                stop()
                onComplete?.invoke()
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun prepare(floatBuffer: FloatArray) {
        stop()
        releaseTrack()
        if (floatBuffer.isEmpty()) {
            isPrepared = false
            return
        }
        pcmBuffer = ScorePlayer.floatToPcm16(floatBuffer)
        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(pcmBuffer!!.size * 2, minBufSize)

        @Suppress("DEPRECATION")
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STATIC
        )
        audioTrack?.write(pcmBuffer!!, 0, pcmBuffer!!.size)
        isPrepared = true
    }

    fun play() {
        if (!isPrepared) return
        val track = audioTrack ?: return
        try {
            track.play()
            isCurrentlyPlaying = true
            handler.post(completionRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "play 失败", e)
        }
    }

    fun stop() {
        isCurrentlyPlaying = false
        handler.removeCallbacks(completionRunnable)
        audioTrack?.let { track ->
            try {
                track.pause()
                track.flush()
                track.reloadStaticData()
            } catch (e: Exception) {
                Log.e(TAG, "stop 失败", e)
            }
        }
    }

    fun isPlaying(): Boolean = isCurrentlyPlaying

    fun release() {
        stop()
        releaseTrack()
        pcmBuffer = null
        isPrepared = false
    }

    private fun releaseTrack() {
        audioTrack?.let { track ->
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "CadencePlayer"
        private const val POLL_INTERVAL_MS = 50L
        private const val sampleRateStatic = CadenceAudioBuilder.SAMPLE_RATE
        private val COMPLETION_TOLERANCE_SAMPLES = sampleRateStatic / 50
    }
}
