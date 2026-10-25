package com.blacksingularities.openexplorer.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.Credential
import com.blacksingularities.openexplorer.data.PasswordStore
import com.blacksingularities.openexplorer.data.SavedTab
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store
import com.blacksingularities.openexplorer.data.deviceLanguage
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** Usługi, które dostarcza aktywność (wybór plików, uprawnienia, komunikaty). */
interface BrowserHost {
    val activity: Activity
    fun pickFiles(intent: Intent, callback: ValueCallback<Array<Uri>>)
    fun requestPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit)
    fun toast(message: String)
    fun isDarkTheme(): Boolean
    /** Potwierdzenie tożsamości (odcisk palca, twarz, PIN lub wzór blokady ekranu). */
    fun authenticate(title: String, onResult: (Boolean) -> Unit)
}

/** Propozycja zalogowania zapisanym kontem na stronie z formularzem logowania. */
class LoginOffer(val tabId: Long, val host: String, val accounts: List<Credential>)

/** Pytanie o zapisanie (albo zmianę) hasła po wysłaniu formularza. */
class SavePrompt(
    val tabId: Long,
    val host: String,
    val origin: String,
    val username: String,
    val password: String,
    /** Istniejący login – wtedy to aktualizacja hasła. */
    val existing: Credential?,
)

/** Okna dialogowe zgłaszane przez strony. */
sealed interface BrowserDialog {
    data class Permission(val origin: String, val what: List<String>, val onResult: (Boolean) -> Unit) : BrowserDialog
    data class Download(
        val url: String,
        val fileName: String,
        val mimeType: String?,
        val size: Long,
        val userAgent: String,
        val incognito: Boolean,
    ) : BrowserDialog
    data class LinkMenu(val link: String?, val image: String?, val incognito: Boolean) : BrowserDialog
    data class Ssl(val url: String, val error: String, val handler: SslErrorHandler) : BrowserDialog
    /** „Tylko HTTPS” nie powiodło się – [httpUrl] to oryginalny, nieszyfrowany adres. */
    data class HttpsFallback(val tab: Tab, val httpUrl: String, val reason: String) : BrowserDialog
    data class Qr(val url: String, val title: String) : BrowserDialog
    data class SiteInfo(val tab: Tab) : BrowserDialog
    data class PageZoom(val tab: Tab) : BrowserDialog
}

/** Krótki komunikat na dole ekranu z opcjonalną akcją (np. „Cofnij”). */
class Snack(val message: String, val action: String? = null, val onAction: (() -> Unit)? = null) {
    val id = System.nanoTime()
}

class FullscreenView(val view: View, val callback: WebChromeClient.CustomViewCallback)

@SuppressLint("SetJavaScriptEnabled")
class BrowserState(
    private val host: BrowserHost,
    val settings: Settings,
    val store: Store,
    val adBlocker: AdBlocker,
    val passwords: PasswordStore,
) {
    private val context: Context get() = host.activity

    /** Napis z zasobów – także dla klas pomocniczych (hasła, pobieranie, narzędzia strony). */
    internal fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 1L

    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableStateOf(-1L)
        private set
    var dialog by mutableStateOf<BrowserDialog?>(null)
    var fullscreen by mutableStateOf<FullscreenView?>(null)
    var findMode by mutableStateOf(false)

    /**
     * Przesunięcie pasków w pikselach: 0 = widoczne, [barMax] = schowane. Paski jadą razem
     * z przewijaną treścią (jak w Chrome), a WebView nie zmienia przy tym rozmiaru – stąd płynność.
     */
    var barShift by mutableFloatStateOf(0f)
        private set
    /** Wysokość chowanych pasków (ustawiana przez interfejs; 0 = paski nie chowają się). */
    var barMax = 0f
    private var barAnim: android.animation.ValueAnimator? = null
    private val settleBarsTask = Runnable { settleBars() }

    /** Przycisk „do góry” po przewinięciu daleko w dół i ruchu w górę. */
    var showScrollTop by mutableStateOf(false)
        private set

    /** Ekran haseł odblokowany odciskiem / PIN-em (blokuje się po wyjściu z aplikacji). */
    var passwordsUnlocked by mutableStateOf(false)
    /** Tekst wpisywany w pasku adresu (podpowiedzi). */
    var suggestQuery by mutableStateOf("")

    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }

    /** Menedżer haseł na stronach. */
    val logins = PasswordBridge(this, host, settings, passwords)
    /** Zapis offline, czytanie na głos, zrzuty ekranu. */
    val tools = PageTools(this, host, settings)
    val downloads = Downloads(this, host, settings, store)
    /** Podgląd kodu strony. */
    val source = SourceViewer(this)

    internal val defaultUa: String by lazy {
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv)", ")")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")
    }

    private val desktopUa: String by lazy {
        val chrome = Regex("Chrome/([\\d.]+)").find(defaultUa)?.groupValues?.get(1) ?: "130.0.0.0"
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }

    fun toast(message: String) = host.toast(message)

    var snack by mutableStateOf<Snack?>(null)

    fun snack(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        snack = Snack(message, action, onAction)
    }

    /** Ostatnio zamknięte karty (do przywrócenia). */
    val closed = mutableStateListOf<SavedTab>()

    fun reopenClosed() {
        val t = closed.removeLastOrNull() ?: return
        newTab(t.url)
    }

    /** Przełączenie na sąsiednią kartę tego samego rodzaju (gest na pasku adresu). */
    fun switchRelative(delta: Int) {
        val cur = current ?: return
        val list = tabs.filter { it.incognito == cur.incognito }
        val i = list.indexOf(cur) + delta
        list.getOrNull(i)?.let { select(it.id) }
    }

    // ---------- Karty ----------

    fun restoreSession() {
        // „Usuwaj dane po wyjściu”: nowe uruchomienie zaczyna się od czystej karty, także gdy system zamknął aplikację.
        if (settings.clearOnExit) {
            clearData(cache = true, cookies = true, history = true, storage = true)
            store.saveSession(emptyList(), 0)
        } else if (settings.restoreTabs) {
            val (saved, active) = store.loadSession()
            saved.forEach { tabs += Tab(nextId++, false, it.url, it.title) }
            if (tabs.isNotEmpty()) {
                currentId = tabs[active.coerceIn(0, tabs.lastIndex)].id
                return
            }
        }
        newTab()
    }

    fun newTab(url: String = "", incognito: Boolean = false, background: Boolean = false, parent: Long? = null): Tab {
        val tab = Tab(nextId++, incognito, parentId = parent)
        val index = parent?.let { p -> tabs.indexOfFirst { it.id == p } }?.takeIf { it >= 0 }?.plus(1) ?: tabs.size
        tabs.add(index.coerceAtMost(tabs.size), tab)
        if (url.isNotEmpty()) load(tab, url)
        if (!background) select(tab.id)
        saveSession()
        return tab
    }

    fun select(id: Long) {
        if (id == currentId) return
        current?.let { old ->
            captureThumbnail(old)
            old.webView?.onPause()
        }
        currentId = id
        findMode = false
        logins.onTabSelected()
        showScrollTop = false
        showBars(animate = false)
        current?.webView?.onResume()
        saveSession()
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]
        if (id == currentId) {
            val next = tabs.firstOrNull { it.id == tab.parentId && it.incognito == tab.incognito }
                ?: tabs.getOrNull(index + 1)?.takeIf { it.incognito == tab.incognito }
                ?: tabs.getOrNull(index - 1)?.takeIf { it.incognito == tab.incognito }
                ?: tabs.getOrNull(index + 1) ?: tabs.getOrNull(index - 1)
            if (next != null) select(next.id)
        }
        tabs.removeAt(index)
        if (!tab.incognito && tab.url.isNotEmpty()) {
            val saved = SavedTab(tab.url, tab.title)
            closed += saved
            if (closed.size > 25) closed.removeAt(0)
            snack(str(R.string.browser_tab_closed), str(R.string.common_undo)) { closed.remove(saved); newTab(saved.url) }
        }
        destroy(tab)
        if (tab.incognito && tabs.none { it.incognito }) clearIncognito()
        if (tabs.isEmpty()) newTab()
        saveSession()
    }

    fun closeAll(incognito: Boolean) {
        tabs.filter { it.incognito == incognito }.forEach { tabs.remove(it); destroy(it) }
        if (incognito) clearIncognito()
        if (tabs.none { it.id == currentId }) {
            currentId = -1
            tabs.lastOrNull()?.let { select(it.id) } ?: newTab()
        }
        saveSession()
    }

    private fun destroy(tab: Tab) {
        tab.webView?.let { wv ->
            (tab.container?.parent as? ViewGroup)?.removeView(tab.container)
            wv.stopLoading()
            wv.destroy()
        }
        tab.webView = null
        tab.container = null
    }

    fun saveSession() {
        val normal = tabs.filter { !it.incognito }
        val active = normal.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        store.saveSession(normal.map { SavedTab(it.url, it.title) }, active)
    }

    // ---------- Nawigacja ----------

    /** Tekst z paska adresu: adres albo zapytanie do wyszukiwarki. */
    fun resolveInput(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:") ||
            lower.startsWith("data:") || lower.startsWith("file:") || lower.startsWith("javascript:")
        ) return text
        val looksLikeHost = !text.contains(' ') && (
            Regex("^[\\w.-]+\\.[a-z]{2,}(:\\d+)?([/?#].*)?$", RegexOption.IGNORE_CASE).matches(text) ||
                Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?([/?#].*)?$").matches(text)
            )
        bang(text)?.let { return it }
        if (looksLikeHost) return (if (settings.httpsOnly) "https://" else "http://") + text
        return settings.searchUrl(text)
    }

    private fun bang(text: String): String? = bangUrl(text, deviceLanguage())

    fun navigate(input: String, tab: Tab? = current) {
        if (input.trim().startsWith("view-source:", ignoreCase = true)) {
            return source.viewUrl(input.trim().substring("view-source:".length))
        }
        val url = resolveInput(input)
        if (url.isEmpty() || tab == null) return
        load(tab, url)
    }

    fun load(tab: Tab, url: String) {
        tab.showHome = false
        tab.readerMode = false
        val target = upgrade(url)
        tab.upgradedFrom = url.takeIf { it != target }
        tab.url = target
        tab.pageHost = Uri.parse(target).host
        val wv = tab.webView
        wv?.let { applySettings(it, tab, tab.pageHost) }
        if (wv == null) tab.pendingUrl = target else wv.loadUrl(target, headers())
    }

    private fun headers(): Map<String, String> =
        if (settings.doNotTrack) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    private fun upgrade(url: String): String {
        if (!settings.httpsOnly || !url.startsWith("http://")) return url
        val h = Uri.parse(url).host ?: return url
        if (h == "localhost" || h.endsWith(".local") || Regex("^\\d+(\\.\\d+){3}$").matches(h)) return url
        if (h.lowercase() in httpAllowed) return url
        return "https://" + url.removePrefix("http://")
    }

    /** Domeny bez HTTPS, które użytkownik świadomie otworzył (do zamknięcia aplikacji). */
    private val httpAllowed = HashSet<String>()

    /** „Otwórz bez szyfrowania” w oknie [BrowserDialog.HttpsFallback]. */
    fun openInsecure(tab: Tab, httpUrl: String) {
        Uri.parse(httpUrl).host?.let { httpAllowed += it.lowercase() }
        load(tab, httpUrl)
    }

    /** Strona po zamianie na https:// nie odpowiada – zapytaj, zamiast pokazywać sam błąd. */
    private fun offerHttpFallback(tab: Tab, failedUrl: String, reason: String): Boolean {
        val from = tab.upgradedFrom ?: return false
        if (Uri.parse(from).host != Uri.parse(failedUrl).host) return false
        tab.upgradedFrom = null
        dialog = BrowserDialog.HttpsFallback(tab, from, reason)
        return true
    }

    fun goHome(tab: Tab? = current) {
        tab ?: return
        val home = settings.homepageUrl
        if (home.isNotBlank()) load(tab, resolveInput(home)) else {
            tab.showHome = true
            findMode = false
        }
    }

    /** Obsługa przycisku wstecz. Zwraca false, gdy nie ma dokąd wrócić. */
    fun back(): Boolean {
        val tab = current ?: return false
        if (fullscreen != null) {
            fullscreen?.callback?.onCustomViewHidden()
            fullscreen = null
            return true
        }
        if (findMode) {
            closeFind()
            return true
        }
        if (tab.showHome) {
            if (tab.webView != null && tab.url.isNotEmpty()) {
                tab.showHome = false
                return true
            }
            if (tab.parentId != null || tabs.size > 1 && tab.incognito) {
                close(tab.id)
                return true
            }
            return false
        }
        val wv = tab.webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
            return true
        }
        if (tab.parentId != null && tabs.any { it.id == tab.parentId }) {
            close(tab.id)
            return true
        }
        tab.showHome = true
        return true
    }

    fun forward() {
        current?.webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        val tab = current ?: return
        val wv = tab.webView
        if (wv == null || tab.showHome) return
        if (tab.loading) wv.stopLoading() else wv.reload()
    }

    // ---------- WebView ----------

    /** Kontener z WebView dla karty (tworzony przy pierwszym wyświetleniu). */
    fun containerFor(tab: Tab): SwipeRefreshLayout {
        tab.container?.let { return it }
        val wv = createWebView(tab)
        val swipe = SwipeRefreshLayout(context).apply {
            addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnChildScrollUpCallback { _, _ -> !settings.pullToRefresh || wv.scrollY > 0 }
            setOnRefreshListener {
                wv.reload()
                isRefreshing = false
            }
        }
        tab.container = swipe
        tab.pendingUrl?.let {
            tab.pendingUrl = null
            wv.loadUrl(it, headers())
        }
        return swipe
    }

    private fun createWebView(tab: Tab, attach: Boolean = true): WebView {
        val themed = ContextThemeWrapper(context, if (host.isDarkTheme()) R.style.WebDark else R.style.WebLight)
        val wv = WebView(themed)
        if (tab.incognito && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE)
                WebViewCompat.setProfile(wv, INCOGNITO_PROFILE)
            }
        }
        tab.desktop = tab.desktop || settings.desktopDefault
        applySettings(wv, tab)
        wv.webViewClient = Client(tab)
        wv.webChromeClient = Chrome(tab)
        wv.setDownloadListener { url, ua, disposition, mime, length -> downloads.onDownload(tab, url, ua, disposition, mime, length) }
        wv.setOnLongClickListener { onLongPress(tab, wv) }
        wv.setFindListener { active, count, _ ->
            tab.findIndex = if (count == 0) 0 else active + 1
            tab.findCount = count
        }
        wv.setOnScrollChangeListener { v, _, y, _, oldY ->
            if (tab.id == currentId) onPageScroll(y, y - oldY, v.height)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
            // Rysowanie fragmentów strony tuż poza ekranem – mniej „białych plam” przy szybkim przewijaniu.
            WebSettingsCompat.setOffscreenPreRaster(wv.settings, true)
        }
        wv.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        logins.attach(wv, tab)
        if (attach) tab.webView = wv
        return wv
    }

    fun applySettings(wv: WebView, tab: Tab, pageHost: String? = tab.pageHost) {
        val rule = store.rule(pageHost)
        val desktop = tab.desktop || rule?.desktop == true
        wv.settings.apply {
            javaScriptEnabled = settings.javascript && rule?.javascript != false
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = !settings.blockPopups
            mediaPlaybackRequiresUserGesture = settings.blockAutoplay
            loadsImagesAutomatically = settings.loadImages
            blockNetworkImage = !settings.loadImages
            textZoom = rule?.textZoom?.takeIf { it > 0 } ?: settings.textZoom
            // Potrzebne do otwierania stron zapisanych offline (strony z sieci i tak nie mają dostępu do file://).
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = if (tab.incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            safeBrowsingEnabled = settings.safeBrowsing
            userAgentString = when {
                desktop -> desktopUa
                settings.customUserAgent.isNotBlank() -> settings.customUserAgent
                else -> defaultUa
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, settings.webDarkMode == "auto" && rule?.darkMode != false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, settings.thirdPartyCookies)
        // Systemowe autouzupełnianie (np. menedżer haseł Edge albo Google) tylko na życzenie –
        // domyślnie hasłami zajmuje się wbudowany menedżer.
        wv.importantForAutofill = if (settings.systemAutofill) View.IMPORTANT_FOR_AUTOFILL_AUTO
        else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    /** Ponowne zastosowanie ustawień do wszystkich otwartych kart. */
    fun applySettingsToAll() {
        tabs.forEach { t -> t.webView?.let { applySettings(it, t) } }
    }

    fun toggleDesktop() {
        val tab = current ?: return
        tab.desktop = !tab.desktop
        val wv = tab.webView ?: return
        applySettings(wv, tab)
        wv.reload()
    }

    private inner class Client(val tab: Tab) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase() ?: return false
            when (scheme) {
                "http" -> {
                    val up = upgrade(uri.toString())
                    if (up != uri.toString() && request.isForMainFrame) {
                        tab.upgradedFrom = uri.toString()
                        view.loadUrl(up, headers())
                        return true
                    }
                    return false
                }
                "https" -> {
                    if (request.isForMainFrame) {
                        val h = uri.host
                        if (h != tab.pageHost) {
                            tab.pageHost = h
                            applySettings(view, tab, h)
                        }
                    }
                    return false
                }
                "about", "data", "blob", "javascript", "file" -> return false
                "intent" -> {
                    try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                        intent.component = null
                        intent.selector = null
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            val fallback = intent.getStringExtra("browser_fallback_url")
                            if (!fallback.isNullOrBlank()) view.loadUrl(fallback)
                            else intent.`package`?.let { openExternal(Uri.parse("market://details?id=$it")) }
                        }
                    } catch (_: Exception) {
                    }
                    return true
                }
                else -> {
                    if (request.hasGesture() || request.isForMainFrame) openExternal(uri)
                    return true
                }
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (settings.adBlock && !request.isForMainFrame && adBlocker.isBlocked(request.url.host) &&
                store.rule(tab.pageHost)?.adBlock != false
            ) {
                main.post { tab.blocked++ }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            tab.loading = true
            tab.url = url
            tab.favicon = favicon
            tab.blocked = 0
            tab.readerMode = false
            tab.pageHost = Uri.parse(url).host
            tab.autoFilled = false
            logins.onPageStarted(tab)
            if (tab.id == currentId) {
                showScrollTop = false
                showBars()
            }
            updateNav(view)
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            tab.upgradedFrom = null
            injectCss(view, tab)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val noHttps = error.errorCode == WebViewClient.ERROR_CONNECT || error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ||
                error.errorCode == WebViewClient.ERROR_TIMEOUT
            if (noHttps) offerHttpFallback(tab, request.url.toString(), str(R.string.https_no_response))
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.loading = false
            tab.progress = 100
            tab.url = url
            view.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
            updateNav(view)
            injectCss(view, tab)
            if (settings.webDarkMode == "force" && store.rule(tab.pageHost)?.darkMode != false) view.evaluateJavascript(Scripts.FORCE_DARK, null)
            if (settings.forceZoom) view.evaluateJavascript(Scripts.FORCE_ZOOM, null)
            logins.onPageFinished(view, tab)
            if (!tab.incognito) saveSession()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            tab.url = url
            updateNav(view)
            if (!tab.incognito && settings.saveHistory && !isReload) store.addHistory(url, view.title ?: "")
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val reason = when (error.primaryError) {
                SslError.SSL_EXPIRED -> str(R.string.ssl_expired)
                SslError.SSL_IDMISMATCH -> str(R.string.ssl_id_mismatch)
                SslError.SSL_UNTRUSTED -> str(R.string.ssl_untrusted)
                SslError.SSL_NOTYETVALID -> str(R.string.ssl_not_yet_valid)
                SslError.SSL_DATE_INVALID -> str(R.string.ssl_date_invalid)
                else -> str(R.string.ssl_other)
            }
            // Strona otwarta jako http:// i podniesiona do https:// – zwykle po prostu nie ma certyfikatu.
            if (offerHttpFallback(tab, error.url, str(R.string.https_bad_certificate, reason))) {
                handler.cancel()
                return
            }
            dialog = BrowserDialog.Ssl(error.url, reason, handler)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val url = tab.url
            destroy(tab)
            tab.pendingUrl = url.ifEmpty { null }
            tab.loading = false
            tab.generation++
            host.toast(str(R.string.browser_renderer_crashed))
            return true
        }
    }

    private fun injectCss(view: WebView, tab: Tab) {
        val adOk = settings.adBlock && store.rule(tab.pageHost)?.adBlock != false
        val css = buildString {
            if (adOk && settings.cosmeticFilter) append(Scripts.COSMETIC_CSS)
            if (settings.hideCookieBanners) append(Scripts.COOKIE_CSS)
        }
        if (css.isNotEmpty()) view.evaluateJavascript(Scripts.injectStyle("__oe_filters", css), null)
    }

    private fun updateNav(view: WebView) {
        val tab = tabs.firstOrNull { it.webView === view } ?: return
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

    // ---------- Paski i przewijanie ----------

    private fun onPageScroll(y: Int, dy: Int, viewHeight: Int) {
        if (settings.scrollTopButton) {
            when {
                y < viewHeight -> showScrollTop = false
                dy < -6 && y > viewHeight * 3 -> showScrollTop = true
                dy > 6 -> showScrollTop = false
            }
        }
        if (barMax <= 0f) return
        barAnim?.cancel()
        var shift = (barShift + dy).coerceIn(0f, barMax)
        // U góry strony paski zawsze wracają.
        if (y < barMax) shift = shift.coerceAtMost(y.toFloat().coerceAtLeast(0f))
        barShift = shift
        main.removeCallbacks(settleBarsTask)
        main.postDelayed(settleBarsTask, 160)
    }

    /** Po zatrzymaniu przewijania paski dojeżdżają do pełnego schowania albo pokazania. */
    private fun settleBars() {
        val y = current?.webView?.scrollY ?: 0
        animateBars(if (barShift > barMax / 2 && y > barMax) barMax else 0f)
    }

    fun showBars(animate: Boolean = true) {
        main.removeCallbacks(settleBarsTask)
        if (animate) animateBars(0f) else {
            barAnim?.cancel()
            barShift = 0f
        }
    }

    private fun animateBars(target: Float) {
        barAnim?.cancel()
        if (barShift == target) return
        barAnim = android.animation.ValueAnimator.ofFloat(barShift, target).apply {
            duration = 220
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { barShift = it.animatedValue as Float }
            start()
        }
    }

    fun scrollToTop() {
        showScrollTop = false
        showBars()
        current?.webView?.evaluateJavascript(Scripts.SCROLL_TOP, null)
    }

    /** Domeny do uzupełniania adresu w pasku (liczone raz na otwarcie paska). */
    fun completionHosts(): List<String> = Omnibox.rankHosts(
        store.history.asSequence().take(3000).map { Omnibox.hostOf(it.url) }.toList(),
        store.tiles.map { Omnibox.hostOf(it.url) } + store.bookmarks.map { Omnibox.hostOf(it.url) },
    )

    private inner class Chrome(val tab: Tab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
            if (newProgress >= 100) tab.loading = false
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) {
                tab.title = title
                if (!tab.incognito) store.updateHistoryTitle(view.url ?: tab.url, title)
            }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            tab.favicon = icon
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (settings.blockPopups && !isUserGesture) {
                host.toast(str(R.string.browser_popup_blocked))
                return false
            }
            val child = Tab(nextId++, tab.incognito, parentId = tab.id)
            child.showHome = false
            val index = tabs.indexOf(tab) + 1
            tabs.add(index.coerceIn(0, tabs.size), child)
            containerFor(child)
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            transport.webView = child.webView
            resultMsg.sendToTarget()
            select(child.id)
            return true
        }

        override fun onCloseWindow(window: WebView) {
            tabs.firstOrNull { it.webView === window }?.let { close(it.id) }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreen?.callback?.onCustomViewHidden()
            fullscreen = FullscreenView(view, callback)
        }

        override fun onHideCustomView() {
            fullscreen = null
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            val intent = try {
                fileChooserParams.createIntent().apply {
                    if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            } catch (_: Exception) {
                Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            }
            host.pickFiles(intent, filePathCallback)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val labels = mutableListOf<String>()
            val perms = mutableListOf<String>()
            request.resources.forEach {
                when (it) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> { labels += str(R.string.permission_camera); perms += Manifest.permission.CAMERA }
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> { labels += str(R.string.permission_microphone); perms += Manifest.permission.RECORD_AUDIO }
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> labels += str(R.string.permission_protected_media)
                }
            }
            if (labels.isEmpty()) {
                request.deny()
                return
            }
            main.post {
                dialog = BrowserDialog.Permission(request.origin.host ?: request.origin.toString(), labels) { allow ->
                    if (!allow) request.deny()
                    else if (perms.isEmpty()) request.grant(request.resources)
                    else host.requestPermissions(perms.toTypedArray()) { ok -> if (ok) request.grant(request.resources) else request.deny() }
                }
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            dialog = BrowserDialog.Permission(Uri.parse(origin).host ?: origin, listOf(str(R.string.permission_location))) { allow ->
                if (!allow) callback.invoke(origin, false, false)
                else host.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                ) { ok -> callback.invoke(origin, ok, ok && !tab.incognito) }
            }
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun openExternal(uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            host.toast(str(R.string.browser_no_app, uri.scheme.orEmpty()))
        }
    }

    // ---------- Menu kontekstowe ----------

    private fun onLongPress(tab: Tab, wv: WebView): Boolean {
        val r = wv.hitTestResult
        when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> dialog = BrowserDialog.LinkMenu(r.extra, null, tab.incognito)
            WebView.HitTestResult.IMAGE_TYPE -> dialog = BrowserDialog.LinkMenu(null, r.extra, tab.incognito)
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val image = r.extra
                val handler = Handler(Looper.getMainLooper()) { msg ->
                    dialog = BrowserDialog.LinkMenu(msg.data.getString("url") ?: image, image, tab.incognito)
                    true
                }
                wv.requestFocusNodeHref(handler.obtainMessage())
            }
            else -> return false
        }
        return true
    }

    // ---------- Narzędzia strony ----------

    fun find(query: String) {
        val wv = current?.webView ?: return
        if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
    }

    fun findNext(forward: Boolean) {
        current?.webView?.findNext(forward)
    }

    fun closeFind() {
        current?.webView?.clearMatches()
        current?.let { it.findCount = 0; it.findIndex = 0 }
        findMode = false
    }

    fun readerMode() {
        val tab = current ?: return
        val wv = tab.webView ?: return
        if (tab.readerMode) {
            tab.readerMode = false
            wv.reload()
            return
        }
        wv.evaluateJavascript(Scripts.reader(host.isDarkTheme())) { result ->
            if (result?.contains("ok") == true) tab.readerMode = true
            else host.toast(str(R.string.browser_reader_unavailable))
        }
    }

    fun touchIcon(callback: (String) -> Unit) {
        val wv = current?.webView ?: return callback("")
        wv.evaluateJavascript(Scripts.TOUCH_ICON) { raw ->
            callback(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: "")
        }
    }

    /** Rozmiar tekstu bieżącej strony (zapamiętywany dla domeny). */
    fun setPageZoom(tab: Tab, zoom: Int) {
        val h = tab.pageHost?.removePrefix("www.") ?: return
        val r = store.rule(h) ?: com.blacksingularities.openexplorer.data.SiteRule(h)
        store.saveRule(r.copy(textZoom = if (zoom == settings.textZoom) 0 else zoom))
        tab.webView?.settings?.textZoom = zoom
    }

    /** Zmiana wyjątku dla domeny bieżącej karty i ponowne wczytanie strony. */
    fun updateRule(tab: Tab, change: (com.blacksingularities.openexplorer.data.SiteRule) -> com.blacksingularities.openexplorer.data.SiteRule) {
        val h = tab.pageHost?.removePrefix("www.") ?: return
        val r = store.rule(h) ?: com.blacksingularities.openexplorer.data.SiteRule(h)
        store.saveRule(change(r))
        tab.webView?.let { applySettings(it, tab); it.reload() }
    }

    fun clearSiteData(tab: Tab) {
        val url = tab.url
        val h = Uri.parse(url).host ?: return
        val cm = CookieManager.getInstance()
        cm.getCookie(url)?.split(";")?.forEach { c ->
            val name = c.substringBefore('=').trim()
            listOf(h, "." + h.removePrefix("www."), h.removePrefix("www.")).forEach { d ->
                cm.setCookie("https://" + h, name + "=; Max-Age=0; Path=/; Domain=" + d)
            }
        }
        cm.flush()
        WebStorage.getInstance().deleteOrigin(Uri.parse(url).scheme + "://" + h)
        tab.webView?.reload()
        snack(str(R.string.browser_site_data_cleared, h.removePrefix("www.")))
    }

    fun captureThumbnail(tab: Tab? = current) {
        val view = tab?.container ?: return
        if (tab.showHome || view.width <= 0 || view.height <= 0) return
        try {
            val scale = 0.35f
            val bmp = Bitmap.createBitmap((view.width * scale).toInt(), (view.height * scale).toInt(), Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            view.draw(canvas)
            tab.thumbnail = bmp
        } catch (_: Throwable) {
        }
    }

    // ---------- Dane przeglądania ----------

    private fun clearIncognito() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            main.postDelayed({ runCatching { ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE) } }, 500)
        }
    }

    fun clearData(cache: Boolean, cookies: Boolean, history: Boolean, storage: Boolean) {
        if (cache) {
            tabs.firstNotNullOfOrNull { it.webView }?.clearCache(true) ?: WebView(context).apply { clearCache(true); destroy() }
        }
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        }
        if (storage) WebStorage.getInstance().deleteAllData()
        if (history) {
            store.clearHistory()
            tabs.forEach { it.webView?.clearHistory() }
        }
    }

    fun onPause() {
        current?.webView?.onPause()
        CookieManager.getInstance().flush()
        saveSession()
    }

    fun onResume() {
        current?.webView?.onResume()
    }

    fun onDestroy(finishing: Boolean) {
        tools.shutdown()
        if (finishing && settings.clearOnExit) clearData(cache = true, cookies = true, history = true, storage = true)
        tabs.forEach { destroy(it) }
    }

    companion object {
        const val INCOGNITO_PROFILE = "incognito"

        val BANGS = mapOf(
            "g" to "https://www.google.com/search?q=%s",
            "d" to "https://duckduckgo.com/?q=%s",
            "b" to "https://www.bing.com/search?q=%s",
            "yt" to "https://www.youtube.com/results?search_query=%s",
            // {lang} – język telefonu (Wikipedia i tłumacz w tym języku).
            "w" to "https://{lang}.wikipedia.org/w/index.php?search=%s",
            "en" to "https://en.wikipedia.org/w/index.php?search=%s",
            "gh" to "https://github.com/search?q=%s",
            "m" to "https://www.google.com/maps/search/%s",
            "i" to "https://www.google.com/search?tbm=isch&q=%s",
            "a" to "https://allegro.pl/listing?string=%s",
            "am" to "https://www.amazon.pl/s?k=%s",
            "r" to "https://www.reddit.com/search/?q=%s",
            "t" to "https://translate.google.com/?sl=auto&tl={lang}&text=%s",
            "so" to "https://stackoverflow.com/search?q=%s",
            "x" to "https://x.com/search?q=%s",
            "imdb" to "https://www.imdb.com/find/?q=%s",
        )

        /** Zamienniki sklepów dla telefonów w innym języku niż polski (Allegro i amazon.pl są lokalne). */
        private val BANGS_INTL = mapOf(
            "a" to "https://www.amazon.com/s?k=%s",
            "am" to "https://www.amazon.com/s?k=%s",
        )

        /** Skróty wyszukiwania: „!yt koty”, „!w Kraków”, „!gh compose”… [lang] – kod języka, np. „pl”. */
        fun bangUrl(text: String, lang: String): String? {
            if (!text.startsWith("!") || !text.contains(' ')) return null
            val key = text.substring(1).substringBefore(' ').lowercase()
            val q = java.net.URLEncoder.encode(text.substringAfter(' ').trim(), "UTF-8")
            val pattern = (if (lang == "pl") null else BANGS_INTL[key]) ?: BANGS[key] ?: return null
            return pattern.replace("%s", q).replace("{lang}", lang)
        }
    }
}
