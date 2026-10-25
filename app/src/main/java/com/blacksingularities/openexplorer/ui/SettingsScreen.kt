package com.blacksingularities.openexplorer.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.BuildConfig
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.data.CONTAINER_COLORS
import com.blacksingularities.openexplorer.data.Container
import com.blacksingularities.openexplorer.data.SEARCH_ENGINES
import com.blacksingularities.openexplorer.data.SearchEngine
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
fun SettingsScreen(
    state: BrowserState,
    updater: Updater,
    onBack: () -> Unit,
    onEditTiles: () -> Unit,
    onOpenPasswords: () -> Unit,
) {
    val s = state.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clearDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    var filtersBusy by remember { mutableStateOf(false) }
    /** Kontener w oknie edycji (nowy, gdy nie ma go jeszcze w Store) i kontener do usunięcia. */
    var containerEdit by remember { mutableStateOf<Container?>(null) }
    var containerDelete by remember { mutableStateOf<Container?>(null) }

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
            state.snack(ctx.getString(R.string.settings_backup_saved))
        }.onFailure { state.toast(ctx.getString(R.string.settings_backup_save_error, it.message.orEmpty())) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error(ctx.getString(R.string.settings_backup_empty_file))
            val o = JSONObject(text)
            o.optJSONObject("settings")?.let { s.import(it) }
            o.optJSONObject("data")?.let { state.store.import(it) }
            state.applySettingsToAll()
            state.snack(ctx.getString(R.string.settings_backup_restored))
        }.onFailure { state.toast(ctx.getString(R.string.settings_backup_invalid, it.message.orEmpty())) }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && copyImage(ctx, uri, wallpaperFile(ctx))) {
            s.wallpaperVersion = System.currentTimeMillis()
            s.homeWallpaper = "image"
        }
    }
    fun pickWallpaper() = wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val pages = listOf(
        SettingsPage("home", stringResource(R.string.settings_page_home), stringResource(R.string.settings_page_home_hint), Icons.Rounded.Dashboard),
        SettingsPage("look", stringResource(R.string.settings_page_look), stringResource(R.string.settings_page_look_hint), Icons.Rounded.Palette),
        SettingsPage("search", stringResource(R.string.settings_page_search), stringResource(R.string.settings_page_search_hint, engineName(s.engine)), Icons.Rounded.Search),
        SettingsPage("passwords", stringResource(R.string.settings_page_passwords), pluralStringResource(R.plurals.settings_page_passwords_hint, state.passwords.items.size, state.passwords.items.size), Icons.Rounded.Key),
        SettingsPage("privacy", stringResource(R.string.settings_page_privacy), stringResource(R.string.settings_page_privacy_hint), Icons.Rounded.Shield),
        SettingsPage("content", stringResource(R.string.settings_page_content), stringResource(R.string.settings_page_content_hint), Icons.Rounded.Language),
        SettingsPage("tabs", stringResource(R.string.settings_page_tabs), stringResource(R.string.settings_page_tabs_hint), Icons.Rounded.Tab),
        SettingsPage("backup", stringResource(R.string.settings_page_backup), stringResource(R.string.settings_page_backup_hint), Icons.Rounded.Backup),
        SettingsPage("updates", stringResource(R.string.settings_page_updates), stringResource(R.string.settings_page_updates_hint, BuildConfig.VERSION_NAME), Icons.Rounded.SystemUpdate),
        SettingsPage("about", stringResource(R.string.settings_page_about), "OpenExplorer " + BuildConfig.VERSION_NAME, Icons.Rounded.Info),
    )
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val current = pages.firstOrNull { it.id == page }

    Panel(
        title = current?.title ?: stringResource(R.string.menu_settings),
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
                        Text(stringResource(R.string.settings_default_browser), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_default_browser_hint), style = MaterialTheme.typography.bodySmall,
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
                        stringResource(R.string.settings_presets),
                        listOf(
                            "logos" to stringResource(R.string.settings_preset_logos), "classic" to stringResource(R.string.settings_preset_classic),
                            "dense" to stringResource(R.string.settings_preset_dense), "zen" to stringResource(R.string.settings_preset_zen),
                        ),
                        "",
                    ) { applyPreset(s, it) }
                    ClickRow(stringResource(R.string.settings_arrange_tiles), stringResource(R.string.settings_arrange_tiles_hint)) { onEditTiles() }

                    SliderRow(stringResource(R.string.settings_home_columns), s.homeColumns, 1..10) { s.homeColumns = it }
                    if (!s.homeAllTiles) SliderRow(stringResource(R.string.settings_home_rows), s.homeRows, 1..12) { s.homeRows = it }
                    ToggleRow(stringResource(R.string.settings_home_all_tiles), stringResource(R.string.settings_home_all_tiles_hint), s.homeAllTiles) { s.homeAllTiles = it }
                    SliderRow(stringResource(R.string.settings_tile_icon_scale), s.tileIconScale, 25..100, "%") { s.tileIconScale = it }
                    SliderRow(stringResource(R.string.settings_tile_corner), s.tileCorner, 0..50, "%") { s.tileCorner = it }
                    SliderRow(stringResource(R.string.settings_tile_spacing), s.tileSpacing, 0..40, " dp") { s.tileSpacing = it }
                    SliderRow(stringResource(R.string.settings_home_width), s.homeWidth, 240..1000, " dp", step = 20) { s.homeWidth = it }
                    ChipsRow(stringResource(R.string.settings_logo_source), listOf("hd" to stringResource(R.string.settings_logo_source_hd), "small" to stringResource(R.string.settings_logo_source_small), "letter" to stringResource(R.string.settings_logo_source_letter)), s.logoSource) { s.logoSource = it }
                    ToggleRow(stringResource(R.string.settings_tile_background), stringResource(R.string.settings_tile_background_hint), s.tileBackground) { s.tileBackground = it }
                    ToggleRow(stringResource(R.string.settings_tile_clip), stringResource(R.string.settings_tile_clip_hint), s.tileClip) { s.tileClip = it }
                    ToggleRow(stringResource(R.string.settings_tile_labels), null, s.tileLabels) { s.tileLabels = it }
                    if (s.tileLabels) SliderRow(stringResource(R.string.settings_tile_label_size), s.tileLabelSize, 8..18, " sp") { s.tileLabelSize = it }
                    ToggleRow(stringResource(R.string.settings_tile_grayscale), null, s.tileGrayscale) { s.tileGrayscale = it }
                    ToggleRow(stringResource(R.string.settings_tile_add_button), null, s.tileAddButton) { s.tileAddButton = it }
                    ChipsRow(stringResource(R.string.settings_home_align), listOf("center" to stringResource(R.string.settings_home_align_center), "top" to stringResource(R.string.settings_home_align_top)), s.homeAlign) { s.homeAlign = it }
                    ChipsRow(stringResource(R.string.settings_home_search_style), listOf("pill" to stringResource(R.string.settings_home_search_style_pill), "line" to stringResource(R.string.settings_home_search_style_line), "none" to stringResource(R.string.settings_home_search_style_none)), s.homeSearchStyle) { s.homeSearchStyle = it }
                    ToggleRow(stringResource(R.string.settings_home_clock), null, s.homeClock) { s.homeClock = it }
                    if (s.homeClock) SliderRow(stringResource(R.string.settings_home_clock_size), s.homeClockSize, 28..96, " sp", step = 4) { s.homeClockSize = it }
                    ToggleRow(stringResource(R.string.settings_home_logo), stringResource(R.string.settings_home_logo_hint), s.homeLogo) { s.homeLogo = it }
                    ToggleRow(stringResource(R.string.settings_home_recent), stringResource(R.string.settings_home_recent_hint), s.homeRecent) { s.homeRecent = it }
                    if (s.homeRecent) SliderRow(stringResource(R.string.settings_home_recent_count), s.homeRecentCount, 1..10) { s.homeRecentCount = it }
                    ChipsRow(
                        stringResource(R.string.settings_home_wallpaper),
                        listOf(
                            "none" to stringResource(R.string.settings_home_wallpaper_none), "aurora" to stringResource(R.string.settings_home_wallpaper_aurora), "gradient" to stringResource(R.string.settings_home_wallpaper_gradient),
                            "solid" to stringResource(R.string.settings_home_wallpaper_solid), "image" to stringResource(R.string.settings_home_wallpaper_image),
                        ),
                        s.homeWallpaper,
                    ) {
                        if (it == "image" && !wallpaperFile(ctx).exists()) pickWallpaper() else s.homeWallpaper = it
                    }
                    if (s.homeWallpaper == "solid") ColorDots(TILE_COLORS.filter { it != 0L }, s.homeWallpaperColor) { s.homeWallpaperColor = it }
                    if (s.homeWallpaper == "image") {
                        ClickRow(stringResource(R.string.settings_pick_wallpaper)) { pickWallpaper() }
                        SliderRow(stringResource(R.string.settings_home_wallpaper_dim), s.homeWallpaperDim, 0..80, "%") { s.homeWallpaperDim = it }
                        if (Build.VERSION.SDK_INT >= 31) SliderRow(stringResource(R.string.settings_home_wallpaper_blur), s.homeWallpaperBlur, 0..30, " dp") { s.homeWallpaperBlur = it }
                    }
                    ClickRow(stringResource(R.string.settings_homepage), stringResource(R.string.settings_homepage_hint), value = s.homepageUrl.ifBlank { stringResource(R.string.settings_homepage_tiles) }) {
                        ask(ctx.getString(R.string.settings_homepage_ask), s.homepageUrl) { s.homepageUrl = it.trim() }
                    }
                    ClickRow(stringResource(R.string.settings_reset_tiles)) { resetConfirm = true }


                    Box(Modifier.height(32.dp))
                }
            }
            "look" -> {
                ChipsRow(stringResource(R.string.settings_theme), listOf("system" to stringResource(R.string.settings_theme_system), "light" to stringResource(R.string.settings_theme_light), "dark" to stringResource(R.string.settings_theme_dark)), s.theme) { s.theme = it }
                ChipsRow(
                    stringResource(R.string.settings_palette),
                    buildList {
                        add("mono" to stringResource(R.string.settings_palette_mono))
                        add("accent" to stringResource(R.string.settings_palette_accent))
                        if (Build.VERSION.SDK_INT >= 31) add("dynamic" to stringResource(R.string.settings_palette_dynamic))
                    },
                    s.palette,
                ) { s.palette = it }
                if (s.palette == "accent") ColorDots(ACCENTS, s.accent) { s.accent = it }
                ToggleRow(stringResource(R.string.settings_amoled), stringResource(R.string.settings_amoled_hint), s.amoled) { s.amoled = it }
                ChipsRow(stringResource(R.string.settings_density), listOf("compact" to stringResource(R.string.settings_density_compact), "normal" to stringResource(R.string.settings_density_normal)), s.density) { s.density = it }
                ToggleRow(stringResource(R.string.settings_address_bar_bottom), stringResource(R.string.settings_address_bar_bottom_hint), s.addressBarBottom) { s.addressBarBottom = it }
                ToggleRow(stringResource(R.string.settings_nav_bar), stringResource(R.string.settings_nav_bar_hint), s.navBar) { s.navBar = it }
                ToggleRow(stringResource(R.string.settings_show_home_button), null, s.showHomeButton) { s.showHomeButton = it }
                ToggleRow(stringResource(R.string.settings_show_reload_button), null, s.showReloadButton) { s.showReloadButton = it }
                ToggleRow(stringResource(R.string.settings_show_tab_counter), null, s.showTabCounter) { s.showTabCounter = it }
                ToggleRow(stringResource(R.string.settings_show_progress), null, s.showProgress) { s.showProgress = it }
                ToggleRow(stringResource(R.string.settings_hide_bars_on_scroll), stringResource(R.string.settings_hide_bars_on_scroll_hint), s.hideBarsOnScroll) { s.hideBarsOnScroll = it }
                ToggleRow(stringResource(R.string.settings_scroll_top_button), stringResource(R.string.settings_scroll_top_button_hint), s.scrollTopButton) { s.scrollTopButton = it }
                ToggleRow(stringResource(R.string.settings_swipe_tabs), stringResource(R.string.settings_swipe_tabs_hint), s.swipeTabs) { s.swipeTabs = it }
                ToggleRow(stringResource(R.string.settings_haptics), null, s.haptics) { s.haptics = it }
                ChipsRow(
                    stringResource(R.string.settings_web_dark_mode),
                    listOf(
                        "auto" to stringResource(R.string.settings_web_dark_mode_auto),
                        "force" to stringResource(R.string.settings_web_dark_mode_force),
                        "off" to stringResource(R.string.settings_web_dark_mode_off),
                    ),
                    s.webDarkMode,
                ) {
                    web { s.webDarkMode = it }
                }


            }
            "search" -> {
                ChipsRow(stringResource(R.string.settings_search_engine), SEARCH_ENGINES.map { it.id to engineName(it) }, s.searchEngine) {
                    s.searchEngine = it
                    if (it == "custom") ask(ctx.getString(R.string.settings_custom_search_ask), s.customSearchUrl) { v -> s.customSearchUrl = v.trim() }
                }
                ToggleRow(stringResource(R.string.settings_search_suggestions), stringResource(R.string.settings_search_suggestions_hint, engineName(s.engine)), s.searchSuggestions) { s.searchSuggestions = it }
                ToggleRow(stringResource(R.string.settings_inline_autocomplete), stringResource(R.string.settings_inline_autocomplete_hint), s.inlineAutocomplete) { s.inlineAutocomplete = it }
                ToggleRow(stringResource(R.string.settings_clipboard_suggestion), stringResource(R.string.settings_clipboard_suggestion_hint), s.clipboardSuggestion) { s.clipboardSuggestion = it }
                Text(
                    stringResource(R.string.settings_bangs_hint, BrowserState.BANGS.keys.joinToString("  ") { "!$it" }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )


            }
            "passwords" -> {
                ClickRow(
                    stringResource(R.string.settings_saved_passwords),
                    pluralStringResource(R.plurals.settings_saved_passwords_hint, state.passwords.items.size, state.passwords.items.size),
                ) { onOpenPasswords() }
                ToggleRow(stringResource(R.string.settings_password_manager), stringResource(R.string.settings_password_manager_hint), s.passwordManager) {
                    s.passwordManager = it
                }
                if (s.passwordManager) {
                    ToggleRow(stringResource(R.string.settings_offer_save_passwords), stringResource(R.string.settings_offer_save_passwords_hint), s.offerSavePasswords) { s.offerSavePasswords = it }
                    ToggleRow(stringResource(R.string.settings_password_autofill), stringResource(R.string.settings_password_autofill_hint), s.passwordAutofill) { s.passwordAutofill = it }
                    ToggleRow(
                        stringResource(R.string.settings_password_auth_fill),
                        stringResource(R.string.settings_password_auth_fill_hint),
                        s.passwordAuthFill,
                    ) { s.passwordAuthFill = it }
                }
                ToggleRow(stringResource(R.string.settings_reveal_password_button), stringResource(R.string.settings_reveal_password_button_hint), s.revealPasswordButton) {
                    s.revealPasswordButton = it
                }
                ToggleRow(
                    stringResource(R.string.settings_system_autofill),
                    stringResource(R.string.settings_system_autofill_hint),
                    s.systemAutofill,
                ) { v -> web { s.systemAutofill = v } }
                Text(
                    stringResource(R.string.settings_passwords_import_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            "privacy" -> {
                ToggleRow(stringResource(R.string.settings_ad_block), stringResource(R.string.settings_ad_block_hint, state.adBlocker.size), s.adBlock) { s.adBlock = it }
                ToggleRow(stringResource(R.string.settings_cosmetic_filter), stringResource(R.string.settings_cosmetic_filter_hint), s.cosmeticFilter) { s.cosmeticFilter = it }
                ToggleRow(stringResource(R.string.settings_hide_cookie_banners), stringResource(R.string.settings_hide_cookie_banners_hint), s.hideCookieBanners) { s.hideCookieBanners = it }
                val date = if (s.filtersUpdated > 0) DateFormat.getDateInstance().format(Date(s.filtersUpdated)) else stringResource(R.string.settings_filters_never)
                ClickRow(
                    stringResource(if (filtersBusy) R.string.settings_filters_busy else R.string.settings_filters_download),
                    stringResource(R.string.settings_filters_hint, date),
                ) {
                    if (!filtersBusy) {
                        filtersBusy = true
                        scope.launch {
                            runCatching { state.adBlocker.update() }
                                .onSuccess { s.filtersUpdated = System.currentTimeMillis(); state.snack(ctx.resources.getQuantityString(R.plurals.settings_filters_updated, it, it)) }
                                .onFailure { state.toast(ctx.getString(R.string.settings_filters_error, it.message.orEmpty())) }
                            filtersBusy = false
                        }
                    }
                }
                ToggleRow(stringResource(R.string.settings_https_only), stringResource(R.string.settings_https_only_hint), s.httpsOnly) { s.httpsOnly = it }
                ToggleRow(stringResource(R.string.settings_safe_browsing), stringResource(R.string.settings_safe_browsing_hint), s.safeBrowsing) { v -> web { s.safeBrowsing = v } }
                ToggleRow(stringResource(R.string.settings_third_party_cookies), stringResource(R.string.settings_third_party_cookies_hint), s.thirdPartyCookies) { v -> web { s.thirdPartyCookies = v } }
                ToggleRow(stringResource(R.string.settings_do_not_track), stringResource(R.string.settings_do_not_track_hint), s.doNotTrack) { s.doNotTrack = it }
                ToggleRow(stringResource(R.string.settings_block_popups), stringResource(R.string.settings_block_popups_hint), s.blockPopups) { v -> web { s.blockPopups = v } }
                ToggleRow(stringResource(R.string.settings_save_history), null, s.saveHistory) { s.saveHistory = it }
                ToggleRow(
                    stringResource(R.string.settings_clear_on_exit),
                    stringResource(R.string.settings_clear_on_exit_hint),
                    s.clearOnExit,
                ) { s.clearOnExit = it }
                ClickRow(stringResource(R.string.settings_clear_now), stringResource(R.string.settings_clear_now_hint)) { clearDialog = true }
                if (state.store.sites.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.settings_site_rules))
                    state.store.sites.values.sortedBy { it.host }.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.host, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    buildList {
                                        if (!r.adBlock) add(stringResource(R.string.settings_rule_ads_allowed))
                                        if (!r.javascript) add(stringResource(R.string.settings_rule_no_js))
                                        if (r.desktop) add(stringResource(R.string.settings_rule_desktop))
                                        if (!r.darkMode) add(stringResource(R.string.settings_rule_no_dark))
                                        if (r.textZoom > 0) add(stringResource(R.string.settings_rule_text_zoom, r.textZoom))
                                        if (r.css.isNotBlank()) add(stringResource(R.string.settings_rule_css))
                                        if (r.js.isNotBlank()) add(stringResource(R.string.settings_rule_js))
                                    }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RoundIcon(Icons.Rounded.Close, stringResource(R.string.settings_rule_reset)) {
                                state.store.saveRule(SiteRule(r.host))
                                state.applySettingsToAll()
                            }
                        }
                    }
                }


            }
            "content" -> {
                ToggleRow(stringResource(R.string.settings_javascript), stringResource(R.string.settings_javascript_hint), s.javascript) { v -> web { s.javascript = v } }
                ToggleRow(stringResource(R.string.settings_load_images), stringResource(R.string.settings_load_images_hint), s.loadImages) { v -> web { s.loadImages = v } }
                ToggleRow(stringResource(R.string.settings_block_autoplay), stringResource(R.string.settings_block_autoplay_hint), s.blockAutoplay) { v -> web { s.blockAutoplay = v } }
                ToggleRow(stringResource(R.string.settings_desktop_default), stringResource(R.string.settings_desktop_default_hint), s.desktopDefault) { s.desktopDefault = it }
                ToggleRow(stringResource(R.string.settings_force_zoom), stringResource(R.string.settings_force_zoom_hint), s.forceZoom) { s.forceZoom = it }
                ToggleRow(stringResource(R.string.settings_pull_to_refresh), stringResource(R.string.settings_pull_to_refresh_hint), s.pullToRefresh) { s.pullToRefresh = it }
                SliderRow(stringResource(R.string.settings_text_zoom), s.textZoom, 50..200, "%", step = 10) { v -> web { s.textZoom = v } }
                SliderRow(stringResource(R.string.settings_tts_rate), s.ttsRate, 50..200, "%", step = 10) { s.ttsRate = it }
                ClickRow(
                    stringResource(R.string.settings_user_agent), stringResource(R.string.settings_user_agent_hint),
                    value = stringResource(if (s.customUserAgent.isBlank()) R.string.zoom_default else R.string.settings_user_agent_custom),
                ) {
                    ask(ctx.getString(R.string.settings_user_agent_ask), s.customUserAgent) { v -> web { s.customUserAgent = v.trim() } }
                }


            }
            "tabs" -> {
                ToggleRow(stringResource(R.string.settings_restore_tabs), null, s.restoreTabs) { s.restoreTabs = it }
                ToggleRow(stringResource(R.string.settings_links_in_background), stringResource(R.string.settings_links_in_background_hint), s.linksInBackground) { s.linksInBackground = it }
                ToggleRow(stringResource(R.string.settings_confirm_close_all), null, s.confirmCloseAll) { s.confirmCloseAll = it }
                ToggleRow(stringResource(R.string.settings_ask_download), stringResource(R.string.settings_ask_download_hint), s.askDownload) { s.askDownload = it }
                Text(
                    stringResource(R.string.settings_tabs_swipe_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                // Kontenery wymagają profili WebView – na starszych WebView sekcji nie ma.
                if (state.multiProfile) {
                    SectionTitle(stringResource(R.string.containers_title))
                    Text(
                        stringResource(R.string.containers_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    state.store.containers.forEach { c ->
                        val count = state.tabs.count { it.containerId == c.id }
                        Row(
                            Modifier.fillMaxWidth().clickable { containerEdit = c }.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color)))
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    pluralStringResource(R.plurals.containers_tabs, count, count),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RoundIcon(Icons.Rounded.Delete, stringResource(R.string.containers_delete)) { containerDelete = c }
                        }
                    }
                    ClickRow(stringResource(R.string.containers_add)) {
                        containerEdit = Container(name = "", color = CONTAINER_COLORS[state.store.containers.size % CONTAINER_COLORS.size])
                    }
                }


            }
            "backup" -> {
                ClickRow(stringResource(R.string.settings_backup_export), stringResource(R.string.settings_backup_export_hint)) { exportLauncher.launch("OpenExplorer-kopia.json") }
                ClickRow(stringResource(R.string.settings_backup_import)) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                Text(
                    stringResource(R.string.settings_backup_passwords_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )


            }
            "updates" -> {
                ToggleRow(stringResource(R.string.settings_auto_update), stringResource(R.string.settings_auto_update_hint), s.autoUpdate) {
                    s.autoUpdate = it
                    com.blacksingularities.openexplorer.update.UpdateWorker.schedule(ctx, replace = true)
                }
                if (s.autoUpdate) {
                    ToggleRow(stringResource(R.string.settings_update_auto_download), stringResource(R.string.settings_update_auto_download_hint), s.updateAutoDownload) { s.updateAutoDownload = it }
                    ToggleRow(stringResource(R.string.settings_update_wifi_only), stringResource(R.string.settings_update_wifi_only_hint), s.updateWifiOnly) {
                        s.updateWifiOnly = it
                        com.blacksingularities.openexplorer.update.UpdateWorker.schedule(ctx, replace = true)
                    }
                    ToggleRow(
                        stringResource(R.string.settings_update_auto_install),
                        stringResource(if (updater.canInstallSilently()) R.string.settings_update_auto_install_ok else R.string.settings_update_auto_install_no),
                        s.updateAutoInstall,
                    ) { s.updateAutoInstall = it }
                }
                if (!updater.canInstall()) {
                    ClickRow(stringResource(R.string.settings_update_permission), stringResource(R.string.settings_update_permission_hint)) { updater.openInstallPermission() }
                }
                val status = when (val st = updater.state) {
                    UpdateState.Checking -> stringResource(R.string.settings_update_checking)
                    UpdateState.UpToDate -> stringResource(R.string.settings_update_up_to_date)
                    is UpdateState.Available -> stringResource(R.string.settings_update_available, st.info.version)
                    is UpdateState.Downloading -> stringResource(R.string.settings_update_downloading, (st.progress * 100).toInt())
                    is UpdateState.Ready -> stringResource(R.string.settings_update_ready)
                    is UpdateState.Installing -> stringResource(R.string.settings_update_installing)
                    is UpdateState.Failed -> stringResource(R.string.settings_update_failed, st.message)
                    UpdateState.Idle -> stringResource(R.string.settings_update_idle, BuildConfig.VERSION_NAME)
                }
                val sub = if (updater.lastCheck > 0) {
                    stringResource(
                        R.string.settings_update_checked, status,
                        android.text.format.DateUtils.getRelativeTimeSpanString(updater.lastCheck).toString().lowercase(),
                    )
                } else status
                (updater.state as? UpdateState.Available)?.let { av ->
                    ClickRow(stringResource(R.string.settings_update_download, av.info.version), if (av.info.size > 0) formatSize(av.info.size) else null) {
                        scope.launch {
                            updater.download(av.info)
                            (updater.state as? UpdateState.Ready)?.let { if (!updater.install(it)) state.toast(ctx.getString(R.string.settings_update_allow_install)) }
                        }
                    }
                }
                (updater.state as? UpdateState.Ready)?.let { r ->
                    ClickRow(stringResource(R.string.settings_update_install, r.info.version), stringResource(R.string.settings_update_install_hint)) {
                        if (!updater.install(r)) state.toast(ctx.getString(R.string.settings_update_allow_install))
                    }
                }
                ClickRow(stringResource(R.string.settings_update_check_now), sub) {
                    scope.launch {
                        updater.bannerHiddenFor = ""
                        updater.check()
                    }
                }


            }
            "about" -> {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.blacksingularities.openexplorer.R.drawable.brand_mark), null, Modifier.size(56.dp))
                ClickRow("OpenExplorer ${BuildConfig.VERSION_NAME}", stringResource(R.string.settings_about_source)) {
                    state.newTab("https://github.com/${BuildConfig.UPDATE_REPO}")
                    onBack()
                }
                ClickRow(stringResource(R.string.settings_about_author)) {
                    state.newTab("https://redzikowski.com")
                    onBack()
                }
                ClickRow(stringResource(R.string.settings_about_coffee), stringResource(R.string.settings_about_coffee_hint)) {
                    state.newTab("https://buycoffee.to/adamredzikowski")
                    onBack()
                }
                ClickRow(stringResource(R.string.settings_reset_all)) { s.resetAll(); state.applySettingsToAll(); state.snack(ctx.getString(R.string.settings_reset_done)) }

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
            confirmButton = { TextButton(onClick = { onSave(v); textInput = null }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { textInput = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    containerEdit?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        var color by remember(c.id) { mutableStateOf(c.color) }
        val isNew = state.store.container(c.id) == null
        AlertDialog(
            onDismissRequest = { containerEdit = null },
            title = { Text(stringResource(if (isNew) R.string.containers_add else R.string.containers_edit)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.containers_name)) }, singleLine = true)
                    Row(
                        Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CONTAINER_COLORS.forEach { k ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Color(k)).clickable { color = k },
                                contentAlignment = Alignment.Center,
                            ) { if (k == color) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Color.White) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    state.store.saveContainer(c.copy(name = name.trim(), color = color))
                    containerEdit = null
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { containerEdit = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    containerDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { containerDelete = null },
            title = { Text(stringResource(R.string.containers_delete_title, c.name)) },
            text = { Text(stringResource(R.string.containers_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteContainer(c.id)
                    containerDelete = null
                    state.snack(ctx.getString(R.string.containers_deleted, c.name))
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { containerDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_tiles_title)) },
            text = { Text(stringResource(R.string.settings_reset_tiles_text)) },
            confirmButton = { TextButton(onClick = { state.store.resetTiles(); resetConfirm = false }) { Text(stringResource(R.string.settings_restore)) } },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (clearDialog) {
        var cache by remember { mutableStateOf(true) }
        var cookies by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(true) }
        var storage by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = {
                Column {
                    CheckRow(stringResource(R.string.settings_clear_cache), cache) { cache = it }
                    CheckRow(stringResource(R.string.settings_clear_history), history) { history = it }
                    CheckRow(stringResource(R.string.settings_clear_cookies), cookies) { cookies = it }
                    CheckRow(stringResource(R.string.settings_clear_storage), storage) { storage = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.clearData(cache, cookies, history, storage)
                    clearDialog = false
                    state.snack(ctx.getString(R.string.settings_data_cleared))
                }) { Text(stringResource(R.string.library_clear)) }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun engineName(e: SearchEngine): String = e.name.ifEmpty { stringResource(R.string.search_engine_custom) }

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
