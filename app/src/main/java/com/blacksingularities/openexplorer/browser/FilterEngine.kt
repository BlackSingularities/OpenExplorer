package com.blacksingularities.openexplorer.browser

/**
 * Reguły w formacie Adblock Plus / uBlock Origin (EasyList, EasyPrivacy, Polskie Filtry).
 * Czyste funkcje bez Androida – testowane w FilterEngineTest.
 *
 * Obsługiwany podzbiór składni:
 * - sieciowe: `||domena^`, `|https://…`, ścieżki, `*`, `^`, końcowe `|`, wyjątki `@@`;
 *   opcje typu (`script`, `image`, `stylesheet`, `subdocument`, `xmlhttprequest`, `media`, `font`,
 *   `ping`, `websocket`, `other`, z negacją `~`), `third-party` / `first-party`, `domain=`, `important`,
 *   a w wyjątkach także `document`, `elemhide`, `generichide`;
 * - kosmetyczne: `##selektor`, `domena,~domena##selektor`, `#@#selektor`.
 * Reguły z wyrażeniami regularnymi, przekierowaniami, skryptletami, filtrami proceduralnymi
 * i innymi nieznanymi opcjami są pomijane (bloker blokuje wtedy mniej, ale nic nie psuje).
 */
object ReqType {
    const val SCRIPT = 1
    const val IMAGE = 1 shl 1
    const val STYLESHEET = 1 shl 2
    const val SUBDOCUMENT = 1 shl 3
    const val XHR = 1 shl 4
    const val MEDIA = 1 shl 5
    const val FONT = 1 shl 6
    const val OTHER = 1 shl 7
    const val PING = 1 shl 8
    const val WEBSOCKET = 1 shl 9
    /** Wszystkie rodzaje zwykłych żądań (reguła bez opcji typu). */
    const val ALL = (1 shl 10) - 1

    // Tylko w wyjątkach dotyczących całej strony.
    const val DOCUMENT = 1 shl 10
    const val ELEMHIDE = 1 shl 11
    const val GENERICHIDE = 1 shl 12

    private val IMAGES = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp")
    private val FONTS = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIAS = setOf("mp4", "webm", "mp3", "m4a", "ogg", "m3u8", "mpd", "ts", "aac", "wav")

    /**
     * Rodzaj żądania zgadywany z nagłówka Accept i rozszerzenia (WebView nie podaje go wprost).
     * Gdy nie da się rozstrzygnąć, zwraca kilka bitów naraz – reguła pasuje, jeśli pasuje do któregoś.
     */
    fun guess(url: String, accept: String?): Int {
        val u = url.lowercase()
        if (u.startsWith("ws://") || u.startsWith("wss://")) return WEBSOCKET
        val a = accept?.lowercase().orEmpty()
        when {
            a.startsWith("text/css") -> return STYLESHEET
            a.startsWith("image/") -> return IMAGE
            a.startsWith("text/html") || a.startsWith("application/xhtml") -> return SUBDOCUMENT
        }
        val path = u.substringAfter("://").substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/', "")
        val ext = if (name.contains('.')) name.substringAfterLast('.') else ""
        return when {
            ext == "js" || ext == "mjs" -> SCRIPT
            ext == "css" -> STYLESHEET
            ext in IMAGES -> IMAGE
            ext in FONTS -> FONT
            ext in MEDIAS -> MEDIA
            a.contains("json") -> XHR
            else -> SCRIPT or XHR or PING or OTHER
        }
    }
}

/** Reguła sieciowa po rozbiorze. Wzorzec jest podzielony na kawałki między gwiazdkami. */
class NetRule internal constructor(
    private val segs: Array<String>,
    /** 0 – dowolne miejsce, 1 – początek adresu (`|`), 2 – początek domeny (`||`). */
    private val anchor: Int,
    private val anchorEnd: Boolean,
    /** Reguła `||domena^` – samo trafienie w indeksie domen oznacza dopasowanie wzorca. */
    internal val hostOnly: Boolean,
    internal val types: Int,
    /** 0 – wszystkie, 1 – tylko zewnętrzne, 2 – tylko z tej samej strony. */
    private val party: Int,
    private val include: Array<String>?,
    private val exclude: Array<String>?,
    val important: Boolean,
) {
    fun optionsMatch(type: Int, thirdParty: Boolean, pageHost: String?): Boolean {
        if (types and type == 0) return false
        if (party == 1 && !thirdParty || party == 2 && thirdParty) return false
        if (include != null && (pageHost == null || include.none { FilterEngine.isSubdomain(pageHost, it) })) return false
        if (exclude != null && pageHost != null && exclude.any { FilterEngine.isSubdomain(pageHost, it) }) return false
        return true
    }

    /** [url] małymi literami, [hostStart]..[hostEnd] – zakres domeny w adresie. */
    fun patternMatch(url: String, hostStart: Int, hostEnd: Int): Boolean {
        if (hostOnly) return true
        val anchored = segs[0].isNotEmpty()
        return when {
            anchor == 1 -> matchFrom(url, 0, anchored)
            anchor == 2 -> {
                if (hostStart < 0) return false
                if (matchFrom(url, hostStart, anchored)) return true
                for (i in hostStart + 1 until hostEnd) {
                    if (url[i - 1] == '.' && matchFrom(url, i, anchored)) return true
                }
                false
            }
            else -> matchFrom(url, 0, false)
        }
    }

    private fun matchFrom(url: String, start: Int, anchoredFirst: Boolean): Boolean {
        var pos = start
        for (i in segs.indices) {
            val seg = segs[i]
            val last = i == segs.lastIndex
            if (seg.isEmpty()) continue
            if (i == 0 && anchoredFirst) {
                val e = matchAt(url, pos, seg)
                if (e < 0 || last && anchorEnd && e != url.length) return false
                pos = e
                continue
            }
            if (last && anchorEnd) {
                val p = url.length - seg.length
                return p >= pos && matchAt(url, p, seg) == url.length
            }
            pos = find(url, pos, seg)
            if (pos < 0) return false
        }
        return !anchorEnd || pos == url.length || segs.last().isEmpty()
    }

    companion object {
        /** Separator `^`: wszystko poza literą, cyfrą i `_ - . %` (także koniec adresu). */
        fun isSeparator(c: Char): Boolean = !(c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%')

        /** Koniec dopasowania kawałka od pozycji [pos] albo -1. */
        internal fun matchAt(url: String, pos: Int, seg: String): Int {
            var i = pos
            for (k in seg.indices) {
                val c = seg[k]
                if (i >= url.length) return if (c == '^' && k == seg.lastIndex) i else -1
                if (c == '^') {
                    if (!isSeparator(url[i])) return -1
                } else if (c != url[i]) return -1
                i++
            }
            return i
        }

        /** Koniec pierwszego (najbardziej na lewo) dopasowania kawałka od pozycji [from] albo -1. */
        internal fun find(url: String, from: Int, seg: String): Int {
            for (p in from..url.length) {
                val e = matchAt(url, p, seg)
                if (e >= 0) return e
            }
            return -1
        }
    }
}

/** Arkusze dla strony: [specific] – reguły tej domeny, [generic] – ogólne (duży, wspólny dla wielu stron). */
class CosmeticCss(val specific: String, val generic: String)

/** Wszystkie wczytane listy: indeksy blokowania, wyjątków i ukrywania elementów. Niezmienne po zbudowaniu. */
class FilterEngine private constructor(b: Builder) {
    private val blockHosts: Set<String> = b.blockHosts
    private val block = b.block
    private val allow = b.allow
    private val genericSelectors: Set<String> = b.generic
    private val genericExceptions: Set<String> = b.genericExceptions
    private val specificHide = b.specificHide
    private val specificAllow = b.specificAllow
    private val genericExcept = b.genericWithExclusions

    /** Wspólny arkusz z regułami ogólnymi – budowany raz, wysyłany do wielu stron. */
    private val genericCss: String by lazy { css(genericSelectors) }

    val networkRules: Int = blockHosts.size + block.size + allow.size
    val cosmeticRules: Int = genericSelectors.size + b.cosmeticCount
    val size: Int get() = networkRules + cosmeticRules

    /** Czy zablokować żądanie [url] wysłane ze strony [pageHost] / [pageUrl]. */
    fun shouldBlock(url: String, pageHost: String?, pageUrl: String? = null, type: Int = ReqType.guess(url, null)): Boolean {
        val u = url.lowercase()
        val (hs, he) = hostRange(u)
        if (hs < 0) return false
        val host = u.substring(hs, he)
        val page = pageHost?.lowercase()
        val third = page == null || baseDomain(host) != baseDomain(page)
        val hit = matchesHost(host) || block.find(u, hs, he, host, type, third, page) != null
        if (!hit) return false
        val important = block.findImportant(u, hs, he, host, type, third, page)
        if (important) return true
        if (allow.find(u, hs, he, host, type, third, page) != null) return false
        if (pageAllows(pageUrl ?: page?.let { "https://$it/" }, page, ReqType.DOCUMENT)) return false
        return true
    }

    /** Wyjątek dla całej strony (`@@…$document`, `$elemhide`, `$generichide`). */
    fun pageAllows(pageUrl: String?, pageHost: String?, kind: Int): Boolean {
        val u = pageUrl?.lowercase() ?: return false
        val (hs, he) = hostRange(u)
        if (hs < 0) return false
        val host = u.substring(hs, he)
        return allow.find(u, hs, he, host, kind, false, pageHost?.lowercase() ?: host) != null
    }

    private fun matchesHost(host: String): Boolean {
        if (blockHosts.isEmpty()) return false
        var h = host
        while (true) {
            if (h in blockHosts) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.lastIndexOf('.')) return false
            h = h.substring(dot + 1)
        }
    }

    /** Arkusz ukrywania elementów dla strony; null, gdy strona ma wyjątek `$elemhide`. */
    fun cosmetic(pageHost: String?, pageUrl: String? = null): CosmeticCss? {
        val host = pageHost?.lowercase() ?: return CosmeticCss("", genericCss)
        val url = pageUrl ?: "https://$host/"
        if (pageAllows(url, host, ReqType.ELEMHIDE)) return null
        val except = HashSet<String>()
        val out = LinkedHashSet<String>()
        forEachParent(host) { h ->
            specificAllow[h]?.let { except += it }
            specificHide[h]?.forEach { r -> if (!r.excludedFor(host) && r.selector !in genericExceptions) out += r.selector }
        }
        genericExcept.forEach { r -> if (!r.excludedFor(host)) out += r.selector }
        out.removeAll(except)
        val generic = when {
            pageAllows(url, host, ReqType.GENERICHIDE) -> ""
            except.any { it in genericSelectors } -> css(genericSelectors.filter { it !in except })
            else -> genericCss
        }
        return CosmeticCss(css(out), generic)
    }

    /** Reguła kosmetyczna z wykluczonymi domenami (`a.pl,~b.a.pl##…`). */
    internal class Hide(val selector: String, private val excluded: Array<String>?) {
        fun excludedFor(host: String) = excluded != null && excluded.any { isSubdomain(host, it) }
    }

    /** Indeks reguł sieciowych: po domenie (dla `||domena…`) i po tokenie z wzorca. */
    internal class Index {
        val byHost = HashMap<String, MutableList<NetRule>>()
        val byToken = HashMap<String, MutableList<NetRule>>()
        /** Reguły bez tokenu – sprawdzane przy każdym żądaniu, więc powinno ich być mało. */
        val rest = ArrayList<NetRule>()
        var size = 0
        private var hasImportant = false

        fun add(rule: NetRule, host: String?, tokens: List<String>) {
            size++
            if (rule.important) hasImportant = true
            when {
                host != null -> byHost.getOrPut(host) { ArrayList(1) } += rule
                tokens.isEmpty() -> rest += rule
                else -> {
                    // Token z najkrótszym kubełkiem – wspólne fragmenty jak „com” czy „ads” nie zapychają jednego miejsca.
                    val best = tokens.minWith(compareBy<String> { byToken[it]?.size ?: 0 }.thenByDescending { it.length })
                    byToken.getOrPut(best) { ArrayList(1) } += rule
                }
            }
        }

        fun find(url: String, hs: Int, he: Int, host: String, type: Int, third: Boolean, page: String?, onlyImportant: Boolean = false): NetRule? {
            fun check(list: List<NetRule>?): NetRule? {
                list ?: return null
                for (r in list) {
                    if (onlyImportant && !r.important) continue
                    if (r.optionsMatch(type, third, page) && r.patternMatch(url, hs, he)) return r
                }
                return null
            }
            var found: NetRule? = null
            forEachParent(host) { h -> if (found == null) found = check(byHost[h]) }
            if (found != null) return found
            check(rest)?.let { return it }
            if (byToken.isEmpty()) return null
            var i = 0
            while (i < url.length) {
                if (!isTokenChar(url[i])) {
                    i++
                    continue
                }
                val s = i
                while (i < url.length && isTokenChar(url[i])) i++
                check(byToken[url.substring(s, i)])?.let { return it }
            }
            return null
        }

        fun findImportant(url: String, hs: Int, he: Int, host: String, type: Int, third: Boolean, page: String?): Boolean =
            hasImportant && find(url, hs, he, host, type, third, page, onlyImportant = true) != null
    }

    /** Zbiera reguły z list i buduje [FilterEngine]. */
    class Builder {
        internal val blockHosts = HashSet<String>()
        internal val block = Index()
        internal val allow = Index()
        internal val generic = LinkedHashSet<String>()
        internal val genericExceptions = HashSet<String>()
        internal val specificHide = HashMap<String, MutableList<Hide>>()
        internal val specificAllow = HashMap<String, MutableList<String>>()
        internal val genericWithExclusions = ArrayList<Hide>()
        internal var cosmeticCount = 0

        /** Domeny z pliku hosts – blokowane razem z subdomenami, jak `||domena^`. Zwraca liczbę domen. */
        fun addHosts(domains: Collection<String>): Int {
            blockHosts.addAll(domains)
            return domains.size
        }

        /** Lista w formacie ABP. Zwraca liczbę przyjętych reguł. */
        fun addAbp(lines: Sequence<String>): Int {
            var n = 0
            for (line in lines) if (addLine(line)) n++
            return n
        }

        fun addAbp(lines: Iterable<String>): Int = addAbp(lines.asSequence())

        /** Jedna reguła; false, gdy to komentarz albo reguła nieobsługiwana. */
        fun addLine(raw: String): Boolean {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[' || line.startsWith("# ")) return false
            val cos = cosmeticSplit(line)
            if (cos != null) return if (cos.first < 0) false else addCosmetic(line, cos.first, cos.second)
            return addNetwork(line)
        }

        fun build(): FilterEngine {
            generic.removeAll(genericExceptions)
            return FilterEngine(this)
        }

        private fun addCosmetic(line: String, at: Int, exception: Boolean): Boolean {
            val selector = line.substring(at + if (exception) 3 else 2).trim()
            if (!validSelector(selector)) return false
            val domains = line.substring(0, at)
            val include = ArrayList<String>()
            val exclude = ArrayList<String>()
            if (domains.isNotEmpty()) {
                for (d in domains.split(',')) {
                    val t = d.trim().lowercase()
                    if (t.startsWith('~')) {
                        if (validDomain(t.substring(1))) exclude += t.substring(1)
                    } else if (validDomain(t)) include += t
                }
                // Same nieobsługiwane domeny (np. „google.*”) – reguła nie dotyczy niczego, co umiemy rozpoznać.
                if (include.isEmpty() && exclude.isEmpty()) return false
            }
            if (exception) {
                if (include.isEmpty()) {
                    if (domains.isNotEmpty()) return false
                    genericExceptions += selector
                } else include.forEach { specificAllow.getOrPut(it) { ArrayList(1) } += selector }
                cosmeticCount++
                return true
            }
            val ex = exclude.takeIf { it.isNotEmpty() }?.toTypedArray()
            when {
                include.isNotEmpty() -> {
                    val hide = Hide(selector, ex)
                    include.forEach { specificHide.getOrPut(it) { ArrayList(1) } += hide }
                    cosmeticCount++
                }
                ex != null -> {
                    genericWithExclusions += Hide(selector, ex)
                    cosmeticCount++
                }
                else -> generic += selector
            }
            return true
        }

        private fun addNetwork(line: String): Boolean {
            var s = line
            val exception = s.startsWith("@@")
            if (exception) s = s.substring(2)
            val dollar = s.lastIndexOf('$')
            val optText = if (dollar >= 0) s.substring(dollar + 1) else ""
            if (dollar >= 0) s = s.substring(0, dollar)
            // Wyrażenia regularne /…/ pomijamy – za wolne przy każdym żądaniu.
            if (s.length > 1 && s.startsWith('/') && s.endsWith('/')) return false

            var types = 0
            var notTypes = 0
            var party = 0
            var important = false
            var include: Array<String>? = null
            var exclude: Array<String>? = null
            if (optText.isNotEmpty()) {
                for (o in optText.split(',')) {
                    val opt = o.trim().lowercase()
                    val neg = opt.startsWith('~')
                    val name = opt.removePrefix("~")
                    val type = typeOf(name, exception)
                    when {
                        type != 0 -> if (neg) notTypes = notTypes or type else types = types or type
                        name == "third-party" || name == "3p" -> party = if (neg) 2 else 1
                        name == "first-party" || name == "1p" -> party = if (neg) 1 else 2
                        name == "important" && !neg -> important = true
                        name == "match-case" -> {}
                        name.startsWith("domain=") || name.startsWith("from=") -> {
                            val inc = ArrayList<String>()
                            val exc = ArrayList<String>()
                            for (d in name.substringAfter('=').split('|')) {
                                if (d.startsWith('~')) {
                                    if (validDomain(d.substring(1))) exc += d.substring(1)
                                } else if (validDomain(d)) inc += d
                            }
                            if (inc.isEmpty() && exc.isEmpty()) return false
                            include = inc.takeIf { it.isNotEmpty() }?.toTypedArray()
                            exclude = exc.takeIf { it.isNotEmpty() }?.toTypedArray()
                        }
                        else -> return false
                    }
                }
            }
            val mask = when {
                types != 0 -> types and notTypes.inv()
                notTypes != 0 -> ReqType.ALL and notTypes.inv()
                else -> ReqType.ALL
            }
            if (mask == 0) return false

            var anchor = 0
            when {
                s.startsWith("||") -> { anchor = 2; s = s.substring(2) }
                s.startsWith("|") -> { anchor = 1; s = s.substring(1) }
            }
            val anchorEnd = s.endsWith('|')
            if (anchorEnd) s = s.dropLast(1)
            s = s.lowercase()
            val core = s.trim('*')
            // Wzorzec bez liter i cyfr (pusty, „*”, „^”) bez ograniczenia domen pasowałby do wszystkiego.
            if (core.none { isTokenChar(it) } && include == null) return false
            if (core.any { it.code > 127 || it == ' ' }) return false

            val hostPart = if (anchor == 2) core.removeSuffix("^") else null
            val isHost = hostPart != null && core == s && hostPart.isNotEmpty() && core.endsWith('^') && !anchorEnd &&
                hostPart.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            val simple = !exception && types == 0 && notTypes == 0 && party == 0 && include == null && exclude == null && !important
            if (isHost && simple) {
                blockHosts += hostPart!!
                return true
            }
            val rule = NetRule(
                segs = s.split('*').toTypedArray(), anchor = anchor, anchorEnd = anchorEnd, hostOnly = isHost,
                types = mask, party = party, include = include, exclude = exclude, important = important,
            )
            (if (exception) allow else block).add(rule, if (isHost) hostPart else null, if (isHost) emptyList() else tokens(s, anchor, anchorEnd))
            return true
        }

        private fun typeOf(name: String, exception: Boolean): Int = when (name) {
            "script" -> ReqType.SCRIPT
            "image" -> ReqType.IMAGE
            "stylesheet", "css" -> ReqType.STYLESHEET
            "subdocument", "frame" -> ReqType.SUBDOCUMENT
            "xmlhttprequest", "xhr" -> ReqType.XHR
            "media" -> ReqType.MEDIA
            "font" -> ReqType.FONT
            "ping", "beacon" -> ReqType.PING
            "websocket" -> ReqType.WEBSOCKET
            "other", "object", "object-subrequest" -> ReqType.OTHER
            "all" -> ReqType.ALL
            // Wyjątki dla całej strony; w regułach blokujących nieobsługiwane (strona główna nie jest blokowana).
            "document", "doc" -> if (exception) ReqType.DOCUMENT else 0
            "elemhide", "ehide" -> if (exception) ReqType.ELEMHIDE else 0
            "generichide", "ghide" -> if (exception) ReqType.GENERICHIDE else 0
            else -> 0
        }
    }

    companion object {
        /** Selektory po których kawałki CSS się dzielą – błąd w jednym nie wyłącza całego arkusza. */
        private const val CHUNK = 20

        private val PROCEDURAL = listOf(
            ":-abp-", ":has-text(", ":contains(", ":upward(", ":xpath(", ":matches-", ":min-text-length(", ":watch-attr(",
            ":others(", ":if(", ":if-not(", ":nth-ancestor(", ":remove(", ":remove-attr(", ":remove-class(", ":style(",
        )

        /**
         * Pozycja znacznika kosmetycznego i czy to wyjątek; (-1, false) dla nieobsługiwanych (`#?#`, `#$#`, `##+js`…);
         * null – to reguła sieciowa.
         */
        internal fun cosmeticSplit(line: String): Pair<Int, Boolean>? {
            var p = line.indexOf('#')
            while (p >= 0) {
                val domains = line.substring(0, p)
                if (domains.none { it == '/' || it == '|' || it == '$' || it == '^' || it == '?' || it == '=' }) {
                    val rest = line.substring(p)
                    when {
                        rest.startsWith("##") -> return p to false
                        rest.startsWith("#@#") -> return p to true
                        rest.startsWith("#?#") || rest.startsWith("#$#") || rest.startsWith("#%#") ||
                            rest.startsWith("#@?#") || rest.startsWith("#@$#") || rest.startsWith("#@%#") ||
                            rest.startsWith("#$?#") || rest.startsWith("#@$?#") -> return -1 to false
                    }
                }
                p = line.indexOf('#', p + 1)
            }
            return null
        }

        internal fun validSelector(sel: String): Boolean {
            if (sel.isEmpty() || sel.startsWith("+js") || sel.startsWith('^') || sel.startsWith('@')) return false
            if (sel.any { it == '{' || it == '}' }) return false
            if (sel.contains("</") || sel.contains("/*")) return false
            return PROCEDURAL.none { sel.contains(it) }
        }

        /** Domena w regule (bez „google.*”, adresów ze ścieżką itp.). */
        internal fun validDomain(d: String): Boolean =
            d.isNotEmpty() && d.length < 254 && !d.startsWith('.') && !d.endsWith('.') &&
                d.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }

        /** [host] to [domain] albo jego subdomena. */
        fun isSubdomain(host: String, domain: String): Boolean =
            host == domain || host.length > domain.length && host.endsWith(domain) && host[host.length - domain.length - 1] == '.'

        internal inline fun forEachParent(host: String, action: (String) -> Unit) {
            var h = host
            while (true) {
                action(h)
                val dot = h.indexOf('.')
                if (dot < 0) return
                h = h.substring(dot + 1)
            }
        }

        private fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%'

        /**
         * Tokeny wzorca, które w pasującym adresie zawsze wystąpią w całości – ograniczone z obu stron
         * separatorem, kotwicą albo innym znakiem (nie gwiazdką i nie otwartym końcem wzorca).
         */
        internal fun tokens(pattern: String, anchor: Int, anchorEnd: Boolean): List<String> {
            val out = ArrayList<String>(4)
            var i = 0
            while (i < pattern.length) {
                if (!isTokenChar(pattern[i])) {
                    i++
                    continue
                }
                val s = i
                while (i < pattern.length && isTokenChar(pattern[i])) i++
                val leftOk = if (s == 0) anchor != 0 else pattern[s - 1] != '*'
                val rightOk = if (i == pattern.length) anchorEnd else pattern[i] != '*'
                if (leftOk && rightOk && i - s >= 2) out += pattern.substring(s, i)
            }
            return out
        }

        /** Zakres domeny w adresie (małymi literami); (-1, -1) gdy adres nie ma domeny. */
        internal fun hostRange(url: String): Pair<Int, Int> {
            val scheme = url.indexOf("://")
            if (scheme < 0) return -1 to -1
            var s = scheme + 3
            var e = s
            while (e < url.length && url[e] != '/' && url[e] != '?' && url[e] != '#') e++
            val at = url.lastIndexOf('@', e - 1)
            if (at >= s) s = at + 1
            var colon = e
            for (k in s until e) if (url[k] == ':') { colon = k; break }
            if (url.startsWith("[", s)) colon = e
            return if (colon > s) s to colon else -1 to -1
        }

        private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gob", "mil", "nom", "info", "biz", "waw")

        /** Przybliżona domena rejestrowana (bez pełnej Public Suffix List): ostatnie dwie części, trzy dla „com.pl”, „co.uk” itp. */
        fun baseDomain(host: String): String {
            val last = host.lastIndexOf('.')
            if (last <= 0 || host.all { it.isDigit() || it == '.' }) return host
            val second = host.lastIndexOf('.', last - 1)
            if (second < 0) return host
            if (host.length - last - 1 == 2 && host.substring(second + 1, last) in SECOND_LEVEL) {
                val third = host.lastIndexOf('.', second - 1)
                return if (third < 0) host else host.substring(third + 1)
            }
            return host.substring(second + 1)
        }

        /** Selektory w kawałkach po [CHUNK] – nieznany przeglądarce selektor wyłącza tylko swój kawałek. */
        internal fun css(selectors: Collection<String>): String {
            if (selectors.isEmpty()) return ""
            val sb = StringBuilder(selectors.size * 32)
            var n = 0
            for (sel in selectors) {
                if (n > 0) sb.append(',')
                sb.append(sel)
                if (++n == CHUNK) {
                    sb.append("{display:none!important}")
                    n = 0
                }
            }
            if (n > 0) sb.append("{display:none!important}")
            return sb.toString()
        }
    }
}
