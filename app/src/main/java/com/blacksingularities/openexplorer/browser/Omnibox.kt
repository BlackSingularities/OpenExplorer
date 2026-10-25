package com.blacksingularities.openexplorer.browser

/** Logika paska adresu bez zależności od Androida. */
object Omnibox {
    /**
     * Dopisuje resztę adresu podczas pisania: „you” → „youtube.com”, „https://gi” → „https://github.com”.
     * [candidates] – domeny bez „www.”, od najczęściej odwiedzanych. Zwraca cały tekst (wpisany + dopisany) albo null.
     */
    fun complete(typed: String, candidates: List<String>): String? {
        if (typed.isEmpty() || typed.any { it.isWhitespace() }) return null
        val lower = typed.lowercase()
        for (c in candidates) {
            for (form in arrayOf(c, "www.$c", "https://$c", "https://www.$c", "http://$c", "http://www.$c")) {
                if (form.length > lower.length && form.startsWith(lower)) return typed + form.substring(typed.length)
            }
        }
        return null
    }

    /** Domeny do uzupełniania: kafelki i zakładki mają pierwszeństwo, potem najczęściej odwiedzane. */
    fun rankHosts(history: List<String>, preferred: List<String>): List<String> =
        rankHosts(history.groupingBy { it }.eachCount(), preferred)

    /** Jak wyżej, z gotową liczbą odwiedzin każdej domeny [visits]. */
    fun rankHosts(visits: Map<String, Int>, preferred: List<String>): List<String> {
        val counts = HashMap(visits)
        preferred.forEach { h -> counts[h] = (counts[h] ?: 0) + 5 }
        return counts.entries.filter { it.key.isNotEmpty() }.sortedByDescending { it.value }.map { it.key }
    }

    /** Domena z adresu, bez „www.” – małymi literami. */
    fun hostOf(url: String): String = url.substringAfter("://", url)
        .substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        .lowercase().removePrefix("www.")
}
