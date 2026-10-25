package com.blacksingularities.openexplorer.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.blacksingularities.openexplorer.browser.Omnibox
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/** Wpis historii przeglądania (tabela „history” w bazie SQLite). */
@Entity(tableName = "history", indices = [Index("time"), Index("url"), Index("host")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val time: Long,
    /** Domena bez „www.”, małymi literami – do kafelków, ostatnich stron i uzupełniania adresu. */
    val host: String = Omnibox.hostOf(url),
    /** Tytuł i adres małymi literami – po nim szuka LIKE (sam LIKE w SQLite nie zna wielkości polskich liter). */
    val search: String = historySearchKey(title, url),
) {
    fun withTitle(title: String, time: Long = this.time) = copy(title = title, time = time, search = historySearchKey(title, url))
}

/** Domena z liczbą odwiedzin i ostatnio odwiedzonym adresem w niej. */
data class HostVisits(val host: String, val url: String, val title: String, val time: Long, val visits: Int)

data class HostCount(val host: String, val visits: Int)

fun historySearchKey(title: String, url: String) = (title + "\n" + url).lowercase()

/** Wzorzec LIKE „zawiera [query]” – znaki %, _ i \ z zapytania są dosłowne (ESCAPE '\'). */
fun likeContains(query: String): String = buildString {
    append('%')
    query.lowercase().forEach { c ->
        if (c == '%' || c == '_' || c == '\\') append('\\')
        append(c)
    }
    append('%')
}

/**
 * Wyszukiwanie przez LIKE, a nie FTS: historia ma najwyżej [HistoryDao.MAX] wierszy, więc przejście
 * po całej kolumnie trwa ułamek milisekundy, a LIKE '%…%' znajduje dowolny fragment („tube” w „youtube.com”),
 * jak dotychczasowe wyszukiwanie. FTS dopasowuje tylko całe słowa lub ich początki, a adres jest dla niego
 * jednym długim „słowem” – do tego dochodzi druga tabela i jej synchronizacja.
 */
@Dao
abstract class HistoryDao {
    @Insert
    abstract suspend fun insert(entry: HistoryEntry): Long

    @Insert
    abstract suspend fun insertAll(entries: List<HistoryEntry>)

    @Update
    abstract suspend fun update(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    abstract suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    abstract suspend fun count(): Int

    @Query("SELECT * FROM history ORDER BY time DESC, id DESC LIMIT :limit")
    abstract suspend fun latest(limit: Int): List<HistoryEntry>

    /** Usuwa najstarsze wpisy ponad [max]. */
    @Query("DELETE FROM history WHERE id IN (SELECT id FROM history ORDER BY time DESC, id DESC LIMIT -1 OFFSET :max)")
    abstract suspend fun trim(max: Int)

    /** Najnowsze wpisy pasujące do [pattern] (z [likeContains]; „%%” = wszystkie). */
    @Query("SELECT * FROM history WHERE search LIKE :pattern ESCAPE '\\' ORDER BY time DESC, id DESC LIMIT :limit")
    abstract fun search(pattern: String, limit: Int): Flow<List<HistoryEntry>>

    /** Ostatnio odwiedzone różne adresy pasujące do [pattern] – podpowiedzi w pasku adresu. */
    @Query(
        "SELECT id, url, title, MAX(time) AS time, host, search FROM history WHERE search LIKE :pattern ESCAPE '\\' " +
            "GROUP BY url ORDER BY time DESC LIMIT :limit",
    )
    abstract suspend fun recentUrls(pattern: String, limit: Int): List<HistoryEntry>

    /** Ostatnio odwiedzone różne domeny – lista pod kafelkami. */
    @Query(
        "SELECT id, url, title, MAX(time) AS time, host, search FROM history WHERE host != '' " +
            "GROUP BY host ORDER BY time DESC LIMIT :limit",
    )
    abstract fun recentHosts(limit: Int): Flow<List<HistoryEntry>>

    /** Najczęściej odwiedzane domeny z ostatnio odwiedzonym adresem w każdej. */
    @Query(
        "SELECT host, url, title, MAX(time) AS time, COUNT(*) AS visits FROM history WHERE host != '' " +
            "GROUP BY host ORDER BY visits DESC, time DESC LIMIT :limit",
    )
    abstract suspend fun topHosts(limit: Int): List<HostVisits>

    /** Liczba odwiedzin domen wśród [recent] najnowszych wpisów. */
    @Query("SELECT host, COUNT(*) AS visits FROM (SELECT host FROM history ORDER BY time DESC LIMIT :recent) WHERE host != '' GROUP BY host")
    abstract suspend fun hostCounts(recent: Int): List<HostCount>

    /** Nowa wizyta; ponowne wejście na ten sam adres co ostatnio tylko odświeża ostatni wpis. */
    @Transaction
    open suspend fun visit(url: String, title: String, time: Long) {
        val last = latest(1).firstOrNull()
        if (last != null && last.url == url) update(last.withTitle(title.ifBlank { last.title }, time))
        else {
            insert(HistoryEntry(url = url, title = title, time = time))
            trim(MAX)
        }
    }

    /** Tytuł strony przychodzi po wpisie – poprawia go tylko wśród kilku najnowszych. */
    @Transaction
    open suspend fun updateRecentTitle(url: String, title: String) {
        val e = latest(6).firstOrNull { it.url == url } ?: return
        if (e.title != title) update(e.withTitle(title))
    }

    /** Wpisy z dawnego pliku JSON; gdy baza ma już historię (przerwana migracja), nic nie dodaje. */
    @Transaction
    open suspend fun importLegacy(entries: List<HistoryEntry>) {
        if (count() > 0) return
        insertAll(entries.take(MAX))
    }

    companion object {
        const val MAX = 5000
    }
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
}

/** Odczyt dawnego pliku history.json (wersje do 1.3.x): tablica {url, title, time}, od najnowszych. */
object LegacyHistory {
    fun parse(json: String): List<HistoryEntry> {
        val a = try {
            JSONArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            if (url.isBlank()) null else HistoryEntry(url = url, title = o.optString("title"), time = o.optLong("time"))
        }
    }
}
