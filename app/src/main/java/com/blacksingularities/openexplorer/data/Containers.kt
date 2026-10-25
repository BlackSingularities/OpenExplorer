package com.blacksingularities.openexplorer.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Kontener kart (np. „Praca”, „Prywatne”): osobny profil WebView z własnymi ciasteczkami,
 * pamięcią stron i podręczną. Karty bez kontenera używają domyślnego profilu.
 */
data class Container(
    val id: String = newContainerId(),
    val name: String,
    val color: Long = CONTAINER_COLORS.first(),
) {
    /** Nazwa profilu WebView (ProfileStore) tego kontenera. */
    val profile: String get() = profileName(id)

    companion object {
        const val PROFILE_PREFIX = "container-"

        fun profileName(id: String) = PROFILE_PREFIX + id
    }
}

/** Kolory do wyboru dla kontenerów (dobrze widoczne w jasnym i ciemnym motywie). */
val CONTAINER_COLORS = listOf(
    0xFF3D7BF7, 0xFFEA4335, 0xFF34A853, 0xFFFBBC05, 0xFF7C5CFF, 0xFFFF5A00, 0xFF00A884, 0xFFD6336C, 0xFF8D6E63,
)

/** Krótki identyfikator z samych liter i cyfr (bezpieczny w nazwie profilu WebView). */
fun newContainerId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

fun Container.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("color", color)

/** Kontener z JSON albo null, gdy brakuje identyfikatora lub nazwy. */
fun JSONObject.toContainer(): Container? {
    val id = optString("id").filter { it.isLetterOrDigit() }
    val name = optString("name").trim()
    if (id.isEmpty() || name.isEmpty()) return null
    return Container(id, name, optLong("color", CONTAINER_COLORS.first()))
}

fun containersToJson(list: List<Container>): JSONArray = JSONArray(list.map { it.toJson() })

/** Lista kontenerów bez błędnych wpisów i powtórzonych identyfikatorów. */
fun containersFromJson(a: JSONArray): List<Container> =
    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.toContainer() }.distinctBy { it.id }

/** Dołączenie kontenerów z kopii zapasowej: istniejące (ten sam id) dostają nazwę i kolor z kopii. */
fun mergeContainers(current: List<Container>, imported: List<Container>): List<Container> {
    val byId = imported.associateBy { it.id }
    val known = current.map { it.id }.toHashSet()
    return current.map { byId[it.id] ?: it } + imported.filter { it.id !in known }
}

/** Sesja kart: [active] – indeks aktywnej karty. Kontener zapisywany tylko, gdy karta go ma. */
fun sessionToJson(tabs: List<SavedTab>, active: Int): JSONArray =
    JSONArray().put(active).put(JSONArray(tabs.map { t ->
        JSONObject().put("url", t.url).put("title", t.title).also { if (t.container.isNotEmpty()) it.put("container", t.container) }
    }))

fun sessionFromJson(a: JSONArray): Pair<List<SavedTab>, Int> = try {
    val list = a.getJSONArray(1)
    val tabs = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        .map { SavedTab(it.optString("url"), it.optString("title"), it.optString("container")) }
    tabs to a.getInt(0)
} catch (_: Exception) {
    emptyList<SavedTab>() to 0
}
