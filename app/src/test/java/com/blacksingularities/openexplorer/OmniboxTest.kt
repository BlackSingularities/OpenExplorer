package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.Omnibox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmniboxTest {
    @Test
    fun complete() {
        val hosts = listOf("youtube.com", "github.com")
        assertEquals("youtube.com", Omnibox.complete("you", hosts))
        assertEquals("YOUtube.com", Omnibox.complete("YOU", hosts))
        assertEquals("www.github.com", Omnibox.complete("www.gi", hosts))
        assertEquals("https://github.com", Omnibox.complete("https://gi", hosts))
        assertNull(Omnibox.complete("youtube.com", hosts))
        assertNull(Omnibox.complete("you tube", hosts))
        assertNull(Omnibox.complete("", hosts))
    }

    @Test
    fun rankHosts() {
        val ranked = Omnibox.rankHosts(listOf("a.pl", "b.pl", "b.pl", "", "b.pl"), preferred = listOf("c.pl"))
        assertEquals(listOf("c.pl", "b.pl", "a.pl"), ranked)
    }

    @Test
    fun hostOf() {
        assertEquals("example.com", Omnibox.hostOf("https://WWW.Example.com:8080/a?b#c"))
        assertEquals("example.com", Omnibox.hostOf("example.com/path"))
    }
}
