package com.blacksingularities.openexplorer.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.blacksingularities.openexplorer.browser.BrowserDialog
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.browser.Omnibox
import com.blacksingularities.openexplorer.browser.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Podpowiedź paska adresu. kind: 0 zapytanie, 1 historia, 2 zakładka, 3 otwarta karta, 4 link ze schowka. */
private data class Suggestion(val text: String, val url: String?, val kind: Int, val tabId: Long = -1L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    editTiles: Boolean,
    onEditTiles: (Boolean) -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val tab = state.current ?: return
    val settings = state.settings
    var editing by remember { mutableStateOf(false) }
    val bottom = settings.addressBarBottom
    val dims = if (settings.density == "compact") CompactDims else NormalDims
    val showNav = settings.navBar && !editing
    val imeVisible = WindowInsets.isImeVisible
    // Tryb nakładki: paski przesuwają się nad stroną zamiast zmieniać jej rozmiar (płynne przewijanie).
    // Przy klawiaturze otwartej na stronie zwykły układ – pole formularza nie schowa się pod paskiem.
    val overlay = settings.hideBarsOnScroll && !(imeVisible && !editing)
    val canHide = overlay && !editing && !tab.showHome && !state.findMode
    val barPx = with(LocalDensity.current) { dims.bar.toPx() }
    SideEffect { state.barMax = if (canHide) barPx else 0f }
    LaunchedEffect(canHide) { if (!canHide) state.showBars() }

    // „Wstecz” przy otwartym pasku adresu zamyka pasek i wraca do strony.
    BackHandler(enabled = editing) { editing = false }
    // Schowanie klawiatury przyciskiem „Wstecz” też zamyka pasek (jak w Chrome) – strona nie zostaje zasłonięta.
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, editing) {
        when {
            !editing -> imeSeen = false
            imeVisible -> imeSeen = true
            imeSeen -> editing = false
        }
    }

    val topPad: Dp = if (!bottom) dims.bar else 0.dp
    val bottomPad: Dp = (if (bottom) dims.bar else 0.dp) + (if (showNav) dims.bar else 0.dp)

    CompositionLocalProvider(LocalDims provides dims, LocalHaptics provides settings.haptics) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.statusBarsPadding())
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                // WebView karty (zostaje w drzewie także pod stroną startową, by zachować stan strony).
                if (tab.webView != null || !tab.showHome) {
                    Box(
                        Modifier.fillMaxSize().then(
                            if (overlay) Modifier.graphicsLayer { translationY = if (bottom) 0f else barPx - state.barShift }
                            else Modifier.padding(top = topPad, bottom = bottomPad),
                        ),
                    ) {
                        key(tab.id, tab.generation) {
                            AndroidView(
                                factory = { state.containerFor(tab).also { (it.parent as? ViewGroup)?.removeView(it) } },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (tab.showHome) {
                    HomePage(
                        settings = settings,
                        store = state.store,
                        incognito = tab.incognito,
                        editMode = editTiles,
                        onEditMode = onEditTiles,
                        onOpen = { state.load(tab, it) },
                        onOpenNewTab = { url, inc -> state.newTab(url, inc, background = settings.linksInBackground && !inc) },
                        onSearchClick = { editing = true },
                        modifier = Modifier.padding(top = topPad, bottom = bottomPad),
                    )
                }

                // Przycisk „do góry”.
                AnimatedVisibility(
                    state.showScrollTop && !tab.showHome && !editing,
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomPad + 16.dp),
                    enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                ) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { state.scrollToTop() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.VerticalAlignTop, "Przewiń na górę", Modifier.size(22.dp)) }
                }

                // Propozycje menedżera haseł.
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad)) {
                    if (!editing) {
                        NewPasswordCard(state)
                        LoginOfferCard(state)
                    }
                }

                AnimatedVisibility(
                    editing,
                    enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { if (bottom) it / 12 else -it / 12 },
                    exit = fadeOut(tween(120)),
                ) {
                    SuggestionsPanel(
                        state, tab, bottom,
                        onPick = { s ->
                            editing = false
                            if (s.kind == 3) state.select(s.tabId) else state.navigate(s.url ?: s.text, tab)
                        },
                        modifier = Modifier.padding(top = topPad, bottom = bottomPad),
                    )
                }
                if (state.findMode) {
                    FindBar(
                        state, tab,
                        Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter)
                            .padding(top = topPad, bottom = bottomPad),
                    )
                }

                // Paski – przesuwane w warstwie graficznej, bez przeliczania układu przy każdej klatce.
                if (!bottom) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().graphicsLayer { translationY = -state.barShift }) {
                        Toolbar(state, tab, editing, { editing = it }, onOpenTabs, onOpenMenu)
                        ProgressLine(state, tab, Modifier.align(Alignment.BottomCenter))
                    }
                }
                if (bottom || showNav) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer {
                            translationY = if (barPx > 0f) state.barShift / barPx * size.height else 0f
                        },
                    ) {
                        if (bottom) {
                            Box {
                                Toolbar(state, tab, editing, { editing = it }, onOpenTabs, onOpenMenu)
                                ProgressLine(state, tab, Modifier.align(Alignment.TopCenter))
                            }
                        }
                        if (showNav) NavBar(state, tab, onOpenTabs, onOpenMenu)
                    }
                }
            }
            Box(Modifier.navigationBarsPadding().imePadding())
        }
        SavePasswordSheet(state)
    }
}

@Composable
private fun ProgressLine(state: BrowserState, tab: Tab, modifier: Modifier) {
    if (tab.loading && !tab.showHome && state.settings.showProgress) {
        LinearProgressIndicator(
            progress = { tab.progress / 100f },
            modifier = modifier.fillMaxWidth().height(2.dp),
            trackColor = Color.Transparent,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun Toolbar(
    state: BrowserState,
    tab: Tab,
    editing: Boolean,
    setEditing: (Boolean) -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val s = state.settings
    val dims = LocalDims.current
    Row(
        Modifier.fillMaxWidth().height(dims.bar).background(MaterialTheme.colorScheme.background).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!editing && s.showHomeButton && !s.navBar) RoundIcon(Icons.Rounded.Home, "Strona startowa") { state.goHome(tab) }
        AddressField(state, tab, editing, setEditing, Modifier.weight(1f).padding(horizontal = 4.dp))
        if (!editing && !s.navBar) {
            if (s.showTabCounter) {
                Box(
                    Modifier.size(dims.button).clip(CircleShape).clickable(onClick = onOpenTabs),
                    contentAlignment = Alignment.Center,
                ) { TabCounter(state.tabs.count { it.incognito == tab.incognito }, tab.incognito) }
            }
            RoundIcon(Icons.Rounded.MoreVert, "Menu", onClick = onOpenMenu)
        }
    }
}

@Composable
private fun NavBar(state: BrowserState, tab: Tab, onOpenTabs: () -> Unit, onOpenMenu: () -> Unit) {
    val dims = LocalDims.current
    Row(
        Modifier.fillMaxWidth().height(dims.bar).background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", enabled = !tab.showHome || tab.parentId != null) { state.back() }
        RoundIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Dalej", enabled = tab.canGoForward && !tab.showHome) { state.forward() }
        RoundIcon(Icons.Rounded.Home, "Strona startowa", active = tab.showHome) { state.goHome(tab) }
        Box(Modifier.size(dims.button).clip(CircleShape).clickable(onClick = onOpenTabs), contentAlignment = Alignment.Center) {
            TabCounter(state.tabs.count { it.incognito == tab.incognito }, tab.incognito)
        }
        RoundIcon(Icons.Rounded.MoreVert, "Menu", onClick = onOpenMenu)
    }
}

/** Kwadracik z liczbą kart (kolory jawne – czytelny w każdym motywie). */
@Composable
fun TabCounter(count: Int, incognito: Boolean) {
    val c = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(22.dp).border(1.8.dp, c, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (incognito && count == 0) Icon(Icons.Rounded.VisibilityOff, "Karty incognito", Modifier.size(12.dp), tint = c)
        else Text(
            if (count > 99) "∞" else "$count", color = c, fontSize = if (count > 9) 10.sp else 11.sp, fontWeight = FontWeight.Bold,
            lineHeight = 11.sp, maxLines = 1,
        )
    }
}

@Composable
private fun AddressField(
    state: BrowserState,
    tab: Tab,
    editing: Boolean,
    setEditing: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dims = LocalDims.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = rememberHaptic()
    var value by remember { mutableStateOf(TextFieldValue("")) }
    // Tekst wpisany przez użytkownika (bez dopisanej podpowiedzi adresu).
    var typed by remember { mutableStateOf("") }
    var swipe by remember { mutableFloatStateOf(0f) }
    val hosts = remember(editing) { if (editing && state.settings.inlineAutocomplete) state.completionHosts() else emptyList() }

    LaunchedEffect(state.suggestQuery) {
        val q = state.suggestQuery
        if (editing && typed != q) {
            typed = q
            value = TextFieldValue(q, TextRange(q.length))
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            val text = if (tab.showHome) "" else tab.url
            typed = text
            value = TextFieldValue(text, TextRange(0, text.length))
            state.suggestQuery = text
            focus.requestFocus()
            keyboard?.show()
        } else focusManager.clearFocus()
    }

    fun onChange(v: TextFieldValue) {
        // Dopisywanie tylko przy pisaniu na końcu (nie przy kasowaniu ani przesuwaniu kursora).
        val inserted = !typed.startsWith(v.text) && v.selection.collapsed && v.selection.end == v.text.length && v.composition == null
        typed = v.text
        state.suggestQuery = v.text
        val full = if (inserted && hosts.isNotEmpty()) Omnibox.complete(v.text, hosts) else null
        value = if (full != null) TextFieldValue(full, TextRange(v.text.length, full.length)) else v
    }

    Row(
        modifier.height(dims.field).clip(CircleShape)
            .background(if (tab.incognito) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Anuluj", size = dims.field - 6.dp, iconSize = 18.dp) { setEditing(false) }
            Box(Modifier.weight(1f).padding(start = 4.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = { onChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { setEditing(false); state.navigate(value.text, tab) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (value.text.isEmpty()) {
                    Text("Szukaj lub wpisz adres  ·  !yt, !w, !gh…", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value.text.isNotEmpty()) {
                RoundIcon(Icons.Rounded.Close, "Wyczyść", size = dims.field - 6.dp, iconSize = 18.dp) {
                    typed = ""
                    value = TextFieldValue("")
                    state.suggestQuery = ""
                }
            }
        } else {
            val statusIcon = when {
                tab.showHome -> Icons.Rounded.Search
                tab.incognito -> Icons.Rounded.VisibilityOff
                tab.secure -> Icons.Rounded.Lock
                tab.url.startsWith("http://") -> Icons.Rounded.Warning
                else -> Icons.Rounded.Public
            }
            RoundIcon(
                statusIcon, "Informacje o stronie", size = dims.field - 6.dp, iconSize = 16.dp,
                tint = if (tab.url.startsWith("http://") && !tab.showHome) colors.error else colors.onSurfaceVariant,
            ) {
                if (tab.showHome) setEditing(true) else state.dialog = BrowserDialog.SiteInfo(tab)
            }
            Row(
                Modifier.weight(1f).fillMaxSize()
                    .offset { IntOffset(swipe.roundToInt() / 3, 0) }
                    .clickable { setEditing(true); state.suggestQuery = "" }
                    .then(
                        if (state.settings.swipeTabs) Modifier.pointerInput(tab.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (abs(swipe) > 160) {
                                        haptic()
                                        state.switchRelative(if (swipe < 0) 1 else -1)
                                    }
                                    swipe = 0f
                                },
                                onDragCancel = { swipe = 0f },
                            ) { change, amount ->
                                change.consume()
                                swipe += amount
                            }
                        } else Modifier,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (tab.showHome) "Szukaj lub wpisz adres" else tab.host.ifEmpty { tab.url },
                    color = if (tab.showHome) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
                if (!tab.showHome && tab.blocked > 0 && state.settings.adBlock) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Rounded.Shield, "Zablokowane", Modifier.size(13.dp), tint = colors.onSurfaceVariant)
                        Text("${tab.blocked}", fontSize = 10.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
            if (!tab.showHome && state.settings.showReloadButton) {
                RoundIcon(
                    if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                    if (tab.loading) "Zatrzymaj" else "Odśwież", size = dims.field - 6.dp, iconSize = 18.dp,
                ) { state.reload() }
            }
        }
    }
}

/** Link w schowku. [text] = null, gdy system wie, że to link, ale treść odczytamy dopiero po dotknięciu (Android 12+). */
private class ClipHint(val text: String?)

private fun clipboardHint(ctx: Context): ClipHint? = runCatching {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return null
    if (!cm.hasPrimaryClip()) return null
    val desc = cm.primaryClipDescription ?: return null
    if (System.currentTimeMillis() - desc.timestamp > 10 * 60_000L) return null
    if (Build.VERSION.SDK_INT >= 31) {
        // Bez odczytu treści (odczyt pokazuje komunikat systemu) – wystarczy klasyfikacja schowka.
        if (desc.classificationStatus != ClipDescription.CLASSIFICATION_COMPLETE) return null
        if (desc.getConfidenceScore(TextClassifier.TYPE_URL) < 0.5f) return null
        return ClipHint(null)
    }
    if (!desc.hasMimeType("text/*")) return null
    val t = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: return null
    if (Regex("^https?://\\S+$").matches(t)) ClipHint(t) else null
}.getOrNull()

private fun readClipboard(ctx: Context): String? = runCatching {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

@Composable
private fun SuggestionsPanel(state: BrowserState, tab: Tab, bottom: Boolean, onPick: (Suggestion) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val query = state.suggestQuery
    var remote by remember { mutableStateOf<List<String>>(emptyList()) }
    val clip = remember { if (state.settings.clipboardSuggestion) clipboardHint(ctx) else null }
    LaunchedEffect(query) {
        remote = emptyList()
        if (query.isBlank() || !state.settings.searchSuggestions || tab.incognito || query.startsWith("!")) return@LaunchedEffect
        delay(160)
        remote = fetchSuggestions(query, state.settings.searchEngine)
    }
    val q = query.trim().lowercase()
    val local = if (q.isEmpty()) {
        if (tab.incognito) emptyList()
        else state.store.history.distinctBy { it.url }.take(8).map { Suggestion(it.title.ifBlank { it.url }, it.url, 1) }
    } else {
        val bm = state.store.bookmarks.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .take(4).map { Suggestion(it.title, it.url, 2) }
        val hist = if (tab.incognito) emptyList() else state.store.history.asSequence()
            .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            .distinctBy { it.url }.take(5).map { Suggestion(it.title.ifBlank { it.url }, it.url, 1) }.toList()
        bm + hist.filter { h -> bm.none { it.url == h.url } }
    }
    val openTabs = if (q.isEmpty()) emptyList() else state.tabs.asSequence()
        .filter { it.id != tab.id && it.incognito == tab.incognito && !it.showHome && it.url.isNotEmpty() }
        .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        .take(3).map { Suggestion(it.displayTitle, it.url, 3, it.id) }.toList()
    val items = buildList {
        if (q.isEmpty() && clip != null) add(Suggestion("Otwórz skopiowany link", clip.text, 4))
        if (q.isNotEmpty()) add(Suggestion(query.trim(), null, 0))
        addAll(remote.filter { it.lowercase() != q }.take(6).map { Suggestion(it, null, 0) })
        addAll(openTabs)
        addAll(local.filter { l -> openTabs.none { it.url == l.url } })
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(reverseLayout = bottom) {
            items(items) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (s.kind == 4) readClipboard(ctx)?.let { onPick(Suggestion(it, null, 0)) }
                        else onPick(s)
                    }.padding(start = 18.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        s.kind == 4 -> Icon(Icons.Rounded.ContentPaste, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        s.url != null -> SiteLogo(s.url, s.text, 0L, "auto", "", "small", false, Modifier.size(20.dp), RoundedCornerShape(5.dp))
                        else -> Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(s.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        val sub = when (s.kind) {
                            3 -> "Przełącz na otwartą kartę"
                            else -> s.url
                        }
                        if (sub != null) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (s.kind) {
                        0 -> RoundIcon(Icons.Rounded.NorthWest, "Wstaw do paska", size = 34.dp, iconSize = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) { state.suggestQuery = s.text + " " }
                        1 -> Icon(Icons.Rounded.History, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        2 -> Icon(Icons.Rounded.Star, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        3 -> Icon(Icons.Rounded.Tab, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Unit
                    }
                }
            }
        }
    }
}

private suspend fun fetchSuggestions(query: String, engine: String): List<String> = withContext(Dispatchers.IO) {
    try {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = if (engine == "google") "https://suggestqueries.google.com/complete/search?client=firefox&q=$q"
        else "https://duckduckgo.com/ac/?type=list&q=$q"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONArray(text).getJSONArray(1)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun FindBar(state: BrowserState, tab: Tab, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 18.dp, end = 4.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            text, { text = it; state.find(it) }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { state.findNext(true) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Text(if (text.isEmpty()) "" else "${tab.findIndex}/${tab.findCount}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        RoundIcon(Icons.Rounded.KeyboardArrowUp, "Poprzedni", size = 38.dp) { state.findNext(false) }
        RoundIcon(Icons.Rounded.KeyboardArrowDown, "Następny", size = 38.dp) { state.findNext(true) }
        RoundIcon(Icons.Rounded.Close, "Zamknij", size = 38.dp) { state.closeFind() }
    }
}

@Composable
fun Favicon(tab: Tab, size: Int = 18) {
    val fav = tab.favicon
    if (fav != null) {
        Image(fav.asImageBitmap(), null, Modifier.size(size.dp).clip(RoundedCornerShape(4.dp)))
    } else if (tab.showHome || tab.url.isEmpty()) {
        Icon(if (tab.incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Home, null, Modifier.size(size.dp))
    } else {
        SiteLogo(tab.url, tab.title, 0L, "auto", "", "small", false, Modifier.size(size.dp), RoundedCornerShape(4.dp))
    }
}
