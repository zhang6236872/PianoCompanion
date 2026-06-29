package com.pianocompanion.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pianocompanion.data.model.Score

/**
 * 乐谱播放器：使用 [ScorePlaybackEngine] 合成音频，通过 [AudioTrack] 播放。
 *
 * 支持播放/停止/跳转，并提供进度回调用于 UI 同步。
 *
 * 用法：
 * ```
 * val player = ScorePlayer()
 * player.prepare(score, tempoBpm = 120)
 * player.onProgress = { currentMs, totalMs -> updateSeekBar(currentMs) }
 * player.onComplete = { stopButton() }
 * player.play()
 * // ...
 * player.stop()
 * player.release()
 * ```
 *
 * @param sampleRate 采样率
 */
class ScorePlayer(
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    private val handler = Handler(Looper.getMainLooper())
    private val engine = ScorePlaybackEngine(sampleRate)

    private var audioTrack: AudioTrack? = null
    private var pcmBuffer: ShortArray? = null
    private var isPrepared = false
    private var isCurrentlyPlaying = false
    private var totalDurationMs: Long = 0L

    /** 进度回调：(当前位置 ms, 总时长 ms) */
    var onProgress: ((Long, Long) -> Unit)? = null

    /** 播放完成回调 */
    var onComplete: (() -> Unit)? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isCurrentlyPlaying) return
            val track = audioTrack ?: return
            val playbackHead = track.playbackHeadPosition
            val currentMs = (playbackHead.toLong() * 1000 / sampleRate)
            onProgress?.invoke(currentMs, totalDurationMs)

            if (currentMs >= totalDurationMs) {
                stop()
                onComplete?.invoke()
            } else {
                handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * 预渲染乐谱到 PCM 缓冲区。需在 play() 之前调用。
     *
     * @param score 要播放的乐谱
     * @param tempoBpm 播放速度（null = 使用乐谱速度）
     * @return 渲染是否成功
     */
    fun prepare(score: Score, tempoBpm: Int? = null): Boolean {
        return try {
            stop()
            releaseTrack()

            val floatBuffer = engine.render(score, tempoBpm)
            pcmBuffer = floatToPcm16(floatBuffer)
            totalDurationMs = engine.samplesToMs(pcmBuffer!!.size)

            // 创建 AudioTrack（MODE_STATIC 写入完整缓冲区）
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "prepare 失败", e)
            isPrepared = false
            false
        }
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
            handler.post(progressRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "play 失败", e)
        }
    }

    /**
     * 暂停播放。
     */
    fun pause() {
        if (!isCurrentlyPlaying) return
        audioTrack?.pause()
        isCurrentlyPlaying = false
        handler.removeCallbacks(progressRunnable)
    }

    /**
     * 停止播放并重置到开头。
     */
    fun stop() {
        isCurrentlyPlaying = false
        handler.removeCallbacks(progressRunnable)
        audioTrack?.let { track ->
            try {
                track.pause()
                track.flush()
                track.reloadStaticData()
            } catch (e: Exception) {
                Log.e(TAG, "stop 失败", e)
            }
        }
        onProgress?.invoke(0L, totalDurationMs)
    }

    /**
     * 跳转到指定位置（毫秒）。
     */
    fun seekTo(positionMs: Long) {
        val track = audioTrack ?: return
        val wasPlaying = isCurrentlyPlaying
        if (wasPlaying) track.pause()

        val sampleOffset = engine.msToSamples(positionMs).coerceIn(0, pcmBuffer?.size ?: 0)
        // MODE_STATIC 下通过设置播放头实现跳转
        track.flush()
        if (sampleOffset < (pcmBuffer?.size ?: 0)) {
            track.write(pcmBuffer!!, sampleOffset, pcmBuffer!!.size - sampleOffset)
        }
        track.reloadStaticData()

        if (wasPlaying) {
            track.play()
            isCurrentlyPlaying = true
            handler.post(progressRunnable)
        }
        onProgress?.invoke(positionMs, totalDurationMs)
    }

    /**
     * 是否正在播放。
     */
    fun isPlaying(): Boolean = isCurrentlyPlaying

    /**
     * 是否已准备好（已渲染乐谱）。
     */
    fun isPrepared(): Boolean = isPrepared

    /**
     * 总时长（毫秒）。
     */
    fun getDuration(): Long = totalDurationMs

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
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "ScorePlayer"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 50L

        /**
         * 将 FloatArray（[-1.0, 1.0]）转换为 16-bit PCM ShortArray。
         */
        fun floatToPcm16(floatBuffer: FloatArray): ShortArray {
            return ShortArray(floatBuffer.size) { i ->
                val clamped = floatBuffer[i].coerceIn(-1f, 1f)
                (clamped * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
