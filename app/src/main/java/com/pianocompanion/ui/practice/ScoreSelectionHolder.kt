package com.pianocompanion.ui.practice

import com.pianocompanion.data.model.Score
import java.util.concurrent.atomic.AtomicReference

/**
 * 跨页面共享的「待练习乐谱」持有器。
 *
 * ## 背景
 * 导航框架（NavController）以字符串 route 为标识，无法直接传递包含音符列表的
 * [Score] 对象。此前从乐谱库 / OMR 识别页导航到练习页时，被选中的乐谱实际上
 * 没有被传递——练习页只能展示自己的内置乐谱选择器。
 *
 * 本持有器是一个轻量的进程内单例，充当「乐谱库 → 练习页」「生成器 → 练习页」
 * 之间的乐谱传递桥梁。它只存活于应用进程内存中（不持久化），因此：
 * - 进程销毁后清空（符合预期：练习页本就有自己的乐谱选择器兜底）
 * - 不引入任何序列化开销
 *
 * ## 线程安全
 * 使用 [AtomicReference] 保证主线程写入与读取的可见性。练习页的 ViewModel 在
 * `init` 中调用 [consume] 一次性取走待练习乐谱。
 */
object ScoreSelectionHolder {

    private val pending = AtomicReference<Score?>(null)

    /**
     * 存入一个待练习乐谱。调用方在存入后应立即导航到练习页。
     */
    fun set(score: Score) {
        pending.set(score)
    }

    /**
     * 取出并清除待练习乐谱（一次性消费）。若无待练习乐谱则返回 null。
     */
    fun consume(): Score? = pending.getAndSet(null)

    /**
     * 查看当前是否有待练习乐谱（不清除）。主要用于测试与诊断。
     */
    fun peek(): Score? = pending.get()

    /**
     * 清除待练习乐谱（不消费）。
     */
    fun clear() {
        pending.set(null)
    }
}
