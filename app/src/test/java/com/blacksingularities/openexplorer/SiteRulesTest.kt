package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.Scripts
import com.blacksingularities.openexplorer.data.SiteRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ustawienia stron z własnym CSS i skryptem: zapis, odczyt i opakowanie skryptu. */
class SiteRulesTest {
    @Test
    fun roundTripWithCode() {
        val r = SiteRule("example.com", adBlock = false, textZoom = 120, css = ".ad{display:none}", js = "console.log(\"ą\")")
        assertEquals(r, SiteRule.fromJson(JSONObject(r.toJson().toString())))
    }

    @Test
    fun oldFormatWithoutCode() {
        val r = SiteRule.fromJson(JSONObject("""{"host":"www.Example.com","javascript":false}"""))
        assertEquals(SiteRule("example.com", javascript = false), r)
        assertEquals("", r!!.css)
        assertNull(SiteRule.fromJson(JSONObject("""{"adBlock":false}""")))
    }

    @Test
    fun emptyCodeIsNotSaved() {
        val o = SiteRule("a.pl", desktop = true).toJson()
        assertFalse(o.has("css"))
        assertFalse(o.has("js"))
    }

    @Test
    fun codeMakesRuleNonDefault() {
        assertTrue(SiteRule("a.pl").isDefault)
        assertTrue(SiteRule("a.pl", css = "  ").isDefault)
        assertFalse(SiteRule("a.pl", css = "body{}").isDefault)
        assertFalse(SiteRule("a.pl", js = "alert(1)").isDefault)
    }

    @Test
    fun longCodeIsTrimmed() {
        val big = "a".repeat(SiteRule.MAX_CODE + 10)
        val r = SiteRule.fromJson(JSONObject().put("host", "a.pl").put("css", big))!!
        assertEquals(SiteRule.MAX_CODE, r.css.length)
    }

    @Test
    fun userScriptRunsOncePerDocument() {
        val s = Scripts.userScript("doIt();")
        assertTrue(s.contains("if(window.__oe_user_js)return;"))
        assertTrue(s.contains("try{\ndoIt();\n}catch(e)"))
        // Komentarz liniowy na końcu kodu nie może zjeść zamknięcia bloku.
        assertTrue(Scripts.userScript("x() // koniec").contains("// koniec\n}catch"))
    }
}
