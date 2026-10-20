package com.blacksingularities.openexplorer.browser

/**
 * Narzędzia podglądu kodu strony (bez zależności od Androida): kolorowanie składni HTML
 * z osadzonym JavaScriptem i CSS, formatowanie zminifikowanego HTML i podział na wiersze.
 */
object SourceTools {
    const val TAG = 1
    const val ATTR = 2
    const val VALUE = 3
    const val COMMENT = 4
    const val DOCTYPE = 5
    const val KEYWORD = 6
    const val STRING = 7
    const val NUMBER = 8
    const val ENTITY = 9

    /** Wiersze dłuższe niż tyle znaków dzielone są na odcinki (zminifikowany kod w jednej linii). */
    const val CHUNK = 2000

    private val JS_KEYWORDS = setOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do", "switch", "case", "break",
        "continue", "new", "this", "typeof", "instanceof", "in", "of", "try", "catch", "finally", "throw", "class",
        "extends", "super", "import", "export", "from", "default", "async", "await", "yield", "delete", "void",
        "null", "undefined", "true", "false", "static", "get", "set",
    )

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )

    /**
     * Tokeny jako trójki (początek, koniec, rodzaj) w jednej tablicy, posortowane i rozłączne.
     * Zwykły tekst między znacznikami nie ma tokenu.
     */
    fun tokenize(text: String): IntArray {
        val out = IntList()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '<') {
                when {
                    text.startsWith("<!--", i) -> {
                        val end = text.indexOf("-->", i + 4).let { if (it < 0) n else it + 3 }
                        out.add(i, end, COMMENT)
                        i = end
                        continue
                    }
                    i + 1 < n && text[i + 1] == '!' || i + 1 < n && text[i + 1] == '?' -> {
                        val end = text.indexOf('>', i).let { if (it < 0) n else it + 1 }
                        out.add(i, end, DOCTYPE)
                        i = end
                        continue
                    }
                    i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '/' && i + 2 < n && text[i + 2].isLetter()) -> {
                        val (end, name, closing, selfClosing) = tag(text, i, out)
                        i = end
                        if (!closing && !selfClosing && (name == "script" || name == "style")) {
                            val close = indexOfIgnoreCase(text, "</$name", i).let { if (it < 0) n else it }
                            code(text, i, close, name == "script", out)
                            i = close
                        }
                        continue
                    }
                }
            } else if (c == '&') {
                var j = i + 1
                while (j < n && j - i < 12 && (text[j].isLetterOrDigit() || text[j] == '#')) j++
                if (j < n && text[j] == ';' && j > i + 1) {
                    out.add(i, j + 1, ENTITY)
                    i = j + 1
                    continue
                }
            }
            i++
        }
        return out.toArray()
    }

    /** Rodzaj kodu na podstawie adresu i typu MIME: "html", "js", "css" albo "text". */
    fun kindOf(url: String, contentType: String = ""): String {
        val ct = contentType.lowercase()
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when {
            "javascript" in ct || "ecmascript" in ct || "json" in ct || path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".json") -> "js"
            "css" in ct || path.endsWith(".css") -> "css"
            "html" in ct || "xml" in ct || "svg" in ct || ct.isEmpty() -> "html"
            else -> "text"
        }
    }

    /** Tokeny dla danego rodzaju kodu ([kindOf]). */
    fun tokenize(text: String, kind: String): IntArray = when (kind) {
        "js", "css" -> IntList().also { code(text, 0, text.length, kind == "js", it) }.toArray()
        "text" -> IntArray(0)
        else -> tokenize(text)
    }

    private data class TagResult(val end: Int, val name: String, val closing: Boolean, val selfClosing: Boolean)

    private fun tag(text: String, start: Int, out: IntList): TagResult {
        val n = text.length
        var i = start + 1
        val closing = text[i] == '/'
        if (closing) i++
        val nameStart = i
        while (i < n && (text[i].isLetterOrDigit() || text[i] == '-' || text[i] == ':' || text[i] == '_')) i++
        val name = text.substring(nameStart, i).lowercase()
        out.add(start, i, TAG)
        var selfClosing = false
        while (i < n) {
            val c = text[i]
            when {
                c == '>' -> {
                    out.add(i, i + 1, TAG)
                    return TagResult(i + 1, name, closing, selfClosing || name in VOID)
                }
                c == '/' && i + 1 < n && text[i + 1] == '>' -> {
                    out.add(i, i + 2, TAG)
                    return TagResult(i + 2, name, closing, true)
                }
                c.isWhitespace() || c == '/' -> i++
                c == '=' -> {
                    i++
                    while (i < n && text[i].isWhitespace()) i++
                    if (i >= n) break
                    val q = text[i]
                    val vStart = i
                    if (q == '"' || q == '\'') {
                        val end = text.indexOf(q, i + 1).let { if (it < 0) n else it + 1 }
                        out.add(vStart, end, VALUE)
                        i = end
                    } else {
                        while (i < n && !text[i].isWhitespace() && text[i] != '>') i++
                        out.add(vStart, i, VALUE)
                    }
                }
                c == '<' -> return TagResult(i, name, closing, true) // uszkodzony znacznik
                else -> {
                    val aStart = i
                    while (i < n && !text[i].isWhitespace() && text[i] != '=' && text[i] != '>' && text[i] != '/' && text[i] != '<') i++
                    if (i == aStart) i++ else out.add(aStart, i, ATTR)
                }
            }
        }
        return TagResult(n, name, closing, true)
    }

    /** Kolorowanie JavaScriptu i CSS: komentarze, napisy, liczby, słowa kluczowe. */
    private fun code(text: String, from: Int, to: Int, js: Boolean, out: IntList) {
        var i = from
        while (i < to) {
            val c = text[i]
            when {
                c == '/' && i + 1 < to && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2).let { if (it < 0 || it + 2 > to) to else it + 2 }
                    out.add(i, end, COMMENT)
                    i = end
                }
                js && c == '/' && i + 1 < to && text[i + 1] == '/' -> {
                    var end = text.indexOf('\n', i).let { if (it < 0 || it > to) to else it }
                    if (end <= i) end = to
                    out.add(i, end, COMMENT)
                    i = end
                }
                c == '"' || c == '\'' || js && c == '`' -> {
                    var j = i + 1
                    while (j < to && text[j] != c) {
                        if (text[j] == '\\') j++
                        if (c != '`' && j < to && text[j] == '\n') break
                        j++
                    }
                    val end = (j + 1).coerceAtMost(to)
                    out.add(i, end, STRING)
                    i = end
                }
                c.isDigit() && (i == from || !(text[i - 1].isLetterOrDigit() || text[i - 1] == '_' || text[i - 1] == '$')) -> {
                    var j = i
                    while (j < to && (text[j].isLetterOrDigit() || text[j] == '.')) j++
                    out.add(i, j, NUMBER)
                    i = j
                }
                c.isLetter() || c == '_' || c == '$' || (!js && c == '@') -> {
                    var j = i + 1
                    while (j < to && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '$' || (!js && text[j] == '-'))) j++
                    val word = text.substring(i, j)
                    if (js && word in JS_KEYWORDS || !js && word.startsWith("@")) out.add(i, j, KEYWORD)
                    i = j
                }
                else -> i++
            }
        }
    }

    private fun indexOfIgnoreCase(text: String, what: String, from: Int): Int {
        var i = from
        while (true) {
            i = text.indexOf('<', i)
            if (i < 0) return -1
            if (text.regionMatches(i, what, 0, what.length, ignoreCase = true)) return i
            i++
        }
    }

    /** Pierwszy token o końcu za [pos] (wyszukiwanie binarne w tablicy trójek). */
    fun firstTokenAfter(tokens: IntArray, pos: Int): Int {
        var lo = 0
        var hi = tokens.size / 3
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (tokens[mid * 3 + 1] <= pos) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Wiersze do wyświetlenia: (początek, koniec, numer linii, czy to dalszy ciąg długiej linii).
     * Zwracane jako cztery tablice o równej długości.
     */
    class Rows(val start: IntArray, val end: IntArray, val line: IntArray, val continuation: BooleanArray) {
        val size: Int get() = start.size

        /** Indeks wiersza zawierającego pozycję w tekście. */
        fun rowAt(pos: Int): Int {
            var lo = 0
            var hi = size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (start[mid] <= pos) lo = mid else hi = mid - 1
            }
            return lo
        }

        /** Pierwszy wiersz linii o danym numerze (1…). */
        fun rowOfLine(n: Int): Int {
            var lo = 0
            var hi = size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (line[mid] < n) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }

    fun rows(text: String, chunk: Int = CHUNK): Rows {
        val s = IntList()
        val e = IntList()
        val l = IntList()
        val cont = ArrayList<Boolean>()
        var lineNo = 1
        var pos = 0
        val n = text.length
        while (true) {
            val nl = text.indexOf('\n', pos).let { if (it < 0) n else it }
            var a = pos
            var first = true
            do {
                val b = (a + chunk).coerceAtMost(nl)
                s.add(a); e.add(b); l.add(lineNo); cont.add(!first)
                first = false
                a = b
            } while (a < nl)
            if (nl >= n) break
            pos = nl + 1
            lineNo++
        }
        return Rows(s.toArray(), e.toArray(), l.toArray(), cont.toBooleanArray())
    }

    /** Pozycje wszystkich wystąpień (bez rozróżniania wielkości liter), najwyżej [limit]. */
    fun find(text: String, query: String, limit: Int = 10_000): IntArray {
        if (query.isEmpty()) return IntArray(0)
        val out = IntList()
        var i = text.indexOf(query, 0, ignoreCase = true)
        while (i >= 0 && out.size < limit) {
            out.add(i)
            i = text.indexOf(query, i + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        return out.toArray()
    }

    private val PIECES = Regex(
        "<!--[\\s\\S]*?-->|<![^>]*>|<(script|style|pre|textarea)\\b[^>]*>[\\s\\S]*?</\\1\\s*>|</?[a-zA-Z][^>]*>",
        RegexOption.IGNORE_CASE,
    )

    /** Czytelny układ HTML: każdy znacznik w osobnej linii z wcięciami (dla kodu zminifikowanego). */
    fun format(html: String, indentUnit: String = "  "): String {
        val sb = StringBuilder(html.length + html.length / 4)
        var depth = 0
        fun line(s: String) {
            if (s.isBlank()) return
            repeat(depth.coerceAtMost(40)) { sb.append(indentUnit) }
            sb.append(s.trim()).append('\n')
        }
        fun text(t: String) {
            val compact = t.replace(Regex("\\s+"), " ").trim()
            if (compact.isNotEmpty()) line(compact)
        }
        var last = 0
        for (m in PIECES.findAll(html)) {
            text(html.substring(last, m.range.first))
            last = m.range.last + 1
            val piece = m.value
            val raw = m.groups[1]?.value
            when {
                raw != null -> {
                    // Blok skryptu / stylu / pre: znacznik otwierający, zawartość wcięta, znacznik zamykający.
                    val openEnd = piece.indexOf('>') + 1
                    val closeStart = piece.lastIndexOf('<')
                    line(piece.substring(0, openEnd))
                    depth++
                    piece.substring(openEnd, closeStart).lines().forEach { line(it) }
                    depth--
                    line(piece.substring(closeStart))
                }
                piece.startsWith("</") -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    line(piece)
                }
                piece.startsWith("<!") -> line(piece)
                else -> {
                    line(piece)
                    val name = piece.substring(1).takeWhile { it.isLetterOrDigit() || it == '-' }.lowercase()
                    if (!piece.endsWith("/>") && name !in VOID) depth++
                }
            }
        }
        text(html.substring(last))
        return sb.toString().trimEnd()
    }

    /** Lista liczb bez opakowywania (szybsza i oszczędniejsza niż ArrayList<Int>). */
    private class IntList {
        private var a = IntArray(1024)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == a.size) a = a.copyOf(a.size * 2)
            a[size++] = v
        }

        fun add(x: Int, y: Int, z: Int) {
            if (y <= x) return
            add(x); add(y); add(z)
        }

        fun toArray(): IntArray = a.copyOf(size)
    }
}
