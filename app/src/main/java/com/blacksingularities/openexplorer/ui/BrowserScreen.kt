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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.browser.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class Suggestion(val text: String, val url: String?, val kind: Int) // 0 zapytanie, 1 historia, 2 zakładka

@Composable
fun BrowserScreen(
    state: BrowserState,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val tab = state.current ?: return
    val settings = state.settings
    var editing by remember { mutableStateOf(false) }
    val bottom = settings.addressBarBottom
    val barsShown = state.barsVisible || editing || tab.showHome

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!bottom) {
            Box(Modifier.statusBarsPadding())
            AnimatedVisibility(barsShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Toolbar(state, tab, editing, { editing = it }, onOpenTabs, onOpenMenu, bottom = false)
            }
        } else Box(Modifier.statusBarsPadding())

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // WebView karty (pozostaje w drzewie także pod stroną startową, by zachować stan).
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
                    onOpen = { state.load(tab, it) },
                    onOpenNewTab = { state.newTab(it, tab.incognito, background = settings.linksInBackground) },
                    onSearchClick = { editing = true },
                )
            }
            if (editing) {
                SuggestionsPanel(state, tab, onPick = { editing = false; state.navigate(it, tab) }, bottom = bottom)
            }
            if (state.findMode) {
                FindBar(state, tab, Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter))
            }
        }

        if (bottom) {
            AnimatedVisibility(barsShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Toolbar(state, tab, editing, { editing = it }, onOpenTabs, onOpenMenu, bottom = true)
            }
        }
        Box(Modifier.navigationBarsPadding().imePadding())
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
    bottom: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, tonalElevation = 0.dp) {
        Column {
            if (bottom) Progress(tab, top = true)
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!editing) {
                    IconButton(onClick = { state.goHome(tab) }) { Icon(Icons.Filled.Home, "Strona startowa") }
                }
                AddressField(state, tab, editing, setEditing, Modifier.weight(1f))
                if (!editing) {
                    if (state.settings.showTabCounter) {
                        IconButton(onClick = onOpenTabs) { TabCounter(state.tabs.count { it.incognito == tab.incognito }, tab.incognito) }
                    }
                    IconButton(onClick = onOpenMenu) { Icon(Icons.Filled.MoreVert, "Menu") }
                }
            }
            if (!bottom) Progress(tab, top = false)
        }
    }
}

@Composable
private fun Progress(tab: Tab, top: Boolean) {
    Box(Modifier.fillMaxWidth().height(2.dp)) {
        if (tab.loading && !tab.showHome) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = Color.Transparent,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun TabCounter(count: Int, incognito: Boolean) {
    Box(
        Modifier.size(22.dp).border(1.8.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (incognito && count == 0) Icon(Icons.Filled.VisibilityOff, null, Modifier.size(12.dp))
        else Text(if (count > 99) ":D" else "$count", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember { mutableStateOf(TextFieldValue("")) }

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

    Surface(
        shape = CircleShape,
        color = if (tab.incognito) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        modifier = modifier.height(44.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                IconButton(onClick = { setEditing(false) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Anuluj", Modifier.size(20.dp))
                }
                Box(Modifier.weight(1f).padding(start = 4.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            state.suggestQuery = it.text
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = {
                            setEditing(false)
                            state.navigate(value.text, tab)
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (value.text.isEmpty()) {
                        Text("Szukaj lub wpisz adres", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                }
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = { value = TextFieldValue(""); state.suggestQuery = "" }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, "Wyczyść", Modifier.size(20.dp))
                    }
                }
            } else {
                Row(
                    Modifier.weight(1f).fillMaxSize().clip(CircleShape).clickable { setEditing(true); state.suggestQuery = "" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        tab.showHome -> Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        tab.incognito -> Icon(Icons.Filled.VisibilityOff, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        tab.secure -> Icon(Icons.Filled.Lock, "Połączenie szyfrowane", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        tab.url.startsWith("http://") -> Icon(Icons.Filled.Warning, "Połączenie nieszyfrowane", Modifier.size(18.dp), tint = colors.error)
                        else -> Icon(Icons.Filled.Public, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    }
                    Text(
                        if (tab.showHome) "Szukaj lub wpisz adres" else tab.host.ifEmpty { tab.url },
                        color = if (tab.showHome) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    if (!tab.showHome && tab.blocked > 0 && state.settings.adBlock) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 2.dp)) {
                            Icon(Icons.Filled.Shield, "Zablokowane elementy", Modifier.size(14.dp), tint = colors.primary)
                            Text("${tab.blocked}", fontSize = 11.sp, color = colors.primary, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
                if (!tab.showHome) {
                    IconButton(onClick = { state.reload() }, modifier = Modifier.size(36.dp)) {
                        Icon(if (tab.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (tab.loading) "Zatrzymaj" else "Odśwież",
                            Modifier.size(20.dp))
                    }
                }
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
        if (query.isBlank() || !state.settings.searchSuggestions || tab.incognito) return@LaunchedEffect
        delay(180)
        remote = fetchSuggestions(query, state.settings.searchEngine)
    }
    val q = query.trim().lowercase()
    val local = if (q.isEmpty()) {
        state.store.history.distinctBy { it.url }.take(8).map { Suggestion(it.title.ifBlank { it.url }, it.url, 1) }
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(reverseLayout = bottom) {
            items(items) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(s.url ?: s.text) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (s.kind) { 1 -> Icons.Filled.History; 2 -> Icons.Filled.Star; else -> Icons.Filled.Search },
                        null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(s.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        if (s.url != null) Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (s.kind == 0) {
                        IconButton(onClick = { state.suggestQuery = s.text + " " }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.NorthWest, "Uzupełnij", Modifier.size(18.dp))
                        }
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
    Surface(modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
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
            IconButton(onClick = { state.findNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, "Poprzedni") }
            IconButton(onClick = { state.findNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, "Następny") }
            IconButton(onClick = { state.closeFind() }) { Icon(Icons.Filled.Close, "Zamknij") }
        }
    }
}

@Composable
fun Favicon(tab: Tab, size: Int = 18) {
    val fav = tab.favicon
    if (fav != null) {
        Image(fav.asImageBitmap(), null, Modifier.size(size.dp).clip(RoundedCornerShape(4.dp)))
    } else if (tab.showHome || tab.url.isEmpty()) {
        Icon(if (tab.incognito) Icons.Filled.VisibilityOff else Icons.Filled.Home, null, Modifier.size(size.dp))
    } else {
        SiteIcon(tab.url, tab.title, 0L, "auto", "", Modifier.size(size.dp), RoundedCornerShape(4.dp))
    }
}

