package com.pianocompanion.data.model

/**
 * Practice modes for different use cases.
 * 
 * - NORMAL: Standard practice with score following, real-time feedback
 * - FOLLOW: Follow-along mode — only tracks position, no error detection
 * - EXAM:   Exam mode — strict scoring, no real-time hints, results at end
 */
enum class PracticeMode(val displayName: String, val description: String) {
    NORMAL("自由练习", "实时弹奏反馈，适合日常练习"),
    FOLLOW("跟谱模式", "只跟踪进度，不判定对错"),
    EXAM("考试模式", "严格评分，结束后出报告")
}
