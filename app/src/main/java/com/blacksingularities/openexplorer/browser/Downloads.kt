package com.blacksingularities.openexplorer.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store

/** Pobieranie plików przez systemowy DownloadManager (z ciasteczkami strony). */
class Downloads(
    private val browser: BrowserState,
    private val host: BrowserHost,
    private val settings: Settings,
    private val store: Store,
) {
    private val main = Handler(Looper.getMainLooper())

    /** Strona zaczęła pobieranie (DownloadListener WebView). */
    fun onDownload(tab: Tab, url: String, ua: String, disposition: String?, mime: String?, length: Long) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            host.toast(browser.str(R.string.download_unsupported))
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mime)
        val request = BrowserDialog.Download(url, name, mime, length, ua, tab.incognito, browser.profileOf(tab))
        if (settings.askDownload) browser.dialog = request else start(request)
        // Karta otwarta tylko po to, by pobrać plik, zostaje pusta – zamknij ją.
        if (tab.webView?.url == null && tab.parentId != null) main.post { browser.close(tab.id) }
    }

    fun start(d: BrowserDialog.Download, fileName: String = d.fileName) {
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
