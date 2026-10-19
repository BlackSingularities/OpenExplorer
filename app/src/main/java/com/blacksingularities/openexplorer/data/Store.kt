package com.blacksingularities.openexplorer.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Kafelek strony startowej. [iconUrl] – własne logo; puste = logo pobierane automatycznie. */
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val color: Long = 0L,
    val iconUrl: String = "",
    /** auto / letter / custom */
    val iconMode: String = "auto",
)

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val folder: String = "",
    val created: Long = System.currentTimeMillis(),
)

data class HistoryEntry(val url: String, val title: String, val time: Long)

data class SavedTab(val url: String, val title: String)

/** Wyjątki dla jednej domeny. */
data class SiteRule(
    val host: String,
    val adBlock: Boolean = true,
    val javascript: Boolean = true,
    val desktop: Boolean = false,
    val darkMode: Boolean = true,
    /** 0 = domyślny rozmiar tekstu. */
    val textZoom: Int = 0,
) {
    val isDefault: Boolean get() = adBlock && javascript && !desktop && darkMode && textZoom == 0
}

val DEFAULT_TILES = listOf(
    Tile(title = "Google", url = "https://www.google.com", color = 0xFF4285F4),
    Tile(title = "YouTube", url = "https://www.youtube.com", color = 0xFFFF0033),
    Tile(title = "Wikipedia", url = "https://pl.wikipedia.org", color = 0xFF636466),
    Tile(title = "Facebook", url = "https://www.facebook.com", color = 0xFF1877F2),
    Tile(title = "Allegro", url = "https://allegro.pl", color = 0xFFFF5A00),
    Tile(title = "Onet", url = "https://www.onet.pl", color = 0xFFE4002B),
    Tile(title = "GitHub", url = "https://github.com", color = 0xFF24292F),
    Tile(title = "Reddit", url = "https://www.reddit.com", color = 0xFFFF4500),
    Tile(title = "Gmail", url = "https://mail.google.com", color = 0xFFEA4335),
)

/** Dane użytkownika (kafelki, zakładki, historia, sesja kart) w plikach JSON. */
class Store(context: Context) {
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<String, Job>()

    val tiles: SnapshotStateList<Tile> = mutableStateListOf()
    val bookmarks: SnapshotStateList<Bookmark> = mutableStateListOf()
    val history: SnapshotStateList<HistoryEntry> = mutableStateListOf()
    val downloads: SnapshotStateList<Long> = mutableStateListOf()
    val sites = androidx.compose.runtime.mutableStateMapOf<String, SiteRule>()

    init {
        val t = readArray("tiles")
        if (t == null) tiles.addAll(DEFAULT_TILES) else tiles.addAll(t.objects().map { it.toTile() })
        readArray("bookmarks")?.objects()?.forEach { bookmarks += it.toBookmark() }
        readArray("history")?.objects()?.forEach {
            history += HistoryEntry(it.optString("url"), it.optString("title"), it.optLong("time"))
        }
        readArray("sites")?.objects()?.forEach {
            val r = SiteRule(
                it.optString("host"), it.optBoolean("adBlock", true), it.optBoolean("javascript", true),
                it.optBoolean("desktop"), it.optBoolean("darkMode", true), it.optInt("textZoom"),
            )
            sites[r.host] = r
        }
        readArray("downloads")?.let { a -> for (i in 0 until a.length()) downloads += a.getLong(i) }
    }

    // Kafelki
    fun saveTile(tile: Tile) {
        val i = tiles.indexOfFirst { it.id == tile.id }
        if (i >= 0) tiles[i] = tile else tiles += tile
        saveTiles()
    }

    fun removeTile(id: String) {
        tiles.removeAll { it.id == id }
        saveTiles()
    }

    fun moveTile(from: Int, to: Int) {
        if (from !in tiles.indices || to !in tiles.indices || from == to) return
        val t = tiles.removeAt(from)
        tiles.add(to, t)
        saveTiles()
    }

    fun resetTiles() {
        tiles.clear()
        tiles.addAll(DEFAULT_TILES.map { it.copy(id = UUID.randomUUID().toString()) })
        saveTiles()
    }

    private fun saveTiles() = write("tiles") { JSONArray(tiles.map { it.toJson() }) }

    // Zakładki
    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(url: String, title: String): Boolean {
        val existing = bookmarks.firstOrNull { it.url == url }
        if (existing != null) bookmarks.remove(existing) else bookmarks.add(0, Bookmark(title = title.ifBlank { url }, url = url))
        saveBookmarks()
        return existing == null
    }

    fun saveBookmark(b: Bookmark) {
        val i = bookmarks.indexOfFirst { it.id == b.id }
        if (i >= 0) bookmarks[i] = b else bookmarks.add(0, b)
        saveBookmarks()
    }

    fun removeBookmark(id: String) {
        bookmarks.removeAll { it.id == id }
        saveBookmarks()
    }

    private fun saveBookmarks() = write("bookmarks") { JSONArray(bookmarks.map { it.toJson() }) }

    // Historia
    fun addHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val first = history.firstOrNull()
        if (first != null && first.url == url) {
            history[0] = first.copy(title = title.ifBlank { first.title }, time = System.currentTimeMillis())
        } else {
            history.add(0, HistoryEntry(url, title, System.currentTimeMillis()))
            while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        }
        saveHistory()
    }

    fun updateHistoryTitle(url: String, title: String) {
        val i = history.indexOfFirst { it.url == url }
        if (i in 0..5 && title.isNotBlank() && history[i].title != title) {
            history[i] = history[i].copy(title = title)
            saveHistory()
        }
    }

    fun removeHistory(entry: HistoryEntry) {
        history.remove(entry)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    private fun saveHistory() = write("history", debounce = 1500) {
        JSONArray(history.map { JSONObject().put("url", it.url).put("title", it.title).put("time", it.time) })
    }

    // Ustawienia stron
    fun rule(host: String?): SiteRule? {
        var h = host?.removePrefix("www.") ?: return null
        while (true) {
            sites[h]?.let { return it }
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.lastIndexOf('.')) return null
            h = h.substring(dot + 1)
        }
    }

    fun saveRule(rule: SiteRule) {
        if (rule.isDefault) sites.remove(rule.host) else sites[rule.host] = rule
        write("sites") {
            JSONArray(sites.values.map {
                JSONObject().put("host", it.host).put("adBlock", it.adBlock).put("javascript", it.javascript)
                    .put("desktop", it.desktop).put("darkMode", it.darkMode).put("textZoom", it.textZoom)
            })
        }
    }

    /** Najczęściej odwiedzane domeny (do szybkiego dodawania kafelków). */
    fun topSites(limit: Int): List<HistoryEntry> = history.groupBy { runCatching { android.net.Uri.parse(it.url).host?.removePrefix("www.") }.getOrNull() ?: "" }
        .filterKeys { it.isNotEmpty() }
        .entries.sortedByDescending { it.value.size }
        .take(limit)
        .map { e -> e.value.first().let { HistoryEntry("https://" + (android.net.Uri.parse(it.url).host ?: e.key), it.title, it.time) } }

    // Pobrane
    fun addDownload(id: Long) {
        downloads.add(0, id)
        write("downloads") { JSONArray(downloads.toList()) }
    }

    fun removeDownload(id: Long) {
        downloads.remove(id)
        write("downloads") { JSONArray(downloads.toList()) }
    }

    // Sesja kart
    fun saveSession(tabs: List<SavedTab>, active: Int) = write("session", debounce = 500) {
        JSONArray().put(active).put(JSONArray(tabs.map { JSONObject().put("url", it.url).put("title", it.title) }))
    }

    fun loadSession(): Pair<List<SavedTab>, Int> {
        val a = readArray("session") ?: return emptyList<SavedTab>() to 0
        return try {
            val tabs = a.getJSONArray(1).objects().map { SavedTab(it.optString("url"), it.optString("title")) }
            tabs to a.getInt(0)
        } catch (_: Exception) {
            emptyList<SavedTab>() to 0
        }
    }

    // Kopia zapasowa
    fun export(): JSONObject = JSONObject()
        .put("tiles", JSONArray(tiles.map { it.toJson() }))
        .put("bookmarks", JSONArray(bookmarks.map { it.toJson() }))

    fun import(o: JSONObject) {
        o.optJSONArray("tiles")?.let { a ->
            tiles.clear()
            tiles.addAll(a.objects().map { it.toTile() })
            saveTiles()
        }
        o.optJSONArray("bookmarks")?.let { a ->
            val known = bookmarks.map { it.url }.toHashSet()
            a.objects().map { it.toBookmark() }.filter { it.url !in known }.forEach { bookmarks += it }
            saveBookmarks()
        }
    }

    private fun readArray(name: String): JSONArray? = try {
        val f = File(dir, "$name.json")
        if (f.exists()) JSONArray(f.readText()) else null
    } catch (_: Exception) {
        null
    }

    /** Zapis w tle; kolejne zapisy tego samego pliku w krótkim czasie są łączone. */
    private fun write(name: String, debounce: Long = 150, build: () -> JSONArray) {
        val json = build().toString()
        pending[name]?.cancel()
        pending[name] = scope.launch {
            delay(debounce)
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(json)
            tmp.renameTo(File(dir, "$name.json"))
        }
    }

    private companion object {
        const val MAX_HISTORY = 5000
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun Tile.toJson() = JSONObject()
    .put("id", id).put("title", title).put("url", url).put("color", color)
    .put("iconUrl", iconUrl).put("iconMode", iconMode)

private fun JSONObject.toTile() = Tile(
    id = optString("id").ifEmpty { UUID.randomUUID().toString() },
    title = optString("title"),
    url = optString("url"),
    color = optLong("color"),
    iconUrl = optString("iconUrl"),
    iconMode = optString("iconMode", "auto"),
)

private fun Bookmark.toJson() = JSONObject()
    .put("id", id).put("title", title).put("url", url).put("folder", folder).put("created", created)

private fun JSONObject.toBookmark() = Bookmark(
    id = optString("id").ifEmpty { UUID.randomUUID().toString() },
    title = optString("title"),
    url = optString("url"),
    folder = optString("folder"),
    created = optLong("created", System.currentTimeMillis()),
)
