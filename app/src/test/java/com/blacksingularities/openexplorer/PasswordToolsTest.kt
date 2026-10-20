package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.Omnibox
import com.blacksingularities.openexplorer.data.PasswordTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordToolsTest {
    @Test
    fun generatedPasswordsAreStrongAndVaried() {
        val all = (1..200).map { PasswordTools.generate() }
        all.forEach { p ->
            assertEquals(16, p.length)
            assertTrue(p.any { it.isLowerCase() } && p.any { it.isUpperCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
            assertTrue(p.none { it in "lIO01" })
            assertEquals(4, PasswordTools.strength(p))
        }
        assertEquals(200, all.toSet().size)
    }

    @Test
    fun strength() {
        assertEquals(0, PasswordTools.strength("qwerty"))
        assertEquals(0, PasswordTools.strength("aaaaaaaaaaaa"))
        assertEquals(0, PasswordTools.strength("abc"))
        assertEquals(1, PasswordTools.strength("kotek123"))
        assertTrue(PasswordTools.strength("Kotek!2024xyzW") >= 3)
    }

    @Test
    fun domains() {
        assertEquals("example.com", PasswordTools.hostOf("https://www.Example.com:443/login?x=1"))
        assertEquals("https://example.com", PasswordTools.normalizeOrigin("example.com/login"))
        assertNull(PasswordTools.normalizeOrigin("android://abc@com.app"))
        assertEquals("google.com", PasswordTools.baseDomain("accounts.google.com"))
        assertEquals("sklep.com.pl", PasswordTools.baseDomain("konto.sklep.com.pl"))
        assertEquals("192.168.1.1", PasswordTools.baseDomain("192.168.1.1"))
    }

    @Test
    fun csvRoundTripAndForeignFormats() {
        val csv = PasswordTools.writeCsv(
            listOf(
                listOf("name", "url", "username", "password", "note"),
                listOf("a", "https://a.pl", "jan", "p,\"x\"\nq", ""),
            ),
        )
        val rows = PasswordTools.parseCsv(csv)
        assertEquals("p,\"x\"\nq", rows[1][3])

        val bitwarden = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            ",,login,Poczta,,,0,https://poczta.example.pl/login,ola@example.pl,Tajne123!,\n"
        val logins = PasswordTools.readLogins(bitwarden)
        assertEquals(1, logins.size)
        assertEquals("https://poczta.example.pl", logins[0].origin)
        assertEquals("ola@example.pl", logins[0].username)
        assertEquals("Tajne123!", logins[0].password)

        val firefox = "\"url\",\"username\",\"password\",\"httpRealm\"\n\"https://www.site.com\",\"u\",\"pw\",\"\"\n"
        assertEquals("https://site.com", PasswordTools.readLogins(firefox).single().origin)
    }

    @Test
    fun addressCompletion() {
        val hosts = listOf("youtube.com", "github.com", "google.com")
        assertEquals("youtube.com", Omnibox.complete("you", hosts))
        assertEquals("YouTube.com", Omnibox.complete("YouT", hosts))
        assertEquals("https://github.com", Omnibox.complete("https://gi", hosts))
        assertEquals("www.google.com", Omnibox.complete("www.goo", hosts))
        assertNull(Omnibox.complete("koty w", hosts))
        assertNull(Omnibox.complete("youtube.com", hosts))
        assertEquals(listOf("b.pl", "a.pl"), Omnibox.rankHosts(listOf("a.pl", "a.pl", "b.pl"), listOf("b.pl")))
    }
}
