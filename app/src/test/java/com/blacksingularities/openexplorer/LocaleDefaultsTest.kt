package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.data.defaultTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skróty wyszukiwania i domyślne kafelki zależne od języka telefonu. */
class LocaleDefaultsTest {
    @Test
    fun wikipediaInDeviceLanguage() {
        assertEquals("https://pl.wikipedia.org/w/index.php?search=Krak%C3%B3w", BrowserState.bangUrl("!w Kraków", "pl"))
        assertEquals("https://en.wikipedia.org/w/index.php?search=London", BrowserState.bangUrl("!w London", "en"))
        assertEquals("https://de.wikipedia.org/w/index.php?search=Berlin", BrowserState.bangUrl("!W Berlin", "de"))
        assertEquals("https://translate.google.com/?sl=auto&tl=en&text=kot", BrowserState.bangUrl("!t kot", "en"))
    }

    @Test
    fun localShopsOnlyInPolish() {
        assertEquals("https://allegro.pl/listing?string=lampa", BrowserState.bangUrl("!a lampa", "pl"))
        assertEquals("https://www.amazon.com/s?k=lamp", BrowserState.bangUrl("!a lamp", "en"))
        assertEquals("https://www.amazon.com/s?k=lamp", BrowserState.bangUrl("!am lamp", "en"))
        assertEquals("https://www.youtube.com/results?search_query=cats", BrowserState.bangUrl("!yt cats", "en"))
    }

    @Test
    fun notABang() {
        assertNull(BrowserState.bangUrl("!nieznany coś", "pl"))
        assertNull(BrowserState.bangUrl("!yt", "pl"))
        assertNull(BrowserState.bangUrl("yt koty", "pl"))
    }

    @Test
    fun tilesPerLanguage() {
        val pl = defaultTiles("pl").map { it.url }
        val en = defaultTiles("en").map { it.url }
        assertTrue("https://allegro.pl" in pl)
        assertTrue("https://pl.wikipedia.org" in pl)
        assertFalse(en.any { it.endsWith(".pl") })
        assertTrue("https://en.wikipedia.org" in en)
        assertEquals(pl.size, en.size)
        // Każde wywołanie daje nowe identyfikatory (przywracanie domyślnych nie dubluje kluczy listy).
        assertTrue(defaultTiles("pl").map { it.id }.intersect(defaultTiles("pl").map { it.id }.toSet()).isEmpty())
    }
}
