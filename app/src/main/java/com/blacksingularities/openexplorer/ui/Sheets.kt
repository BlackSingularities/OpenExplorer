package com.blacksingularities.openexplorer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.blacksingularities.openexplorer.MainActivity
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.browser.BrowserDialog
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.browser.Tab
import com.blacksingularities.openexplorer.data.Tile
import java.util.Locale

private class MenuAction(val icon: ImageVector, val label: String, val active: Boolean = false, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(state: BrowserState, onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val tab = state.current ?: return
    val page = !tab.showHome && tab.url.isNotEmpty()
    val bookmarked = page && state.store.bookmarks.any { it.url == tab.url }
    fun run(block: () -> Unit) = { onDismiss(); block() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tab.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (page) Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (page) {
                    FilledTonalIconButton(onClick = { state.store.toggleBookmark(tab.url, tab.title) }) {
                        Icon(if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder, "Zakładka")
                    }
                }
                FilledTonalIconButton(onClick = run { state.forward() }, enabled = tab.canGoForward && !tab.showHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Dalej")
                }
            }
            Spacer(Modifier.height(14.dp))
            val actions = buildList {
                add(MenuAction(Icons.Filled.Add, "Nowa karta", onClick = run { state.newTab() }))
                add(MenuAction(Icons.Filled.VisibilityOff, "Incognito", onClick = run { state.newTab(incognito = true) }))
                add(MenuAction(Icons.Filled.Bookmarks, "Zakładki", onClick = run { onNavigate("bookmarks") }))
                add(MenuAction(Icons.Filled.History, "Historia", onClick = run { onNavigate("history") }))
                add(MenuAction(Icons.Filled.Download, "Pobrane", onClick = run { onNavigate("downloads") }))
                if (page) {
                    add(MenuAction(Icons.Filled.Share, "Udostępnij", onClick = run { share(ctx, tab.url, tab.title) }))
                    add(MenuAction(Icons.Filled.FindInPage, "Znajdź", onClick = run { state.findMode = true }))
                    add(MenuAction(Icons.Filled.DesktopWindows, "Wersja desktop", tab.desktop, onClick = run { state.toggleDesktop() }))
                    add(MenuAction(Icons.AutoMirrored.Filled.MenuBook, "Czytnik", tab.readerMode, onClick = run { state.readerMode() }))
                    add(MenuAction(Icons.Filled.Translate, "Tłumacz", onClick = run {
                        val lang = Locale.getDefault().language
                        state.load(tab, "https://translate.google.com/translate?sl=auto&tl=$lang&u=" + java.net.URLEncoder.encode(tab.url, "UTF-8"))
                    }))
                    add(MenuAction(Icons.Filled.DashboardCustomize, "Do kafelków", onClick = run {
                        state.store.saveTile(Tile(title = tab.title.take(24).ifBlank { tab.host }, url = tab.url))
                        state.toast("Dodano do strony startowej")
                    }))
                    add(MenuAction(Icons.Filled.AddToHomeScreen, "Na ekran główny", onClick = run { addShortcut(ctx, tab) }))
                    add(MenuAction(Icons.Filled.Print, "Drukuj / PDF", onClick = run { print(ctx, tab) }))
                    add(MenuAction(Icons.Filled.Code, "Źródło strony", onClick = run { state.viewSource() }))
                    add(MenuAction(Icons.Filled.ContentCopy, "Kopiuj adres", onClick = run { copy(ctx, tab.url); state.toast("Skopiowano adres") }))
                }
                add(MenuAction(Icons.Filled.Settings, "Ustawienia", onClick = run { onNavigate("settings") }))
            }
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(actions) { a ->
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = a.onClick).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (a.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) { Icon(a.icon, null, tint = if (a.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
                        Text(a.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

fun share(ctx: Context, url: String, title: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title)
    ctx.startActivity(Intent.createChooser(i, "Udostępnij"))
}

fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", text))
}

private fun print(ctx: Context, tab: Tab) {
    val wv = tab.webView ?: return
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val name = tab.title.ifBlank { "Strona" }
    pm.print(name, wv.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
}

private fun addShortcut(ctx: Context, tab: Tab) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return
    val icon = tab.favicon?.let { IconCompat.createWithBitmap(it) } ?: IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)
    val info = ShortcutInfoCompat.Builder(ctx, "site-" + tab.url.hashCode())
        .setShortLabel(tab.title.ifBlank { tab.host }.take(24))
        .setIcon(icon)
        .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(tab.url)))
        .build()
    ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
}

// ---------------- Karty ----------------

@Composable
fun TabsScreen(state: BrowserState, onClose: () -> Unit) {
    val current = state.current
    var incognito by remember { mutableStateOf(current?.incognito == true) }
    var confirm by remember { mutableStateOf(false) }
    val list = state.tabs.filter { it.incognito == incognito }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Zamknij") }
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    SegmentedButton(!incognito, { incognito = false }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text("Karty (${state.tabs.count { !it.incognito }})")
                    }
                    SegmentedButton(incognito, { incognito = true }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text("Incognito (${state.tabs.count { it.incognito }})")
                    }
                }
                TextButton(onClick = { if (state.settings.confirmCloseAll && list.size > 1) confirm = true else { state.closeAll(incognito); onClose() } },
                    enabled = list.isNotEmpty()) { Text("Zamknij\nwszystkie", fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp) }
            }
            Box(Modifier.weight(1f)) {
                if (list.isEmpty()) {
                    Text(if (incognito) "Brak kart incognito" else "Brak kart", Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { t -> TabCard(t, t.id == current?.id, { state.select(t.id); onClose() }, { state.close(t.id) }) }
                }
            }
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { state.newTab(incognito = incognito); onClose() },
                    icon = { Icon(if (incognito) Icons.Filled.VisibilityOff else Icons.Filled.Add, null) },
                    text = { Text(if (incognito) "Nowa karta incognito" else "Nowa karta") },
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Zamknąć wszystkie karty?") },
            text = { Text("Zostanie zamkniętych ${list.size} kart.") },
            confirmButton = { TextButton(onClick = { confirm = false; state.closeAll(incognito); onClose() }) { Text("Zamknij") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.78f)
            .then(if (selected) Modifier.border(2.5.dp, colors.primary, RoundedCornerShape(18.dp)) else Modifier),
    ) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 2.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Favicon(tab, 16)
                Text(tab.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Close, "Zamknij kartę", Modifier.size(18.dp)) }
            }
            Box(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, bottom = 4.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface)) {
                val thumb = tab.thumbnail
                if (thumb != null && !tab.showHome) {
                    Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = Alignment.TopCenter)
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Favicon(tab, 36)
                        if (!tab.showHome) Text(tab.host, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp),
                            color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------- Okna dialogowe stron ----------------

@Composable
fun BrowserDialogs(state: BrowserState) {
    val ctx = LocalContext.current
    when (val d = state.dialog) {
        null -> Unit
        is BrowserDialog.Permission -> AlertDialog(
            onDismissRequest = { state.dialog = null; d.onResult(false) },
            title = { Text("Prośba o dostęp") },
            text = { Text("${d.origin} chce uzyskać dostęp: ${d.what.joinToString()}.") },
            confirmButton = { TextButton(onClick = { state.dialog = null; d.onResult(true) }) { Text("Zezwól") } },
            dismissButton = { TextButton(onClick = { state.dialog = null; d.onResult(false) }) { Text("Odmów") } },
        )
        is BrowserDialog.Download -> {
            var name by remember(d) { mutableStateOf(d.fileName) }
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                icon = { Icon(Icons.Filled.Download, null) },
                title = { Text("Pobrać plik?") },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it }, label = { Text("Nazwa pliku") }, singleLine = true)
                        val size = if (d.size > 0) formatSize(d.size) else "nieznany rozmiar"
                        Text("${android.net.Uri.parse(d.url).host} · $size", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { state.dialog = null; state.startDownload(d, name.ifBlank { d.fileName }) }) { Text("Pobierz") } },
                dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Anuluj") } },
            )
        }
        is BrowserDialog.LinkMenu -> {
            val target = d.link ?: d.image ?: ""
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text(target, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                text = {
                    Column {
                        fun item(icon: ImageVector, label: String, action: () -> Unit) = @Composable {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { state.dialog = null; action() }.padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(icon, null, Modifier.size(20.dp))
                                Text(label, Modifier.padding(start = 16.dp))
                            }
                        }
                        if (d.link != null) {
                            item(Icons.AutoMirrored.Filled.OpenInNew, "Otwórz w nowej karcie") { state.newTab(d.link, d.incognito, background = false, parent = state.current?.id) }()
                            item(Icons.Filled.Add, "Otwórz w tle") { state.newTab(d.link, d.incognito, background = true, parent = state.current?.id); state.toast("Otwarto w tle") }()
                            if (!d.incognito) item(Icons.Filled.VisibilityOff, "Otwórz w karcie incognito") { state.newTab(d.link, incognito = true) }()
                            item(Icons.Filled.ContentCopy, "Kopiuj adres linku") { copy(ctx, d.link); state.toast("Skopiowano") }()
                            item(Icons.Filled.Share, "Udostępnij link") { share(ctx, d.link, "") }()
                            item(Icons.Filled.Download, "Pobierz link") { state.download(d.link, d.incognito) }()
                        }
                        if (d.image != null) {
                            item(Icons.Filled.Image, "Otwórz obraz") { state.newTab(d.image, d.incognito, parent = state.current?.id) }()
                            item(Icons.Filled.Download, "Pobierz obraz") { state.download(d.image, d.incognito) }()
                            item(Icons.Filled.ContentCopy, "Kopiuj adres obrazu") { copy(ctx, d.image); state.toast("Skopiowano") }()
                        }
                    }
                },
                confirmButton = {},
            )
        }
        is BrowserDialog.Source -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text("Źródło strony", maxLines = 1) },
            text = {
                SelectionContainer {
                    Text(
                        d.html.take(300_000), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { copy(ctx, d.html); state.toast("Skopiowano źródło") }) { Text("Kopiuj") } },
            dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Zamknij") } },
        )
        is BrowserDialog.Ssl -> AlertDialog(
            onDismissRequest = { state.dialog = null; d.handler.cancel() },
            title = { Text("Połączenie nie jest prywatne") },
            text = { Text("${d.error}\n\nOsoby trzecie mogą próbować wykraść Twoje dane z ${android.net.Uri.parse(d.url).host}.") },
            confirmButton = { TextButton(onClick = { state.dialog = null; d.handler.cancel() }) { Text("Wróć do bezpieczeństwa") } },
            dismissButton = { TextButton(onClick = { state.dialog = null; d.handler.proceed() }) { Text("Kontynuuj mimo to", color = MaterialTheme.colorScheme.error) } },
        )
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
