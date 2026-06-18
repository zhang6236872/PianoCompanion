package com.pianocompanion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pianocompanion.MainActivity
import com.pianocompanion.R

/**
 * Home screen widget for quick practice access.
 * Shows practice stats + tap to start practicing immediately.
 */
class PracticeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        // Load today's practice stats from SharedPreferences
        val sharedPref = context.getSharedPreferences("practice_stats", Context.MODE_PRIVATE)
        val totalSessions = sharedPref.getInt("total_sessions", 0)
        val avgAccuracy = sharedPref.getFloat("avg_accuracy", 0f)
        val lastPracticeDate = sharedPref.getString("last_practice_date", "—")

        val views = RemoteViews(context.packageName, R.layout.widget_practice).apply {
            setTextViewText(R.id.widget_sessions, "$totalSessions 次")
            setTextViewText(R.id.widget_accuracy, "${(avgAccuracy * 100).toInt()}%")

            // Click → open MainActivity (which navigates to practice tab)
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "practice")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    companion object {
        /**
         * Update all widget instances — call after practice session.
         */
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PracticeWidgetProvider::class.java)
            )
            if (widgetIds.isNotEmpty()) {
                val intent = Intent(context, PracticeWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
