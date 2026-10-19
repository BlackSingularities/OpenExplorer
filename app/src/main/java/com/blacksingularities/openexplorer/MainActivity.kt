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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), BrowserHost {
    private lateinit var state: BrowserState
    private lateinit var updater: Updater
    private var screen by mutableStateOf("browser")
    private var menuOpen by mutableStateOf(false)
    private var darkTheme = false

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

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
        state = BrowserState(this, app.settings, app.store, app.adBlocker)
        updater = Updater(this)
        state.restoreSession()
        handleIntent(intent, initial = true)

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)) {}
        }

        val s = app.settings
        if (s.autoUpdate && System.currentTimeMillis() - s.lastUpdateCheck > 3 * 3600_000L) {
            lifecycleScope.launch {
                s.lastUpdateCheck = System.currentTimeMillis()
                val r = updater.check()
                if (r is UpdateState.Available && r.info.version == s.skippedVersion) updater.dismiss()
                else if (r !is UpdateState.Available) updater.dismiss()
            }
        }

        setContent {
            val tab = state.current
            darkTheme = isDark(s)
            OpenExplorerTheme(s, incognito = tab?.incognito == true && screen == "browser") {
                val dark = isDark(s) || tab?.incognito == true
                LaunchedEffect(dark) {
                    val c = WindowCompat.getInsetsController(window, window.decorView)
                    c.isAppearanceLightStatusBars = !dark
                    c.isAppearanceLightNavigationBars = !dark
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { sc ->
                        when (sc) {
                            "tabs" -> TabsScreen(state) { screen = "browser" }
                            "settings" -> SettingsScreen(state, updater) { screen = "browser" }
                            "bookmarks" -> BookmarksScreen(state, { screen = "browser" }) { openFromLibrary(it) }
                            "history" -> HistoryScreen(state, { screen = "browser" }) { openFromLibrary(it) }
                            "downloads" -> DownloadsScreen(state) { screen = "browser" }
                            else -> BrowserScreen(
                                state,
                                onOpenTabs = { state.captureThumbnail(); screen = "tabs" },
                                onOpenMenu = { menuOpen = true },
                            )
                        }
                    }
                    if (menuOpen) MenuSheet(state, { menuOpen = false }) { screen = it }
                    BrowserDialogs(state)
                    UpdateDialog()
                    state.fullscreen?.let { fs ->
                        AndroidView(
                            factory = { fs.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                        )
                    }
                }
                BackHandler(enabled = true) { onBack() }
                LaunchedEffect(state.fullscreen) { setFullscreenBars(state.fullscreen != null) }
            }
        }
    }

    private fun onBack() {
        when {
            menuOpen -> menuOpen = false
            screen != "browser" -> screen = "browser"
            state.back() -> Unit
            else -> moveTaskToBack(true)
        }
    }

    private fun openFromLibrary(url: String) {
        screen = "browser"
        val tab = state.current
        if (tab != null && tab.showHome) state.load(tab, url) else state.newTab(url, incognito = tab?.incognito == true)
    }

    @Composable
    private fun UpdateDialog() {
        val scope = rememberCoroutineScope()
        val s = (application as OpenExplorerApp).settings
        when (val st = updater.state) {
            is UpdateState.Available -> AlertDialog(
                onDismissRequest = { updater.dismiss() },
                icon = { Icon(Icons.Filled.SystemUpdate, null) },
                title = { Text("Nowa wersja ${st.info.version}") },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text("Zainstalowana: ${updater.currentVersion}" + if (st.info.size > 0) " · ${formatSize(st.info.size)}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (st.info.notes.isNotBlank()) Text(st.info.notes, Modifier.padding(top = 12.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { scope.launch { updater.download(st.info) } }) { Text("Pobierz i zainstaluj") } },
                dismissButton = {
                    TextButton(onClick = { s.skippedVersion = st.info.version; updater.dismiss() }) { Text("Pomiń tę wersję") }
                },
            )
            is UpdateState.Downloading -> AlertDialog(
                onDismissRequest = {},
                title = { Text("Pobieranie aktualizacji") },
                text = { LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {},
            )
            is UpdateState.Ready -> AlertDialog(
                onDismissRequest = { updater.dismiss() },
                title = { Text("Aktualizacja gotowa") },
                text = { Text("Wersja ${st.info.version} została pobrana. Zainstaluj ją teraz – karty zostaną przywrócone.") },
                confirmButton = { TextButton(onClick = { if (!updater.install(st.file)) toast("Zezwól na instalację z tego źródła i spróbuj ponownie") }) { Text("Zainstaluj") } },
                dismissButton = { TextButton(onClick = { updater.dismiss() }) { Text("Później") } },
            )
            else -> Unit
        }
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
            toast("Brak aplikacji do wyboru plików")
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

    override fun onPause() {
        super.onPause()
        state.onPause()
    }

    override fun onResume() {
        super.onResume()
        state.onResume()
    }

    override fun onDestroy() {
        state.onDestroy(isFinishing)
        super.onDestroy()
    }
}
