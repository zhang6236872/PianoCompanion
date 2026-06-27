package com.pianocompanion.following

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoRampUpTest {

    // === 构造与初始化 ===

    @Test
    fun `默认配置 startBpm=60 targetBpm=120 increment=5 loopsPerStep=2`() {
        val ramp = TempoRampUp()
        assertEquals(60, ramp.startBpm)
        assertEquals(120, ramp.targetBpm)
        assertEquals(5, ramp.bpmIncrement)
        assertEquals(2, ramp.loopsPerStep)
        assertEquals(60, ramp.currentBpm)
        assertEquals(0, ramp.currentStep)
        assertEquals(0, ramp.loopsAtCurrentStep)
        assertFalse(ramp.isComplete())
    }

    @Test
    fun `totalSteps 计算正确`() {
        assertEquals(12, TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 5).totalSteps())
        assertEquals(6, TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 10).totalSteps())
        assertEquals(10, TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 6).totalSteps()) // ceil(60/6)
        assertEquals(3, TempoRampUp(startBpm = 90, targetBpm = 120, bpmIncrement = 10).totalSteps())
        assertEquals(0, TempoRampUp(startBpm = 120, targetBpm = 120, bpmIncrement = 5).totalSteps())
    }

    @Test
    fun `progressRatio 初始为零`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 2)
        assertEquals(0f, ramp.progressRatio(), 0.001f)
    }

    // === 基本提速流程 ===

    @Test
    fun `完成 loopsPerStep 次循环后提速一次`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 2)

        // 第一次循环：仍在积累
        val changed1 = ramp.advance()
        assertFalse(changed1)
        assertEquals(60, ramp.currentBpm)
        assertEquals(0, ramp.currentStep)
        assertEquals(1, ramp.loopsAtCurrentStep)

        // 第二次循环：达到 loopsPerStep，提速到 65
        val changed2 = ramp.advance()
        assertTrue(changed2)
        assertEquals(65, ramp.currentBpm)
        assertEquals(1, ramp.currentStep)
        assertEquals(0, ramp.loopsAtCurrentStep)
    }

    @Test
    fun `loopsPerStep=1 时每次循环都提速`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 70, bpmIncrement = 5, loopsPerStep = 1)

        assertTrue(ramp.advance())
        assertEquals(65, ramp.currentBpm)
        assertTrue(ramp.advance())
        assertEquals(70, ramp.currentBpm)
        assertTrue(ramp.isComplete())
    }

    @Test
    fun `连续多步提速直到目标速度`() {
        val ramp = TempoRampUp(startBpm = 50, targetBpm = 70, bpmIncrement = 5, loopsPerStep = 1)

        ramp.advance() // 55
        ramp.advance() // 60
        ramp.advance() // 65
        assertEquals(65, ramp.currentBpm)
        assertEquals(3, ramp.currentStep)

        val changed = ramp.advance() // 70 = target
        assertTrue(changed)
        assertEquals(70, ramp.currentBpm)
        assertTrue(ramp.isComplete())
        assertEquals(1f, ramp.progressRatio(), 0.001f)
    }

    @Test
    fun `达到目标速度后再 advance 返回 false`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 1)

        ramp.advance() // 65, complete
        assertTrue(ramp.isComplete())

        val changed = ramp.advance()
        assertFalse(changed)
        assertEquals(65, ramp.currentBpm)
    }

    @Test
    fun `bpmIncrement 不能整除间距时最终精确达到 targetBpm`() {
        // start=60, target=70, increment=7 → 60→67→70(clamp)
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 70, bpmIncrement = 7, loopsPerStep = 1)

        ramp.advance()
        assertEquals(67, ramp.currentBpm)
        assertFalse(ramp.isComplete())

        ramp.advance()
        assertEquals(70, ramp.currentBpm) // clamped from 74 to 70
        assertTrue(ramp.isComplete())
    }

    @Test
    fun `startBpm 等于 targetBpm 时立即完成`() {
        val ramp = TempoRampUp(startBpm = 120, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 2)
        // reset() 会检测 startBpm >= targetBpm → completed
        ramp.reset()
        assertTrue(ramp.isComplete())
        assertEquals(0, ramp.totalSteps())
        assertEquals(1f, ramp.progressRatio(), 0.001f)
    }

    // === 回调 ===

    @Test
    fun `onTempoChange 在提速时触发`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 1)
        var lastNewBpm: Int? = null
        var callCount = 0
        ramp.onTempoChange = { bpm ->
            lastNewBpm = bpm
            callCount++
        }

        ramp.advance()
        assertEquals(65, lastNewBpm)
        assertEquals(1, callCount)

        ramp.advance()
        assertEquals(70, lastNewBpm)
        assertEquals(2, callCount)
    }

    @Test
    fun `onTempoChange 在非提速的循环不触发`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 2)
        var callCount = 0
        ramp.onTempoChange = { callCount++ }

        ramp.advance() // 只积累，不提速
        assertEquals(0, callCount)
    }

    @Test
    fun `onComplete 达到目标速度时触发一次`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 1)
        var completeCount = 0
        ramp.onComplete = { completeCount++ }

        ramp.advance()
        assertEquals(1, completeCount)

        ramp.advance() // 已经完成，不应再触发
        assertEquals(1, completeCount)
    }

    // === 准确率门控 ===

    @Test
    fun `requireMinAccuracy=false 时准确率不影响提速`() {
        val ramp = TempoRampUp(
            startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 1,
            requireMinAccuracy = false, minAccuracy = 0.9f
        )

        // 即使准确率很低也能提速
        val changed = ramp.advance(accuracy = 0.1f)
        assertTrue(changed)
        assertEquals(65, ramp.currentBpm)
    }

    @Test
    fun `requireMinAccuracy=true 且准确率不达标时不提速`() {
        val ramp = TempoRampUp(
            startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 2,
            requireMinAccuracy = true, minAccuracy = 0.8f
        )

        // 准确率 0.5 < 0.8，不计入循环次数
        val changed = ramp.advance(accuracy = 0.5f)
        assertFalse(changed)
        assertEquals(60, ramp.currentBpm)
        assertEquals(0, ramp.loopsAtCurrentStep) // 不计数

        // 之后两次达标的循环才能提速
        assertFalse(ramp.advance(accuracy = 0.9f))
        assertEquals(1, ramp.loopsAtCurrentStep)

        assertTrue(ramp.advance(accuracy = 1.0f))
        assertEquals(65, ramp.currentBpm)
        assertEquals(0, ramp.loopsAtCurrentStep)
    }

    @Test
    fun `requireMinAccuracy=true 但 accuracy=null 时不门控`() {
        val ramp = TempoRampUp(
            startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 1,
            requireMinAccuracy = true, minAccuracy = 0.99f
        )

        // accuracy 为 null 时视为无准确率信息，不进行门控
        val changed = ramp.advance(accuracy = null)
        assertTrue(changed)
        assertEquals(65, ramp.currentBpm)
    }

    @Test
    fun `requireMinAccuracy=true 且准确率恰好达标时正常计步`() {
        val ramp = TempoRampUp(
            startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 1,
            requireMinAccuracy = true, minAccuracy = 0.8f
        )

        // 0.8 >= 0.8（恰好达标），正常提速
        assertTrue(ramp.advance(accuracy = 0.8f))
        assertEquals(65, ramp.currentBpm)
    }

    @Test
    fun `低准确率不中断已积累的循环计数`() {
        val ramp = TempoRampUp(
            startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 3,
            requireMinAccuracy = true, minAccuracy = 0.8f
        )

        ramp.advance(accuracy = 1.0f) // count=1
        ramp.advance(accuracy = 1.0f) // count=2
        assertEquals(2, ramp.loopsAtCurrentStep)

        ramp.advance(accuracy = 0.5f) // 不达标，count 仍为 2（不重置）
        assertEquals(2, ramp.loopsAtCurrentStep)

        ramp.advance(accuracy = 0.9f) // count=3 → 提速
        assertEquals(65, ramp.currentBpm)
        assertEquals(0, ramp.loopsAtCurrentStep)
    }

    // === reset ===

    @Test
    fun `reset 回到起始状态`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 70, bpmIncrement = 5, loopsPerStep = 1)
        ramp.advance()
        ramp.advance()
        assertEquals(70, ramp.currentBpm)
        assertTrue(ramp.isComplete())

        ramp.reset()
        assertEquals(60, ramp.currentBpm)
        assertEquals(0, ramp.currentStep)
        assertEquals(0, ramp.loopsAtCurrentStep)
        assertFalse(ramp.isComplete())
    }

    @Test
    fun `reset 后配置参数更新生效`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 70, bpmIncrement = 5, loopsPerStep = 1)
        ramp.advance()

        ramp.startBpm = 80
        ramp.targetBpm = 100
        ramp.reset()
        assertEquals(80, ramp.currentBpm)
        assertEquals(0, ramp.currentStep)
        assertFalse(ramp.isComplete())
    }

    @Test
    fun `reset 后 startBpm 大于 targetBpm 自动交换`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 70, bpmIncrement = 5, loopsPerStep = 1)
        ramp.startBpm = 120
        ramp.targetBpm = 80
        ramp.reset()
        // normalizeConfig 交换后 startBpm=80, targetBpm=120
        assertEquals(80, ramp.currentBpm)
        assertEquals(120, ramp.targetBpm)
    }

    // === 配置规范化 ===

    @Test
    fun `targetBpm 小于 startBpm 时自动交换`() {
        val ramp = TempoRampUp(startBpm = 120, targetBpm = 60, bpmIncrement = 5, loopsPerStep = 2)
        // 交换后 startBpm=60, targetBpm=120
        assertEquals(60, ramp.startBpm)
        assertEquals(120, ramp.targetBpm)
        assertEquals(60, ramp.currentBpm)
        assertEquals(12, ramp.totalSteps())
    }

    @Test
    fun `bpmIncrement 小于 1 时 clamp 到 1`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 62, bpmIncrement = 0, loopsPerStep = 1)
        assertEquals(1, ramp.bpmIncrement)
    }

    @Test
    fun `loopsPerStep 小于 1 时 clamp 到 1`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 0)
        assertEquals(1, ramp.loopsPerStep)
        // loopsPerStep=1, 每次 advance 都提速
        assertTrue(ramp.advance())
    }

    @Test
    fun `BPM 超出节拍器范围时 clamp`() {
        val ramp = TempoRampUp(startBpm = 20, targetBpm = 300, bpmIncrement = 5, loopsPerStep = 2)
        assertEquals(40, ramp.startBpm)
        assertEquals(240, ramp.targetBpm)
    }

    @Test
    fun `minAccuracy 超出范围时 clamp`() {
        val ramp = TempoRampUp(
            startBpm = 60, targetBpm = 65, bpmIncrement = 5, loopsPerStep = 1,
            requireMinAccuracy = true, minAccuracy = 2.0f
        )
        // clamp 后 minAccuracy=1.0，必须满分才能提速
        assertFalse(ramp.advance(accuracy = 0.95f))
        assertTrue(ramp.advance(accuracy = 1.0f))
    }

    // === 边界情况 ===

    @Test
    fun `progressRatio 在多步推进中正确更新`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 90, bpmIncrement = 10, loopsPerStep = 1)
        assertEquals(0f, ramp.progressRatio(), 0.001f)

        ramp.advance() // 70, step 1
        assertEquals(0.333f, ramp.progressRatio(), 0.01f)

        ramp.advance() // 80, step 2
        assertEquals(0.667f, ramp.progressRatio(), 0.01f)

        ramp.advance() // 90, step 3, complete
        assertEquals(1f, ramp.progressRatio(), 0.001f)
    }

    @Test
    fun `大跨度渐速 40-240 BPM 端到端`() {
        val ramp = TempoRampUp(startBpm = 40, targetBpm = 240, bpmIncrement = 20, loopsPerStep = 2)
        assertEquals(10, ramp.totalSteps())

        var changes = 0
        ramp.onTempoChange = { changes++ }

        for (i in 0 until 20) { // 10 步 × 2 循环 = 20 次 advance
            ramp.advance()
        }
        assertEquals(240, ramp.currentBpm)
        assertEquals(10, ramp.currentStep)
        assertTrue(ramp.isComplete())
        assertEquals(10, changes) // 10 次提速
    }

    @Test
    fun `effectiveBpm 等于 currentBpm`() {
        val ramp = TempoRampUp(startBpm = 60, targetBpm = 120, bpmIncrement = 5, loopsPerStep = 1)
        assertEquals(ramp.currentBpm, ramp.effectiveBpm())
        ramp.advance()
        assertEquals(ramp.currentBpm, ramp.effectiveBpm())
    }
}
