package com.blacksingularities.openexplorer.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import com.blacksingularities.openexplorer.R

/** Lista filtrów do pobrania: plik hosts (same domeny) albo lista w formacie Adblock Plus. */
class FilterList(val id: String, val url: String, val hosts: Boolean = false, val minRules: Int = 1000)

/**
 * Blokowanie reklam i śledzenia. Wbudowana lista domen działa od razu; w ustawieniach można
 * dobrać listy StevenBlack (domeny), EasyList, EasyPrivacy i Polskie Filtry (reguły ABP).
 * Lista jest włączona, gdy jej plik leży w pamięci aplikacji.
 */
class AdBlocker(context: Context) {
    private val app = context.applicationContext
    private val dir = File(context.filesDir, "filters")
    private val main = Handler(Looper.getMainLooper())
    private val lock = Mutex()

    @Volatile
    private var engine: FilterEngine = FilterEngine.Builder().apply { addHosts(BUILT_IN) }.build()

    /** Liczba reguł na każdej włączonej liście (stan dla ustawień). */
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Liczba wszystkich reguł (z wbudowaną listą). */
    var size by mutableStateOf(BUILT_IN.size)
        private set

    init {
        Thread { runCatching { reload() } }.apply { isDaemon = true }.start()
    }

    private fun file(list: FilterList) = if (list.hosts) File(app.filesDir, "hosts.txt") else File(dir, "${list.id}.txt")

    fun isEnabled(list: FilterList) = file(list).exists()

    /** Data pobrania listy (0 – nie pobrana). */
    fun updated(list: FilterList): Long = file(list).takeIf { it.exists() }?.lastModified() ?: 0L

    val hasLists: Boolean get() = LISTS.any { isEnabled(it) }

    /**
     * Czy zablokować żądanie podstrony lub zasobu. Wywoływane z wątku sieci WebView
     * dla każdego żądania – tylko odczyt z gotowego, niezmiennego indeksu.
     */
    fun shouldBlock(url: String, pageHost: String?, pageUrl: String?, accept: String?): Boolean =
        engine.shouldBlock(url, pageHost, pageUrl, ReqType.guess(url, accept))

    /** Ukrywanie elementów dla strony; null – strona ma wyjątek na listach. */
    fun cosmetic(pageHost: String?, pageUrl: String?): CosmeticCss? = engine.cosmetic(pageHost, pageUrl)

    /** Pobiera i włącza listę. Zwraca liczbę jej reguł. */
    suspend fun enable(list: FilterList): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            download(list)
            reload()[list.id] ?: 0
        }
    }

    /** Wyłącza listę i usuwa jej plik. */
    suspend fun disable(list: FilterList) = lock.withLock {
        withContext(Dispatchers.IO) {
            file(list).delete()
            reload()
        }
    }

    /** Odświeża wszystkie włączone listy. Zwraca liczbę wszystkich reguł; błąd, gdy któraś się nie pobrała. */
    suspend fun update(): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            var error: Throwable? = null
            for (list in LISTS) {
                if (isEnabled(list)) runCatching { download(list) }.onFailure { error = error ?: it }
            }
            reload()
            error?.let { throw it }
            engine.size
        }
    }

    private fun download(list: FilterList) {
        val conn = URL(list.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            val text = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_SIZE) error(app.getString(R.string.adblock_incomplete))
                }
                buf.toString("UTF-8")
            }
            val lines = text.lines()
            val out: String
            if (list.hosts) {
                val parsed = parse(lines)
                if (parsed.size < list.minRules) error(app.getString(R.string.adblock_incomplete))
                out = parsed.joinToString("\n")
            } else {
                val n = FilterEngine.Builder().addAbp(lines)
                if (n < list.minRules) error(app.getString(R.string.adblock_incomplete))
                out = text
            }
            dir.mkdirs()
            val target = file(list)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(out)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error(app.getString(R.string.adblock_incomplete))
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Buduje indeks od nowa ze wszystkich pobranych list (w tle, przeglądanie działa w tym czasie na starym). */
    private fun reload(): Map<String, Int> {
        val start = SystemClock.elapsedRealtime()
        val b = FilterEngine.Builder()
        b.addHosts(BUILT_IN)
        val found = LinkedHashMap<String, Int>()
        for (list in LISTS) {
            val f = file(list)
            if (!f.exists()) continue
            found[list.id] = runCatching {
                if (list.hosts) b.addHosts(f.useLines { parse(it.toList()) }) else f.useLines { b.addAbp(it) }
            }.getOrDefault(0)
        }
        val e = b.build()
        engine = e
        Log.i("AdBlocker", "Wczytano ${e.networkRules} reguł sieciowych i ${e.cosmeticRules} kosmetycznych w ${SystemClock.elapsedRealtime() - start} ms")
        main.post {
            counts = found
            size = e.size
        }
        return found
    }

    companion object {
        const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        private const val MAX_SIZE = 16 * 1024 * 1024

        /** Dostępne listy w kolejności wyświetlania; nazwy i opisy w napisach adblock_list_*. */
        val LISTS = listOf(
            FilterList("stevenblack", HOSTS_URL, hosts = true),
            FilterList("easylist", "https://easylist.to/easylist/easylist.txt"),
            FilterList("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
            FilterList("polish", "https://raw.githubusercontent.com/MajkiIT/polish-ads-filter/master/polish-adblock-filters/adblock.txt", minRules = 500),
        )

        /** Domena albo któraś z jej nadrzędnych jest na liście (bez samej końcówki typu „pl”). */
        fun matches(host: String?, domains: Set<String>): Boolean {
            var h = host?.lowercase() ?: return false
            while (true) {
                if (h in domains) return true
                val dot = h.indexOf('.')
                if (dot < 0 || dot == h.lastIndexOf('.')) return false
                h = h.substring(dot + 1)
            }
        }

        /** Plik hosts („0.0.0.0 domena” albo sama domena w wierszu), komentarze po „#”. */
        fun parse(lines: List<String>): Set<String> {
            val out = HashSet<String>(lines.size)
            for (raw in lines) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
                val host = if (parts.size >= 2) parts[1] else parts[0]
                if (host == "localhost" || host == "0.0.0.0" || !host.contains('.')) continue
                out += host.lowercase()
            }
            return out
        }

        val BUILT_IN = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
            "googletagservices.com", "googletagmanager.com", "adservice.google.com", "pagead2.googlesyndication.com",
            "adnxs.com", "adsrvr.org", "advertising.com", "amazon-adsystem.com", "criteo.com", "criteo.net",
            "taboola.com", "outbrain.com", "scorecardresearch.com", "quantserve.com", "moatads.com",
            "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
            "adform.net", "adition.com", "yieldlab.net", "teads.tv", "media.net", "zedo.com",
            "hotjar.com", "mouseflow.com", "crazyegg.com", "mixpanel.com", "segment.io", "segment.com",
            "connect.facebook.net", "facebook.net", "ads-twitter.com", "analytics.twitter.com",
            "ads.linkedin.com", "bat.bing.com", "clarity.ms", "adcolony.com", "applovin.com",
            "unityads.unity3d.com", "chartboost.com", "inmobi.com", "mopub.com", "popads.net",
            "propellerads.com", "exoclick.com", "juicyads.com", "trafficjunky.net", "adsterra.com",
            "revcontent.com", "mgid.com", "gemius.pl", "hit.gemius.pl", "adocean.pl", "netsprint.eu",
            "smartadserver.pl", "cpx.to", "admitad.com", "bidswitch.net", "3lift.com", "sharethrough.com",
            "indexww.com", "lijit.com", "sovrn.com", "33across.com", "contextweb.com", "spotxchange.com",
            "springserve.com", "yieldmo.com", "omnitagjs.com", "adsafeprotected.com", "doubleverify.com",
            "imrworldwide.com", "chartbeat.com", "chartbeat.net", "newrelic.com", "nr-data.net",
            "branch.io", "app-measurement.com", "appsflyer.com", "adjust.com", "kochava.com",
            "onesignal.com", "pushwoosh.com", "an.yandex.ru", "mc.yandex.ru",
            "ad.wp.pl", "adtraction.com", "tradedoubler.com", "awin1.com", "zemanta.com", "piwik.pro",
        )
    }
}
