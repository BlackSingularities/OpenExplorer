package com.blacksingularities.openexplorer.browser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONTokener
import com.blacksingularities.openexplorer.R

/** Podgląd kodu strony i jej zasobów: aktualny DOM z WebView i oryginał pobrany z serwera. */
class SourceViewer(private val browser: BrowserState) {
    private val main = Handler(Looper.getMainLooper())

    /** Otwarty podgląd kodu strony (pełny ekran) albo null. */
    var view by mutableStateOf<SourceView?>(null)

    private val userAgent: String get() = browser.current?.webView?.settings?.userAgentString ?: browser.defaultUa

    /** Kod bieżącej strony: aktualny DOM od razu, oryginał z serwera i lista zasobów w tle. */
    fun viewPage() {
        val tab = browser.current ?: return
        if (tab.showHome) return browser.toast(browser.str(R.string.source_home_has_no_code))
        val wv = tab.webView
        val v = SourceView(tab.url, tab.title, tab.incognito, hasDom = wv != null)
        view = v
        if (wv != null) {
            wv.evaluateJavascript(Scripts.SOURCE) { raw -> v.dom = decodeJs(raw) }
            wv.evaluateJavascript(Scripts.RESOURCES) { raw ->
                v.resources = runCatching {
                    val arr = JSONArray(decodeJs(raw))
                    List(arr.length()) { i ->
                        val o = arr.getJSONObject(i)
                        SourceResource(o.optString("type"), o.optString("url"))
                    }
                }.getOrDefault(emptyList())
            }
        }
        fetch(v, userAgent)
    }

    /** „view-source:adres” wpisane w pasek adresu. */
    fun viewUrl(url: String) {
        val target = browser.resolveInput(url).ifEmpty { return }
        val v = SourceView(target, Uri.parse(target).host ?: target, browser.current?.incognito == true)
        view = v
        fetch(v, userAgent)
    }

    /** Otwiera kod zasobu (skryptu, arkusza stylów) z listy zasobów. */
    fun viewResource(url: String) {
        val v = SourceView(url, url.substringAfterLast('/').substringBefore('?').ifBlank { url }, view?.incognito == true)
        v.parent = view
        view = v
        fetch(v, userAgent)
    }

    fun close() {
        view = view?.parent
    }

    private fun decodeJs(raw: String?): String =
        runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: ""

    private fun fetch(view: SourceView, ua: String) {
        val url = view.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            view.serverError = browser.str(R.string.source_http_only)
            return
        }
        val cookies = if (view.incognito) null else runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        Thread {
            val result = runCatching {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", ua)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
                try {
                    val code = conn.responseCode
                    val stream = if (code >= 400) conn.errorStream else conn.inputStream
                    val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                        .find(conn.contentType ?: "")?.groupValues?.get(1)
                    val bytes = stream?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        while (out.size() < MAX_SOURCE) {
                            val r = input.read(buf)
                            if (r < 0) break
                            out.write(buf, 0, r)
                        }
                        out.toByteArray()
                    } ?: ByteArray(0)
                    val cs = runCatching { charset(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
                    val info = SourceInfo(
                        status = code,
                        contentType = conn.contentType ?: "",
                        size = bytes.size.toLong(),
                        truncated = bytes.size >= MAX_SOURCE,
                        headers = conn.headerFields.filterKeys { it != null }
                            .map { (k, v) -> k to v.joinToString(", ") }.sortedBy { it.first.lowercase() },
                    )
                    String(bytes, cs) to info
                } finally {
                    conn.disconnect()
                }
            }
            main.post {
                result.onSuccess { (text, info) -> view.server = text; view.info = info }
                    .onFailure { view.serverError = browser.str(R.string.source_fetch_failed, it.message ?: it.javaClass.simpleName) }
            }
        }.start()
    }

    companion object {
        /** Największy pobierany kod strony (8 MB). */
        const val MAX_SOURCE = 8 * 1024 * 1024
    }
}
