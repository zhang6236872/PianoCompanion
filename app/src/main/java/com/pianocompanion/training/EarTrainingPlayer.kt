package com.pianocompanion.training

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.pianocompanion.audio.ScorePlayer

/**
 * 听音训练音频播放器（Android 层）。
 *
 * 使用 [AudioTrack]（MODE_STATIC）播放 [EarTrainingAudioBuilder] 渲染的 PCM 缓冲区。
 * 支持播放、停止、重播。与 [ScorePlayer] 类似但更简化——无需进度条或跳转。
 *
 * 资源管理：使用完毕必须调用 [release]。
 *
 * @param sampleRate 采样率
 */
class EarTrainingPlayer(
    private val sampleRate: Int = EarTrainingAudioBuilder.SAMPLE_RATE
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
            // 当播放头接近缓冲区末尾时视为完成
            if (headPos >= totalSamples - COMPLETION_TOLERANCE_SAMPLES) {
                stop()
                onComplete?.invoke()
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 预加载 PCM Float 缓冲区。
     *
     * @param floatBuffer [-1.0, 1.0] 范围的浮点采样数据
     */
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

    /**
     * 开始播放。
     */
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

    /**
     * 停止播放并重置到开头（可重新播放）。
     */
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

    /**
     * 释放所有资源。调用后不可再使用。
     */
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
        private const val TAG = "EarTrainingPlayer"
        private const val POLL_INTERVAL_MS = 50L
        // 允许提前少量样本判定完成（AudioTrack 播放头报告可能有延迟）
        private val COMPLETION_TOLERANCE_SAMPLES = sampleRateStatic / 50
        private const val sampleRateStatic = EarTrainingAudioBuilder.SAMPLE_RATE
    }
}
