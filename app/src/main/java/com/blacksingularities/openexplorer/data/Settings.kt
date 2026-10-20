package com.blacksingularities.openexplorer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Silnik wyszukiwania: [query] zawiera „%s” w miejscu zapytania. */
data class SearchEngine(val id: String, val name: String, val query: String, val home: String)

val SEARCH_ENGINES = listOf(
    SearchEngine("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
    SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com"),
    SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com"),
    SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com"),
    SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s", "https://www.startpage.com"),
    SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "https://www.ecosia.org"),
    SearchEngine("qwant", "Qwant", "https://www.qwant.com/?q=%s", "https://www.qwant.com"),
    SearchEngine("custom", "Własny", "", ""),
)

/**
 * Ustawienia aplikacji. Każde pole jest stanem Compose (zmiana od razu odświeża interfejs)
 * i jest zapisywane w SharedPreferences.
 */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val all = mutableListOf<Pref<*>>()

    private inner class Pref<T>(
        val key: String,
        val default: T,
        val read: (String, T) -> T,
        val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        val state = mutableStateOf(read(key, default))

        init {
            all += this
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit().also { it.write(key, value) }.apply()
        }

        fun reset() {
            state.value = default
        }
    }

    private fun b(key: String, d: Boolean) = Pref(key, d, { k, v -> sp.getBoolean(k, v) }, { k, v -> putBoolean(k, v) })
    private fun i(key: String, d: Int) = Pref(key, d, { k, v -> sp.getInt(k, v) }, { k, v -> putInt(k, v) })
    private fun l(key: String, d: Long) = Pref(key, d, { k, v -> sp.getLong(k, v) }, { k, v -> putLong(k, v) })
    private fun s(key: String, d: String) = Pref(key, d, { k, v -> sp.getString(k, v) ?: v }, { k, v -> putString(k, v) })

    // Wygląd
    /** system / light / dark */
    var theme by s("theme", "system")
    /** mono (czarno-biały jak OpenPDF) / accent / dynamic (Material You) */
    var palette by s("palette", "mono")
    var amoled by b("amoled", true)
    /** Kolor akcentu (ARGB) dla palety „accent”. */
    var accent by l("accent", 0xFF3D7BF7)
    var addressBarBottom by b("address_bar_bottom", false)
    /** Osobny pasek z przyciskami wstecz / dalej / start / karty / menu. */
    var navBar by b("nav_bar", false)
    /** compact / normal */
    var density by s("density", "compact")
    /** auto (zgodnie z motywem) / force (filtr) / off */
    var webDarkMode by s("web_dark_mode", "auto")
    var hideBarsOnScroll by b("hide_bars_on_scroll", true)
    var showTabCounter by b("show_tab_counter", true)
    var showHomeButton by b("show_home_button", false)
    var showReloadButton by b("show_reload_button", true)
    var swipeTabs by b("swipe_tabs", true)
    var haptics by b("haptics", true)
    var showProgress by b("show_progress", true)
    /** Okrągły przycisk „do góry” po dłuższym przewinięciu strony. */
    var scrollTopButton by b("scroll_top_button", true)

    // Strona startowa
    var homeColumns by i("home_columns", 3)
    var homeRows by i("home_rows", 3)
    /** Bez limitu wierszy – wszystkie kafelki. */
    var homeAllTiles by b("home_all_tiles", false)
    /** Zaokrąglenie kafelka/logo w procentach (0 = kwadrat, 50 = koło). */
    var tileCorner by i("tile_corner", 24)
    /** Rozmiar logo w procentach rozmiaru kafelka. */
    var tileIconScale by i("tile_icon_scale", 62)
    /** Odstęp między kafelkami (dp). */
    var tileSpacing by i("tile_spacing", 14)
    var tileLabels by b("tile_labels", true)
    var tileLabelSize by i("tile_label_size", 12)
    var tileBackground by b("tile_background", false)
    /** Logo bez przycinania kształtem (samo logo, przezroczyste tło). */
    var tileClip by b("tile_clip", true)
    var tileGrayscale by b("tile_grayscale", false)
    var tileAddButton by b("tile_add_button", true)
    /** hd (duże logo) / small (favicon) / letter (same litery) */
    var logoSource by s("logo_source", "hd")
    /** Maksymalna szerokość siatki (dp). */
    var homeWidth by i("home_width", 520)
    /** top / center */
    var homeAlign by s("home_align", "center")
    /** pill / line / none */
    var homeSearchStyle by s("home_search_style", "pill")
    var homeClock by b("home_clock", false)
    var homeClockSize by i("home_clock_size", 56)
    var homeLogo by b("home_logo", false)
    /** none / gradient / aurora / solid / image */
    var homeWallpaper by s("home_wallpaper", "none")
    var homeWallpaperColor by l("home_wallpaper_color", 0xFF1E3A8A)
    var homeWallpaperDim by i("home_wallpaper_dim", 30)
    var homeWallpaperBlur by i("home_wallpaper_blur", 0)
    /** Zmieniany po wybraniu nowej tapety (unieważnia pamięć podręczną obrazu). */
    var wallpaperVersion by l("wallpaper_version", 0L)
    var homeRecent by b("home_recent", false)
    var homeRecentCount by i("home_recent_count", 4)

    // Wyszukiwanie
    var searchEngine by s("search_engine", "google")
    var customSearchUrl by s("custom_search_url", "")
    var searchSuggestions by b("search_suggestions", true)
    var homepageUrl by s("homepage_url", "")
    /** Dopisywanie reszty adresu podczas pisania („you” → „youtube.com”). */
    var inlineAutocomplete by b("inline_autocomplete", true)
    /** Podpowiedź „otwórz skopiowany link” w pasku adresu. */
    var clipboardSuggestion by b("clipboard_suggestion", true)

    // Hasła
    /** Własny menedżer haseł (zapisywanie i uzupełnianie na stronach). */
    var passwordManager by b("password_manager", true)
    var offerSavePasswords by b("offer_save_passwords", true)
    /** Wypełnianie bez dotknięcia, gdy dla strony jest dokładnie jeden login. */
    var passwordAutofill by b("password_autofill", false)
    /** Odcisk palca / PIN przed każdym uzupełnieniem hasła na stronie. */
    var passwordAuthFill by b("password_auth_fill", false)
    /** Przycisk z okiem przy polach haseł na stronach. */
    var revealPasswordButton by b("reveal_password_button", true)
    /** Systemowe autouzupełnianie (Google, Edge, Bitwarden…) w polach stron. */
    var systemAutofill by b("system_autofill", false)

    // Prywatność i bezpieczeństwo
    var adBlock by b("ad_block", true)
    var httpsOnly by b("https_only", true)
    var thirdPartyCookies by b("third_party_cookies", false)
    var doNotTrack by b("do_not_track", true)
    var blockPopups by b("block_popups", true)
    var saveHistory by b("save_history", true)
    var clearOnExit by b("clear_on_exit", false)
    var safeBrowsing by b("safe_browsing", true)
    var hideCookieBanners by b("hide_cookie_banners", true)
    var cosmeticFilter by b("cosmetic_filter", true)
    var blockAutoplay by b("block_autoplay", false)
    var filtersUpdated by l("filters_updated", 0L)

    // Treści
    var javascript by b("javascript", true)
    var desktopDefault by b("desktop_default", false)
    var textZoom by i("text_zoom", 100)
    var loadImages by b("load_images", true)
    var forceZoom by b("force_zoom", true)
    var pullToRefresh by b("pull_to_refresh", true)
    var customUserAgent by s("custom_user_agent", "")

    // Karty
    var restoreTabs by b("restore_tabs", true)
    var linksInBackground by b("links_in_background", false)
    var confirmCloseAll by b("confirm_close_all", true)

    // Pobieranie
    var askDownload by b("ask_download", true)

    // Mowa
    var ttsRate by i("tts_rate", 100)

    // Aktualizacje
    var autoUpdate by b("auto_update", true)
    /** Pobieranie nowej wersji bez pytania (w tle i przy otwarciu aplikacji). */
    var updateAutoDownload by b("update_auto_download", true)
    var updateWifiOnly by b("update_wifi_only", false)
    /** Cicha instalacja w tle (Android 12+), gdy system na to pozwala. */
    var updateAutoInstall by b("update_auto_install", true)
    var lastUpdateCheck by l("last_update_check", 0L)
    var skippedVersion by s("skipped_version", "")

    val engine: SearchEngine
        get() = SEARCH_ENGINES.firstOrNull { it.id == searchEngine } ?: SEARCH_ENGINES[0]

    /** Adres wyszukiwania zapytania w wybranym silniku. */
    fun searchUrl(query: String): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val pattern = if (searchEngine == "custom" && customSearchUrl.contains("%s")) customSearchUrl else engine.query.ifEmpty { SEARCH_ENGINES[0].query }
        return pattern.replace("%s", q)
    }

    /** Wszystkie ustawienia jako JSON (kopia zapasowa). */
    fun export(): JSONObject = JSONObject().also { o -> sp.all.forEach { (k, v) -> o.put(k, v) } }

    fun import(o: JSONObject) {
        val e = sp.edit()
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> if (k in LONG_KEYS) e.putLong(k, v.toLong()) else e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Number -> e.putLong(k, v.toLong())
                is String -> e.putString(k, v)
            }
        }
        e.commit()
        reload()
    }

    fun resetAll() {
        sp.edit().clear().commit()
        all.forEach { it.reset() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun reload() {
        all.forEach { p -> (p as Pref<Any?>).state.value = p.read(p.key, p.default) }
    }

    private companion object {
        val LONG_KEYS = setOf("wallpaper_version", "accent", "home_wallpaper_color", "filters_updated", "last_update_check")
    }
}
