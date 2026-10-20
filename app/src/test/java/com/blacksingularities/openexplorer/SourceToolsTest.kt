package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.browser.SourceTools
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceToolsTest {
    private fun spans(text: String): List<Pair<String, Int>> {
        val t = SourceTools.tokenize(text)
        return (0 until t.size / 3).map { text.substring(t[it * 3], t[it * 3 + 1]) to t[it * 3 + 2] }
    }

    @Test
    fun htmlTokens() {
        val s = spans("<!DOCTYPE html><a href=\"/x\" data-id=5 hidden>Tom &amp; Jerry</a><!-- uwaga -->")
        assertEquals("<!DOCTYPE html>" to SourceTools.DOCTYPE, s[0])
        assertEquals("<a" to SourceTools.TAG, s[1])
        assertEquals("href" to SourceTools.ATTR, s[2])
        assertEquals("\"/x\"" to SourceTools.VALUE, s[3])
        assertEquals("data-id" to SourceTools.ATTR, s[4])
        assertEquals("5" to SourceTools.VALUE, s[5])
        assertEquals("hidden" to SourceTools.ATTR, s[6])
        assertEquals(">" to SourceTools.TAG, s[7])
        assertTrue("&amp;" to SourceTools.ENTITY in s)
        assertEquals("<!-- uwaga -->" to SourceTools.COMMENT, s.last())
    }

    @Test
    fun scriptAndStyleTokens() {
        val s = spans("<script>const a = 'x<y'; // c\nreturn 42</script><style>@media x { a { color: \"red\" } }</style>")
        assertTrue("const" to SourceTools.KEYWORD in s)
        assertTrue("'x<y'" to SourceTools.STRING in s)
        assertTrue("// c" to SourceTools.COMMENT in s)
        assertTrue("42" to SourceTools.NUMBER in s)
        assertTrue("</script" to SourceTools.TAG in s)
        assertTrue("@media" to SourceTools.KEYWORD in s)
        assertTrue("\"red\"" to SourceTools.STRING in s)
        // Tokeny posortowane i rozłączne.
        val t = SourceTools.tokenize("<p class=a>x</p><script>if(1){}</script>")
        for (i in 0 until t.size / 3 - 1) assertTrue(t[i * 3 + 1] <= t[(i + 1) * 3])
    }

    @Test
    fun rowsSplitLongLines() {
        val text = "ab\n" + "x".repeat(25) + "\n\nz"
        val r = SourceTools.rows(text, chunk = 10)
        assertArrayEquals(intArrayOf(1, 2, 2, 2, 3, 4), r.line)
        assertEquals(listOf(false, false, true, true, false, false), r.continuation.toList())
        assertEquals(1, r.rowAt(text.indexOf('x')))
        assertEquals(4, r.rowOfLine(3))
        assertEquals("z", text.substring(r.start[5], r.end[5]))
    }

    @Test
    fun findAndFormat() {
        assertArrayEquals(intArrayOf(0, 6), SourceTools.find("Koty, kOTY", "koty"))
        val f = SourceTools.format("<html><body><div><p>Ala <b>ma</b></p><br><img src=x></div><script>a();b();</script></body></html>")
        val lines = f.lines()
        assertEquals("<html>", lines[0])
        assertTrue(lines.contains("      <p>"))
        assertTrue(lines.contains("      <br>"))
        assertTrue(lines.contains("      <img src=x>"))
        assertTrue(lines.contains("    </div>"))
        assertEquals("</html>", lines.last())
    }
}
