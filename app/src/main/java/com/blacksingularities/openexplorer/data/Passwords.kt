package com.blacksingularities.openexplorer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.blacksingularities.openexplorer.R

/** Zapisany login. [origin] – „https://example.com”. */
data class Credential(
    val id: String = UUID.randomUUID().toString(),
    val origin: String,
    val username: String,
    val password: String,
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0L,
    val uses: Int = 0,
) {
    val host: String get() = PasswordTools.hostOf(origin)
}

/**
 * Własny menedżer haseł (niezależny od Google, Edge i innych usług autouzupełniania).
 *
 * Hasła i lista stron „nigdy nie zapisuj” leżą w jednym pliku zaszyfrowanym AES-256-GCM.
 * Klucz powstaje w Android Keystore (sprzętowo, jeśli telefon to umożliwia) i nie da się go
 * odczytać ani wynieść z urządzenia – plik skopiowany gdzie indziej jest bezużyteczny.
 * Plik jest wyłączony z systemowej kopii zapasowej (reguły w res/xml).
 */
class PasswordStore(context: Context) {
    private val app = context.applicationContext
    private val file = File(File(context.filesDir, "data").apply { mkdirs() }, "passwords.bin")
    private val writer = Executors.newSingleThreadExecutor()

    val items: SnapshotStateList<Credential> = mutableStateListOf()
    /** Domeny, dla których użytkownik nie chce zapisywać haseł. */
    val never: SnapshotStateList<String> = mutableStateListOf()

    /** Komunikat, gdy istniejącego pliku nie dało się odszyfrować (np. po przeniesieniu na inny telefon). */
    var loadError by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(String(decrypt(file.readBytes()), Charsets.UTF_8))
            o.optJSONArray("items")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { items += it.toCredential() } }
            o.optJSONArray("never")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { never += it } }
        } catch (_: Exception) {
            // Bez klucza pliku nie da się odczytać – odkładamy go na bok zamiast nadpisywać.
            file.renameTo(File(file.parentFile, "passwords.unreadable-${System.currentTimeMillis()}.bin"))
            loadError = app.getString(R.string.passwords_load_error)
        }
    }

    private fun persist() {
        val json = JSONObject()
            .put("v", 1)
            .put("items", JSONArray(items.map { it.toJson() }))
            .put("never", JSONArray(never.toList()))
            .toString()
        writer.execute {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(encrypt(json.toByteArray(Charsets.UTF_8)))
                tmp.renameTo(file)
            }
        }
    }

    // ---------------- Wyszukiwanie ----------------

    /** Loginy dla domeny: najpierw dokładnie ta domena, potem jej subdomeny i domena główna. */
    fun forHost(host: String): List<Credential> {
        val h = host.lowercase().removePrefix("www.")
        val base = PasswordTools.baseDomain(h)
        return items.filter { it.host == h || PasswordTools.baseDomain(it.host) == base }
            .sortedWith(compareByDescending<Credential> { it.host == h }.thenByDescending { it.lastUsed }.thenBy { it.username.lowercase() })
    }

    fun find(host: String, username: String): Credential? {
        val h = host.lowercase().removePrefix("www.")
        return items.firstOrNull { it.host == h && it.username == username }
    }

    fun byId(id: String): Credential? = items.firstOrNull { it.id == id }

    /** Liczba wpisów z tym samym hasłem (do ostrzeżenia o powtórzonych hasłach). */
    fun reuseCount(password: String): Int = items.count { it.password == password }

    // ---------------- Zmiany ----------------

    fun save(c: Credential) {
        val i = items.indexOfFirst { it.id == c.id }
        if (i >= 0) items[i] = c.copy(updated = System.currentTimeMillis()) else items.add(c)
        persist()
    }

    /** Nowy login albo nowe hasło dla istniejącego (ta sama domena i nazwa użytkownika). */
    fun upsert(origin: String, username: String, password: String): Credential {
        val existing = find(PasswordTools.hostOf(origin), username)
        val c = existing?.copy(password = password, updated = System.currentTimeMillis())
            ?: Credential(origin = origin, username = username, password = password)
        save(c)
        return c
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        persist()
    }

    fun markUsed(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(lastUsed = System.currentTimeMillis(), uses = items[i].uses + 1)
        persist()
    }

    fun isNever(host: String) = host.lowercase().removePrefix("www.") in never

    fun addNever(host: String) {
        val h = host.lowercase().removePrefix("www.")
        if (h !in never) never.add(h)
        persist()
    }

    fun removeNever(host: String) {
        never.remove(host)
        persist()
    }

    fun clear() {
        items.clear()
        persist()
    }

    // ---------------- Import i eksport ----------------

    /** Import z CSV (Chrome, Edge, Firefox, Bitwarden…). Zwraca liczbę dodanych lub zmienionych loginów. */
    fun importCsv(text: String): Int {
        var n = 0
        PasswordTools.readLogins(text).forEach { l ->
            val existing = find(PasswordTools.hostOf(l.origin), l.username)
            if (existing == null) {
                items.add(Credential(origin = l.origin, username = l.username, password = l.password, note = l.note))
                n++
            } else if (existing.password != l.password) {
                items[items.indexOf(existing)] = existing.copy(password = l.password, updated = System.currentTimeMillis())
                n++
            }
        }
        if (n > 0) persist()
        return n
    }

    /** Eksport w formacie Chrome (zrozumiałym dla większości menedżerów haseł). */
    fun exportCsv(): String = PasswordTools.writeCsv(
        listOf(listOf("name", "url", "username", "password", "note")) +
            items.sortedBy { it.host }.map { listOf(it.host, it.origin, it.username, it.password, it.note) },
    )

    // ---------------- Szyfrowanie ----------------

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** [długość IV][IV][szyfrogram z tagiem GCM] */
    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + c.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val n = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + n)
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return c.doFinal(data, 1 + n, data.size - 1 - n)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "openexplorer.passwords.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private fun Credential.toJson() = JSONObject()
    .put("id", id).put("origin", origin).put("username", username).put("password", password).put("note", note)
    .put("created", created).put("updated", updated).put("lastUsed", lastUsed).put("uses", uses)

private fun JSONObject.toCredential() = Credential(
    id = optString("id").ifEmpty { UUID.randomUUID().toString() },
    origin = optString("origin"),
    username = optString("username"),
    password = optString("password"),
    note = optString("note"),
    created = optLong("created"),
    updated = optLong("updated"),
    lastUsed = optLong("lastUsed"),
    uses = optInt("uses"),
)
