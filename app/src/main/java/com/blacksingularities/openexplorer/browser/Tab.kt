package com.blacksingularities.openexplorer.browser

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Jedna karta. Widok WebView powstaje dopiero przy pierwszym wyświetleniu (leniwe przywracanie sesji). */
class Tab(
    val id: Long,
    val incognito: Boolean,
    url: String = "",
    title: String = "",
    /** Karta, z której otwarto tę kartę – wstecz na pierwszej stronie wraca do niej. */
    val parentId: Long? = null,
) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var findIndex by mutableIntStateOf(0)
    var findCount by mutableIntStateOf(0)
    var readerMode by mutableStateOf(false)
    /** Zwiększane po odtworzeniu WebView (np. po awarii procesu strony). */
    var generation by mutableIntStateOf(0)

    /** Strona startowa (kafelki) zamiast WebView. */
    var showHome by mutableStateOf(url.isEmpty())

    /** Adres do wczytania, gdy WebView jeszcze nie istnieje. */
    var pendingUrl: String? = url.ifEmpty { null }

    /** Domena bieżącej strony (czytana także z wątku sieci przez bloker). */
    @Volatile
    var pageHost: String? = null

    var webView: WebView? = null

    /** Połączenie z menedżerem haseł w bieżącym dokumencie (główna ramka) i jego domena. */
    var passReply: androidx.webkit.JavaScriptReplyProxy? = null
    var passHost: String? = null
    var passScheme: String? = null
    /** Skrypt haseł wstrzykiwany automatycznie na początku każdego dokumentu. */
    var passScriptAtStart = false
    /** Ta strona została już wypełniona automatycznie (nie powtarzaj po każdej zmianie formularza). */
    var autoFilled = false
    var container: SwipeRefreshLayout? = null

    val secure: Boolean get() = url.startsWith("https://")

    val host: String
        get() = runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

    val displayTitle: String
        get() = when {
            showHome && url.isEmpty() -> "Nowa karta"
            title.isNotBlank() -> title
            else -> host.ifEmpty { url }
        }
}
