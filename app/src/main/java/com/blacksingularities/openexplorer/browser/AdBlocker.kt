package com.blacksingularities.openexplorer.browser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Blokowanie reklam i śledzenia po domenach (dopasowanie domeny i wszystkich jej nadrzędnych).
 * Wbudowana lista działa od razu; pełną listę hostów (StevenBlack) można pobrać w ustawieniach.
 */
class AdBlocker(context: Context) {
    private val file = File(context.filesDir, "hosts.txt")

    @Volatile
    private var domains: Set<String> = BUILT_IN

    val size: Int get() = domains.size

    init {
        Thread {
            if (file.exists()) {
                runCatching { domains = BUILT_IN + parse(file.readLines()) }
            }
        }.apply { isDaemon = true }.start()
    }

    fun isBlocked(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        val d = domains
        while (true) {
            if (h in d) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.lastIndexOf('.')) return false
            h = h.substring(dot + 1)
        }
    }

    /** Pobiera pełną listę filtrów. Zwraca liczbę domen. */
    suspend fun update(): Int = withContext(Dispatchers.IO) {
        val conn = URL(HOSTS_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            val lines = conn.inputStream.bufferedReader().readLines()
            val parsed = parse(lines)
            if (parsed.size < 1000) error("Lista filtrów jest niepełna")
            file.writeText(parsed.joinToString("\n"))
            domains = BUILT_IN + parsed
            domains.size
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(lines: List<String>): Set<String> {
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

    companion object {
        const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

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
            "onesignal.com", "pushwoosh.com", "yandex.ru/ads", "an.yandex.ru", "mc.yandex.ru",
            "ad.wp.pl", "adtraction.com", "tradedoubler.com", "awin1.com", "zemanta.com", "piwik.pro",
        )
    }
}
