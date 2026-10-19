package com.blacksingularities.openexplorer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.blacksingularities.openexplorer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String, val size: Long, val page: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Aktualizacje z wydań GitHub: każdy merge do main publikuje nowy plik APK. */
class Updater(private val context: Context) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    val currentVersion: String = BuildConfig.VERSION_NAME

    suspend fun check(): UpdateState {
        state = UpdateState.Checking
        state = try {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            if (isNewer(info.version, currentVersion)) UpdateState.Available(info) else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Błąd sieci")
        }
        return state
    }

    private fun fetchLatest(): ReleaseInfo {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        try {
            if (conn.responseCode != 200) error("GitHub: ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = o.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (url.isEmpty()) error("Wydanie nie zawiera pliku APK")
            return ReleaseInfo(
                version = o.optString("tag_name").removePrefix("v"),
                notes = o.optString("body").substringBefore("\n---").trim(),
                apkUrl = url,
                size = size,
                page = o.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(info: ReleaseInfo) {
        state = UpdateState.Downloading(info, 0f)
        state = try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "OpenExplorer-${info.version}.apk")
                var conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                // GitHub przekierowuje na inny host – śledzenie ręczne na wypadek zmiany protokołu.
                var hops = 0
                while (conn.responseCode in 300..399 && hops++ < 5) {
                    val next = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(next).openConnection() as HttpURLConnection
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0 && done - last > 256 * 1024) {
                                last = done
                                val p = done.toFloat() / total
                                withContext(Dispatchers.Main) { state = UpdateState.Downloading(info, p) }
                            }
                        }
                    }
                }
                conn.disconnect()
                out
            }
            UpdateState.Ready(info, file)
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Nie udało się pobrać aktualizacji")
        }
    }

    /** Otwiera instalator systemowy. Zwraca false, gdy trzeba najpierw zezwolić na instalację. */
    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    fun dismiss() {
        state = UpdateState.Idle
    }

    companion object {
        /** Porównanie numeryczne: „1.0.12” > „1.0.9”; przyrostki typu „-dev” są pomijane. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
            val r = parts(remote)
            val l = parts(local)
            if (r.isEmpty()) return false
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
