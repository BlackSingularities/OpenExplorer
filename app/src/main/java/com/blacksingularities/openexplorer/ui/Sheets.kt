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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.rounded.Layers
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.blacksingularities.openexplorer.data.Container
import com.blacksingularities.openexplorer.data.SiteRule
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
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), enabled = page && tab.canGoBack, onClick = run { state.back() })
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.menu_forward), enabled = page && tab.canGoForward, onClick = run { state.forward() })
                RoundIcon(Icons.Rounded.Refresh, stringResource(R.string.menu_reload), enabled = page, onClick = run { state.reload() })
                RoundIcon(if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.menu_bookmark), active = bookmarked, enabled = page) {
                    val added = state.store.toggleBookmark(tab.url, tab.title)
                    state.snack(ctx.getString(if (added) R.string.menu_bookmark_added else R.string.menu_bookmark_removed))
                }
                RoundIcon(Icons.Rounded.Share, stringResource(R.string.common_share), enabled = page, onClick = run { share(ctx, tab.url, tab.title) })
            }
            if (page) {
                Text(tab.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Text(tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            val actions = buildList {
                add(MenuAction(Icons.Rounded.Add, stringResource(R.string.menu_new_tab), onClick = run { state.newTab() }))
                add(MenuAction(Icons.Rounded.VisibilityOff, stringResource(R.string.menu_incognito), onClick = run { state.newTab(incognito = true) }))
                add(MenuAction(Icons.Rounded.Bookmarks, stringResource(R.string.library_bookmarks), onClick = run { onNavigate("bookmarks") }))
                add(MenuAction(Icons.Rounded.History, stringResource(R.string.library_history), onClick = run { onNavigate("history") }))
                add(MenuAction(Icons.Rounded.Download, stringResource(R.string.menu_downloads), onClick = run { onNavigate("downloads") }))
                add(MenuAction(Icons.Rounded.DownloadForOffline, stringResource(R.string.menu_offline), onClick = run { onNavigate("offline") }))
                add(MenuAction(Icons.Rounded.Key, stringResource(R.string.passwords_title), onClick = run { onNavigate("passwords") }))
                if (state.closed.isNotEmpty()) add(MenuAction(Icons.Rounded.RestorePage, stringResource(R.string.menu_restore_tab), onClick = run { state.reopenClosed() }))
                if (page) {
                    add(MenuAction(Icons.Rounded.FindInPage, stringResource(R.string.menu_find), onClick = run { state.findMode = true }))
                    add(MenuAction(Icons.Rounded.DesktopWindows, stringResource(R.string.menu_desktop), tab.desktop, onClick = run { state.toggleDesktop() }))
                    add(MenuAction(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.menu_reader), tab.readerMode, onClick = run { state.readerMode() }))
                    add(MenuAction(if (state.tools.speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, stringResource(if (state.tools.speaking) R.string.menu_stop_reading else R.string.menu_read_aloud),
                        state.tools.speaking, onClick = run { state.tools.readAloud() }))
                    add(MenuAction(Icons.Rounded.FormatSize, stringResource(R.string.menu_text_size), onClick = run { state.dialog = BrowserDialog.PageZoom(tab) }))
                    add(MenuAction(Icons.Rounded.Translate, stringResource(R.string.menu_translate), onClick = run {
                        val lang = Locale.getDefault().language
                        state.load(tab, "https://translate.google.com/translate?sl=auto&tl=$lang&u=" + java.net.URLEncoder.encode(tab.url, "UTF-8"))
                    }))
                    add(MenuAction(Icons.Rounded.DownloadForOffline, stringResource(R.string.menu_save_offline), onClick = run { state.tools.saveOffline() }))
                    add(MenuAction(Icons.Rounded.Screenshot, stringResource(R.string.menu_screenshot), onClick = run { state.tools.screenshot() }))
                    add(MenuAction(Icons.Rounded.QrCode2, stringResource(R.string.menu_qr), onClick = run { state.dialog = BrowserDialog.Qr(tab.url, tab.title) }))
                    add(MenuAction(Icons.Rounded.DashboardCustomize, stringResource(R.string.menu_add_tile), onClick = run {
                        state.store.saveTile(Tile(title = tab.title.take(24).ifBlank { tab.host }, url = tab.url))
                        state.snack(ctx.getString(R.string.menu_tile_added))
                    }))
                    add(MenuAction(Icons.Rounded.AddToHomeScreen, stringResource(R.string.menu_shortcut), onClick = run { addShortcut(ctx, tab) }))
                    add(MenuAction(Icons.Rounded.Print, stringResource(R.string.menu_print), onClick = run { print(ctx, tab) }))
                    add(MenuAction(Icons.Rounded.Code, stringResource(R.string.source_title_default), onClick = run { state.source.viewPage() }))
                    add(MenuAction(Icons.Rounded.ContentCopy, stringResource(R.string.common_copy_address), onClick = run { copy(ctx, tab.url); state.snack(ctx.getString(R.string.common_address_copied)) }))
                } else {
                    add(MenuAction(Icons.Rounded.Wallpaper, stringResource(R.string.menu_edit_tiles), onClick = run { onEditTiles() }))
                }
                add(MenuAction(Icons.Rounded.Settings, stringResource(R.string.menu_settings), onClick = run { onNavigate("settings") }))
                add(MenuAction(Icons.AutoMirrored.Rounded.ExitToApp, stringResource(R.string.menu_exit), onClick = run { onNavigate("exit") }))
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
    ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.common_share)))
}

fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", text))
}

private fun print(ctx: Context, tab: Tab) {
    val wv = tab.webView ?: return
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val name = tab.title.ifBlank { ctx.getString(R.string.menu_print_default_name) }
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

/** Etykieta kontenera: zaokrąglony znacznik w kolorze kontenera z jego nazwą. */
@Composable
fun ContainerLabel(c: Container, modifier: Modifier = Modifier) {
    val bg = Color(c.color)
    Text(
        c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
        color = if (bg.luminance() > 0.55f) Color.Black else Color.White,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun TabsScreen(state: BrowserState, onClose: () -> Unit) {
    val current = state.current
    var incognito by remember { mutableStateOf(current?.incognito == true) }
    var confirm by remember { mutableStateOf(false) }
    val containers = if (state.multiProfile) state.store.containers.toList() else emptyList()
    // Filtr kontenera zwykłych kart: null = wszystkie, "" = bez kontenera, inaczej id kontenera.
    var box by remember { mutableStateOf<String?>(null) }
    val boxFilter = if (incognito) null else box?.takeIf { id -> id.isEmpty() || containers.any { it.id == id } }
    val list = state.tabs.filter { it.incognito == incognito && (boxFilter == null || it.containerId == boxFilter) }
    val newIn = boxFilter?.let { id -> containers.firstOrNull { it.id == id } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.Close, stringResource(R.string.common_close), onClick = onClose)
                Row(
                    Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
                ) {
                    listOf(
                        false to stringResource(R.string.tabs_normal, state.tabs.count { !it.incognito }),
                        true to stringResource(R.string.tabs_incognito, state.tabs.count { it.incognito }),
                    ).forEach { (k, l) ->
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
                    RoundIcon(Icons.Rounded.RestorePage, stringResource(R.string.tabs_restore_closed)) { state.reopenClosed() }
                }
                TextButton(
                    onClick = { if (state.settings.confirmCloseAll && list.size > 1) confirm = true else { state.closeAll(incognito, boxFilter); onClose() } },
                    enabled = list.isNotEmpty(),
                ) { Text(stringResource(R.string.tabs_close_all_short), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp) }
            }
            if (!incognito && containers.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.tabs_all), "" to stringResource(R.string.tabs_no_container)) +
                        containers.map { it.id to it.name }
                    options.forEach { (id, label) ->
                        val on = id == boxFilter
                        val c = containers.firstOrNull { it.id == id }
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                                .border(1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                                .clickable { box = id }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (c != null) Box(Modifier.padding(end = 6.dp).size(8.dp).clip(RoundedCornerShape(50)).background(Color(c.color)))
                            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (list.isEmpty()) {
                    Text(stringResource(if (incognito) R.string.tabs_empty_incognito else R.string.tabs_empty), Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { t ->
                        TabCard(
                            t, t.id == current?.id, containers.firstOrNull { it.id == t.containerId },
                            { state.select(t.id); onClose() }, { state.close(t.id) }, Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { state.newTab(incognito = incognito, container = newIn?.id.orEmpty()); onClose() },
            icon = { Icon(if (incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Add, null) },
            text = {
                Text(
                    when {
                        incognito -> stringResource(R.string.tabs_new_incognito)
                        newIn != null -> stringResource(R.string.tabs_new_in_container, newIn.name)
                        else -> stringResource(R.string.menu_new_tab)
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.tabs_close_all_title)) },
            text = { Text(pluralStringResource(R.plurals.tabs_close_all_text, list.size, list.size)) },
            confirmButton = { TextButton(onClick = { confirm = false; state.closeAll(incognito, boxFilter); onClose() }) { Text(stringResource(R.string.common_close)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, container: Container?, onClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
            Text(tab.displayTitle.ifEmpty { stringResource(R.string.menu_new_tab) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            RoundIcon(Icons.Rounded.Close, stringResource(R.string.tabs_close_tab), size = 32.dp, iconSize = 16.dp, onClick = onClose)
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
            if (container != null) ContainerLabel(container, Modifier.align(Alignment.BottomStart).padding(6.dp))
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
            title = { Text(stringResource(R.string.dialog_permission_title)) },
            text = { Text(stringResource(R.string.dialog_permission_text, d.origin, d.what.joinToString())) },
            confirmButton = { TextButton(onClick = { state.dialog = null; d.onResult(true) }) { Text(stringResource(R.string.dialog_allow)) } },
            dismissButton = { TextButton(onClick = { state.dialog = null; d.onResult(false) }) { Text(stringResource(R.string.dialog_deny)) } },
        )
        is BrowserDialog.Download -> {
            var name by remember(d) { mutableStateOf(d.fileName) }
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                icon = { Icon(Icons.Rounded.Download, null) },
                title = { Text(stringResource(R.string.dialog_download_title)) },
                text = {
                    Column {
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.dialog_file_name)) }, singleLine = true)
                        val size = if (d.size > 0) formatSize(d.size) else stringResource(R.string.dialog_unknown_size)
                        Text("${android.net.Uri.parse(d.url).host} · $size", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { state.dialog = null; state.downloads.start(d, name.ifBlank { d.fileName }) }) { Text(stringResource(R.string.dialog_download)) } },
                dismissButton = { TextButton(onClick = { state.dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
        is BrowserDialog.LinkMenu -> {
            val target = d.link ?: d.image ?: ""
            val parent = state.current?.id
            val profile = state.profileOf(state.current)
            // Rozwinięta lista kontenerów pod „Otwórz w kontenerze”.
            var pickContainer by remember(d) { mutableStateOf(false) }
            val containers = if (state.multiProfile && !d.incognito) state.store.containers.toList() else emptyList()
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text(target, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        fun act(block: () -> Unit): () -> Unit = { state.dialog = null; block() }
                        if (d.link != null) {
                            MenuItemRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.common_open_new_tab), act { state.newTab(d.link, d.incognito, parent = parent, container = d.container) })
                            MenuItemRow(Icons.Rounded.Add, stringResource(R.string.link_open_background), act {
                                state.newTab(d.link, d.incognito, background = true, parent = parent, container = d.container)
                                state.snack(ctx.getString(R.string.link_opened_background), ctx.getString(R.string.source_go)) { state.tabs.lastOrNull { it.url == d.link }?.let { state.select(it.id) } }
                            })
                            if (!d.incognito) MenuItemRow(Icons.Rounded.VisibilityOff, stringResource(R.string.link_open_incognito), act { state.newTab(d.link, incognito = true) })
                            if (containers.isNotEmpty()) {
                                MenuItemRow(Icons.Rounded.Layers, stringResource(R.string.link_open_container)) { pickContainer = !pickContainer }
                                if (pickContainer) {
                                    // Bez kontenera tylko wtedy, gdy link pochodzi z karty w kontenerze.
                                    val targets = (if (d.container.isNotEmpty()) listOf<Container?>(null) else emptyList()) +
                                        containers.filter { it.id != d.container }
                                    targets.forEach { c ->
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .clickable(onClick = act { state.newTab(d.link, container = c?.id.orEmpty()) })
                                                .padding(start = 36.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (c != null) ContainerLabel(c) else Text(stringResource(R.string.tabs_no_container))
                                        }
                                    }
                                }
                            }
                            MenuItemRow(Icons.Rounded.ContentCopy, stringResource(R.string.link_copy), act { copy(ctx, d.link); state.snack(ctx.getString(R.string.common_copied)) })
                            MenuItemRow(Icons.Rounded.Share, stringResource(R.string.link_share), act { share(ctx, d.link, "") })
                            MenuItemRow(Icons.Rounded.QrCode2, stringResource(R.string.link_qr), act { state.dialog = BrowserDialog.Qr(d.link, "") })
                            MenuItemRow(Icons.Rounded.Download, stringResource(R.string.link_download), act { state.downloads.download(d.link, d.incognito, profile) })
                        }
                        if (d.image != null) {
                            MenuItemRow(Icons.Rounded.Image, stringResource(R.string.image_open), act { state.newTab(d.image, d.incognito, parent = parent, container = d.container) })
                            MenuItemRow(Icons.Rounded.Download, stringResource(R.string.image_download), act { state.downloads.download(d.image, d.incognito, profile) })
                            MenuItemRow(Icons.Rounded.ContentCopy, stringResource(R.string.image_copy), act { copy(ctx, d.image); state.snack(ctx.getString(R.string.common_copied)) })
                        }
                    }
                },
                confirmButton = {},
            )
        }
        is BrowserDialog.Ssl -> AlertDialog(
            onDismissRequest = { state.dialog = null; d.handler.cancel() },
            title = { Text(stringResource(R.string.ssl_title)) },
            text = { Text(stringResource(R.string.ssl_text, d.error, android.net.Uri.parse(d.url).host.orEmpty())) },
            confirmButton = { TextButton(onClick = { state.dialog = null; d.handler.cancel() }) { Text(stringResource(R.string.ssl_back)) } },
            dismissButton = { TextButton(onClick = { state.dialog = null; d.handler.proceed() }) { Text(stringResource(R.string.ssl_continue), color = MaterialTheme.colorScheme.error) } },
        )
        is BrowserDialog.HttpsFallback -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text(stringResource(R.string.https_fallback_title)) },
            text = {
                Text(stringResource(R.string.https_fallback_text, d.reason, android.net.Uri.parse(d.httpUrl).host.orEmpty()))
            },
            confirmButton = { TextButton(onClick = { state.dialog = null }) { Text(stringResource(R.string.https_fallback_cancel)) } },
            dismissButton = {
                TextButton(onClick = { state.dialog = null; state.openInsecure(d.tab, d.httpUrl) }) {
                    Text(stringResource(R.string.https_fallback_open), color = MaterialTheme.colorScheme.error)
                }
            },
        )
        is BrowserDialog.Qr -> {
            val bmp = remember(d.url) { runCatching { qrBitmap(d.url) }.getOrNull() }
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text(stringResource(R.string.menu_qr)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), stringResource(R.string.menu_qr), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                filterQuality = FilterQuality.None)
                        }
                        Text(d.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { state.dialog = null }) { Text(stringResource(R.string.common_close)) } },
                dismissButton = { TextButton(onClick = { copy(ctx, d.url); state.snack(ctx.getString(R.string.common_address_copied)) }) { Text(stringResource(R.string.common_copy_address)) } },
            )
        }
        is BrowserDialog.PageZoom -> {
            val tab = d.tab
            val initial = state.store.rule(tab.pageHost)?.textZoom?.takeIf { it > 0 } ?: state.settings.textZoom
            var zoom by remember(tab.id) { mutableFloatStateOf(initial.toFloat()) }
            AlertDialog(
                onDismissRequest = { state.dialog = null },
                title = { Text(stringResource(R.string.zoom_title, zoom.toInt())) },
                text = {
                    Column {
                        Slider(zoom, { zoom = it; tab.webView?.settings?.textZoom = it.toInt() }, valueRange = 50f..250f, steps = 19)
                        Text(stringResource(R.string.zoom_remembered, tab.host), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { state.setPageZoom(tab, zoom.toInt()); state.dialog = null }) { Text(stringResource(R.string.common_save)) } },
                dismissButton = {
                    TextButton(onClick = { state.setPageZoom(tab, state.settings.textZoom); state.dialog = null }) { Text(stringResource(R.string.zoom_default)) }
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
    val rule = state.store.rule(host) ?: SiteRule(host)
    // Otwarty edytor własnego kodu: "css", "js" albo null.
    var code by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = { state.dialog = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteLogo(tab.url, tab.title, 0L, "auto", "", "hd", false, Modifier.size(36.dp), RoundedCornerShape(10.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(host, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            tab.secure -> stringResource(R.string.site_secure)
                            tab.url.startsWith("http://") -> stringResource(R.string.site_insecure)
                            else -> stringResource(R.string.site_local)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tab.url.startsWith("http://")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.store.container(tab.containerId)?.let { ContainerLabel(it, Modifier.padding(top = 4.dp)) }
                }
            }
            if (state.settings.adBlock) {
                Text(stringResource(R.string.site_blocked, tab.blocked), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 10.dp))
            }
            SectionTitle(stringResource(R.string.site_settings))
            ToggleRow(stringResource(R.string.site_ad_block), null, rule.adBlock) { v -> state.updateRule(tab) { it.copy(adBlock = v) } }
            ToggleRow(stringResource(R.string.site_javascript), null, rule.javascript) { v -> state.updateRule(tab) { it.copy(javascript = v) } }
            ToggleRow(stringResource(R.string.site_desktop), null, rule.desktop) { v -> state.updateRule(tab) { it.copy(desktop = v) } }
            ToggleRow(stringResource(R.string.site_dark), stringResource(R.string.site_dark_hint), rule.darkMode) { v -> state.updateRule(tab) { it.copy(darkMode = v) } }
            SectionTitle(stringResource(R.string.site_custom_code))
            val none = stringResource(R.string.site_code_none)
            val on = stringResource(R.string.site_code_on)
            ClickRow(stringResource(R.string.site_custom_css), stringResource(R.string.site_custom_css_hint), value = if (rule.css.isBlank()) none else on) { code = "css" }
            ClickRow(stringResource(R.string.site_custom_js), stringResource(R.string.site_custom_js_hint), value = if (rule.js.isBlank()) none else on) { code = "js" }
            if (tab.incognito && (rule.css.isNotBlank() || rule.js.isNotBlank())) {
                Text(stringResource(R.string.site_code_incognito), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            val saved = state.passwords.forHost(host).count { it.host == host }
            if (saved > 0) {
                Text(stringResource(R.string.site_saved_logins, saved), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            ClickRow(stringResource(R.string.site_clear), stringResource(R.string.site_clear_hint, host)) { state.clearSiteData(tab); state.dialog = null }
        }
    }
    code?.let { kind -> CustomCodeDialog(kind == "js", host, if (kind == "js") rule.js else rule.css, onDismiss = { code = null }) { text ->
        state.updateRule(tab) { if (kind == "js") it.copy(js = text) else it.copy(css = text) }
        code = null
    } }
}

/** Edytor własnego CSS albo skryptu domeny; przy skrypcie wyraźne ostrzeżenie o dostępie do strony. */
@Composable
private fun CustomCodeDialog(js: Boolean, host: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(js) { mutableStateOf(initial) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (js) R.string.site_custom_js else R.string.site_custom_css)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (js) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp))
                            .background(colors.errorContainer).padding(12.dp),
                    ) {
                        Text(stringResource(R.string.site_js_warning_title), color = colors.onErrorContainer,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.site_js_warning, host), color = colors.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                OutlinedTextField(
                    text, { text = it.take(SiteRule.MAX_CODE) },
                    Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    placeholder = {
                        Text(if (js) "document.querySelector('.baner')?.remove();" else ".baner { display: none !important; }",
                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                Text(stringResource(R.string.site_code_applies, host), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
