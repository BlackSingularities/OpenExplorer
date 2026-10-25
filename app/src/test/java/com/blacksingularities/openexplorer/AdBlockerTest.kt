package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.AdBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {
    private val list = setOf("doubleclick.net", "an.yandex.ru")

    @Test
    fun parentDomains() {
        assertTrue(AdBlocker.matches("doubleclick.net", list))
        assertTrue(AdBlocker.matches("stats.g.DoubleClick.net", list))
        assertTrue(AdBlocker.matches("an.yandex.ru", list))
        assertFalse(AdBlocker.matches("yandex.ru", list))
        assertFalse(AdBlocker.matches("notdoubleclick.net", list))
        assertFalse(AdBlocker.matches("example.net", setOf("net")))
        assertFalse(AdBlocker.matches(null, list))
    }

    @Test
    fun hostsFile() {
        val parsed = AdBlocker.parse(
            listOf(
                "# StevenBlack",
                "127.0.0.1 localhost",
                "0.0.0.0 0.0.0.0",
                "0.0.0.0 Ads.Example.com # reklamy",
                "0.0.0.0\ttracker.example.org",
                "samadomena.pl",
                "",
                "0.0.0.0 broadcasthost",
            ),
        )
        assertEquals(setOf("ads.example.com", "tracker.example.org", "samadomena.pl"), parsed)
    }

    /** Wbudowana lista dopasowuje tylko domeny – wpis ze ścieżką nigdy by nie zadziałał. */
    @Test
    fun builtInHasOnlyHosts() {
        AdBlocker.BUILT_IN.forEach { assertFalse(it, it.contains('/') || it.contains(' ') || it != it.lowercase()) }
    }
}
