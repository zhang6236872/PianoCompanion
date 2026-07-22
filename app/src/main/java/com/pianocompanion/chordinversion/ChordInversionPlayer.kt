package com.pianocompanion.chordinversion

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.pianocompanion.audio.ScorePlayer

/**
 * 和弦转位听辨训练音频播放器（Android 层）。
 *
 * 使用 [AudioTrack]（MODE_STATIC）播放 [ChordInversionAudioBuilder] 渲染的 PCM 缓冲区。
 * 支持播放、停止、重播。资源管理：使用完毕必须调用 [release]。
 *
 * @param sampleRate 采样率
 */
class ChordInversionPlayer(
    private val sampleRate: Int = ChordInversionAudioBuilder.DEFAULT_SAMPLE_RATE
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

    /** 预加载 PCM Float 缓冲区。 */
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

    /** 开始播放。 */
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

    /** 停止播放并重置到开头（可重新播放）。 */
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

    /** 是否正在播放。 */
    fun isPlaying(): Boolean = isCurrentlyPlaying

    /** 释放所有资源。调用后不可再使用。 */
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
        private const val TAG = "ChordInversionPlayer"
        private const val POLL_INTERVAL_MS = 50L
        private const val sampleRateStatic = ChordInversionAudioBuilder.DEFAULT_SAMPLE_RATE
        private val COMPLETION_TOLERANCE_SAMPLES = sampleRateStatic / 50
    }
}
