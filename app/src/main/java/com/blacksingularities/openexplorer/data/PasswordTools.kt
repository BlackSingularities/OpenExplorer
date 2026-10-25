package com.blacksingularities.openexplorer.data

import java.security.SecureRandom

/** Narzędzia menedżera haseł bez zależności od Androida (łatwe do testowania). */
object PasswordTools {
    /** Domeny drugiego poziomu, pod którymi rejestruje się serwisy (np. sklep.com.pl). */
    private val TWO_LEVEL = setOf(
        "com.pl", "net.pl", "org.pl", "edu.pl", "gov.pl", "info.pl", "biz.pl", "waw.pl",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "co.nz",
        "com.br", "com.tr", "co.in", "com.mx", "com.cn", "co.za",
    )

    /** Domena z adresu, bez „www.”, portu i ścieżki. */
    fun hostOf(origin: String): String = origin.trim()
        .substringAfter("://")
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfter('@').substringBefore(':')
        .lowercase().removePrefix("www.")

    /** „example.com/login” → „https://example.com”; null dla adresów, które nie są stronami (np. android://). */
    fun normalizeOrigin(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        val full = if (t.contains("://")) t else "https://$t"
        val scheme = full.substringBefore("://").lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = hostOf(full)
        if (host.isEmpty() || host.any { it.isWhitespace() }) return null
        return "$scheme://$host"
    }

    /** Domena główna: „accounts.google.com” → „google.com”, „a.sklep.com.pl” → „sklep.com.pl”. */
    fun baseDomain(host: String): String {
        val h = host.removePrefix("www.")
        val parts = h.split('.')
        if (parts.size <= 2 || h.all { it.isDigit() || it == '.' }) return h
        val last2 = parts.takeLast(2).joinToString(".")
        return if (last2 in TWO_LEVEL) parts.takeLast(3).joinToString(".") else last2
    }

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*-_=+?"

    /** Losowe hasło bez łatwych do pomylenia znaków (l, I, O, 0, 1), z każdej grupy co najmniej jeden znak. */
    fun generate(length: Int = 16, symbols: Boolean = true, random: java.util.Random = SecureRandom()): String {
        val sets = buildList {
            add(LOWER); add(UPPER); add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        val all = sets.joinToString("")
        val n = length.coerceIn(8, 64)
        val chars = CharArray(n) { all[random.nextInt(all.length)] }
        val positions = (0 until n).toMutableList().also { java.util.Collections.shuffle(it, random) }
        sets.forEachIndexed { i, s -> chars[positions[i]] = s[random.nextInt(s.length)] }
        return String(chars)
    }

    private val COMMON = setOf(
        "123456", "password", "123456789", "12345678", "12345", "qwerty", "1234567", "111111", "123123", "abc123",
        "password1", "1234", "iloveyou", "000000", "qwerty123", "haslo", "haslo123", "zaq12wsx", "polska", "admin",
        "welcome", "monkey", "dragon", "1q2w3e4r", "qwertyuiop", "marcin", "kochamcie", "misiek",
    )

    /** Siła hasła: 0 bardzo słabe … 4 bardzo silne. */
    fun strength(p: String): Int {
        if (p.isEmpty() || p.lowercase() in COMMON) return 0
        var classes = 0
        if (p.any { it.isLowerCase() }) classes++
        if (p.any { it.isUpperCase() }) classes++
        if (p.any { it.isDigit() }) classes++
        if (p.any { !it.isLetterOrDigit() }) classes++
        var score = when {
            p.length >= 16 -> 3
            p.length >= 12 -> 2
            p.length >= 8 -> 1
            else -> 0
        }
        score += when {
            classes >= 3 -> 1
            classes == 1 -> -1
            else -> 0
        }
        if (p.toSet().size <= 3) score = 0
        return score.coerceIn(0, 4)
    }

    /** Prosty parser CSV (cudzysłowy, przecinki i nowe linie w polach). */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        val s = text.removePrefix("﻿")
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> {
                    row.add(field.toString())
                    field.clear()
                }
                '\r' -> Unit
                '\n' -> {
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    fun writeCsv(rows: List<List<String>>): String = rows.joinToString("\n") { r ->
        r.joinToString(",") { f ->
            if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + f.replace("\"", "\"\"") + "\"" else f
        }
    } + "\n"

    data class CsvLogin(val origin: String, val username: String, val password: String, val note: String)

    /** Loginy z pliku CSV Chrome, Edge, Firefoksa, Bitwardena i podobnych (kolumny rozpoznawane po nazwach). */
    fun readLogins(text: String): List<CsvLogin> {
        val rows = parseCsv(text)
        if (rows.size < 2) return emptyList()
        val head = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } } ?: -1
        val url = col("url", "login_uri", "website", "origin", "web site", "address", "hostname")
        val user = col("username", "login_username", "login", "user", "email", "user name")
        val pass = col("password", "login_password")
        val note = col("note", "notes", "extra")
        if (url < 0 || pass < 0) return emptyList()
        return rows.drop(1).mapNotNull { r ->
            val origin = normalizeOrigin(r.getOrElse(url) { "" }) ?: return@mapNotNull null
            val p = r.getOrElse(pass) { "" }
            if (p.isEmpty()) return@mapNotNull null
            CsvLogin(
                origin,
                if (user >= 0) r.getOrElse(user) { "" } else "",
                p,
                if (note >= 0) r.getOrElse(note) { "" } else "",
            )
        }
    }
}
