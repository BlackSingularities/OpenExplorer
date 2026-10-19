package com.blacksingularities.openexplorer.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
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
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.SavedTab
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** Usługi, które dostarcza aktywność (wybór plików, uprawnienia, komunikaty). */
interface BrowserHost {
    val activity: Activity
    fun pickFiles(intent: Intent, callback: ValueCallback<Array<Uri>>)
    fun requestPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit)
    fun toast(message: String)
    fun isDarkTheme(): Boolean
}

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
    data class Source(val title: String, val html: String) : BrowserDialog
    data class Ssl(val url: String, val error: String, val handler: SslErrorHandler) : BrowserDialog
}

class FullscreenView(val view: View, val callback: WebChromeClient.CustomViewCallback)

@SuppressLint("SetJavaScriptEnabled")
class BrowserState(
    private val host: BrowserHost,
    val settings: Settings,
    val store: Store,
    val adBlocker: AdBlocker,
) {
    private val context: Context get() = host.activity
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 1L

    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableStateOf(-1L)
        private set
    var dialog by mutableStateOf<BrowserDialog?>(null)
    var fullscreen by mutableStateOf<FullscreenView?>(null)
    var findMode by mutableStateOf(false)
    var barsVisible by mutableStateOf(true)
    /** Tekst wpisywany w pasku adresu (podpowiedzi). */
    var suggestQuery by mutableStateOf("")

    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }

    private val defaultUa: String by lazy {
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv)", ")")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")
    }

    private val desktopUa: String by lazy {
        val chrome = Regex("Chrome/([\\d.]+)").find(defaultUa)?.groupValues?.get(1) ?: "130.0.0.0"
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }

    fun toast(message: String) = host.toast(message)

    // ---------- Karty ----------

    fun restoreSession() {
        if (settings.restoreTabs) {
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
        barsVisible = true
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
        if (looksLikeHost) return (if (settings.httpsOnly) "https://" else "http://") + text
        return settings.searchUrl(text)
    }

    fun navigate(input: String, tab: Tab? = current) {
        val url = resolveInput(input)
        if (url.isEmpty() || tab == null) return
        load(tab, url)
    }

    fun load(tab: Tab, url: String) {
        tab.showHome = false
        tab.readerMode = false
        val target = upgrade(url)
        tab.url = target
        val wv = tab.webView
        if (wv == null) tab.pendingUrl = target else wv.loadUrl(target, headers())
    }

    private fun headers(): Map<String, String> =
        if (settings.doNotTrack) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    private fun upgrade(url: String): String {
        if (!settings.httpsOnly || !url.startsWith("http://")) return url
        val h = Uri.parse(url).host ?: return url
        if (h == "localhost" || h.endsWith(".local") || Regex("^\\d+(\\.\\d+){3}$").matches(h)) return url
        return "https://" + url.removePrefix("http://")
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
        wv.setDownloadListener { url, ua, disposition, mime, length -> onDownload(tab, url, ua, disposition, mime, length) }
        wv.setOnLongClickListener { onLongPress(tab, wv) }
        wv.setFindListener { active, count, _ ->
            tab.findIndex = if (count == 0) 0 else active + 1
            tab.findCount = count
        }
        var lastY = 0
        wv.setOnScrollChangeListener { _, _, y, _, _ ->
            if (tab.id == currentId && settings.hideBarsOnScroll) {
                val dy = y - lastY
                if (y < 24) barsVisible = true
                else if (dy > 18) barsVisible = false
                else if (dy < -18) barsVisible = true
            }
            lastY = y
        }
        if (attach) tab.webView = wv
        return wv
    }

    fun applySettings(wv: WebView, tab: Tab) {
        wv.settings.apply {
            javaScriptEnabled = settings.javascript
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
            textZoom = settings.textZoom
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = if (tab.incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            safeBrowsingEnabled = settings.safeBrowsing
            userAgentString = when {
                tab.desktop -> desktopUa
                settings.customUserAgent.isNotBlank() -> settings.customUserAgent
                else -> defaultUa
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, settings.webDarkMode == "auto")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, settings.thirdPartyCookies)
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
                        view.loadUrl(up, headers())
                        return true
                    }
                    return false
                }
                "https", "about", "data", "blob", "javascript" -> return false
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
            if (settings.adBlock && !request.isForMainFrame && adBlocker.isBlocked(request.url.host)) {
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
            updateNav(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.loading = false
            tab.progress = 100
            tab.url = url
            view.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
            updateNav(view)
            if (settings.webDarkMode == "force") view.evaluateJavascript(Scripts.FORCE_DARK, null)
            if (settings.forceZoom) view.evaluateJavascript(Scripts.FORCE_ZOOM, null)
            if (!tab.incognito) saveSession()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            tab.url = url
            updateNav(view)
            if (!tab.incognito && settings.saveHistory && !isReload) store.addHistory(url, view.title ?: "")
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val reason = when (error.primaryError) {
                SslError.SSL_EXPIRED -> "Certyfikat wygasł."
                SslError.SSL_IDMISMATCH -> "Certyfikat wystawiono dla innej domeny."
                SslError.SSL_UNTRUSTED -> "Certyfikat nie pochodzi z zaufanego źródła."
                SslError.SSL_NOTYETVALID -> "Certyfikat nie jest jeszcze ważny."
                SslError.SSL_DATE_INVALID -> "Nieprawidłowa data certyfikatu."
                else -> "Połączenie nie jest bezpieczne."
            }
            dialog = BrowserDialog.Ssl(error.url, reason, handler)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val url = tab.url
            destroy(tab)
            tab.pendingUrl = url.ifEmpty { null }
            tab.loading = false
            tab.generation++
            host.toast("Strona przestała działać – wczytano ją ponownie")
            return true
        }
    }

    private fun updateNav(view: WebView) {
        val tab = tabs.firstOrNull { it.webView === view } ?: return
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

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
                host.toast("Zablokowano wyskakujące okno")
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
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> { labels += "kamera"; perms += Manifest.permission.CAMERA }
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> { labels += "mikrofon"; perms += Manifest.permission.RECORD_AUDIO }
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> labels += "odtwarzanie chronionych treści"
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
            dialog = BrowserDialog.Permission(Uri.parse(origin).host ?: origin, listOf("lokalizacja")) { allow ->
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
            host.toast("Brak aplikacji do otwarcia: ${uri.scheme}")
        }
    }

    // ---------- Pobieranie ----------

    private fun onDownload(tab: Tab, url: String, ua: String, disposition: String?, mime: String?, length: Long) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            host.toast("Ten rodzaj pobierania nie jest obsługiwany")
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mime)
        val request = BrowserDialog.Download(url, name, mime, length, ua, tab.incognito)
        if (settings.askDownload) dialog = request else startDownload(request)
        // Karta otwarta tylko po to, by pobrać plik, zostaje pusta – zamknij ją.
        if (tab.webView?.url == null && tab.parentId != null) main.post { close(tab.id) }
    }

    fun startDownload(d: BrowserDialog.Download, fileName: String = d.fileName) {
        try {
            val req = DownloadManager.Request(Uri.parse(d.url)).apply {
                CookieManager.getInstance().getCookie(d.url)?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", d.userAgent)
                setTitle(fileName)
                setDescription(Uri.parse(d.url).host)
                d.mimeType?.let { setMimeType(it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            if (!d.incognito) store.addDownload(id)
            host.toast("Pobieranie: $fileName")
        } catch (e: Exception) {
            host.toast("Nie udało się pobrać: ${e.message}")
        }
    }

    /** Pobranie obrazka lub linku z menu kontekstowego. */
    fun download(url: String, incognito: Boolean) {
        val name = URLUtil.guessFileName(url, null, null)
        startDownload(BrowserDialog.Download(url, name, null, -1, defaultUa, incognito))
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
            else host.toast("Tryb czytania nie jest dostępny na tej stronie")
        }
    }

    fun viewSource() {
        val tab = current ?: return
        tab.webView?.evaluateJavascript(Scripts.SOURCE) { raw ->
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw ?: ""
            dialog = BrowserDialog.Source(tab.url, html)
        }
    }

    fun touchIcon(callback: (String) -> Unit) {
        val wv = current?.webView ?: return callback("")
        wv.evaluateJavascript(Scripts.TOUCH_ICON) { raw ->
            callback(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: "")
        }
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
        if (finishing && settings.clearOnExit) clearData(cache = true, cookies = true, history = true, storage = true)
        tabs.forEach { destroy(it) }
    }

    companion object {
        const val INCOGNITO_PROFILE = "incognito"
    }
}
