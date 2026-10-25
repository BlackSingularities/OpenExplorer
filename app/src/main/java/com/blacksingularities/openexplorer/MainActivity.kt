package com.blacksingularities.openexplorer

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.browser.BrowserHost
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.ui.BookmarksScreen
import com.blacksingularities.openexplorer.ui.BrowserDialogs
import com.blacksingularities.openexplorer.ui.BrowserScreen
import com.blacksingularities.openexplorer.ui.DownloadsScreen
import com.blacksingularities.openexplorer.ui.HistoryScreen
import com.blacksingularities.openexplorer.ui.MenuSheet
import com.blacksingularities.openexplorer.ui.OpenExplorerTheme
import com.blacksingularities.openexplorer.ui.SettingsScreen
import com.blacksingularities.openexplorer.ui.TabsScreen
import com.blacksingularities.openexplorer.ui.formatSize
import com.blacksingularities.openexplorer.ui.isDark
import com.blacksingularities.openexplorer.update.UpdateState
import com.blacksingularities.openexplorer.update.Updater
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import com.blacksingularities.openexplorer.ui.CompactDims
import com.blacksingularities.openexplorer.ui.LocalDims
import com.blacksingularities.openexplorer.ui.LocalHaptics
import com.blacksingularities.openexplorer.ui.NormalDims
import com.blacksingularities.openexplorer.ui.OfflineScreen
import com.blacksingularities.openexplorer.ui.PasswordsScreen
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import com.blacksingularities.openexplorer.ui.SnackHost
import com.blacksingularities.openexplorer.ui.UpdateBanner
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), BrowserHost {
    private lateinit var state: BrowserState
    private lateinit var updater: Updater
    private var screen by mutableStateOf("browser")
    private var menuOpen by mutableStateOf(false)
    private var editTiles by mutableStateOf(false)
    private var darkTheme = false

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var authCallback: ((Boolean) -> Unit)? = null
    /** Kiedy aplikacja zeszła z ekranu (hasła blokują się po dłuższej nieobecności). */
    private var stoppedAt = 0L

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = authCallback
        authCallback = null
        cb?.invoke(result.resultCode == Activity.RESULT_OK)
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) ?: run {
            // Wiele plików przychodzi w ClipData.
            val clip = result.data?.clipData
            if (result.resultCode == Activity.RESULT_OK && clip != null) Array(clip.itemCount) { clip.getItemAt(it).uri } else null
        })
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val cb = permissionCallback
        permissionCallback = null
        cb?.invoke(res.values.any { it })
    }

    override val activity: Activity get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as OpenExplorerApp
        state = BrowserState(this, app.settings, app.store, app.adBlocker, app.passwords)
        updater = app.updater
        state.restoreSession()
        handleIntent(intent, initial = true)

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)) {}
        }

        val s = app.settings

        setContent {
            val tab = state.current
            darkTheme = isDark(s)
            OpenExplorerTheme(s, incognito = tab?.incognito == true && screen == "browser") {
                val dark = isDark(s) || (tab?.incognito == true && screen == "browser")
                LaunchedEffect(dark) {
                    val c = WindowCompat.getInsetsController(window, window.decorView)
                    c.isAppearanceLightStatusBars = !dark
                    c.isAppearanceLightNavigationBars = !dark
                }
                val dims = if (s.density == "compact") CompactDims else NormalDims
                // Globalna obsługa „Wstecz” – rejestrowana jako pierwsza, by ekrany (np. podstrony ustawień) mogły ją przesłonić.
                BackHandler(enabled = true) { onBack() }
                // Kolor treści zgodny z motywem (bez tego teksty i ikony poza Surface są czarne także w ciemnym motywie).
                CompositionLocalProvider(
                    LocalDims provides dims,
                    LocalHaptics provides s.haptics,
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { sc ->
                            val back = { screen = "browser" }
                            when (sc) {
                                "tabs" -> TabsScreen(state, back)
                                "settings" -> SettingsScreen(
                                    state, updater, back,
                                    onEditTiles = { screen = "browser"; editTiles = true; state.current?.let { state.goHome(it) } },
                                    onOpenPasswords = { screen = "passwords" },
                                )
                                "passwords" -> PasswordsScreen(state, back, onOpen = { openFromLibrary(it) }) { title, cb -> authenticate(title, cb) }
                                "bookmarks" -> BookmarksScreen(state, back) { openFromLibrary(it) }
                                "history" -> HistoryScreen(state, back) { openFromLibrary(it) }
                                "downloads" -> DownloadsScreen(state, back)
                                "offline" -> OfflineScreen(state, back) { openFromLibrary(it) }
                                else -> BrowserScreen(
                                    state,
                                    editTiles = editTiles,
                                    onEditTiles = { editTiles = it },
                                    onOpenTabs = { state.captureThumbnail(); screen = "tabs" },
                                    onOpenMenu = { menuOpen = true },
                                )
                            }
                        }
                        if (menuOpen) {
                            MenuSheet(state, { menuOpen = false }, { if (it == "exit") exitBrowser() else screen = it }) { editTiles = true }
                        }
                        state.source.view?.let { com.blacksingularities.openexplorer.ui.SourceScreen(state, it) }
                        BrowserDialogs(state)
                        UpdateBanner(
                            updater,
                            onUpdate = { startUpdate() },
                            onInstall = { (updater.state as? UpdateState.Ready)?.let { installUpdate(it) } },
                            onRetry = { retryUpdate() },
                            onHide = { updater.dismiss() },
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                        )
                        SnackHost(state, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp))
                        state.fullscreen?.let { fs ->
                            AndroidView(
                                factory = { fs.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                            )
                        }
                    }
                }
                LaunchedEffect(state.fullscreen) { setFullscreenBars(state.fullscreen != null) }
                // Ekran haseł nie trafia na zrzuty ekranu ani do podglądu ostatnich aplikacji.
                LaunchedEffect(screen) {
                    if (screen == "passwords") window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    private fun onBack() {
        when {
            menuOpen -> menuOpen = false
            screen != "browser" -> screen = "browser"
            editTiles -> editTiles = false
            state.back() -> Unit
            else -> moveTaskToBack(true)
        }
    }

    /** „Wyjdź” z menu: zamyka aplikację (i czyści dane, jeśli tak ustawiono). */
    private fun exitBrowser() {
        menuOpen = false
        finishAndRemoveTask()
    }

    private fun openFromLibrary(url: String) {
        screen = "browser"
        val tab = state.current
        if (tab != null && tab.showHome) state.load(tab, url) else state.newTab(url, incognito = tab?.incognito == true)
    }

    /** Jedno dotknięcie: pobranie APK i od razu instalacja (jak w OpenPDF). */
    private fun startUpdate() {
        val info = (updater.state as? UpdateState.Available)?.info ?: return
        lifecycleScope.launch {
            updater.download(info)
            (updater.state as? UpdateState.Ready)?.let { installUpdate(it) }
        }
    }

    private fun retryUpdate() {
        val info = (updater.state as? UpdateState.Failed)?.info
        lifecycleScope.launch {
            if (info != null) {
                updater.download(info)
                (updater.state as? UpdateState.Ready)?.let { installUpdate(it) }
            } else updater.check()
        }
    }

    private fun installUpdate(ready: UpdateState.Ready) {
        if (!updater.install(ready)) toast(getString(R.string.update_allow_source))
    }

    /**
     * Automatyczne sprawdzenie przy każdym powrocie do aplikacji (najwyżej co godzinę) i –
     * gdy włączone – pobranie w tle, żeby aktualizacja była gotowa do instalacji jednym dotknięciem.
     */
    private fun autoCheck(force: Boolean = false) {
        val s = (application as OpenExplorerApp).settings
        if (!s.autoUpdate && !force) return
        if (!force && System.currentTimeMillis() - updater.lastCheck < 60 * 60_000L) {
            autoDownload()
            return
        }
        lifecycleScope.launch {
            updater.check(quiet = !force)
            autoDownload()
        }
    }

    private fun autoDownload() {
        val s = (application as OpenExplorerApp).settings
        val info = (updater.state as? UpdateState.Available)?.info ?: return
        if (!s.updateAutoDownload || (s.updateWifiOnly && !updater.unmetered())) return
        lifecycleScope.launch { updater.download(info) }
    }

    private fun setFullscreenBars(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, initial = false)
    }

    private fun handleIntent(intent: Intent?, initial: Boolean) {
        intent ?: return
        if (intent.action == Updater.ACTION_INSTALL) {
            // Z powiadomienia: instalacja gotowej aktualizacji albo pobranie i instalacja.
            updater.clearNotification()
            when (val st = updater.state) {
                is UpdateState.Ready -> installUpdate(st)
                is UpdateState.Available -> startUpdate()
                else -> lifecycleScope.launch {
                    updater.check()
                    when (val r = updater.state) {
                        is UpdateState.Ready -> installUpdate(r)
                        is UpdateState.Available -> startUpdate()
                        else -> Unit
                    }
                }
            }
            return
        }
        val text: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra("query")
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (text.isNullOrBlank()) return
        screen = "browser"
        // Adres z udostępnionego tekstu (np. „Zobacz https://…”).
        val url = Regex("https?://\\S+").find(text)?.value ?: text
        val cur = state.current
        if (cur != null && cur.showHome && cur.url.isEmpty() && !cur.incognito) state.navigate(url, cur)
        else state.newTab(state.resolveInput(url))
    }

    // ---- BrowserHost ----

    override fun pickFiles(intent: Intent, callback: ValueCallback<Array<Uri>>) {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        try {
            filePicker.launch(intent)
        } catch (_: Exception) {
            fileCallback = null
            callback.onReceiveValue(null)
            toast(getString(R.string.main_no_file_picker))
        }
    }

    override fun requestPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            onResult(true)
            return
        }
        permissionCallback = onResult
        permissionLauncher.launch(permissions)
    }

    override fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun isDarkTheme(): Boolean = darkTheme

    /**
     * Potwierdzenie tożsamości systemowym oknem: odcisk palca / twarz albo PIN, wzór lub hasło
     * blokady ekranu. Bez ustawionej blokady ekranu nie ma czego sprawdzać – wtedy przepuszcza.
     */
    override fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            onResult(true)
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
        }
        try {
            when {
                Build.VERSION.SDK_INT >= 30 -> BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setSubtitle("OpenExplorer")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                    .authenticate(CancellationSignal(), mainExecutor, callback)
                Build.VERSION.SDK_INT >= 29 -> BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setSubtitle("OpenExplorer")
                    .setDeviceCredentialAllowed(true)
                    .build()
                    .authenticate(CancellationSignal(), mainExecutor, callback)
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = km.createConfirmDeviceCredentialIntent(title, "OpenExplorer")
                    if (intent == null) onResult(true) else {
                        authCallback = onResult
                        authLauncher.launch(intent)
                    }
                }
            }
        } catch (_: Exception) {
            onResult(false)
            toast(getString(R.string.main_auth_failed))
        }
    }

    override fun onPause() {
        super.onPause()
        Updater.foreground = false
        state.onPause()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Hasła zostają odblokowane przy krótkich wyjściach (np. wybór pliku), ale nie po dłuższej nieobecności.
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 30_000L) state.passwordsUnlocked = false
    }

    override fun onResume() {
        super.onResume()
        Updater.foreground = true
        state.onResume()
        updater.clearNotification()
        updater.resumePendingInstall()
        autoCheck()
    }

    override fun onDestroy() {
        state.onDestroy(isFinishing)
        super.onDestroy()
    }
}
