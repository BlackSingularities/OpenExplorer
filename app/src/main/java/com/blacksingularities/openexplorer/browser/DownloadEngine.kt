package com.blacksingularities.openexplorer.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ServiceCompat
import com.blacksingularities.openexplorer.MainActivity
import com.blacksingularities.openexplorer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, FAILED, DONE }

/** Jedno pobieranie. Pola z „by mutableStateOf” odświeżają listę pobranych na bieżąco. */
class DownloadTask(
    val id: String,
    val url: String,
    fileName: String,
    mime: String?,
    val userAgent: String,
    val referer: String?,
    /** Profil WebView (ciasteczka); null = domyślny. */
    val profile: String?,
    /** Pobieranie z karty incognito – nie trafia do zapisanej listy. */
    val incognito: Boolean,
    val created: Long = System.currentTimeMillis(),
) {
    var fileName by mutableStateOf(fileName)
    var mime: String? = mime
    var status by mutableStateOf(DownloadStatus.QUEUED)
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(-1L)
    /** Bajty na sekundę (średnia z ostatnich sekund). */
    var speed by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    /** Plik w MediaStore (folder „Pobrane”). */
    var uri by mutableStateOf<String?>(null)
    var finished by mutableLongStateOf(0L)

    val host: String get() = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
    val active: Boolean get() = status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED
    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

    /** Pozostały czas w sekundach; -1, gdy nieznany. */
    val etaSeconds: Long get() = if (total > 0 && speed > 0) (total - done) / speed else -1

    internal var job: Job? = null
    /** Poprzednie (anulowane) podejście – nowe czeka na jego koniec, żeby dwa nie pisały do jednego pliku. */
    internal var lastJob: Job? = null
    @Volatile internal var connection: HttpURLConnection? = null

    fun toJson(): JSONObject = JSONObject().put("id", id).put("url", url).put("fileName", fileName)
        .put("mime", mime ?: "").put("ua", userAgent).put("referer", referer ?: "").put("profile", profile ?: "")
        .put("created", created).put("status", status.name).put("done", done).put("total", total)
        .put("uri", uri ?: "").put("error", error ?: "").put("finished", finished)

    companion object {
        fun fromJson(o: JSONObject): DownloadTask? {
            val url = o.optString("url").takeIf { it.isNotEmpty() } ?: return null
            return DownloadTask(
                o.optString("id").ifEmpty { UUID.randomUUID().toString() }, url, o.optString("fileName"),
                o.optString("mime").ifEmpty { null }, o.optString("ua"), o.optString("referer").ifEmpty { null },
                o.optString("profile").ifEmpty { null }, incognito = false, created = o.optLong("created"),
            ).apply {
                status = runCatching { DownloadStatus.valueOf(o.optString("status")) }.getOrDefault(DownloadStatus.FAILED)
                done = o.optLong("done")
                total = o.optLong("total", -1)
                uri = o.optString("uri").ifEmpty { null }
                error = o.optString("error").ifEmpty { null }
                finished = o.optLong("finished")
            }
        }
    }
}

/**
 * Własne pobieranie plików zamiast systemowego DownloadManagera: postęp, prędkość i pozostały czas,
 * pauza, wznawianie od miejsca przerwania (nagłówek Range), samoczynne ponawianie po zerwaniu sieci,
 * powiadomienie z postępem i – po zakończeniu – z opcją otwarcia pliku.
 *
 * Pliki trafiają od razu do folderu „Pobrane” przez MediaStore (Android 10+); do zakończenia są
 * oznaczone jako „w toku”, więc inne aplikacje nie widzą niedokończonych plików.
 */
class DownloadEngine(private val context: Context) {
    val tasks = mutableStateListOf<DownloadTask>()

    /** Ciasteczka do adresu w danym profilu – podawane przez przeglądarkę (profile WebView żyją w niej). */
    var cookieSource: ((url: String, profile: String?) -> String?)? = null

    /** Wywoływane po zakończeniu pobierania (komunikat „Pobrano – Otwórz” w otwartej aplikacji). */
    var onFinished: ((DownloadTask) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val file = File(context.filesDir, "data/downloads.json")
    private val nm = context.getSystemService(NotificationManager::class.java)
    private var lastNotify = 0L

    /** Wczytanie listy po starcie aplikacji; przerwane pobierania czekają na wznowienie. */
    fun restore() {
        val list = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return
        for (i in 0 until list.length()) {
            val t = DownloadTask.fromJson(list.optJSONObject(i) ?: continue) ?: continue
            if (t.status == DownloadStatus.RUNNING || t.status == DownloadStatus.QUEUED) t.status = DownloadStatus.PAUSED
            tasks += t
        }
    }

    fun enqueue(
        url: String, fileName: String, mime: String?, userAgent: String, referer: String?, profile: String?, incognito: Boolean,
    ): DownloadTask {
        val task = DownloadTask(UUID.randomUUID().toString(), url, fileName, fixMime(mime, fileName), userAgent, referer, profile, incognito)
        tasks.add(0, task)
        save()
        pump()
        return task
    }

    fun pause(task: DownloadTask) {
        if (!task.active) return
        task.lastJob = task.job
        task.job?.cancel()
        task.job = null
        disconnect(task)
        task.status = DownloadStatus.PAUSED
        task.speed = 0
        save()
        pump()
    }

    fun pauseAll() = tasks.filter { it.active }.forEach { pause(it) }

    /** Wznowienie albo ponowna próba (od miejsca przerwania, gdy serwer na to pozwala). */
    fun resume(task: DownloadTask) {
        if (task.active || task.status == DownloadStatus.DONE) return
        task.error = null
        task.status = DownloadStatus.QUEUED
        save()
        pump()
    }

    /** Usunięcie z listy; niedokończony plik jest kasowany, gotowy – tylko gdy [deleteFile]. */
    fun remove(task: DownloadTask, deleteFile: Boolean = false) {
        task.lastJob = task.job
        task.job?.cancel()
        task.job = null
        disconnect(task)
        if (task.status != DownloadStatus.DONE || deleteFile) deleteUri(task.uri)
        tasks.remove(task)
        nm?.cancel(task.id.hashCode())
        save()
        pump()
    }

    /** Usunięcie z listy wszystkich zakończonych (pliki zostają). */
    fun clearFinished() {
        tasks.removeAll { it.status == DownloadStatus.DONE }
        save()
    }

    /** Uruchamia oczekujące pobierania (najwyżej [PARALLEL] naraz) i pilnuje usługi pierwszoplanowej. */
    private fun pump() {
        val running = tasks.count { it.status == DownloadStatus.RUNNING }
        tasks.filter { it.status == DownloadStatus.QUEUED && it.job == null }.reversed()
            .take((PARALLEL - running).coerceAtLeast(0))
            .forEach { start(it) }
        if (tasks.any { it.active }) DownloadService.start(context) else DownloadService.stop(context)
        notifyProgress(force = true)
    }

    private fun start(task: DownloadTask) {
        task.status = DownloadStatus.RUNNING
        task.error = null
        // Ciasteczka czytane na głównym wątku (profile WebView tego wymagają).
        val cookie = runCatching { cookieSource?.invoke(task.url, task.profile) }.getOrNull()
            ?: if (task.profile == null) runCatching { android.webkit.CookieManager.getInstance().getCookie(task.url) }.getOrNull() else null
        val previous = task.lastJob
        task.job = scope.launch {
            previous?.join()
            var attempt = 0
            while (true) {
                try {
                    transfer(task, cookie)
                    ui { finish(task) }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpError) {
                    // Błąd serwera (np. 404, 403) – ponawianie nic nie da.
                    ui { fail(task, context.getString(R.string.download_error_http, e.code)) }
                    return@launch
                } catch (e: Exception) {
                    attempt++
                    if (attempt > RETRIES) {
                        ui { fail(task, e.message ?: e.javaClass.simpleName) }
                        return@launch
                    }
                    ui { task.speed = 0 }
                    delay(RETRY_DELAYS[(attempt - 1).coerceAtMost(RETRY_DELAYS.lastIndex)])
                }
            }
        }
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    /** Właściwe pobieranie (wątek w tle); dopisuje do pliku, gdy serwer obsługuje wznawianie. */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun transfer(task: DownloadTask, cookie: String?) {
        var url = URL(task.url)
        var conn: HttpURLConnection
        var redirects = 0
        // Wznowienie od faktycznego rozmiaru pliku (licznik na ekranie może być o chwilę opóźniony).
        val existing = task.uri?.let { Uri.parse(it) }
        val offset = existing?.let { fileSize(it) } ?: 0L
        // Przekierowania obsługiwane ręcznie – HttpURLConnection nie przechodzi między http i https.
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", task.userAgent)
                setRequestProperty("Accept-Encoding", "identity")
                cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                task.referer?.let { setRequestProperty("Referer", it) }
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            val code = conn.responseCode
            if (code in 300..399 && code != 304) {
                val location = conn.getHeaderField("Location") ?: throw HttpError(code)
                conn.disconnect()
                if (++redirects > 8) throw HttpError(code)
                url = URL(url, location)
                continue
            }
            if (code == 416 && task.total > 0 && offset >= task.total) {
                conn.disconnect()
                return // Plik już kompletny.
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw HttpError(code)
            }
            break
        }
        // Pauza zamyka połączenie – przerywa blokujący odczyt z sieci.
        task.connection = conn
        try {
            val append = conn.responseCode == HttpURLConnection.HTTP_PARTIAL && offset > 0 && existing != null
            val length = conn.contentLengthLong
            var target = existing
            if (target == null) {
                val created = createFile(task.fileName, task.mime ?: conn.contentType?.substringBefore(';'))
                val mime = fixMime(task.mime ?: conn.contentType, created.second)
                target = created.first
                ui {
                    task.uri = created.first.toString()
                    task.fileName = created.second
                    task.mime = mime
                    save()
                }
            }
            val start = if (append) offset else 0L
            val total = if (length >= 0) length + start else -1
            ui {
                task.done = start
                task.total = total
            }
            // Bez wznowienia plik jest nadpisywany od początku („wt” obcina starą zawartość).
            val out = context.contentResolver.openOutputStream(target, if (append) "wa" else "wt")
                ?: throw IOException("Brak dostępu do pliku")
            out.use { stream ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = start
                    var windowStart = SystemClock.elapsedRealtime()
                    var windowBytes = 0L
                    var lastUi = 0L
                    while (true) {
                        coroutineContextEnsureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        stream.write(buf, 0, n)
                        done += n
                        windowBytes += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUi >= 250) {
                            lastUi = now
                            val elapsed = now - windowStart
                            val speed = if (elapsed > 0) windowBytes * 1000 / elapsed else 0
                            if (elapsed > 3000) {
                                windowStart = now
                                windowBytes = 0
                            }
                            val snapshot = done
                            ui {
                                task.done = snapshot
                                if (speed > 0) task.speed = speed
                                notifyProgress()
                            }
                        }
                    }
                    val final = done
                    ui { task.done = final }
                    if (total > 0 && final < total) throw IOException("Połączenie przerwane")
                }
            }
        } finally {
            task.connection = null
            conn.disconnect()
        }
    }

    /** Zamknięcie połączenia w tle (na głównym wątku sieć jest zabroniona). */
    private fun disconnect(task: DownloadTask) {
        val conn = task.connection ?: return
        scope.launch { runCatching { conn.disconnect() } }
    }

    /** Rozmiar pliku w MediaStore; 0, gdy go nie ma. */
    private fun fileSize(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0) } ?: 0L
    }.getOrDefault(0L)

    private suspend fun coroutineContextEnsureActive() = kotlin.coroutines.coroutineContext.ensureActive()

    private fun finish(task: DownloadTask) {
        // Wstrzymane lub usunięte w ostatniej chwili – wynik starego podejścia jest pomijany.
        if (task.status != DownloadStatus.RUNNING || task !in tasks) return
        task.job = null
        task.status = DownloadStatus.DONE
        task.speed = 0
        if (task.total <= 0) task.total = task.done
        task.finished = System.currentTimeMillis()
        task.uri?.let { publish(Uri.parse(it)) }
        save()
        pump()
        notifyFinished(task)
        onFinished?.invoke(task)
    }

    private fun fail(task: DownloadTask, message: String) {
        if (task.status != DownloadStatus.RUNNING || task !in tasks) return
        task.job = null
        task.status = DownloadStatus.FAILED
        task.speed = 0
        task.error = message
        save()
        pump()
        notifyFailed(task)
    }

    // ---------- Plik w folderze „Pobrane” ----------

    /** Nowy plik „w toku” w MediaStore; zwraca adres i ostateczną nazwę (system dopisuje „(1)” przy powtórce). */
    private fun createFile(name: String, mime: String?): Pair<Uri, String> {
        if (Build.VERSION.SDK_INT < 29) throw IOException("Android 10+")
        return createMediaStore(name, mime)
    }

    @RequiresApi(29)
    private fun createMediaStore(name: String, mime: String?): Pair<Uri, String> {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, fixMime(mime, name) ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore")
        val finalName = runCatching {
            resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: name
        return uri to finalName
    }

    /** Zdjęcie znacznika „w toku” – plik staje się widoczny w menedżerze plików i galerii. */
    private fun publish(uri: Uri) {
        if (Build.VERSION.SDK_INT < 29) return
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
    }

    private fun deleteUri(uri: String?) {
        uri ?: return
        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
    }

    /** Czy plik nadal istnieje (użytkownik mógł go usunąć menedżerem plików). */
    fun exists(task: DownloadTask): Boolean {
        val uri = task.uri ?: return false
        return runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }

    private fun save() {
        val snapshot = JSONArray(tasks.filter { !it.incognito }.map { it.toJson() }).toString()
        scope.launch {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(snapshot)
                tmp.renameTo(file)
            }
        }
    }

    private fun ui(block: () -> Unit) {
        main.post(block)
    }

    // ---------- Powiadomienia ----------

    private fun channels() {
        nm ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.download_channel_progress), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, context.getString(R.string.download_channel_done), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openDownloadsIntent(code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_OPEN_DOWNLOADS, "1")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Zbiorcze powiadomienie z postępem (to samo, które trzyma usługę pierwszoplanową). */
    internal fun progressNotification(): Notification {
        channels()
        val active = tasks.filter { it.active }
        val total = active.sumOf { it.total.coerceAtLeast(0) }
        val done = active.sumOf { it.done }
        val known = active.isNotEmpty() && active.all { it.total > 0 }
        val title = when (active.size) {
            0 -> context.getString(R.string.download_notification_idle)
            1 -> active[0].fileName
            else -> context.resources.getQuantityString(R.plurals.download_notification_many, active.size, active.size)
        }
        val speed = active.sumOf { it.speed }
        val text = buildString {
            append(if (known) formatBytes(done) + " / " + formatBytes(total) else formatBytes(done))
            if (speed > 0) append(" · ").append(formatBytes(speed)).append("/s")
        }
        val pauseAll = PendingIntent.getService(
            context, 10, Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setProgress(100, if (known && total > 0) (done * 100 / total).toInt() else 0, !known)
            .setContentIntent(openDownloadsIntent(11))
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, context.getString(R.string.download_pause_all), pauseAll).build())
            .build()
    }

    private fun notifyProgress(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotify < 1000) return
        lastNotify = now
        if (tasks.none { it.active }) return
        runCatching { nm?.notify(PROGRESS_ID, progressNotification()) }
    }

    private fun notifyFinished(task: DownloadTask) {
        channels()
        val uri = task.uri?.let { Uri.parse(it) } ?: return
        val open = PendingIntent.getActivity(
            context, task.id.hashCode(),
            Intent.createChooser(viewIntent(uri, task.mime), context.getString(R.string.download_open)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(task.fileName)
            .setContentText(context.getString(R.string.download_done_text, formatBytes(task.total.coerceAtLeast(task.done))))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, context.getString(R.string.download_open), open).build())
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, context.getString(R.string.download_show_all), openDownloadsIntent(12)).build())
            .build()
        runCatching { nm?.notify(task.id.hashCode(), n) }
    }

    private fun notifyFailed(task: DownloadTask) {
        channels()
        val n = Notification.Builder(context, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(task.fileName)
            .setContentText(context.getString(R.string.download_failed_text, task.error.orEmpty()))
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent(13))
            .build()
        runCatching { nm?.notify(task.id.hashCode(), n) }
    }

    /** Usługa przestała być potrzebna – usuwa też zbiorcze powiadomienie postępu. */
    internal fun clearProgress() {
        runCatching { nm?.cancel(PROGRESS_ID) }
    }

    companion object {
        const val PARALLEL = 3
        const val RETRIES = 4
        private val RETRY_DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000)
        const val CHANNEL_PROGRESS = "downloads"
        const val CHANNEL_DONE = "downloads_done"
        const val PROGRESS_ID = 4100
        /** Dodatek intencji otwierającej ekran pobranych (z powiadomienia). */
        const val EXTRA_OPEN_DOWNLOADS = "com.blacksingularities.openexplorer.extra.OPEN_DOWNLOADS"

        /** Własne pobieranie wymaga MediaStore (Android 10+); starsze systemy używają DownloadManagera. */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= 29

        fun viewIntent(uri: Uri, mime: String?): Intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        fun shareIntent(uri: Uri, mime: String?): Intent = Intent(Intent.ACTION_SEND)
            .setType(mime ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        /** Typ pliku z rozszerzenia, gdy serwer podał ogólny albo żaden. */
        fun fixMime(mime: String?, name: String): String? {
            val clean = mime?.substringBefore(';')?.trim()?.lowercase()
            if (!clean.isNullOrEmpty() && clean != "application/octet-stream" && clean != "binary/octet-stream") return clean
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: clean
        }

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1L shl 20).toDouble())
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}

/**
 * Usługa pierwszoplanowa na czas pobierania – system nie zabija procesu, gdy przeglądarka jest
 * w tle. Sama logika jest w [DownloadEngine]; usługa tylko pokazuje powiadomienie z postępem.
 */
class DownloadService : Service() {
    private val engine get() = (application as com.blacksingularities.openexplorer.OpenExplorerApp).downloadEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, DownloadEngine.PROGRESS_ID, engine.progressNotification(), type) }
        when (intent?.action) {
            ACTION_PAUSE_ALL -> engine.pauseAll()
            ACTION_STOP -> stopNow()
        }
        if (engine.tasks.none { it.active }) stopNow()
        return START_NOT_STICKY
    }

    /** Android 15+: limit czasu usług „dataSync” – wstrzymanie zamiast zabicia procesu. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        engine.pauseAll()
        stopNow()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        running = false
        super.onDestroy()
    }

    internal fun stopNow() {
        running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        engine.clearProgress()
        stopSelf()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.blacksingularities.openexplorer.action.PAUSE_DOWNLOADS"
        private const val ACTION_STOP = "com.blacksingularities.openexplorer.action.STOP_DOWNLOADS"
        private var running = false
        private var instance: DownloadService? = null

        fun start(context: Context) {
            if (running) return
            running = true
            runCatching { context.startForegroundService(Intent(context, DownloadService::class.java)) }
                .onFailure { running = false }
        }

        /** Zatrzymanie bez intencji – z tła system nie pozwala uruchamiać usług, nawet żeby je zamknąć. */
        fun stop(context: Context) {
            val service = instance
            if (service != null) service.stopNow()
            else if (running) {
                running = false
                (context.getSystemService(NotificationManager::class.java))?.cancel(DownloadEngine.PROGRESS_ID)
            }
        }
    }
}
