package com.blacksingularities.openexplorer

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blacksingularities.openexplorer.data.AppDatabase
import com.blacksingularities.openexplorer.data.HistoryDao
import com.blacksingularities.openexplorer.data.HistoryEntry
import com.blacksingularities.openexplorer.data.LegacyHistory
import com.blacksingularities.openexplorer.data.likeContains
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Historia w bazie Room – na JVM przez Robolectric (bez urządzenia, uruchamiane w CI z testDebugUnitTest). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.history()
    }

    @After
    fun close() = db.close()

    private suspend fun all(query: String = "") = dao.search(likeContains(query), Int.MAX_VALUE).first()

    @Test
    fun parsesLegacyJson() {
        val json = """[
            {"url": "https://a.pl/", "title": "A", "time": 30},
            {"url": "", "title": "pusty", "time": 20},
            "nie obiekt",
            {"url": "https://www.B.pl/x", "time": 10}
        ]"""
        val entries = LegacyHistory.parse(json)
        assertEquals(listOf("https://a.pl/", "https://www.B.pl/x"), entries.map { it.url })
        assertEquals(listOf("A", ""), entries.map { it.title })
        assertEquals(listOf(30L, 10L), entries.map { it.time })
        assertEquals("b.pl", entries[1].host)
        assertTrue(LegacyHistory.parse("{zepsuty").isEmpty())
    }

    @Test
    fun legacyImportRunsOnce() = runBlocking {
        val entries = LegacyHistory.parse("""[{"url": "https://a.pl", "title": "A", "time": 2}, {"url": "https://b.pl", "title": "B", "time": 1}]""")
        dao.importLegacy(entries)
        dao.importLegacy(entries)
        assertEquals(listOf("https://a.pl", "https://b.pl"), all().map { it.url })
    }

    @Test
    fun repeatedVisitUpdatesLastEntry() = runBlocking {
        dao.visit("https://a.pl", "A", 1)
        dao.visit("https://a.pl", "", 2)
        dao.visit("https://b.pl", "B", 3)
        dao.visit("https://a.pl", "A2", 4)
        val list = all()
        assertEquals(listOf("https://a.pl", "https://b.pl", "https://a.pl"), list.map { it.url })
        assertEquals(listOf(4L, 3L, 2L), list.map { it.time })
        assertEquals("A", list[2].title)
    }

    @Test
    fun titleUpdatedOnlyForRecentEntries() = runBlocking {
        dao.visit("https://old.pl", "", 1)
        for (i in 2..10) dao.visit("https://n$i.pl", "", i.toLong())
        dao.updateRecentTitle("https://old.pl", "Stara")
        dao.updateRecentTitle("https://n10.pl", "Nowa")
        assertEquals("", all("old.pl").single().title)
        assertEquals(1, all("nowa").size)
    }

    @Test
    fun historyIsLimited() = runBlocking {
        dao.insertAll((1..HistoryDao.MAX).map { HistoryEntry(url = "https://s$it.pl", title = "", time = it.toLong()) })
        dao.visit("https://nowa.pl", "", HistoryDao.MAX + 1L)
        assertEquals(HistoryDao.MAX, dao.count())
        assertTrue(all("s1.pl").none { it.url == "https://s1.pl" })
        assertEquals("https://nowa.pl", dao.latest(1).single().url)
    }

    @Test
    fun searchIgnoresCaseOfPolishLetters() = runBlocking {
        dao.visit("https://lodz.pl", "ŁÓDŹ – Miasto", 1)
        dao.visit("https://www.youtube.com/watch?v=1", "Film", 2)
        dao.visit("https://x.pl/100%25", "sto % procent", 3)
        dao.visit("https://y.pl", "sto procent", 4)
        assertEquals(listOf("https://lodz.pl"), all("łódź").map { it.url })
        assertEquals(listOf("https://www.youtube.com/watch?v=1"), all("TUBE").map { it.url })
        // % i _ w zapytaniu są zwykłymi znakami.
        assertEquals(listOf("https://x.pl/100%25"), all("% p").map { it.url })
        assertTrue(all("y_pl").isEmpty())
    }

    @Test
    fun sitesAndSuggestions() = runBlocking {
        dao.visit("https://www.a.pl/1", "A1", 1)
        dao.visit("https://b.pl", "B", 2)
        dao.visit("https://a.pl/2", "A2", 3)
        dao.visit("https://c.pl", "C", 4)
        dao.visit("https://a.pl/2", "A2", 5)
        dao.visit("https://b.pl", "B", 6)

        val top = dao.topHosts(2)
        assertEquals(listOf("a.pl", "b.pl"), top.map { it.host })
        assertEquals(listOf(3, 2), top.map { it.visits })
        assertEquals("https://a.pl/2", top[0].url)

        assertEquals(listOf("b.pl", "a.pl", "c.pl"), dao.recentHosts(10).first().map { it.host })
        assertEquals(listOf("https://b.pl", "https://a.pl/2", "https://c.pl"), dao.recentUrls(likeContains(""), 3).map { it.url })
        assertEquals(mapOf("b.pl" to 1, "a.pl" to 1), dao.hostCounts(2).associate { it.host to it.visits })
    }
}
