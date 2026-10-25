package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.data.Container
import com.blacksingularities.openexplorer.data.SavedTab
import com.blacksingularities.openexplorer.data.containersFromJson
import com.blacksingularities.openexplorer.data.containersToJson
import com.blacksingularities.openexplorer.data.mergeContainers
import com.blacksingularities.openexplorer.data.newContainerId
import com.blacksingularities.openexplorer.data.sessionFromJson
import com.blacksingularities.openexplorer.data.sessionToJson
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zapis kontenerów kart i sesji z kontenerami. */
class ContainersTest {
    @Test
    fun containersRoundTrip() {
        val list = listOf(Container("abc123", "Praca", 0xFF3D7BF7), Container("def456", "Prywatne", 0xFFEA4335))
        assertEquals(list, containersFromJson(JSONArray(containersToJson(list).toString())))
    }

    @Test
    fun invalidContainersAreSkipped() {
        val json = JSONArray(
            """[{"id":"a1","name":"Praca"},{"id":"","name":"Bez id"},{"id":"b2","name":"  "},{"id":"a1","name":"Duplikat"},
            |{"id":"../x","name":"Ścieżka"},"tekst"]""".trimMargin(),
        )
        val list = containersFromJson(json)
        assertEquals(listOf("a1", "x"), list.map { it.id })
        assertEquals("Praca", list[0].name)
    }

    @Test
    fun profileNames() {
        assertEquals("container-abc", Container("abc", "A").profile)
        val id = newContainerId()
        assertEquals(12, id.length)
        assertTrue(id.all { it.isLetterOrDigit() })
        assertFalse(id == newContainerId())
    }

    @Test
    fun mergeKeepsOrderAndUpdatesNames() {
        val current = listOf(Container("a", "Praca", 1), Container("b", "Dom", 2))
        val imported = listOf(Container("b", "Prywatne", 3), Container("c", "Zakupy", 4))
        val merged = mergeContainers(current, imported)
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals(Container("b", "Prywatne", 3), merged[1])
    }

    @Test
    fun sessionWithContainers() {
        val tabs = listOf(SavedTab("https://a.pl", "A"), SavedTab("https://b.pl", "B", "abc"))
        val json = sessionToJson(tabs, 1)
        assertFalse(json.getJSONArray(1).getJSONObject(0).has("container"))
        assertEquals(tabs to 1, sessionFromJson(JSONArray(json.toString())))
    }

    @Test
    fun oldSessionFormat() {
        // Sesja zapisana przez wersję bez kontenerów.
        val (tabs, active) = sessionFromJson(JSONArray("""[0,[{"url":"https://a.pl","title":"A"}]]"""))
        assertEquals(listOf(SavedTab("https://a.pl", "A", "")), tabs)
        assertEquals(0, active)
        assertEquals(emptyList<SavedTab>() to 0, sessionFromJson(JSONArray("[]")))
    }
}
