package com.blacksingularities.openexplorer.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blacksingularities.openexplorer.BuildConfig
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.data.SEARCH_ENGINES
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.SiteRule
import com.blacksingularities.openexplorer.update.UpdateState
import com.blacksingularities.openexplorer.update.Updater
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Gotowe układy strony startowej. */
private fun applyPreset(s: Settings, preset: String) {
    when (preset) {
        "logos" -> {
            s.tileBackground = false; s.tileLabels = false; s.tileClip = true; s.tileCorner = 22
            s.tileIconScale = 72; s.tileSpacing = 18; s.homeColumns = 4; s.homeRows = 3; s.tileGrayscale = false
            s.homeSearchStyle = "line"; s.homeClock = false; s.homeLogo = false; s.homeWallpaper = "none"; s.tileAddButton = false
        }
        "classic" -> {
            s.tileBackground = true; s.tileLabels = true; s.tileClip = true; s.tileCorner = 24; s.tileGrayscale = false
            s.tileIconScale = 55; s.tileSpacing = 12; s.homeColumns = 3; s.homeRows = 3
            s.homeSearchStyle = "pill"; s.homeClock = false; s.homeLogo = true; s.tileAddButton = true
        }
        "dense" -> {
            s.tileBackground = false; s.tileLabels = true; s.tileLabelSize = 10; s.tileClip = true; s.tileCorner = 50
            s.tileIconScale = 64; s.tileSpacing = 8; s.homeColumns = 5; s.homeRows = 5; s.tileGrayscale = false
            s.homeSearchStyle = "pill"; s.homeClock = false; s.homeLogo = false
        }
        "zen" -> {
            s.tileBackground = false; s.tileLabels = false; s.tileClip = false; s.tileGrayscale = true
            s.tileIconScale = 50; s.tileSpacing = 24; s.homeColumns = 3; s.homeRows = 2
            s.homeSearchStyle = "none"; s.homeClock = true; s.homeLogo = false; s.homeWallpaper = "none"; s.tileAddButton = false
        }
    }
}

@Composable
fun SettingsScreen(state: BrowserState, updater: Updater, onBack: () -> Unit, onEditTiles: () -> Unit) {
    val s = state.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clearDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    var filtersBusy by remember { mutableStateOf(false) }

    fun ask(label: String, initial: String, onSave: (String) -> Unit) {
        textInput = Triple(label, initial, onSave)
    }

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
            state.snack("Zapisano kopię zapasową")
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
            state.snack("Przywrócono kopię zapasową")
        }.onFailure { state.toast("Nieprawidłowy plik kopii: ${it.message}") }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && copyImage(ctx, uri, wallpaperFile(ctx))) {
            s.wallpaperVersion = System.currentTimeMillis()
            s.homeWallpaper = "image"
        }
    }
    fun pickWallpaper() = wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val pages = listOf(
        SettingsPage("home", "Strona startowa", "Kafelki, loga, siatka, tło", Icons.Rounded.Dashboard),
        SettingsPage("look", "Wygląd", "Motyw, kolory, paski, gesty", Icons.Rounded.Palette),
        SettingsPage("search", "Wyszukiwanie", s.engine.name + " · skróty !yt, !w…", Icons.Rounded.Search),
        SettingsPage("privacy", "Prywatność i bezpieczeństwo", "Reklamy, HTTPS, ciasteczka, wyjątki stron", Icons.Rounded.Shield),
        SettingsPage("content", "Treści stron", "JavaScript, tekst, czytanie na głos", Icons.Rounded.Language),
        SettingsPage("tabs", "Karty i pobieranie", "Sesja, linki w tle, pobieranie", Icons.Rounded.Tab),
        SettingsPage("backup", "Kopia zapasowa", "Eksport i import danych", Icons.Rounded.Backup),
        SettingsPage("updates", "Aktualizacje", "Wersja " + BuildConfig.VERSION_NAME, Icons.Rounded.SystemUpdate),
        SettingsPage("about", "O aplikacji", "OpenExplorer " + BuildConfig.VERSION_NAME, Icons.Rounded.Info),
    )
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val current = pages.firstOrNull { it.id == page }

    Panel(
        title = current?.title ?: "Ustawienia",
        onClose = onBack,
        onBack = if (page != null) ({ page = null }) else null,
        scrollable = page != "home",
    ) {
        when (page) {
            null -> {
                Box(
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { setDefaultBrowser(ctx) }.padding(16.dp),
                ) {
                    Column {
                        Text("Ustaw jako domyślną przeglądarkę", style = MaterialTheme.typography.titleMedium)
                        Text("Linki z innych aplikacji otworzą się w OpenExplorer", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                pages.forEach { pg -> CategoryRow(pg) { page = pg.id } }
                Box(Modifier.height(24.dp))
            }
            "home" -> {
                Box(
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                ) {
                    HomePage(
                        s, state.store, incognito = false, editMode = false, onEditMode = {}, onOpen = {}, onOpenNewTab = { _, _ -> },
                        onSearchClick = {}, modifier = Modifier.fillMaxSize(),
                    )
                    // Podgląd tylko do oglądania – przechwytuje dotyk bez efektu.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    ChipsRow(
                        "Gotowe układy",
                        listOf("logos" to "Same loga", "classic" to "Kafelki", "dense" to "Gęsta siatka", "zen" to "Zen"),
                        "",
                    ) { applyPreset(s, it) }
                    ClickRow("Uporządkuj kafelki", "Przeciąganie, edycja i usuwanie na stronie startowej") { onEditTiles() }

                    SliderRow("Kolumny", s.homeColumns, 1..10) { s.homeColumns = it }
                    if (!s.homeAllTiles) SliderRow("Wiersze", s.homeRows, 1..12) { s.homeRows = it }
                    ToggleRow("Wszystkie kafelki", "Siatka bez limitu wierszy", s.homeAllTiles) { s.homeAllTiles = it }
                    SliderRow("Wielkość logo", s.tileIconScale, 25..100, "%") { s.tileIconScale = it }
                    SliderRow("Zaokrąglenie", s.tileCorner, 0..50, "%") { s.tileCorner = it }
                    SliderRow("Odstępy", s.tileSpacing, 0..40, " dp") { s.tileSpacing = it }
                    SliderRow("Szerokość siatki", s.homeWidth, 240..1000, " dp", step = 20) { s.homeWidth = it }
                    ChipsRow("Logo", listOf("hd" to "Duże logo", "small" to "Ikona", "letter" to "Litery"), s.logoSource) { s.logoSource = it }
                    ToggleRow("Tło kafelków", "Wyłączone = same loga", s.tileBackground) { s.tileBackground = it }
                    ToggleRow("Przycinaj logo do kształtu", null, s.tileClip) { s.tileClip = it }
                    ToggleRow("Podpisy", null, s.tileLabels) { s.tileLabels = it }
                    if (s.tileLabels) SliderRow("Rozmiar podpisów", s.tileLabelSize, 8..18, " sp") { s.tileLabelSize = it }
                    ToggleRow("Loga w odcieniach szarości", null, s.tileGrayscale) { s.tileGrayscale = it }
                    ToggleRow("Przycisk dodawania kafelka", null, s.tileAddButton) { s.tileAddButton = it }
                    ChipsRow("Położenie", listOf("center" to "Na środku", "top" to "U góry"), s.homeAlign) { s.homeAlign = it }
                    ChipsRow("Pole wyszukiwania", listOf("pill" to "Pigułka", "line" to "Minimalne", "none" to "Brak"), s.homeSearchStyle) { s.homeSearchStyle = it }
                    ToggleRow("Zegar i data", null, s.homeClock) { s.homeClock = it }
                    if (s.homeClock) SliderRow("Rozmiar zegara", s.homeClockSize, 28..96, " sp", step = 4) { s.homeClockSize = it }
                    ToggleRow("Nazwa przeglądarki", null, s.homeLogo) { s.homeLogo = it }
                    ToggleRow("Ostatnio odwiedzane", null, s.homeRecent) { s.homeRecent = it }
                    if (s.homeRecent) SliderRow("Liczba ostatnich", s.homeRecentCount, 1..10) { s.homeRecentCount = it }
                    ChipsRow(
                        "Tło",
                        listOf("none" to "Brak", "aurora" to "Zorza", "gradient" to "Gradient", "solid" to "Kolor", "image" to "Obraz"),
                        s.homeWallpaper,
                    ) {
                        if (it == "image" && !wallpaperFile(ctx).exists()) pickWallpaper() else s.homeWallpaper = it
                    }
                    if (s.homeWallpaper == "solid") ColorDots(TILE_COLORS.filter { it != 0L }, s.homeWallpaperColor) { s.homeWallpaperColor = it }
                    if (s.homeWallpaper == "image") {
                        ClickRow("Wybierz obraz z galerii") { pickWallpaper() }
                        SliderRow("Przyciemnienie", s.homeWallpaperDim, 0..80, "%") { s.homeWallpaperDim = it }
                        if (Build.VERSION.SDK_INT >= 31) SliderRow("Rozmycie", s.homeWallpaperBlur, 0..30, " dp") { s.homeWallpaperBlur = it }
                    }
                    ClickRow("Własna strona domowa", value = s.homepageUrl.ifBlank { "Kafelki" }) {
                        ask("Adres strony domowej (puste = kafelki)", s.homepageUrl) { s.homepageUrl = it.trim() }
                    }
                    ClickRow("Przywróć domyślne kafelki") { resetConfirm = true }


                    Box(Modifier.height(32.dp))
                }
            }
            "look" -> {
                ChipsRow("Motyw", listOf("system" to "Systemowy", "light" to "Jasny", "dark" to "Ciemny"), s.theme) { s.theme = it }
                ChipsRow(
                    "Kolory",
                    buildList {
                        add("mono" to "Czarno-białe")
                        add("accent" to "Akcent")
                        if (Build.VERSION.SDK_INT >= 31) add("dynamic" to "Material You")
                    },
                    s.palette,
                ) { s.palette = it }
                if (s.palette == "accent") ColorDots(ACCENTS, s.accent) { s.accent = it }
                ToggleRow("Czerń AMOLED", "W trybie ciemnym", s.amoled) { s.amoled = it }
                ChipsRow("Gęstość", listOf("compact" to "Kompaktowa", "normal" to "Zwykła"), s.density) { s.density = it }
                ToggleRow("Pasek adresu na dole", "Wygodniejszy przy obsłudze jedną ręką", s.addressBarBottom) { s.addressBarBottom = it }
                ToggleRow("Pasek nawigacji", "Wstecz, dalej, start, karty i menu na dole", s.navBar) { s.navBar = it }
                ToggleRow("Przycisk strony startowej", null, s.showHomeButton) { s.showHomeButton = it }
                ToggleRow("Przycisk odświeżania", null, s.showReloadButton) { s.showReloadButton = it }
                ToggleRow("Licznik kart", null, s.showTabCounter) { s.showTabCounter = it }
                ToggleRow("Pasek postępu", null, s.showProgress) { s.showProgress = it }
                ToggleRow("Ukrywaj paski przy przewijaniu", null, s.hideBarsOnScroll) { s.hideBarsOnScroll = it }
                ToggleRow("Przesuń pasek adresu, aby zmienić kartę", null, s.swipeTabs) { s.swipeTabs = it }
                ToggleRow("Wibracje przy dotyku", null, s.haptics) { s.haptics = it }
                ChipsRow("Ciemne strony", listOf("auto" to "Zgodnie z motywem", "force" to "Zawsze", "off" to "Nigdy"), s.webDarkMode) {
                    web { s.webDarkMode = it }
                }


            }
            "search" -> {
                ChipsRow("Wyszukiwarka", SEARCH_ENGINES.map { it.id to it.name }, s.searchEngine) {
                    s.searchEngine = it
                    if (it == "custom") ask("Adres wyszukiwania z %s w miejscu zapytania", s.customSearchUrl) { v -> s.customSearchUrl = v.trim() }
                }
                ToggleRow("Podpowiedzi", "Wysyła wpisywany tekst do wyszukiwarki", s.searchSuggestions) { s.searchSuggestions = it }
                Text(
                    "Skróty: " + BrowserState.BANGS.keys.joinToString("  ") { "!$it" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )


            }
            "privacy" -> {
                ToggleRow("Blokowanie reklam i śledzenia", "Domen na liście: ${state.adBlocker.size}", s.adBlock) { s.adBlock = it }
                ToggleRow("Ukrywanie miejsc po reklamach", null, s.cosmeticFilter) { s.cosmeticFilter = it }
                ToggleRow("Ukrywanie banerów cookies", null, s.hideCookieBanners) { s.hideCookieBanners = it }
                val date = if (s.filtersUpdated > 0) DateFormat.getDateInstance().format(Date(s.filtersUpdated)) else "nigdy"
                ClickRow(if (filtersBusy) "Pobieranie listy filtrów…" else "Aktualizuj listę filtrów", "StevenBlack · ostatnio: $date") {
                    if (!filtersBusy) {
                        filtersBusy = true
                        scope.launch {
                            runCatching { state.adBlocker.update() }
                                .onSuccess { s.filtersUpdated = System.currentTimeMillis(); state.snack("Lista filtrów: $it domen") }
                                .onFailure { state.toast("Nie udało się pobrać: ${it.message}") }
                            filtersBusy = false
                        }
                    }
                }
                ToggleRow("Tylko HTTPS", "Automatycznie przechodzi na szyfrowane połączenie", s.httpsOnly) { s.httpsOnly = it }
                ToggleRow("Bezpieczne przeglądanie", "Ostrzega przed groźnymi stronami", s.safeBrowsing) { v -> web { s.safeBrowsing = v } }
                ToggleRow("Ciasteczka stron trzecich", null, s.thirdPartyCookies) { v -> web { s.thirdPartyCookies = v } }
                ToggleRow("Nie śledź (DNT i GPC)", null, s.doNotTrack) { s.doNotTrack = it }
                ToggleRow("Blokuj wyskakujące okna", null, s.blockPopups) { v -> web { s.blockPopups = v } }
                ToggleRow("Zapisuj historię", null, s.saveHistory) { s.saveHistory = it }
                ToggleRow("Czyść dane przy zamknięciu", null, s.clearOnExit) { s.clearOnExit = it }
                ClickRow("Wyczyść dane przeglądania", "Pamięć podręczna, ciasteczka, historia") { clearDialog = true }
                if (state.store.sites.isNotEmpty()) {
                    SectionTitle("Wyjątki stron")
                    state.store.sites.values.sortedBy { it.host }.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.host, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    buildList {
                                        if (!r.adBlock) add("reklamy dozwolone")
                                        if (!r.javascript) add("bez JavaScript")
                                        if (r.desktop) add("desktop")
                                        if (!r.darkMode) add("bez przyciemniania")
                                        if (r.textZoom > 0) add("tekst ${r.textZoom}%")
                                    }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RoundIcon(Icons.Rounded.Close, "Usuń wyjątek") {
                                state.store.saveRule(SiteRule(r.host))
                                state.applySettingsToAll()
                            }
                        }
                    }
                }


            }
            "content" -> {
                ToggleRow("JavaScript", null, s.javascript) { v -> web { s.javascript = v } }
                ToggleRow("Wczytuj obrazy", null, s.loadImages) { v -> web { s.loadImages = v } }
                ToggleRow("Blokuj autoodtwarzanie", "Multimedia startują po dotknięciu", s.blockAutoplay) { v -> web { s.blockAutoplay = v } }
                ToggleRow("Wersja desktop domyślnie", null, s.desktopDefault) { s.desktopDefault = it }
                ToggleRow("Wymuś powiększanie", "Także na stronach, które je blokują", s.forceZoom) { s.forceZoom = it }
                ToggleRow("Przeciągnij, aby odświeżyć", null, s.pullToRefresh) { s.pullToRefresh = it }
                SliderRow("Rozmiar tekstu", s.textZoom, 50..200, "%", step = 10) { v -> web { s.textZoom = v } }
                SliderRow("Tempo czytania na głos", s.ttsRate, 50..200, "%", step = 10) { s.ttsRate = it }
                ClickRow("User-Agent", value = if (s.customUserAgent.isBlank()) "Domyślny" else "Własny") {
                    ask("Własny User-Agent (puste = domyślny)", s.customUserAgent) { v -> web { s.customUserAgent = v.trim() } }
                }


            }
            "tabs" -> {
                ToggleRow("Przywracaj karty", "Po ponownym uruchomieniu", s.restoreTabs) { s.restoreTabs = it }
                ToggleRow("Otwieraj linki w tle", "Z kafelków i menu kontekstowego", s.linksInBackground) { s.linksInBackground = it }
                ToggleRow("Potwierdzaj zamykanie wszystkich kart", null, s.confirmCloseAll) { s.confirmCloseAll = it }
                ToggleRow("Pytaj przed pobraniem", "Pozwala zmienić nazwę pliku", s.askDownload) { s.askDownload = it }


            }
            "backup" -> {
                ClickRow("Eksportuj", "Ustawienia, kafelki, zakładki") { exportLauncher.launch("OpenExplorer-kopia.json") }
                ClickRow("Importuj z pliku") { importLauncher.launch(arrayOf("application/json", "*/*")) }


            }
            "updates" -> {
                ToggleRow("Sprawdzaj automatycznie", "Przy otwarciu aplikacji i co kilka godzin w tle", s.autoUpdate) {
                    s.autoUpdate = it
                    com.blacksingularities.openexplorer.update.UpdateWorker.schedule(ctx, replace = true)
                }
                if (s.autoUpdate) {
                    ToggleRow("Pobieraj automatycznie", "Aktualizacja czeka gotowa do instalacji", s.updateAutoDownload) { s.updateAutoDownload = it }
                    ToggleRow("Tylko przez Wi-Fi", null, s.updateWifiOnly) {
                        s.updateWifiOnly = it
                        com.blacksingularities.openexplorer.update.UpdateWorker.schedule(ctx, replace = true)
                    }
                    ToggleRow(
                        "Instaluj w tle bez pytania",
                        if (updater.canInstallSilently()) "Dostępne na tym urządzeniu" else "Wymaga Androida 12+ i jednej aktualizacji z poziomu aplikacji",
                        s.updateAutoInstall,
                    ) { s.updateAutoInstall = it }
                }
                if (!updater.canInstall()) {
                    ClickRow("Zezwól na instalowanie aktualizacji", "Wymagane jednorazowo w ustawieniach systemu") { updater.openInstallPermission() }
                }
                val sub = when (val st = updater.state) {
                    UpdateState.Checking -> "Sprawdzanie…"
                    UpdateState.UpToDate -> "Masz najnowszą wersję"
                    is UpdateState.Available -> "Dostępna wersja ${st.info.version}"
                    is UpdateState.Downloading -> "Pobieranie ${(st.progress * 100).toInt()}%"
                    is UpdateState.Ready -> "Gotowa do instalacji"
                    is UpdateState.Installing -> "Instalowanie…"
                    is UpdateState.Failed -> "Błąd: ${st.message}"
                    UpdateState.Idle -> "Wersja ${BuildConfig.VERSION_NAME}"
                } + if (updater.lastCheck > 0) " · sprawdzono " +
                    android.text.format.DateUtils.getRelativeTimeSpanString(updater.lastCheck).toString().lowercase() else ""
                (updater.state as? UpdateState.Available)?.let { av ->
                    ClickRow("Pobierz i zainstaluj ${av.info.version}", if (av.info.size > 0) formatSize(av.info.size) else null) {
                        scope.launch {
                            updater.download(av.info)
                            (updater.state as? UpdateState.Ready)?.let { if (!updater.install(it)) state.toast("Zezwól na instalację i wróć do aplikacji") }
                        }
                    }
                }
                (updater.state as? UpdateState.Ready)?.let { r ->
                    ClickRow("Zainstaluj wersję ${r.info.version}", "Pobrana i sprawdzona") {
                        if (!updater.install(r)) state.toast("Zezwól na instalację i wróć do aplikacji")
                    }
                }
                ClickRow("Sprawdź aktualizacje", sub) {
                    scope.launch {
                        updater.bannerHiddenFor = ""
                        updater.check()
                    }
                }


            }
            "about" -> {
                ClickRow("OpenExplorer ${BuildConfig.VERSION_NAME}", "Kod źródłowy i wydania na GitHub") {
                    state.newTab("https://github.com/${BuildConfig.UPDATE_REPO}")
                    onBack()
                }
                ClickRow("Przywróć ustawienia domyślne") { s.resetAll(); state.applySettingsToAll(); state.snack("Przywrócono ustawienia") }

            }
        }
        Box(Modifier.height(24.dp))
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
                    state.snack("Wyczyszczono dane")
                }) { Text("Wyczyść") }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Anuluj") } },
        )
    }
}

private class SettingsPage(val id: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
private fun CategoryRow(page: SettingsPage, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(page.icon, null, Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(page.title, style = MaterialTheme.typography.bodyLarge)
            Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ColorDots(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.take(9).forEach { c ->
            val col = Color(c)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(col)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp),
                    tint = if (col.luminance() > 0.55f) Color.Black else Color.White)
            }
        }
    }
}

private fun setDefaultBrowser(ctx: Context) {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                @Suppress("DEPRECATION")
                (ctx as? android.app.Activity)?.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 42)
                return
            }
        }
        ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (_: Exception) {
        ctx.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}
