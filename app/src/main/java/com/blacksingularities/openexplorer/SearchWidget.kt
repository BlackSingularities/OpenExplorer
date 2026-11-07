package com.blacksingularities.openexplorer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Widżet wyszukiwania na ekran główny: pole wyszukiwania, wyszukiwanie głosowe i karta incognito. */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_search).apply {
            setOnClickPendingIntent(R.id.widget_logo, pending(context, AppShortcuts.ACTION_NEW_TAB, 0))
            setOnClickPendingIntent(R.id.widget_search, pending(context, AppShortcuts.ACTION_SEARCH, 1))
            setOnClickPendingIntent(R.id.widget_voice, pending(context, AppShortcuts.ACTION_VOICE, 2))
            setOnClickPendingIntent(R.id.widget_incognito, pending(context, AppShortcuts.ACTION_INCOGNITO, 3))
        }
        manager.updateAppWidget(ids, views)
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code, AppShortcuts.intent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
