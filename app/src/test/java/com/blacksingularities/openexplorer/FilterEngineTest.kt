package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.FilterEngine
import com.blacksingularities.openexplorer.browser.ReqType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private fun engine(vararg lines: String) = FilterEngine.Builder().apply { addAbp(lines.toList()) }.build()

    private val net = engine(
        "[Adblock Plus 2.0]",
        "! komentarz",
        "||ads.example.com^",
        "||tracker.net^\$third-party",
        "/banner/*/img^",
        "|https://cdn.example.org/ad.js|",
        "||example.com/ads/*.gif",
        "@@||ads.example.com/allowed^",
        "||script.io^\$script",
        "||img.io^\$~image",
        "||only.io^\$domain=news.pl|~sport.news.pl",
        "||imp.io^\$important",
        "@@||imp.io^",
        "@@||trusted.pl^\$document",
        "-reklama-\$image",
    )

    private fun blocked(url: String, page: String? = "site.pl", type: Int = ReqType.SCRIPT, pageUrl: String? = null) =
        net.shouldBlock(url, page, pageUrl, type)

    @Test
    fun domainAnchor() {
        assertTrue(blocked("https://ads.example.com/x.js"))
        assertTrue(blocked("https://sub.ADS.example.com/x"))
        assertFalse(blocked("https://notads.example.com/x"))
        assertFalse(blocked("https://example.com/"))
        assertTrue(blocked("http://ads.example.com:8080/a"))
    }

    @Test
    fun pathsWildcardsAndSeparators() {
        assertTrue(blocked("https://foo.com/banner/123/img?x=1"))
        assertTrue(blocked("https://foo.com/banner/1/img"))
        assertFalse(blocked("https://foo.com/banner/1/img.png"))
        assertTrue(blocked("https://cdn.example.org/ad.js"))
        assertFalse(blocked("https://cdn.example.org/ad.js?v=1"))
        assertTrue(blocked("https://www.example.com/ads/x/y.gif"))
        assertFalse(blocked("https://www.example.com/ads/x/y.png"))
        assertFalse(blocked("https://www.example.com/news/ads.gif"))
    }

    @Test
    fun options() {
        assertTrue(blocked("https://tracker.net/p", page = "site.pl"))
        assertFalse(blocked("https://tracker.net/p", page = "www.tracker.net"))
        assertTrue(blocked("https://script.io/a", type = ReqType.SCRIPT))
        assertFalse(blocked("https://script.io/a", type = ReqType.IMAGE))
        assertFalse(blocked("https://img.io/a", type = ReqType.IMAGE))
        assertTrue(blocked("https://img.io/a", type = ReqType.SCRIPT))
        assertTrue(blocked("https://only.io/a", page = "news.pl"))
        assertTrue(blocked("https://only.io/a", page = "www.news.pl"))
        assertFalse(blocked("https://only.io/a", page = "sport.news.pl"))
        assertFalse(blocked("https://only.io/a", page = "other.pl"))
        assertTrue(blocked("https://x.pl/-reklama-1.jpg", type = ReqType.IMAGE))
        assertFalse(blocked("https://x.pl/-reklama-1.jpg", type = ReqType.SCRIPT))
        // Kilka możliwych rodzajów naraz (nieznany rodzaj żądania).
        assertTrue(blocked("https://script.io/a", type = ReqType.SCRIPT or ReqType.XHR))
    }

    @Test
    fun exceptions() {
        assertFalse(blocked("https://ads.example.com/allowed/a.js"))
        assertTrue(blocked("https://ads.example.com/allowedx/a.js"))
        assertTrue(blocked("https://imp.io/a"))
        assertFalse(blocked("https://ads.example.com/x", page = "trusted.pl", pageUrl = "https://trusted.pl/artykul"))
        assertFalse(blocked("https://ads.example.com/x", page = "www.trusted.pl"))
    }

    @Test
    fun hostsListsMergeWithRules() {
        val e = FilterEngine.Builder().apply {
            addHosts(setOf("doubleclick.net"))
            addAbp(listOf("@@||doubleclick.net/ok^"))
        }.build()
        assertTrue(e.shouldBlock("https://stats.g.doubleclick.net/x", "site.pl"))
        assertFalse(e.shouldBlock("https://doubleclick.net/ok/1", "site.pl"))
        assertEquals(2, e.networkRules)
    }

    @Test
    fun unsupportedRulesAreSkipped() {
        val b = FilterEngine.Builder()
        val bad = listOf(
            "", "!komentarz", "[Adblock Plus 2.0]", "# komentarz", "||", "|", "|||", "@@", "$", "*", "^", "##", "#@#",
            "||a.pl^\$domain=", "/reklama[0-9]+/", "||x.com^\$redirect=noopjs", "||x.com^\$popup", "a\$b\$c",
            "||x.com^\$document", "||x.com^\$csp=script-src 'none'", "example.org##+js(abort-on-property-read, x)",
            "news.pl#?#div:-abp-has(.ad)", "news.pl##div:has-text(Reklama)", "news.pl#\$#body { overflow: auto }",
            "news.pl##.a:style(display: none)", "google.*##.reklama", "x.pl##.a{color:red}", "||żółw.pl^",
        )
        bad.forEach { assertFalse(it, b.addLine(it)) }
        val e = b.build()
        assertEquals(0, e.size)
        assertFalse(e.shouldBlock("https://x.com/a.js", "site.pl"))
    }

    @Test
    fun countsAcceptedRules() {
        val b = FilterEngine.Builder()
        val n = b.addAbp(listOf("! tytuł", "||a.pl^", "||b.pl^\$third-party", "##.ad", "a.pl##.x", "/regex/", "||c.pl^\$popup"))
        assertEquals(4, n)
        val e = b.build()
        assertEquals(2, e.networkRules)
        assertEquals(2, e.cosmeticRules)
    }

    private val cos = engine(
        "##.ad-banner",
        "##.generic2",
        "##div[id^=\"google_ads\"]",
        "example.com##.promo",
        "example.com,~shop.example.com##.side-ad",
        "~forum.pl##.everywhere-but-forum",
        "example.com#@#.ad-banner",
        "#@#.generic2",
        "news.pl##div:has-text(Reklama)",
        "@@||nogeneric.pl^\$generichide",
        "@@||nohide.pl^\$elemhide",
    )

    @Test
    fun cosmeticPerDomain() {
        val c = cos.cosmetic("www.example.com")!!
        assertTrue(c.specific.contains(".promo"))
        assertTrue(c.specific.contains(".side-ad"))
        assertTrue(c.specific.contains(".everywhere-but-forum"))
        assertFalse(c.generic.contains(".ad-banner"))
        assertTrue(c.generic.contains("div[id^=\"google_ads\"]"))
        assertFalse(c.generic.contains(".generic2"))

        val shop = cos.cosmetic("shop.example.com")!!
        assertTrue(shop.specific.contains(".promo"))
        assertFalse(shop.specific.contains(".side-ad"))

        val forum = cos.cosmetic("forum.pl")!!
        assertFalse(forum.specific.contains(".everywhere-but-forum"))
        assertTrue(forum.generic.contains(".ad-banner"))
        assertFalse(forum.specific.contains(".promo"))
    }

    @Test
    fun cosmeticPageExceptions() {
        assertEquals("", cos.cosmetic("nogeneric.pl")!!.generic)
        assertNull(cos.cosmetic("www.nohide.pl"))
        assertNotNull(cos.cosmetic("news.pl"))
        assertFalse(cos.cosmetic("news.pl")!!.specific.contains("has-text"))
    }

    @Test
    fun cssIsSplitIntoChunks() {
        val e = engine(*Array(45) { "##.reklama$it" })
        val css = e.cosmetic("a.pl")!!.generic
        assertEquals(3, Regex("\\{display:none!important\\}").findAll(css).count())
        assertTrue(css.startsWith(".reklama0,.reklama1,"))
    }

    @Test
    fun requestTypeGuess() {
        assertEquals(ReqType.SCRIPT, ReqType.guess("https://a.pl/x/app.js?v=2", "*/*"))
        assertEquals(ReqType.IMAGE, ReqType.guess("https://a.pl/x", "image/avif,image/webp,*/*;q=0.8"))
        assertEquals(ReqType.STYLESHEET, ReqType.guess("https://a.pl/x", "text/css,*/*;q=0.1"))
        assertEquals(ReqType.SUBDOCUMENT, ReqType.guess("https://a.pl/frame", "text/html,application/xhtml+xml"))
        assertEquals(ReqType.FONT, ReqType.guess("https://a.pl/f.woff2", null))
        assertEquals(ReqType.WEBSOCKET, ReqType.guess("wss://a.pl/socket", null))
        val unknown = ReqType.guess("https://a.pl/collect", "*/*")
        assertTrue(unknown and ReqType.SCRIPT != 0 && unknown and ReqType.XHR != 0)
        assertEquals(0, unknown and ReqType.IMAGE)
    }

    @Test
    fun baseDomains() {
        assertEquals("example.com", FilterEngine.baseDomain("a.b.example.com"))
        assertEquals("wp.pl", FilterEngine.baseDomain("sport.wp.pl"))
        assertEquals("bbc.co.uk", FilterEngine.baseDomain("www.bbc.co.uk"))
        assertEquals("sklep.com.pl", FilterEngine.baseDomain("www.sklep.com.pl"))
        assertEquals("192.168.1.1", FilterEngine.baseDomain("192.168.1.1"))
        assertEquals("localhost", FilterEngine.baseDomain("localhost"))
    }

    @Test
    fun subdomains() {
        assertTrue(FilterEngine.isSubdomain("a.news.pl", "news.pl"))
        assertTrue(FilterEngine.isSubdomain("news.pl", "news.pl"))
        assertFalse(FilterEngine.isSubdomain("fakenews.pl", "news.pl"))
    }

    /** Lista wielkości EasyList + EasyPrivacy: wczytanie i dopasowanie muszą być szybkie (bez przeglądania wszystkich reguł). */
    @Test
    fun largeListIsIndexed() {
        val lines = ArrayList<String>()
        for (i in 0 until 90_000) lines += "||ads$i.example$i.com^"
        for (i in 0 until 6_000) lines += "||track$i.net^\$third-party"
        for (i in 0 until 12_000) lines += "/banner$i/*.gif"
        for (i in 0 until 1_000) lines += "@@||cdn$i.org/ok^"
        for (i in 0 until 14_000) lines += "##.reklama-$i"
        for (i in 0 until 15_000) lines += "site${i % 3000}.pl##.box-$i"
        val t0 = System.nanoTime()
        val e = FilterEngine.Builder().apply { addAbp(lines) }.build()
        val loadMs = (System.nanoTime() - t0) / 1_000_000
        assertEquals(lines.size, e.size)

        val urls = (0 until 20_000).map { i ->
            when (i % 4) {
                0 -> "https://ads${i % 90_000}.example${i % 90_000}.com/x.js"
                1 -> "https://cdn.news.pl/banner${i % 12_000}/a/b.gif"
                2 -> "https://www.site.pl/static/js/app.$i.js?v=3"
                else -> "https://track${i % 6000}.net/pixel"
            }
        }
        val t1 = System.nanoTime()
        var hits = 0
        for (u in urls) if (e.shouldBlock(u, "www.site.pl", null, ReqType.SCRIPT or ReqType.IMAGE)) hits++
        val matchUs = (System.nanoTime() - t1) / 1_000 / urls.size
        assertEquals(15_000, hits)
        val t2 = System.nanoTime()
        val css = e.cosmetic("www.site42.pl")!!
        val cosUs = (System.nanoTime() - t2) / 1_000
        assertTrue(css.specific.contains(".box-42,"))
        println("FilterEngine: ${e.size} reguł, wczytanie $loadMs ms, dopasowanie $matchUs µs/żądanie, kosmetyka $cosUs µs, ogólny CSS ${css.generic.length} znaków")
        assertTrue("dopasowanie $matchUs µs", matchUs < 2_000)
    }
}
