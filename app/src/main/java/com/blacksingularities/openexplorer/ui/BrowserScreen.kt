package com.blacksingularities.openexplorer.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.blacksingularities.openexplorer.browser.BrowserDialog
import com.blacksingularities.openexplorer.browser.BrowserState
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

private data class Suggestion(val text: String, val url: String?, val kind: Int) // 0 zapytanie, 1 historia, 2 zakładka

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
    val barsShown = state.barsVisible || editing || tab.showHome
    val dims = if (settings.density == "compact") CompactDims else NormalDims

    CompositionLocalProvider(LocalDims provides dims, LocalHaptics provides settings.haptics) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.statusBarsPadding())
            if (!bottom) {
                AnimatedVisibility(barsShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Toolbar(state, tab, editing, { editing = it }, onOpenTabs, onOpenMenu)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                // WebView karty (zostaje w drzewie także pod stroną startową, by zachować stan strony).
                if (tab.webView != null || !tab.showHome) {
                    key(tab.id, tab.generation) {
                        AndroidView(
                            factory = { state.containerFor(tab).also { (it.parent as? ViewGroup)?.removeView(it) } },
                            modifier = Modifier.fillMaxSize(),
                        )
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
                    )
                }
                if (editing) {
                    SuggestionsPanel(state, tab, onPick = { editing = false; state.navigate(it, tab) }, bottom = bottom)
                }
                if (state.findMode) {
                    FindBar(state, tab, Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter))
                }
                if (tab.loading && !tab.showHome && settings.showProgress) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp).align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter),
                        trackColor = Color.Transparent,
                        drawStopIndicator = {},
                    )
                }
            }

            if (bottom) {
                AnimatedVisibility(barsShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Toolbar(state, tab, editing, { editing = it }, onOpenTabs, onOpenMenu)
                }
            }
            if (settings.navBar && !editing) {
                AnimatedVisibility(barsShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    NavBar(state, tab, onOpenTabs, onOpenMenu)
                }
            }
            Box(Modifier.navigationBarsPadding().imePadding())
        }
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
    var swipe by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.suggestQuery) {
        val q = state.suggestQuery
        if (editing && value.text != q) value = TextFieldValue(q, TextRange(q.length))
    }
    LaunchedEffect(editing) {
        if (editing) {
            val text = if (tab.showHome) "" else tab.url
            value = TextFieldValue(text, TextRange(0, text.length))
            state.suggestQuery = text
            focus.requestFocus()
            keyboard?.show()
        } else focusManager.clearFocus()
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
                    onValueChange = { value = it; state.suggestQuery = it.text },
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

@Composable
private fun SuggestionsPanel(state: BrowserState, tab: Tab, onPick: (String) -> Unit, bottom: Boolean) {
    val query = state.suggestQuery
    var remote by remember { mutableStateOf<List<String>>(emptyList()) }
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
    val items = (if (q.isNotEmpty()) listOf(Suggestion(query.trim(), null, 0)) else emptyList()) +
        remote.filter { it.lowercase() != q }.take(6).map { Suggestion(it, null, 0) } + local

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(reverseLayout = bottom) {
            items(items) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(s.url ?: s.text) }.padding(start = 18.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s.url != null) {
                        SiteLogo(s.url, s.text, 0L, "auto", "", "small", false, Modifier.size(20.dp), RoundedCornerShape(5.dp))
                    } else {
                        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(s.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        if (s.url != null) Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (s.kind) {
                        0 -> RoundIcon(Icons.Rounded.NorthWest, "Uzupełnij", size = 34.dp, iconSize = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) { state.suggestQuery = s.text + " " }
                        1 -> Icon(Icons.Rounded.History, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Icon(Icons.Rounded.Star, null, Modifier.padding(end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
