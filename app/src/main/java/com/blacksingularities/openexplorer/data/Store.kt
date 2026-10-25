package com.blacksingularities.openexplorer.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** Karta zapisana w sesji (albo ostatnio zamknięta); [container] – id kontenera, puste = bez kontenera. */
data class SavedTab(val url: String, val title: String, val container: String = "")

/** Wyjątki dla jednej domeny. */
data class SiteRule(
    val host: String,
    val adBlock: Boolean = true,
    val javascript: Boolean = true,
    val desktop: Boolean = false,
    val darkMode: Boolean = true,
    /** 0 = domyślny rozmiar tekstu. */
    val textZoom: Int = 0,
    /** Własny arkusz stylów wstrzykiwany po wczytaniu strony. */
    val css: String = "",
    /** Własny skrypt uruchamiany po wczytaniu strony (nie w kartach incognito). */
    val js: String = "",
) {
    val isDefault: Boolean get() = adBlock && javascript && !desktop && darkMode && textZoom == 0 && css.isBlank() && js.isBlank()

    fun toJson(): JSONObject = JSONObject().put("host", host).put("adBlock", adBlock).put("javascript", javascript)
        .put("desktop", desktop).put("darkMode", darkMode).put("textZoom", textZoom)
        .also { if (css.isNotBlank()) it.put("css", css) }
        .also { if (js.isNotBlank()) it.put("js", js) }

    companion object {
        /** Najdłuższy własny CSS lub skrypt (znaki) – większe wklejki są przycinane. */
        const val MAX_CODE = 50_000

        /** Reguła z JSON (także ze starszych wersji bez css/js); null bez domeny. */
        fun fromJson(o: JSONObject): SiteRule? {
            val host = o.optString("host").trim().lowercase().removePrefix("www.")
            if (host.isEmpty()) return null
            return SiteRule(
                host, o.optBoolean("adBlock", true), o.optBoolean("javascript", true),
                o.optBoolean("desktop"), o.optBoolean("darkMode", true), o.optInt("textZoom"),
                o.optString("css").take(MAX_CODE), o.optString("js").take(MAX_CODE),
            )
        }
    }
}

/** Kod języka telefonu, np. „pl”, „en”, „he” (nowe kody, nie przestarzałe „iw”); nieznany – „en”. */
fun deviceLanguage(): String = java.util.Locale.getDefault().toLanguageTag().substringBefore('-').lowercase()
    .takeIf { it.length in 2..3 && it != "und" } ?: "en"

/**
 * Domyślne kafelki dla języka [lang]: po polsku z Allegro i Onetem, w innych językach
 * z Amazonem i X oraz Wikipedią w języku telefonu.
 */
fun defaultTiles(lang: String): List<Tile> {
    val pl = lang == "pl"
    return listOf(
        Tile(title = "Google", url = "https://www.google.com", color = 0xFF4285F4),
        Tile(title = "YouTube", url = "https://www.youtube.com", color = 0xFFFF0033),
        Tile(title = "Wikipedia", url = "https://$lang.wikipedia.org", color = 0xFF636466),
        Tile(title = "Facebook", url = "https://www.facebook.com", color = 0xFF1877F2),
        if (pl) Tile(title = "Allegro", url = "https://allegro.pl", color = 0xFFFF5A00)
        else Tile(title = "Amazon", url = "https://www.amazon.com", color = 0xFFFF9900),
        if (pl) Tile(title = "Onet", url = "https://www.onet.pl", color = 0xFFE4002B)
        else Tile(title = "X", url = "https://x.com", color = 0xFF0F1419),
        Tile(title = "GitHub", url = "https://github.com", color = 0xFF24292F),
        Tile(title = "Reddit", url = "https://www.reddit.com", color = 0xFFFF4500),
        Tile(title = "Gmail", url = "https://mail.google.com", color = 0xFFEA4335),
    )
}

/** Dane użytkownika: kafelki, zakładki i sesja kart w plikach JSON, historia w bazie SQLite (Room). */
class Store(context: Context) {
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashMap<String, Job>()

    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "openexplorer.db").build()
    private val historyDao = db.history()

    /** Zapisy historii po kolei, w tej samej kolejności co wywołania (wizyta, tytuł, czyszczenie). */
    private val historyWrites = Dispatchers.IO.limitedParallelism(1)

    val tiles: SnapshotStateList<Tile> = mutableStateListOf()
    val bookmarks: SnapshotStateList<Bookmark> = mutableStateListOf()
    val downloads: SnapshotStateList<Long> = mutableStateListOf()
    val sites = androidx.compose.runtime.mutableStateMapOf<String, SiteRule>()
    val containers: SnapshotStateList<Container> = mutableStateListOf()

    init {
        val t = readArray("tiles")
        if (t == null) tiles.addAll(defaultTiles(deviceLanguage())) else tiles.addAll(t.objects().map { it.toTile() })
        readArray("bookmarks")?.objects()?.forEach { bookmarks += it.toBookmark() }
        readArray("sites")?.objects()?.forEach { o -> SiteRule.fromJson(o)?.let { sites[it.host] = it } }
        readArray("downloads")?.let { a -> for (i in 0 until a.length()) downloads += a.getLong(i) }
        readArray("containers")?.let { containers.addAll(containersFromJson(it)) }
        migrateHistory()
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
        tiles.addAll(defaultTiles(deviceLanguage()))
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
        val time = System.currentTimeMillis()
        scope.launch(historyWrites) { historyDao.visit(url, title, time) }
    }

    fun updateHistoryTitle(url: String, title: String) {
        if (title.isBlank()) return
        scope.launch(historyWrites) { historyDao.updateRecentTitle(url, title) }
    }

    fun removeHistory(entry: HistoryEntry) {
        scope.launch(historyWrites) { historyDao.delete(entry.id) }
    }

    fun clearHistory() {
        scope.launch(historyWrites) {
            historyDao.clear()
            File(dir, "history.json").delete()
        }
    }

    /** Najnowsze wpisy zawierające [query] w tytule lub adresie (puste = wszystkie), najwyżej [limit]. */
    fun historyFlow(query: String, limit: Int): Flow<List<HistoryEntry>> = historyDao.search(likeContains(query.trim()), limit)

    /** Ostatnio odwiedzone różne domeny (lista na stronie startowej). */
    fun recentSites(limit: Int): Flow<List<HistoryEntry>> = historyDao.recentHosts(limit)

    /** Różne adresy z historii do podpowiedzi w pasku adresu. */
    suspend fun historySuggestions(query: String, limit: Int): List<HistoryEntry> =
        withContext(Dispatchers.IO) { historyDao.recentUrls(likeContains(query.trim()), limit) }

    /** Liczba odwiedzin domen wśród [recent] najnowszych wpisów (uzupełnianie adresu). */
    suspend fun hostVisits(recent: Int): Map<String, Int> =
        withContext(Dispatchers.IO) { historyDao.hostCounts(recent).associate { it.host to it.visits } }

    /**
     * Jednorazowe przeniesienie historii z data/history.json (do wersji 1.3.x) do bazy. Plik jest usuwany
     * dopiero po zapisie wpisów, a nie zostaje jako kopia – inaczej „Wyczyść historię” nie usuwałaby
     * starych wpisów. Gdy migracja przerwie się po zapisie, a przed usunięciem pliku, kolejna niczego nie dubluje.
     */
    private fun migrateHistory() {
        val file = File(dir, "history.json")
        if (!file.exists()) return
        scope.launch(historyWrites) {
            val entries = try {
                LegacyHistory.parse(file.readText())
            } catch (_: Exception) {
                emptyList()
            }
            historyDao.importLegacy(entries)
            file.delete()
        }
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
        saveSites()
    }

    private fun saveSites() = write("sites") { JSONArray(sites.values.map { it.toJson() }) }

    /** Najczęściej odwiedzane domeny (do szybkiego dodawania kafelków). */
    suspend fun topSites(limit: Int): List<HistoryEntry> = withContext(Dispatchers.IO) {
        historyDao.topHosts(limit).map { v ->
            val host = runCatching { android.net.Uri.parse(v.url).host }.getOrNull() ?: v.host
            HistoryEntry(url = "https://$host", title = v.title, time = v.time)
        }
    }

    // Pobrane
    fun addDownload(id: Long) {
        downloads.add(0, id)
        write("downloads") { JSONArray(downloads.toList()) }
    }

    fun removeDownload(id: Long) {
        downloads.remove(id)
        write("downloads") { JSONArray(downloads.toList()) }
    }

    // Kontenery kart
    fun container(id: String): Container? = if (id.isEmpty()) null else containers.firstOrNull { it.id == id }

    fun saveContainer(c: Container) {
        val i = containers.indexOfFirst { it.id == c.id }
        if (i >= 0) containers[i] = c else containers += c
        saveContainers()
    }

    fun removeContainer(id: String) {
        containers.removeAll { it.id == id }
        saveContainers()
    }

    private fun saveContainers() = write("containers") { containersToJson(containers) }

    // Sesja kart
    fun saveSession(tabs: List<SavedTab>, active: Int) = write("session", debounce = 500) { sessionToJson(tabs, active) }

    fun loadSession(): Pair<List<SavedTab>, Int> =
        readArray("session")?.let { sessionFromJson(it) } ?: (emptyList<SavedTab>() to 0)

    // Kopia zapasowa
    fun export(): JSONObject = JSONObject()
        .put("tiles", JSONArray(tiles.map { it.toJson() }))
        .put("bookmarks", JSONArray(bookmarks.map { it.toJson() }))
        .put("containers", containersToJson(containers))
        .put("sites", JSONArray(sites.values.map { it.toJson() }))

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
        o.optJSONArray("containers")?.let { a ->
            val merged = mergeContainers(containers.toList(), containersFromJson(a))
            containers.clear()
            containers.addAll(merged)
            saveContainers()
        }
        // Ustawienia stron (z własnym CSS i skryptami): reguła z kopii zastępuje regułę tej samej domeny.
        o.optJSONArray("sites")?.let { a ->
            a.objects().mapNotNull { SiteRule.fromJson(it) }.forEach { if (it.isDefault) sites.remove(it.host) else sites[it.host] = it }
            saveSites()
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
