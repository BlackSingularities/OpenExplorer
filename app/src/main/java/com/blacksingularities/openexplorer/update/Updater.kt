package com.blacksingularities.openexplorer.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.blacksingularities.openexplorer.BuildConfig
import com.blacksingularities.openexplorer.MainActivity
import com.blacksingularities.openexplorer.OpenExplorerApp
import com.blacksingularities.openexplorer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** [sha256] – skrót pliku APK podany przez GitHub (puste, gdy wydanie go nie ma). */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val page: String,
    val sha256: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    /** [info] – wydanie, którego dotyczy błąd (pozwala ponowić pobieranie/instalację). */
    data class Failed(val message: String, val info: ReleaseInfo? = null) : UpdateState
}

/**
 * Aktualizacje z wydań GitHub (każdy merge do main publikuje nowy APK).
 *
 * Jedna instancja na proces (w [OpenExplorerApp]) – stan widzi interfejs, zadanie w tle
 * i odbiornik statusu instalacji. Cykl: sprawdzenie → pobranie (z weryfikacją) → instalacja
 * przez PackageInstaller; od Androida 12 bez okna systemu, gdy poprzednią wersję zainstalowała
 * sama aplikacja. Po instalacji aplikacja uruchamia się ponownie (albo pokazuje powiadomienie).
 */
class Updater(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Baner ukryty przez użytkownika (do czasu nowszej wersji albo ponownego uruchomienia). */
    var bannerHiddenFor by mutableStateOf("")

    val currentVersion: String = BuildConfig.VERSION_NAME

    var lastCheck: Long
        get() = prefs.getLong(LAST_CHECK, 0L)
        private set(v) = prefs.edit().putLong(LAST_CHECK, v).apply()

    private fun set(s: UpdateState) {
        if (Looper.myLooper() == Looper.getMainLooper()) state = s else main.post { state = s }
    }

    /** Sprawdza najnowsze wydanie. Przy [quiet] błąd sieci nie jest pokazywany (sprawdzanie automatyczne). */
    suspend fun check(quiet: Boolean = false): UpdateState = lock.withLock {
        val before = state
        // Trwające pobieranie / gotowa aktualizacja nie są przerywane sprawdzaniem.
        if (before is UpdateState.Downloading || before is UpdateState.Installing) return@withLock before
        if (!quiet) set(UpdateState.Checking)
        val result = try {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            lastCheck = System.currentTimeMillis()
            when {
                !isNewer(info.version, currentVersion) -> {
                    cleanup()
                    UpdateState.UpToDate
                }
                before is UpdateState.Ready && before.info.version == info.version && before.file.exists() -> before
                else -> readyFile(info)?.let { UpdateState.Ready(info, it) } ?: UpdateState.Available(info)
            }
        } catch (e: Exception) {
            if (quiet) before.takeUnless { it is UpdateState.Checking } ?: UpdateState.Idle
            else UpdateState.Failed(e.message ?: context.getString(R.string.update_network_error))
        }
        set(result)
        result
    }

    private fun fetchLatest(): ReleaseInfo {
        val conn = open("https://redzikowski.com/api/github/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                200 -> Unit
                403, 429 -> error(context.getString(R.string.update_rate_limited))
                404 -> error(context.getString(R.string.update_no_releases))
                else -> error(context.getString(R.string.update_http_error, conn.responseCode))
            }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = o.optJSONArray("assets")
            var url = ""
            var size = 0L
            var sha256 = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    sha256 = parseDigest(a.optString("digest"))
                    break
                }
            }
            if (url.isEmpty()) error(context.getString(R.string.update_no_apk))
            return ReleaseInfo(
                version = o.optString("tag_name").removePrefix("v"),
                notes = o.optString("body").substringBefore("\n---").trim(),
                apkUrl = url,
                size = size,
                page = o.optString("html_url"),
                sha256 = sha256,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "OpenExplorer/" + BuildConfig.VERSION_NAME + " (Android)")
    }

    private val dir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private fun target(info: ReleaseInfo) = File(dir, "OpenExplorer-${info.version}.apk")

    /** Wcześniej pobrany, poprawny plik tej wersji (np. pobrany w tle). */
    private fun readyFile(info: ReleaseInfo): File? = target(info).takeIf { it.exists() && verify(it, info) }

    /**
     * Plik musi mieć oczekiwany rozmiar i skrót SHA-256 (gdy GitHub go podaje), być pakietem
     * tej aplikacji i być podpisany tym samym kluczem co zainstalowana wersja.
     */
    private fun verify(file: File, info: ReleaseInfo): Boolean {
        if (info.size > 0 && file.length() != info.size) return false
        if (info.sha256.isNotEmpty() && !sha256Hex(file).equals(info.sha256, ignoreCase = true)) return false
        val archive = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, SIGNATURE_FLAGS)
        }.getOrNull() ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, SIGNATURE_FLAGS)
        }.getOrNull() ?: return false
        val remote = signers(archive)
        // Starsze Androidy nie zawsze odczytują podpis z pliku – wtedy sprawdzi go instalator systemu.
        if (remote.isEmpty() && Build.VERSION.SDK_INT < 28) return true
        return sameSigner(remote, signers(installed))
    }

    /** Skróty SHA-256 certyfikatów podpisu (z historią rotacji klucza od Androida 9). */
    private fun signers(p: android.content.pm.PackageInfo): Set<String> {
        val certs: Array<android.content.pm.Signature> = (
            if (Build.VERSION.SDK_INT >= 28) {
                val s = p.signingInfo ?: return emptySet()
                if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                p.signatures
            }
            ) ?: return emptySet()
        return certs.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cleanup() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Pobiera APK (wznawia częściowe pobranie). Zwraca plik albo null przy błędzie. */
    suspend fun download(info: ReleaseInfo): File? = lock.withLock {
        readyFile(info)?.let {
            set(UpdateState.Ready(info, it))
            return@withLock it
        }
        set(UpdateState.Downloading(info, 0f))
        try {
            val file = withContext(Dispatchers.IO) {
                dir.listFiles()?.filter { !it.name.startsWith("OpenExplorer-${info.version}") }?.forEach { it.delete() }
                val out = target(info)
                val part = File(dir, out.name + ".part")
                var done = if (part.exists()) part.length() else 0L
                var conn = open(info.apkUrl)
                if (done > 0) conn.setRequestProperty("Range", "bytes=$done-")
                // Przekierowanie GitHub → serwer plików (ręcznie, gdyby zmienił się protokół).
                var hops = 0
                while (conn.responseCode in 300..399 && hops++ < 5) {
                    val next = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    conn = open(next)
                    if (done > 0) conn.setRequestProperty("Range", "bytes=$done-")
                }
                val code = conn.responseCode
                if (code !in 200..299) error(context.getString(R.string.update_download_http, code))
                if (code == 200) done = 0 // serwer zignorował Range – od początku
                val total = info.size.takeIf { it > 0 } ?: (conn.contentLengthLong.takeIf { it > 0 }?.plus(done) ?: -1)
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(part, done > 0).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var last = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0 && done - last > 192 * 1024) {
                                last = done
                                set(UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
                conn.disconnect()
                if (!verify(part, info)) {
                    part.delete()
                    error(context.getString(R.string.update_corrupted))
                }
                out.delete()
                if (!part.renameTo(out)) error(context.getString(R.string.update_cannot_save))
                out
            }
            set(UpdateState.Ready(info, file))
            file
        } catch (e: Exception) {
            set(UpdateState.Failed(e.message ?: context.getString(R.string.update_download_failed), info))
            null
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Cicha instalacja: Android 12+ i bieżącą wersję zainstalowała sama aplikacja. */
    fun canInstallSilently(): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !canInstall()) return false
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return installer == context.packageName
    }

    fun openInstallPermission() {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Instaluje gotową aktualizację. Zwraca false, gdy brak zgody na instalację – wtedy
     * otwiera ustawienia i zapamiętuje, by dokończyć po powrocie do aplikacji.
     */
    fun install(ready: UpdateState.Ready): Boolean {
        if (!canInstall()) {
            prefs.edit().putBoolean(PENDING_INSTALL, true).apply()
            openInstallPermission()
            return false
        }
        prefs.edit().remove(PENDING_INSTALL).apply()
        if (!ready.file.exists()) {
            set(UpdateState.Available(ready.info))
            return false
        }
        set(UpdateState.Installing(ready.info))
        try {
            installSession(ready.file)
        } catch (e: Exception) {
            // Awaryjnie: systemowy instalator przez FileProvider.
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", ready.file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { set(UpdateState.Failed(context.getString(R.string.update_install_failed, e.message.orEmpty()), ready.info)) }
            main.postDelayed({ if (state is UpdateState.Installing) set(ready) }, 4000)
        }
        return true
    }

    /** Po powrocie z ustawień zgody na instalację – dokończenie przerwanej instalacji. */
    fun resumePendingInstall() {
        if (!prefs.getBoolean(PENDING_INSTALL, false) || !canInstall()) return
        (state as? UpdateState.Ready)?.let { install(it) }
    }

    private fun installSession(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
        }
        prefs.edit().putLong(RESTART_KEY, System.currentTimeMillis()).apply()
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    /** Wynik instalacji z odbiornika. */
    internal fun onInstallResult(status: Int, message: String?) {
        val info = (state as? UpdateState.Installing)?.info ?: (state as? UpdateState.Ready)?.info
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // Użytkownik anulował okno systemu – plik zostaje, można spróbować ponownie.
                val f = info?.let { target(it) }
                set(if (info != null && f != null && f.exists()) UpdateState.Ready(info, f) else UpdateState.Idle)
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                set(UpdateState.Failed(context.getString(R.string.update_conflict), info))
            PackageInstaller.STATUS_FAILURE_STORAGE -> set(UpdateState.Failed(context.getString(R.string.update_no_storage), info))
            else -> set(UpdateState.Failed(context.getString(R.string.update_install_failed, message ?: context.getString(R.string.update_status_code, status)), info))
        }
    }

    fun dismiss() {
        val s = state
        bannerHiddenFor = when (s) {
            is UpdateState.Available -> s.info.version
            is UpdateState.Ready -> s.info.version
            is UpdateState.Failed -> s.info?.version ?: ""
            else -> ""
        }
        if (s is UpdateState.Failed || s is UpdateState.UpToDate) set(UpdateState.Idle)
    }

    // ---------------- Powiadomienia ----------------

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(nm: NotificationManager) {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.settings_page_updates), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Powiadomienie z intencją (otwarcie aplikacji albo okno instalatora). */
    fun notify(title: String, text: String, intent: Intent, id: Int = NOTIFICATION_ID) {
        if (!canNotify()) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        channel(nm)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(id, n) }
    }

    fun notifyReady(info: ReleaseInfo) = notify(
        context.getString(R.string.update_ready_title, info.version),
        context.getString(R.string.update_ready_text),
        Intent(context, MainActivity::class.java).setAction(ACTION_INSTALL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun clearNotification() {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }

    /** Czy sieć jest niemierzona (Wi-Fi) – dla opcji „pobieraj tylko przez Wi-Fi”. */
    fun unmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        const val ACTION_STATUS = "com.blacksingularities.openexplorer.UPDATE_STATUS"
        const val ACTION_INSTALL = "com.blacksingularities.openexplorer.INSTALL_UPDATE"
        const val PREFS = "openexplorer_update"
        const val RESTART_KEY = "restart"
        private const val LAST_CHECK = "last_check"
        private const val PENDING_INSTALL = "pending_install"
        private const val CHANNEL = "updates"
        const val NOTIFICATION_ID = 4711

        /** Aplikacja na pierwszym planie (okno instalatora można pokazać od razu). */
        @Volatile
        var foreground = false

        fun get(context: Context): Updater = (context.applicationContext as OpenExplorerApp).updater

        /** Po aktualizacji: ponowne otwarcie aplikacji albo powiadomienie, gdy system blokuje start z tła. */
        fun relaunch(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val requested = prefs.getLong(RESTART_KEY, 0L)
            if (requested == 0L || System.currentTimeMillis() - requested > 60 * 60_000L) return
            prefs.edit().remove(RESTART_KEY).apply()
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
            runCatching { context.startActivity(launch) }
            get(context).notify(
                context.getString(R.string.update_done_title, BuildConfig.VERSION_NAME),
                context.getString(R.string.update_done_text),
                launch,
            )
        }

        @Suppress("DEPRECATION")
        private val SIGNATURE_FLAGS =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

        /** „sha256:ABC…” z API GitHub → „abc…”; inne algorytmy i błędny format → pusty tekst. */
        fun parseDigest(digest: String?): String {
            val hex = digest?.trim()?.takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substring(7) ?: return ""
            return hex.lowercase().takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } } ?: ""
        }

        fun sha256Hex(bytes: ByteArray): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Wspólny certyfikat podpisu – pusty zbiór po którejkolwiek stronie oznacza brak zgodności. */
        fun sameSigner(remote: Set<String>, local: Set<String>): Boolean =
            remote.isNotEmpty() && local.isNotEmpty() && remote.any { it in local }

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

/** Status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno (albo powiadomienie w tle). */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = Updater.get(context)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val started = Updater.foreground && runCatching { context.startActivity(confirm) }.isSuccess
                if (!started) updater.notify(context.getString(R.string.update_confirm_title), context.getString(R.string.update_confirm_text), confirm)
            }
        }
        updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}

/** Nowa wersja zainstalowana – system wysyła to do zaktualizowanej aplikacji. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Updater.relaunch(context)
            UpdateWorker.schedule(context)
        }
    }
}
