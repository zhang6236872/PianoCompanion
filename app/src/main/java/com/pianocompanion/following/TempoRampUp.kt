package com.pianocompanion.following

import kotlin.math.ceil

/**
 * 渐速练习控制器 (Tempo Ramp-Up Trainer)
 *
 * 钢琴练习中最核心的技巧之一：**慢练加速**。从较慢的速度开始反复练习一个
 * 段落，每完成 [loopsPerStep] 次循环后自动提高速度 [bpmIncrement] BPM，
 * 直到达到目标速度 [targetBpm]。这种渐进式提速法被所有专业钢琴教育者推荐，
 * 能帮助大脑在慢速下建立正确的肌肉记忆，再逐步适应表演速度。
 *
 * 与 [SectionLooper]（v2.53.0）配合使用：每次段落循环完成时调用 [advance]，
 * 该方法判定是否到了提速的时机。可选项 [requireMinAccuracy] 在准确率不达标时
 * 拒绝计入循环次数——迫使练习者真正掌握当前速度后再提速。
 *
 * 典型集成流程：
 * 1. 用户在 UI 中配置起始速度、目标速度、每次提速量、每步循环次数；
 * 2. [ScoreFollower] 的 [onSectionLoop] 回调中调用 [advance]（可传入本轮准确率）；
 * 3. [advance] 返回 true 时，调用方将 [currentBpm] 应用到节拍器（[com.pianocompanion.audio.Metronome.setBpm]）；
 * 4. [isComplete] 为 true 时，提示用户已达到目标速度。
 *
 * 纯 Kotlin，无 Android 依赖，完全可单元测试。
 *
 * @param startBpm 起始速度（BPM），默认 60（适合慢练）。
 * @param targetBpm 目标速度（BPM），默认 120（标准表演速度）。
 * @param bpmIncrement 每次提速的增量（BPM），默认 5。
 * @param loopsPerStep 在当前速度下需要完成多少次循环后才提速，默认 2。
 * @param requireMinAccuracy 是否要求准确率达到 [minAccuracy] 才计入循环次数，默认 false。
 * @param minAccuracy 当 [requireMinAccuracy] 为 true 时的最低准确率阈值（0~1），默认 0.8。
 */
class TempoRampUp(
    var startBpm: Int = DEFAULT_START_BPM,
    var targetBpm: Int = DEFAULT_TARGET_BPM,
    var bpmIncrement: Int = DEFAULT_BPM_INCREMENT,
    var loopsPerStep: Int = DEFAULT_LOOPS_PER_STEP,
    var requireMinAccuracy: Boolean = false,
    var minAccuracy: Float = DEFAULT_MIN_ACCURACY
) {
    init {
        normalizeConfig()
    }

    /** 当前速度（BPM），从 [startBpm] 开始逐步递增到 [targetBpm]。 */
    var currentBpm: Int = startBpm
        private set

    /** 已完成的提速次数（每次成功提速 +1），从 0 开始。 */
    var currentStep: Int = 0
        private set

    /** 在当前速度下已完成的循环次数（达到 [loopsPerStep] 时提速并归零）。 */
    var loopsAtCurrentStep: Int = 0
        private set

    /** 是否已达到目标速度。 */
    var completed: Boolean = false
        private set

    /** 速度变化时回调，参数为新的 BPM。 */
    var onTempoChange: ((Int) -> Unit)? = null

    /** 达到目标速度时回调（仅触发一次）。 */
    var onComplete: (() -> Unit)? = null

    /**
     * 总共需要多少次提速才能从 [startBpm] 达到 [targetBpm]。
     * 例如 startBpm=60, targetBpm=120, bpmIncrement=5 → 12 步。
     */
    fun totalSteps(): Int {
        if (bpmIncrement <= 0) return 0
        return ceil((targetBpm - startBpm).toDouble() / bpmIncrement).toInt().coerceAtLeast(0)
    }

    /** 提速进度比例（0f ~ 1f）：[currentStep] / [totalSteps]。 */
    fun progressRatio(): Float {
        val total = totalSteps()
        return if (total <= 0) 1f else (currentStep.toFloat() / total).coerceIn(0f, 1f)
    }

    /** 是否已达到目标速度。 */
    fun isComplete(): Boolean = completed

    /** 已达到目标速度时，当前速度 = [targetBpm]；否则 = [currentBpm]。 */
    fun effectiveBpm(): Int = currentBpm

    /**
     * 在每次段落循环完成时调用，判定是否到了提速的时机。
     *
     * @param accuracy 本轮循环的准确率（0~1）。仅在 [requireMinAccuracy] 为 true 时使用。
     *   传 null 时视为不进行准确率门控（即使 requireMinAccuracy=true 也放行）。
     * @return true 表示本次调用触发了提速（调用方应将 [currentBpm] 应用到节拍器）；
     *   false 表示仍在当前速度积累循环次数或已完成。
     */
    fun advance(accuracy: Float? = null): Boolean {
        if (completed) return false

        // 准确率门控：未达标则本轮不计入循环次数
        if (requireMinAccuracy && accuracy != null && accuracy < minAccuracy) {
            return false
        }

        loopsAtCurrentStep++

        if (loopsAtCurrentStep >= loopsPerStep) {
            loopsAtCurrentStep = 0
            val newBpm = (currentBpm + bpmIncrement).coerceAtMost(targetBpm)
            if (newBpm != currentBpm) {
                currentBpm = newBpm
                currentStep++
                onTempoChange?.invoke(currentBpm)
            }
            if (currentBpm >= targetBpm) {
                completed = true
                onComplete?.invoke()
            }
            return true
        }
        return false
    }

    /**
     * 重置到初始状态（回到 [startBpm]，步数/循环/完成状态归零）。
     * 调用 [normalizeConfig] 后以最新配置的 [startBpm] 为起点。
     */
    fun reset() {
        normalizeConfig()
        currentBpm = startBpm
        currentStep = 0
        loopsAtCurrentStep = 0
        completed = startBpm >= targetBpm
    }

    /**
     * 规范化配置参数，保证不变式：
     * - [bpmIncrement] ≥ 1
     * - [loopsPerStep] ≥ 1
     * - [targetBpm] ≥ [startBpm]（若反转则交换）
     * - BPM 范围 clamp 到 [MIN_BPM] ~ [MAX_BPM]
     */
    private fun normalizeConfig() {
        bpmIncrement = bpmIncrement.coerceAtLeast(1)
        loopsPerStep = loopsPerStep.coerceAtLeast(1)
        if (targetBpm < startBpm) {
            val tmp = startBpm
            startBpm = targetBpm
            targetBpm = tmp
        }
        startBpm = startBpm.coerceIn(MIN_BPM, MAX_BPM)
        targetBpm = targetBpm.coerceIn(MIN_BPM, MAX_BPM)
        // 交换后可能再次违反 startBpm ≤ targetBpm（因 clamp），强制纠正
        if (startBpm > targetBpm) {
            startBpm = targetBpm
        }
        minAccuracy = minAccuracy.coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_START_BPM = 60
        const val DEFAULT_TARGET_BPM = 120
        const val DEFAULT_BPM_INCREMENT = 5
        const val DEFAULT_LOOPS_PER_STEP = 2
        const val DEFAULT_MIN_ACCURACY = 0.8f

        /** 节拍器支持的 BPM 下限（与 Metronome 一致）。 */
        const val MIN_BPM = 40
        /** 节拍器支持的 BPM 上限（与 Metronome 一致）。 */
        const val MAX_BPM = 240
    }
}
