package com.blacksingularities.openexplorer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale

private class MenuAction(val icon: ImageVector, val label: String, val active: Boolean = false, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(state: BrowserState, onDismiss: () -> Unit, onNavigate: (String) -> Unit, onEditTiles: () -> Unit) {
    val ctx = LocalContext.current
    val tab = state.current ?: return
    val page = !tab.showHome && tab.url.isNotEmpty()
    val bookmarked = page && state.store.bookmarks.any { it.url == tab.url }
    fun run(block: () -> Unit): () -> Unit = { onDismiss(); block() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 4.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline)) },
    ) {
        Column(Modifier.padding(horizontal = 12.dp).navigationBarsPadding()) {
            // Szybkie akcje strony w jednym rzędzie.
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", enabled = page && tab.canGoBack, onClick = run { state.back() })
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowForward, "Dalej", enabled = page && tab.canGoForward, onClick = run { state.forward() })
                RoundIcon(Icons.Rounded.Refresh, "Odśwież", enabled = page, onClick = run { state.reload() })
                RoundIcon(if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Zakładka", active = bookmarked, enabled = page) {
                    val added = state.store.toggleBookmark(tab.url, tab.title)
                    state.snack(if (added) "Dodano zakładkę" else "Usunięto zakładkę")
                }
                RoundIcon(Icons.Rounded.Share, "Udostępnij", enabled = page, onClick = run { share(ctx, tab.url, tab.title) })
            }
            if (page) {
                Text(tab.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            val actions = buildList {
                add(MenuAction(Icons.Rounded.Add, "Nowa karta", onClick = run { state.newTab() }))
                add(MenuAction(Icons.Rounded.VisibilityOff, "Incognito", onClick = run { state.newTab(incognito = true) }))
                add(MenuAction(Icons.Rounded.Bookmarks, "Zakładki", onClick = run { onNavigate("bookmarks") }))
                add(MenuAction(Icons.Rounded.History, "Historia", onClick = run { onNavigate("history") }))
                add(MenuAction(Icons.Rounded.Download, "Pobrane pliki", onClick = run { onNavigate("downloads") }))
                add(MenuAction(Icons.Rounded.DownloadForOffline, "Zapisane strony", onClick = run { onNavigate("offline") }))
                add(MenuAction(Icons.Rounded.Key, "Hasła", onClick = run { onNavigate("passwords") }))
                if (state.closed.isNotEmpty()) add(MenuAction(Icons.Rounded.RestorePage, "Przywróć kartę", onClick = run { state.reopenClosed() }))
                if (page) {
                    add(MenuAction(Icons.Rounded.FindInPage, "Znajdź na stronie", onClick = run { state.findMode = true }))
                    add(MenuAction(Icons.Rounded.DesktopWindows, "Wersja komputerowa", tab.desktop, onClick = run { state.toggleDesktop() }))
                    add(MenuAction(Icons.AutoMirrored.Rounded.MenuBook, "Tryb czytania", tab.readerMode, onClick = run { state.readerMode() }))
                    add(MenuAction(if (state.speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, if (state.speaking) "Przestań czytać" else "Czytaj na głos",
                        state.speaking, onClick = run { state.readAloud() }))
                    add(MenuAction(Icons.Rounded.FormatSize, "Wielkość tekstu", onClick = run { state.dialog = BrowserDialog.PageZoom(tab) }))
                    add(MenuAction(Icons.Rounded.Translate, "Przetłumacz", onClick = run {
                        val lang = Locale.getDefault().language
                        state.load(tab, "https://translate.google.com/translate?sl=auto&tl=$lang&u=" + java.net.URLEncoder.encode(tab.url, "UTF-8"))
                    }))
                    add(MenuAction(Icons.Rounded.DownloadForOffline, "Zapisz bez sieci", onClick = run { state.saveOffline() }))
                    add(MenuAction(Icons.Rounded.Screenshot, "Zrzut ekranu", onClick = run { state.screenshot() }))
                    add(MenuAction(Icons.Rounded.QrCode2, "Kod QR", onClick = run { state.dialog = BrowserDialog.Qr(tab.url, tab.title) }))
                    add(MenuAction(Icons.Rounded.DashboardCustomize, "Dodaj kafelek", onClick = run {
                        state.store.saveTile(Tile(title = tab.title.take(24).ifBlank { tab.host }, url = tab.url))
                        state.snack("Dodano do strony startowej")
                    }))
                    add(MenuAction(Icons.Rounded.AddToHomeScreen, "Skrót na pulpicie", onClick = run { addShortcut(ctx, tab) }))
                    add(MenuAction(Icons.Rounded.Print, "Drukuj / PDF", onClick = run { print(ctx, tab) }))
                    add(MenuAction(Icons.Rounded.Code, "Kod strony", onClick = run { state.viewSource() }))
                    add(MenuAction(Icons.Rounded.ContentCopy, "Kopiuj adres", onClick = run { copy(ctx, tab.url); state.snack("Skopiowano adres") }))
                } else {
                    add(MenuAction(Icons.Rounded.Wallpaper, "Edytuj kafelki", onClick = run { onEditTiles() }))
                }
                add(MenuAction(Icons.Rounded.Settings, "Ustawienia", onClick = run { onNavigate("settings") }))
                add(MenuAction(Icons.AutoMirrored.Rounded.ExitToApp, "Wyjdź", onClick = run { onNavigate("exit") }))
            }
            LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.heightIn(max = 480.dp).padding(top = 6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(actions) { a ->
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = a.onClick).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(50))
                                .background(if (a.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(a.icon, null, Modifier.size(20.dp),
                                tint = if (a.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                        Text(a.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
                            lineHeight = 13.sp, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp))
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

private fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

// ---------------- Karty ----------------

@Composable
fun TabsScreen(state: BrowserState, onClose: () -> Unit) {
    val current = state.current
    var incognito by remember { mutableStateOf(current?.incognito == true) }
    var confirm by remember { mutableStateOf(false) }
    val list = state.tabs.filter { it.incognito == incognito }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.Close, "Zamknij", onClick = onClose)
                Row(
                    Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
                ) {
                    listOf(false to "Karty · ${state.tabs.count { !it.incognito }}", true to "Incognito · ${state.tabs.count { it.incognito }}").forEach { (k, l) ->
                        val on = k == incognito
                        Text(
                            l, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { incognito = k }.padding(vertical = 8.dp),
                        )
                    }
                }
                if (state.closed.isNotEmpty() && !incognito) {
                    RoundIcon(Icons.Rounded.RestorePage, "Przywróć zamkniętą kartę") { state.reopenClosed() }
                }
                TextButton(
                    onClick = { if (state.settings.confirmCloseAll && list.size > 1) confirm = true else { state.closeAll(incognito); onClose() } },
                    enabled = list.isNotEmpty(),
                ) { Text("Zamknij\nwszystkie", fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp) }
            }
            Box(Modifier.weight(1f)) {
                if (list.isEmpty()) {
                    Text(if (incognito) "Brak kart incognito" else "Brak kart", Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { t ->
                        TabCard(t, t.id == current?.id, { state.select(t.id); onClose() }, { state.close(t.id) }, Modifier.animateItem())
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { state.newTab(incognito = incognito); onClose() },
            icon = { Icon(if (incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Add, null) },
            text = { Text(if (incognito) "Nowa incognito" else "Nowa karta") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
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
private fun TabCard(tab: Tab, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Przesunięcie karty w bok zamyka ją (jak w Chrome).
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .graphicsLayer {
                translationX = drag.value
                alpha = 1f - (abs(drag.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.85f
            }
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val w = size.width.toFloat()
                            if (abs(drag.value) > w * 0.35f) {
                                drag.animateTo(if (drag.value > 0) w * 1.2f else -w * 1.2f, tween(160))
                                onClose()
                            } else drag.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount) }
                }
            }
            .fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHigh)
            .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 10.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Favicon(tab, 15)
            Text(tab.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            RoundIcon(Icons.Rounded.Close, "Zamknij kartę", size = 32.dp, iconSize = 16.dp, onClick = onClose)
        }
        Box(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, bottom = 4.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface)) {
            val thumb = tab.thumbnail
            if (thumb != null && !tab.showHome) {
                Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Favicon(tab, 34)
                    if (!tab.showHome) Text(tab.host, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp),
                        color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// ---------------- Okna dialogowe stron ----------------

@Composable
private fun MenuItemRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(label, Modifier.padding(start = 16.dp))
    }
}

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
                icon = { Icon(Icons.Rounded.Download, null) },
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
            val parent = state.current?.id
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text(target, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                text = {
                    Column {
                        fun act(block: () -> Unit): () -> Unit = { state.dialog = null; block() }
                        if (d.link != null) {
                            MenuItemRow(Icons.AutoMirrored.Rounded.OpenInNew, "Otwórz w nowej karcie", act { state.newTab(d.link, d.incognito, parent = parent) })
                            MenuItemRow(Icons.Rounded.Add, "Otwórz w tle", act {
                                state.newTab(d.link, d.incognito, background = true, parent = parent)
                                state.snack("Otwarto w tle", "Przejdź") { state.tabs.lastOrNull { it.url == d.link }?.let { state.select(it.id) } }
                            })
                            if (!d.incognito) MenuItemRow(Icons.Rounded.VisibilityOff, "Otwórz w incognito", act { state.newTab(d.link, incognito = true) })
                            MenuItemRow(Icons.Rounded.ContentCopy, "Kopiuj adres linku", act { copy(ctx, d.link); state.snack("Skopiowano") })
                            MenuItemRow(Icons.Rounded.Share, "Udostępnij link", act { share(ctx, d.link, "") })
                            MenuItemRow(Icons.Rounded.QrCode2, "Kod QR linku", act { state.dialog = BrowserDialog.Qr(d.link, "") })
                            MenuItemRow(Icons.Rounded.Download, "Pobierz link", act { state.download(d.link, d.incognito) })
                        }
                        if (d.image != null) {
                            MenuItemRow(Icons.Rounded.Image, "Otwórz obraz", act { state.newTab(d.image, d.incognito, parent = parent) })
                            MenuItemRow(Icons.Rounded.Download, "Pobierz obraz", act { state.download(d.image, d.incognito) })
                            MenuItemRow(Icons.Rounded.ContentCopy, "Kopiuj adres obrazu", act { copy(ctx, d.image); state.snack("Skopiowano") })
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
            confirmButton = { TextButton(onClick = { copy(ctx, d.html); state.snack("Skopiowano źródło") }) { Text("Kopiuj") } },
            dismissButton = { TextButton(onClick = { state.dialog = null }) { Text("Zamknij") } },
        )
        is BrowserDialog.Ssl -> AlertDialog(
            onDismissRequest = { state.dialog = null; d.handler.cancel() },
            title = { Text("Połączenie nie jest prywatne") },
            text = { Text("${d.error}\n\nOsoby trzecie mogą próbować przechwycić dane z ${android.net.Uri.parse(d.url).host}.") },
            confirmButton = { TextButton(onClick = { state.dialog = null; d.handler.cancel() }) { Text("Wróć do bezpieczeństwa") } },
            dismissButton = { TextButton(onClick = { state.dialog = null; d.handler.proceed() }) { Text("Kontynuuj", color = MaterialTheme.colorScheme.error) } },
        )
        is BrowserDialog.Qr -> {
            val bmp = remember(d.url) { runCatching { qrBitmap(d.url) }.getOrNull() }
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text("Kod QR") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), "Kod QR", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                filterQuality = FilterQuality.None)
                        }
                        Text(d.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { state.dialog = null }) { Text("Zamknij") } },
                dismissButton = { TextButton(onClick = { copy(ctx, d.url); state.snack("Skopiowano adres") }) { Text("Kopiuj adres") } },
            )
        }
        is BrowserDialog.PageZoom -> {
            val tab = d.tab
            val initial = state.store.rule(tab.pageHost)?.textZoom?.takeIf { it > 0 } ?: state.settings.textZoom
            var zoom by remember(tab.id) { mutableFloatStateOf(initial.toFloat()) }
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text("Rozmiar tekstu · ${zoom.toInt()}%") },
                text = {
                    Column {
                        Slider(zoom, { zoom = it; tab.webView?.settings?.textZoom = it.toInt() }, valueRange = 50f..250f, steps = 19)
                        Text("Zapamiętywany dla ${tab.host}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { state.setPageZoom(tab, zoom.toInt()); state.dialog = null }) { Text("Zapisz") } },
                dismissButton = {
                    TextButton(onClick = { state.setPageZoom(tab, state.settings.textZoom); state.dialog = null }) { Text("Domyślny") }
                },
            )
        }
        is BrowserDialog.SiteInfo -> SiteInfoSheet(state, d.tab)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteInfoSheet(state: BrowserState, tab: Tab) {
    val host = tab.pageHost?.removePrefix("www.") ?: tab.host
    val rule = state.store.rule(host) ?: com.blacksingularities.openexplorer.data.SiteRule(host)
    ModalBottomSheet(
        onDismissRequest = { state.dialog = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteLogo(tab.url, tab.title, 0L, "auto", "", "hd", false, Modifier.size(36.dp), RoundedCornerShape(10.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(host, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            tab.secure -> "Połączenie szyfrowane (HTTPS)"
                            tab.url.startsWith("http://") -> "Połączenie nieszyfrowane"
                            else -> "Strona lokalna"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tab.url.startsWith("http://")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.settings.adBlock) {
                Text("Zablokowano elementów: ${tab.blocked}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 10.dp))
            }
            SectionTitle("Ustawienia tej strony")
            ToggleRow("Blokuj reklamy", null, rule.adBlock) { v -> state.updateRule(tab) { it.copy(adBlock = v) } }
            ToggleRow("JavaScript", null, rule.javascript) { v -> state.updateRule(tab) { it.copy(javascript = v) } }
            ToggleRow("Zawsze wersja komputerowa", null, rule.desktop) { v -> state.updateRule(tab) { it.copy(desktop = v) } }
            ToggleRow("Ciemny wygląd", "Gdy włączony jest ciemny motyw", rule.darkMode) { v -> state.updateRule(tab) { it.copy(darkMode = v) } }
            val saved = state.passwords.forHost(host).count { it.host == host }
            if (saved > 0) {
                Text("Zapisane loginy dla tej strony: $saved (menu → Hasła)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            ClickRow("Wyczyść ciasteczka i dane strony", "Wylogowuje z $host") { state.clearSiteData(tab); state.dialog = null }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
