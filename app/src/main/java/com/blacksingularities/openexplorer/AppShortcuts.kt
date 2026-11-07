package com.blacksingularities.openexplorer

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Skróty po przytrzymaniu ikony aplikacji (i z widżetu): nowa karta, karta incognito, wyszukiwanie.
 * Dynamiczne, a nie z XML – identyfikator pakietu różni się między wariantami (np. „.debug”).
 */
object AppShortcuts {
    const val ACTION_NEW_TAB = "com.blacksingularities.openexplorer.action.NEW_TAB"
    const val ACTION_INCOGNITO = "com.blacksingularities.openexplorer.action.INCOGNITO"
    const val ACTION_SEARCH = "com.blacksingularities.openexplorer.action.SEARCH"
    /** Wyszukiwanie głosowe z widżetu. */
    const val ACTION_VOICE = "com.blacksingularities.openexplorer.action.VOICE"

    fun intent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun publish(context: Context) {
        val list = listOf(
            Triple(ACTION_SEARCH, R.string.shortcut_search, R.drawable.ic_shortcut_search),
            Triple(ACTION_NEW_TAB, R.string.shortcut_new_tab, R.drawable.ic_shortcut_add),
            Triple(ACTION_INCOGNITO, R.string.shortcut_incognito, R.drawable.ic_shortcut_incognito),
        ).mapIndexed { rank, (action, label, icon) ->
            ShortcutInfoCompat.Builder(context, action.substringAfterLast('.').lowercase())
                .setShortLabel(context.getString(label))
                .setLongLabel(context.getString(label))
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(intent(context, action))
                .setRank(rank)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}
