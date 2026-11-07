package com.blacksingularities.openexplorer.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import com.blacksingularities.openexplorer.update.Updater
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store

/**
 * Pobieranie plików: na Androidzie 10+ własnym silnikiem ([DownloadEngine] – postęp, pauza, wznawianie),
 * na starszych przez systemowy DownloadManager (z ciasteczkami strony).
 */
class Downloads(
    private val browser: BrowserState,
    private val host: BrowserHost,
    private val settings: Settings,
    private val store: Store,
) {
    private val main = Handler(Looper.getMainLooper())
    val engine = (host.activity.application as com.blacksingularities.openexplorer.OpenExplorerApp).downloadEngine

    init {
        // Ciasteczka z profilu karty (incognito i kontenery mają własne) – czytane w chwili startu lub wznowienia.
        engine.cookieSource = { url, profile -> browser.cookiesOf(profile)?.getCookie(url) }
        engine.onFinished = { t ->
            if (Updater.foreground) browser.snack(browser.str(R.string.download_finished, t.fileName), browser.str(R.string.download_open)) { open(t) }
        }
    }

    /** Otwiera pobrany plik w aplikacji wybranej przez użytkownika. */
    fun open(t: DownloadTask) {
        val uri = t.uri?.let { Uri.parse(it) }
        if (uri == null || !engine.exists(t)) {
            host.toast(browser.str(R.string.download_missing))
            return
        }
        try {
            host.activity.startActivity(Intent.createChooser(DownloadEngine.viewIntent(uri, t.mime), browser.str(R.string.download_open)))
        } catch (_: Exception) {
            host.toast(browser.str(R.string.library_no_app_for_file))
        }
    }

    /** Strona zaczęła pobieranie (DownloadListener WebView). */
    fun onDownload(tab: Tab, url: String, ua: String, disposition: String?, mime: String?, length: Long) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            host.toast(browser.str(R.string.download_unsupported))
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mime)
        val request = BrowserDialog.Download(url, name, mime, length, ua, tab.incognito, browser.profileOf(tab), referer = tab.url.takeIf { it.startsWith("http") })
        if (settings.askDownload) browser.dialog = request else start(request)
        // Karta otwarta tylko po to, by pobrać plik, zostaje pusta – zamknij ją.
        if (tab.webView?.url == null && tab.parentId != null) main.post { browser.close(tab.id) }
    }

    fun start(d: BrowserDialog.Download, fileName: String = d.fileName) {
        if (DownloadEngine.supported) {
            engine.enqueue(d.url, fileName, d.mimeType, d.userAgent, d.referer, d.profile, d.incognito)
            browser.snack(browser.str(R.string.download_started, fileName), browser.str(R.string.download_show)) { host.showScreen("downloads") }
            return
        }
        try {
            val req = DownloadManager.Request(Uri.parse(d.url)).apply {
                // Ciasteczka z profilu karty (incognito i kontenery mają własne).
                browser.cookiesOf(d.profile)?.getCookie(d.url)?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", d.userAgent)
                setTitle(fileName)
                setDescription(Uri.parse(d.url).host)
                d.mimeType?.let { setMimeType(it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = host.activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            if (!d.incognito) store.addDownload(id)
            host.toast(browser.str(R.string.download_started, fileName))
        } catch (e: Exception) {
            host.toast(browser.str(R.string.settings_filters_error, e.message.orEmpty()))
        }
    }

    /** Pobranie obrazka lub linku z menu kontekstowego. */
    fun download(url: String, incognito: Boolean, profile: String? = null) {
        val name = URLUtil.guessFileName(url, null, null)
        start(BrowserDialog.Download(url, name, null, -1, browser.defaultUa, incognito, profile))
    }
}
