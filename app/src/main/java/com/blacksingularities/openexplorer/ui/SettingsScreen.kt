package com.blacksingularities.openexplorer.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blacksingularities.openexplorer.BuildConfig
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.data.SEARCH_ENGINES
import com.blacksingularities.openexplorer.update.UpdateState
import com.blacksingularities.openexplorer.update.Updater
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private class Choice(val title: String, val options: List<Pair<String, String>>, val selected: String, val onPick: (String) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: BrowserState, updater: Updater, onBack: () -> Unit) {
    val s = state.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<Choice?>(null) }
    var clearDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    fun ask(label: String, initial: String, onSave: (String) -> Unit) {
        textInput = Triple(label, initial, onSave)
    }
    var resetConfirm by remember { mutableStateOf(false) }
    var filtersBusy by remember { mutableStateOf(false) }

    // Zmiany wpływające na WebView stosowane od razu we wszystkich kartach.
    fun web(block: () -> Unit) {
        block()
        state.applySettingsToAll()
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val json = JSONObject().put("app", "OpenExplorer").put("version", 1)
                .put("settings", s.export()).put("data", state.store.export())
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
            state.toast("Zapisano kopię zapasową")
        }.onFailure { state.toast("Błąd zapisu: ${it.message}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Pusty plik")
            val o = JSONObject(text)
            o.optJSONObject("settings")?.let { s.import(it) }
            o.optJSONObject("data")?.let { state.store.import(it) }
            state.applySettingsToAll()
            state.toast("Przywrócono kopię zapasową")
        }.onFailure { state.toast("Nieprawidłowy plik kopii: ${it.message}") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Surface(
                    onClick = { setDefaultBrowser(ctx) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Ustaw jako domyślną przeglądarkę", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Linki z innych aplikacji otworzą się w OpenExplorer.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            section("Wygląd")
            item {
                Clickable("Motyw", mapOf("system" to "Systemowy", "light" to "Jasny", "dark" to "Ciemny")[s.theme] ?: "") {
                    choice = Choice("Motyw", listOf("system" to "Systemowy", "light" to "Jasny", "dark" to "Ciemny"), s.theme) { s.theme = it }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) item { Toggle("Kolory Material You", "Barwy z tapety telefonu", s.dynamicColor) { s.dynamicColor = it } }
            if (!s.dynamicColor || Build.VERSION.SDK_INT < 31) item { AccentPicker(s.accent) { s.accent = it } }
            item { Toggle("Czarny motyw AMOLED", "Głęboka czerń w trybie ciemnym", s.amoled) { s.amoled = it } }
            item { Toggle("Pasek adresu na dole", "Wygodniejszy przy obsłudze jedną ręką", s.addressBarBottom) { s.addressBarBottom = it } }
            item { Toggle("Ukrywaj pasek przy przewijaniu", null, s.hideBarsOnScroll) { s.hideBarsOnScroll = it } }
            item { Toggle("Licznik kart na pasku", null, s.showTabCounter) { s.showTabCounter = it } }
            item {
                val opts = listOf("auto" to "Zgodnie z motywem", "force" to "Zawsze ciemne (filtr)", "off" to "Wyłączone")
                Clickable("Ciemne strony", opts.first { it.first == s.webDarkMode }.second) {
                    choice = Choice("Ciemne strony", opts, s.webDarkMode) { v -> web { s.webDarkMode = v } }
                }
            }

            section("Strona startowa")
            item { SliderRow("Kolumny kafelków", s.homeColumns, 2f..6f, 3) { s.homeColumns = it } }
            item { SliderRow("Wiersze kafelków", s.homeRows, 1f..8f, 6) { s.homeRows = it } }
            item {
                val opts = listOf("rounded" to "Zaokrąglone", "circle" to "Okrągłe", "square" to "Kwadratowe")
                Clickable("Kształt kafelków", opts.first { it.first == s.tileShape }.second) {
                    choice = Choice("Kształt kafelków", opts, s.tileShape) { s.tileShape = it }
                }
            }
            item { SliderRow("Wielkość logo (%)", s.tileIconScale, 30f..100f, 13) { s.tileIconScale = it } }
            item { Toggle("Tło kafelków", null, s.tileBackground) { s.tileBackground = it } }
            item { Toggle("Podpisy kafelków", null, s.tileLabels) { s.tileLabels = it } }
            item { Toggle("Pole wyszukiwania", null, s.homeSearchBar) { s.homeSearchBar = it } }
            item { Toggle("Zegar i data", null, s.homeClock) { s.homeClock = it } }
            item { Toggle("Nazwa przeglądarki", null, s.homeLogo) { s.homeLogo = it } }
            item { Toggle("Ostatnio odwiedzane", null, s.homeRecent) { s.homeRecent = it } }
            item {
                val opts = listOf("aurora" to "Zorza", "gradient" to "Gradient akcentu", "solid" to "Jednolity kolor", "none" to "Brak")
                Clickable("Tło strony startowej", opts.first { it.first == s.homeWallpaper }.second) {
                    choice = Choice("Tło", opts, s.homeWallpaper) { s.homeWallpaper = it }
                }
            }
            if (s.homeWallpaper == "solid") item { AccentPicker(s.homeWallpaperColor, TILE_COLORS.filter { it != 0L }) { s.homeWallpaperColor = it } }
            item {
                Clickable("Własna strona domowa", s.homepageUrl.ifBlank { "Kafelki (domyślnie)" }) {
                    ask("Adres strony domowej (puste = kafelki)", s.homepageUrl) { s.homepageUrl = it.trim() }
                }
            }
            item { Clickable("Przywróć domyślne kafelki", null) { resetConfirm = true } }

            section("Wyszukiwanie")
            item {
                Clickable("Wyszukiwarka", s.engine.name) {
                    choice = Choice("Wyszukiwarka", SEARCH_ENGINES.map { it.id to it.name }, s.searchEngine) {
                        s.searchEngine = it
                        if (it == "custom") ask("Adres wyszukiwania z %s", s.customSearchUrl) { v -> s.customSearchUrl = v.trim() }
                    }
                }
            }
            item { Toggle("Podpowiedzi wyszukiwania", "Wysyła wpisywany tekst do wyszukiwarki", s.searchSuggestions) { s.searchSuggestions = it } }

            section("Prywatność i bezpieczeństwo")
            item { Toggle("Blokowanie reklam i śledzenia", "Domen na liście: ${state.adBlocker.size}", s.adBlock) { s.adBlock = it } }
            item {
                val date = if (s.filtersUpdated > 0) DateFormat.getDateInstance().format(Date(s.filtersUpdated)) else "nigdy"
                Clickable(if (filtersBusy) "Pobieranie listy filtrów…" else "Aktualizuj listę filtrów", "Pełna lista StevenBlack · ostatnio: $date") {
                    if (filtersBusy) return@Clickable
                    filtersBusy = true
                    scope.launch {
                        runCatching { state.adBlocker.update() }
                            .onSuccess { s.filtersUpdated = System.currentTimeMillis(); state.toast("Lista filtrów: $it domen") }
                            .onFailure { state.toast("Nie udało się pobrać: ${it.message}") }
                        filtersBusy = false
                    }
                }
            }
            item { Toggle("Tylko HTTPS", "Automatycznie przechodzi na szyfrowane połączenie", s.httpsOnly) { s.httpsOnly = it } }
            item { Toggle("Bezpieczne przeglądanie", "Ostrzega przed groźnymi stronami", s.safeBrowsing) { v -> web { s.safeBrowsing = v } } }
            item { Toggle("Ciasteczka stron trzecich", null, s.thirdPartyCookies) { v -> web { s.thirdPartyCookies = v } } }
            item { Toggle("Nie śledź (DNT i GPC)", null, s.doNotTrack) { s.doNotTrack = it } }
            item { Toggle("Blokuj wyskakujące okna", null, s.blockPopups) { v -> web { s.blockPopups = v } } }
            item { Toggle("Zapisuj historię", null, s.saveHistory) { s.saveHistory = it } }
            item { Toggle("Czyść dane przy wyjściu", "Gdy aplikacja zostanie zamknięta", s.clearOnExit) { s.clearOnExit = it } }
            item { Clickable("Wyczyść dane przeglądania", "Pamięć podręczna, ciasteczka, historia") { clearDialog = true } }

            section("Treści stron")
            item { Toggle("JavaScript", null, s.javascript) { v -> web { s.javascript = v } } }
            item { Toggle("Wczytuj obrazy", null, s.loadImages) { v -> web { s.loadImages = v } } }
            item { Toggle("Blokuj autoodtwarzanie", "Multimedia startują dopiero po dotknięciu", s.blockAutoplay) { v -> web { s.blockAutoplay = v } } }
            item { Toggle("Wersja desktop domyślnie", null, s.desktopDefault) { s.desktopDefault = it } }
            item { Toggle("Wymuś powiększanie", "Także na stronach, które je blokują", s.forceZoom) { s.forceZoom = it } }
            item { Toggle("Przeciągnij, aby odświeżyć", null, s.pullToRefresh) { s.pullToRefresh = it } }
            item { SliderRow("Rozmiar tekstu (%)", s.textZoom, 50f..200f, 14) { v -> web { s.textZoom = v } } }
            item {
                Clickable("Identyfikator przeglądarki (User-Agent)", s.customUserAgent.ifBlank { "Domyślny" }) {
                    ask("Własny User-Agent (puste = domyślny)", s.customUserAgent) { v -> web { s.customUserAgent = v.trim() } }
                }
            }

            section("Karty i pobieranie")
            item { Toggle("Przywracaj karty", "Po ponownym uruchomieniu", s.restoreTabs) { s.restoreTabs = it } }
            item { Toggle("Otwieraj linki w tle", "Z kafelków i menu kontekstowego", s.linksInBackground) { s.linksInBackground = it } }
            item { Toggle("Potwierdzaj zamykanie wszystkich kart", null, s.confirmCloseAll) { s.confirmCloseAll = it } }
            item { Toggle("Pytaj przed pobraniem", "Pozwala zmienić nazwę pliku", s.askDownload) { s.askDownload = it } }

            section("Kopia zapasowa")
            item { Clickable("Eksportuj ustawienia, kafelki i zakładki", null) { exportLauncher.launch("OpenExplorer-kopia.json") } }
            item { Clickable("Importuj z pliku", null) { importLauncher.launch(arrayOf("application/json", "*/*")) } }

            section("Aktualizacje")
            item { Toggle("Automatyczne sprawdzanie", "Nowe wersje z GitHub przy uruchomieniu", s.autoUpdate) { s.autoUpdate = it } }
            item {
                val st = updater.state
                val sub = when (st) {
                    UpdateState.Checking -> "Sprawdzanie…"
                    UpdateState.UpToDate -> "Masz najnowszą wersję"
                    is UpdateState.Available -> "Dostępna wersja ${st.info.version}"
                    is UpdateState.Downloading -> "Pobieranie ${(st.progress * 100).toInt()}%"
                    is UpdateState.Ready -> "Gotowa do instalacji"
                    is UpdateState.Failed -> "Błąd: ${st.message}"
                    UpdateState.Idle -> "Wersja ${BuildConfig.VERSION_NAME}"
                }
                Clickable("Sprawdź aktualizacje", sub) {
                    scope.launch {
                        s.lastUpdateCheck = System.currentTimeMillis()
                        s.skippedVersion = ""
                        updater.check()
                    }
                }
            }

            section("O aplikacji")
            item { Clickable("OpenExplorer ${BuildConfig.VERSION_NAME}", "Kod źródłowy i wydania na GitHub") { state.newTab("https://github.com/${BuildConfig.UPDATE_REPO}"); onBack() } }
            item { Clickable("Przywróć ustawienia domyślne", null) { s.resetAll(); state.applySettingsToAll(); state.toast("Przywrócono ustawienia") } }
            item { Box(Modifier.padding(40.dp)) }
        }
    }

    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(c.title) },
            text = {
                Column {
                    c.options.forEach { (k, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { choice = null; c.onPick(k) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = k == c.selected, onClick = { choice = null; c.onPick(k) })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choice = null }) { Text("Zamknij") } },
        )
    }

    textInput?.let { (label, initial, onSave) ->
        var v by remember(label) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { textInput = null },
            title = { Text(label) },
            text = { OutlinedTextField(v, { v = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onSave(v); textInput = null }) { Text("Zapisz") } },
            dismissButton = { TextButton(onClick = { textInput = null }) { Text("Anuluj") } },
        )
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("Przywrócić domyślne kafelki?") },
            text = { Text("Twoje kafelki zostaną zastąpione zestawem domyślnym.") },
            confirmButton = { TextButton(onClick = { state.store.resetTiles(); resetConfirm = false }) { Text("Przywróć") } },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Anuluj") } },
        )
    }

    if (clearDialog) {
        var cache by remember { mutableStateOf(true) }
        var cookies by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(true) }
        var storage by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Wyczyść dane") },
            text = {
                Column {
                    CheckRow("Pamięć podręczna", cache) { cache = it }
                    CheckRow("Historia", history) { history = it }
                    CheckRow("Ciasteczka (wylogowuje)", cookies) { cookies = it }
                    CheckRow("Dane stron (pamięć lokalna)", storage) { storage = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.clearData(cache, cookies, history, storage)
                    clearDialog = false
                    state.toast("Wyczyszczono dane")
                }) { Text("Wyczyść") }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Anuluj") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) {
    item {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp))
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Clickable(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value, onChange)
        Text(label)
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${local.toInt()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(local, { local = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(Math.round(local)) })
    }
}

@Composable
private fun AccentPicker(selected: Long, colors: List<Long> = ACCENTS, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.take(8).forEach { c ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) { if (c == selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

private fun setDefaultBrowser(ctx: Context) {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                (ctx as? android.app.Activity)?.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 42)
                return
            }
        }
        ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (_: Exception) {
        ctx.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}
