package com.pianocompanion.intervalsequence

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * 音程序列记忆训练 Android 音频播放器。
 *
 * 使用 AudioTrack MODE_STATIC 播放由 [IntervalSequenceAudioBuilder] 渲染的 PCM Float 数据。
 */
class IntervalSequencePlayer(
    private val sampleRate: Int = IntervalSequenceAudioBuilder.DEFAULT_SAMPLE_RATE
) {
    private val audioBuilder = IntervalSequenceAudioBuilder(sampleRate)
    private var audioTrack: AudioTrack? = null

    /**
     * 播放题目音频。
     */
    fun play(question: IntervalSequenceQuestion) {
        try {
            stop()

            val pcm = audioBuilder.render(question)

            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT
            )
            val bufferSize = maxOf(pcm.size * 4, minBufSize)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // 将 float PCM 转为 byte array（little-endian float32）
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.setNotificationMarkerPosition(pcm.size)
            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.e(TAG, "播放失败", e)
        }
    }

    /**
     * 停止播放。
     */
    fun stop() {
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                Log.e(TAG, "停止播放异常", e)
            }
        }
        audioTrack = null
    }

    /**
     * 释放资源。
     */
    fun release() {
        stop()
    }

    companion object {
        private const val TAG = "IntervalSequencePlayer"
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    }
}
