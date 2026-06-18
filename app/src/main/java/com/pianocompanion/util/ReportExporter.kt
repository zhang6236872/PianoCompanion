package com.pianocompanion.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pianocompanion.data.model.SessionRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates and shares practice reports.
 * Supports sharing as text (via Android Share Sheet) or as a text file.
 */
object ReportExporter {

    /**
     * Generate a human-readable practice report from session records.
     */
    fun generateReport(
        sessions: List<SessionRecord>,
        title: String = "钢琴练习报告"
    ): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        sb.appendLine("🎹 $title")
        sb.appendLine("📅 生成时间: ${dateFormat.format(Date())}")
        sb.appendLine()

        if (sessions.isEmpty()) {
            sb.appendLine("暂无练习记录")
            return sb.toString()
        }

        // Summary
        val totalDuration = sessions.sumOf { it.durationMs }
        val totalNotes = sessions.sumOf { it.totalNotes }
        val totalCorrect = sessions.sumOf { it.correctNotes }
        val avgAccuracy = if (totalNotes > 0) totalCorrect.toFloat() / totalNotes else 0f

        sb.appendLine("📊 总览")
        sb.appendLine("├ 练习次数: ${sessions.size} 次")
        sb.appendLine("├ 总时长: ${formatDuration(totalDuration)}")
        sb.appendLine("├ 总音符数: $totalNotes")
        sb.appendLine("└ 平均准确率: ${(avgAccuracy * 100).toInt()}%")
        sb.appendLine()

        // Recent sessions (last 10)
        val recent = sessions.takeLast(10).reversed()
        sb.appendLine("📋 最近练习 (${recent.size} 条)")
        sb.appendLine("─".repeat(40))

        recent.forEach { session ->
            val date = dateFormat.format(Date(session.startTime))
            val duration = formatDuration(session.durationMs)
            val accPercent = (session.accuracy * 100).toInt()

            sb.appendLine("🎵 ${session.scoreTitle}")
            sb.appendLine("   $date · $duration")
            sb.appendLine("   准确率: $accPercent% (${session.correctNotes}/${session.totalNotes})")
            if (session.missedNotes > 0 || session.extraNotes > 0) {
                sb.appendLine("   漏弹: ${session.missedNotes} · 多弹: ${session.extraNotes}")
            }
            sb.appendLine()
        }

        // Best & worst
        val best = sessions.maxByOrNull { it.accuracy }
        val worst = sessions.minByOrNull { it.accuracy }
        if (best != null && sessions.size > 1) {
            sb.appendLine("🏆 最佳: ${best.scoreTitle} (${(best.accuracy * 100).toInt()}%)")
        }
        if (worst != null && sessions.size > 1 && worst != best) {
            sb.appendLine("💪 待提升: ${worst.scoreTitle} (${(worst.accuracy * 100).toInt()}%)")
        }

        sb.appendLine()
        sb.appendLine("— Piano Companion 🎹")

        return sb.toString()
    }

    /**
     * Share report as plain text via Android Share Sheet.
     */
    fun shareAsText(context: Context, report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "🎹 钢琴练习报告")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "分享练习报告"))
    }

    /**
     * Save report to a file and share via FileProvider.
     */
    fun shareAsFile(context: Context, report: String, fileName: String = "practice_report.txt") {
        val reportDir = File(context.cacheDir, "reports")
        reportDir.mkdirs()
        val file = File(reportDir, fileName)
        file.writeText(report)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "🎹 钢琴练习报告")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享练习报告文件"))
    }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        return if (minutes > 0) "${minutes}分${seconds}秒"
        else "${seconds}秒"
    }
}
