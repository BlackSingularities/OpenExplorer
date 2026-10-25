package com.blacksingularities.openexplorer.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.HistoryEntry
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store
import com.blacksingularities.openexplorer.data.Tile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val TILE_COLORS = listOf(
    0L, 0xFF4285F4, 0xFFEA4335, 0xFFFBBC05, 0xFF34A853, 0xFF7C5CFF, 0xFFFF5A00, 0xFF00A884,
    0xFFD6336C, 0xFF24292F, 0xFF0EA5E9, 0xFF8D6E63, 0xFF000000, 0xFFFFFFFF,
)

fun hostOf(url: String): String = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

/** Źródła logo strony, od najlepszego: apple-touch-icon (pełne logo), Google 256 px, DuckDuckGo. */
fun logoSources(url: String, quality: String): List<String> {
    val raw = runCatching { Uri.parse(url).host }.getOrNull() ?: return emptyList()
    val host = hostOf(url)
    return when (quality) {
        "small" -> listOf("https://www.google.com/s2/favicons?domain=$host&sz=64")
        "letter" -> emptyList()
        else -> listOf(
            "https://$raw/apple-touch-icon.png",
            "https://www.google.com/s2/favicons?domain=$host&sz=256",
            "https://icons.duckduckgo.com/ip3/$host.ico",
        )
    }
}

fun tileShape(corner: Int): Shape = RoundedCornerShape(corner.coerceIn(0, 50))

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

fun wallpaperFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")
fun tileIconDir(ctx: Context) = File(ctx.filesDir, "tile-icons").apply { mkdirs() }

/** Skopiowanie obrazu wybranego z galerii do plików aplikacji. */
fun copyImage(ctx: Context, uri: Uri, target: File): Boolean = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
}.getOrDefault(false)

@Composable
fun HomePage(
    settings: Settings,
    store: Store,
    incognito: Boolean,
    editMode: Boolean,
    onEditMode: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String, Boolean) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Tile?>(null) }
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.4f
    val wallpaper = if (incognito) "none" else settings.homeWallpaper
    val onBg = when (wallpaper) {
        "solid" -> if (Color(settings.homeWallpaperColor).luminance() < 0.45f) Color.White else Color.Black
        "image" -> Color.White
        else -> colors.onBackground
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        when (wallpaper) {
            "gradient" -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background))))
            "aurora" -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0xFF0F1B33), Color(0xFF181430), Color(0xFF0E1F22))
                        else listOf(Color(0xFFE7EEFF), Color(0xFFF3EAFE), Color(0xFFE6F7F2)),
                    ),
                ),
            )
            "solid" -> Box(Modifier.fillMaxSize().background(Color(settings.homeWallpaperColor)))
            "image" -> {
                val file = wallpaperFile(ctx)
                if (file.exists()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(ctx).data(file)
                            .memoryCacheKey("wallpaper-${settings.wallpaperVersion}")
                            .diskCacheKey("wallpaper-${settings.wallpaperVersion}").build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(
                            if (settings.homeWallpaperBlur > 0) Modifier.blur(settings.homeWallpaperBlur.dp) else Modifier,
                        ),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.homeWallpaperDim / 100f)))
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minH = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = true),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = minH).padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (settings.homeAlign == "center" && !editMode) Arrangement.Center else Arrangement.Top,
                ) {
                    if (editMode) EditBanner(onBg) { onEditMode(false) }
                    if (incognito) {
                        Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(40.dp), tint = onBg.copy(alpha = 0.85f))
                        Text(stringResource(R.string.menu_incognito), style = MaterialTheme.typography.titleLarge, color = onBg, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            stringResource(R.string.home_incognito_hint),
                            color = onBg.copy(alpha = 0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp).widthIn(max = 340.dp),
                        )
                    } else {
                        if (settings.homeClock) Clock(onBg, settings.homeClockSize)
                        if (settings.homeLogo) {
                            Text("OpenExplorer", color = onBg, fontSize = 26.sp, fontWeight = FontWeight.Light,
                                letterSpacing = (-0.5).sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                        }
                        if (settings.homeClock || settings.homeLogo) Spacer(Modifier.height(18.dp))
                    }
                    when (settings.homeSearchStyle) {
                        "pill" -> SearchPill(settings, onBg, filled = true, onSearchClick)
                        "line" -> SearchPill(settings, onBg, filled = false, onSearchClick)
                    }
                    if (settings.homeSearchStyle != "none") Spacer(Modifier.height(26.dp))

                    TileGrid(settings, store, onBg, editMode, onEditMode, onOpen, onOpenNewTab, onEdit = { editing = it })

                    val showRecent = settings.homeRecent && !incognito
                    val recent by remember(showRecent, settings.homeRecentCount) {
                        if (showRecent) store.recentSites(settings.homeRecentCount) else flowOf(emptyList())
                    }.collectAsState(emptyList())
                    if (showRecent && !editMode && recent.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Column(Modifier.fillMaxWidth().widthIn(max = settings.homeWidth.dp)) {
                            recent.forEach { h ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(h.url) }
                                        .padding(horizontal = 6.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SiteLogo(h.url, h.title, 0L, "auto", "", settings.logoSource, false, Modifier.size(22.dp), RoundedCornerShape(6.dp))
                                    Text(h.title.ifBlank { hostOf(h.url) }, color = onBg.copy(alpha = 0.85f), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { tile ->
        TileEditor(
            tile = tile,
            store = store,
            settings = settings,
            isNew = store.tiles.none { it.id == tile.id },
            onDismiss = { editing = null },
            onSave = { store.saveTile(it); editing = null },
        )
    }
}

@Composable
private fun EditBanner(onBg: Color, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(RoundedCornerShape(50))
            .background(onBg.copy(alpha = 0.08f)).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_reorder_hint), color = onBg.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onDone) { Text(stringResource(R.string.home_done), fontWeight = FontWeight.Bold, color = onBg) }
    }
}

@Composable
private fun SearchPill(settings: Settings, onBg: Color, filled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = settings.homeWidth.dp).height(48.dp).clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (filled) Arrangement.Start else Arrangement.Center,
    ) {
        val c = if (filled) MaterialTheme.colorScheme.onSurfaceVariant else onBg.copy(alpha = 0.6f)
        Icon(Icons.Rounded.Search, null, tint = c, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.home_search_hint), color = c, modifier = Modifier.padding(start = 10.dp), maxLines = 1,
            style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Clock(color: Color, size: Int) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = color, fontSize = size.sp, fontWeight = FontWeight.Thin)
    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), color = color.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileGrid(
    settings: Settings,
    store: Store,
    onBg: Color,
    editMode: Boolean,
    onEditMode: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String, Boolean) -> Unit,
    onEdit: (Tile) -> Unit,
) {
    val cols = settings.homeColumns.coerceIn(1, 12)
    val limit = if (settings.homeAllTiles || editMode) Int.MAX_VALUE else cols * settings.homeRows.coerceIn(1, 30)
    val tiles = store.tiles.take(limit)
    val showAdd = editMode || (settings.tileAddButton && store.tiles.size < limit)
    val requestedSpacing = settings.tileSpacing.dp
    val density = LocalDensity.current

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var cellHeightPx by remember { mutableIntStateOf(1) }

    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = settings.homeWidth.dp)) {
        // Odstępy nie mogą zabrać więcej niż połowy szerokości (inaczej komórki miałyby ujemny rozmiar).
        val spacing = if (cols > 1) minOf(requestedSpacing, maxWidth / (2 * (cols - 1))) else requestedSpacing
        val cellW = ((maxWidth - spacing * (cols - 1)) / cols).coerceAtLeast(1.dp)
        val cellWPx = with(density) { (cellW + spacing).toPx() }
        val rowStepPx = cellHeightPx + with(density) { spacing.toPx() }
        val count = tiles.size + if (showAdd) 1 else 0
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { index ->
                        val tile = tiles.getOrNull(index)
                        val dragging = index == dragIndex
                        Box(
                            Modifier.width(cellW)
                                .onSizeChanged { if (it.height > 0) cellHeightPx = it.height }
                                .zIndex(if (dragging) 1f else 0f)
                                .offset { if (dragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else IntOffset.Zero }
                                .graphicsLayer { if (dragging) { scaleX = 1.08f; scaleY = 1.08f } }
                                .then(
                                    if (editMode && tile != null) Modifier.pointerInput(index, tiles.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragIndex = index; dragOffset = Offset.Zero },
                                            onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                            onDragEnd = {
                                                val dx = (dragOffset.x / cellWPx).roundToInt()
                                                val dy = (dragOffset.y / rowStepPx).roundToInt()
                                                val target = (index + dx + dy * cols).coerceIn(0, tiles.lastIndex)
                                                store.moveTile(index, target)
                                                dragIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = { dragIndex = -1; dragOffset = Offset.Zero },
                                        )
                                    } else Modifier,
                                ),
                        ) {
                            if (tile == null) AddTile(settings, onBg) { onEdit(Tile(title = "", url = "")) }
                            else TileView(tile, settings, store, onBg, editMode, onEditMode, onOpen, onOpenNewTab, onEdit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTile(settings: Settings, onBg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatioSquare().clip(tileShape(settings.tileCorner)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize(settings.tileIconScale.coerceIn(25, 100) / 100f * 0.7f).clip(CircleShape).background(onBg.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, stringResource(R.string.menu_add_tile), tint = onBg.copy(alpha = 0.55f)) }
        }
        if (settings.tileLabels) Text(" ", fontSize = settings.tileLabelSize.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun Modifier.aspectRatioSquare() = this.aspectRatio(1f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    settings: Settings,
    store: Store,
    onBg: Color,
    editMode: Boolean,
    onEditMode: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String, Boolean) -> Unit,
    onEdit: (Tile) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val shape = tileShape(settings.tileCorner)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatioSquare().clip(shape)
                .then(if (settings.tileBackground) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)) else Modifier)
                .combinedClickable(
                    onClick = { if (editMode) onEdit(tile) else onOpen(tile.url) },
                    onLongClick = if (editMode) null else ({ haptic(); menu = true }),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val scale = settings.tileIconScale.coerceIn(25, 100) / 100f
            SiteLogo(
                tile.url, tile.title, tile.color, tile.iconMode, tile.iconUrl, settings.logoSource, settings.tileGrayscale,
                Modifier.fillMaxSize(scale),
                if (settings.tileClip || settings.tileBackground) tileShape(if (settings.tileBackground) settings.tileCorner / 2 else settings.tileCorner)
                else RoundedCornerShape(0),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.common_open_new_tab)) }, onClick = { menu = false; onOpenNewTab(tile.url, false) })
                DropdownMenuItem(text = { Text(stringResource(R.string.link_open_incognito)) }, onClick = { menu = false; onOpenNewTab(tile.url, true) })
                DropdownMenuItem(text = { Text(stringResource(R.string.common_edit)) }, onClick = { menu = false; onEdit(tile) })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_arrange_tiles)) }, onClick = { menu = false; onEditMode(true) })
                DropdownMenuItem(text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; store.removeTile(tile.id) })
            }
            if (editMode) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { store.removeTile(tile.id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, stringResource(R.string.common_delete), Modifier.size(14.dp)) }
            }
        }
        if (settings.tileLabels) {
            Text(
                tile.title.ifBlank { hostOf(tile.url) }, color = onBg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = settings.tileLabelSize.sp, modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/**
 * Logo strony. Tryby: auto (najlepsze dostępne logo z kolejnych źródeł), letter (litera na kolorze),
 * custom (adres obrazka albo plik wybrany z galerii).
 */
@Composable
fun SiteLogo(
    url: String,
    title: String,
    color: Long,
    mode: String,
    customIcon: String,
    quality: String,
    gray: Boolean,
    modifier: Modifier,
    shape: Shape,
) {
    val sources = remember(url, mode, customIcon, quality) {
        when {
            mode == "letter" || url.isBlank() -> emptyList()
            mode == "custom" && customIcon.isNotBlank() -> listOf(customIcon) + logoSources(url, quality)
            else -> logoSources(url, quality)
        }
    }
    var index by remember(sources) { mutableIntStateOf(0) }
    val filter = if (gray) grayscale else null
    if (index >= sources.size) {
        val c = if (color != 0L) Color(color) else MaterialTheme.colorScheme.surfaceContainerHighest
        Box(modifier.clip(shape).background(c), contentAlignment = Alignment.Center) {
            Text(
                title.ifBlank { hostOf(url) }.take(1).uppercase(),
                color = if (c.luminance() > 0.55f) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            )
        }
        return
    }
    val src = sources[index]
    AsyncImage(
        model = if (src.startsWith("/")) File(src) else src,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        colorFilter = filter,
        onError = { index++ },
        onSuccess = { s ->
            // Pomijamy miniaturowe ikony (np. domyślny glob 16 px) – lepsze logo może być w kolejnym źródle.
            val d = s.result.drawable
            if (d.intrinsicWidth in 1..31 && index < sources.lastIndex) index++
        },
        modifier = modifier.clip(shape),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileEditor(tile: Tile, store: Store, settings: Settings, isNew: Boolean, onDismiss: () -> Unit, onSave: (Tile) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(tile.title) }
    var url by remember { mutableStateOf(tile.url) }
    var color by remember { mutableStateOf(tile.color) }
    var mode by remember { mutableStateOf(tile.iconMode) }
    var icon by remember { mutableStateOf(tile.iconUrl) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val f = File(tileIconDir(ctx), tile.id + "-" + System.currentTimeMillis() + ".img")
            if (copyImage(ctx, uri, f)) {
                icon = f.absolutePath
                mode = "custom"
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.home_new_tile else R.string.home_edit_tile)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    key(normalizeUrl(url), mode, icon) {
                        SiteLogo(normalizeUrl(url), title, color, mode, icon, settings.logoSource, false, Modifier.size(64.dp),
                            tileShape(settings.tileCorner))
                    }
                }
                OutlinedTextField(
                    url, { url = it }, label = { Text(stringResource(R.string.library_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.home_tile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val topSites by produceState(emptyList<HistoryEntry>(), isNew) { if (isNew) value = store.topSites(8) }
                if (isNew && url.isBlank()) {
                    val suggestions = (topSites.map { it.url to it.title } + store.bookmarks.take(6).map { it.url to it.title })
                        .distinctBy { hostOf(it.first) }.filter { s -> store.tiles.none { hostOf(it.url) == hostOf(s.first) } }.take(10)
                    if (suggestions.isNotEmpty()) {
                        Text(stringResource(R.string.home_frequent), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { (u, t) ->
                                Text(
                                    hostOf(u), style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { url = u; if (title.isBlank()) title = t.take(20) }.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
                ChipsRowCompact(
                    listOf(
                        "auto" to stringResource(R.string.home_icon_auto),
                        "letter" to stringResource(R.string.home_icon_letter),
                        "custom" to stringResource(R.string.home_icon_custom),
                    ),
                    mode,
                ) { mode = it }
                if (mode == "custom") {
                    OutlinedTextField(
                        if (icon.startsWith("/")) "" else icon, { icon = it }, label = { Text(stringResource(R.string.home_icon_url)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(if (icon.startsWith("/")) R.string.home_icon_picked else R.string.home_icon_pick))
                    }
                }
                if (mode == "letter") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TILE_COLORS.forEach { c ->
                            val col = if (c == 0L) MaterialTheme.colorScheme.surfaceContainerHighest else Color(c)
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(col).clickable { color = c },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (color == c) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp),
                                    tint = if (col.luminance() > 0.55f) Color.Black else Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = {
                    val u = normalizeUrl(url)
                    onSave(
                        tile.copy(
                            title = title.ifBlank { hostOf(u).substringBefore('.').replaceFirstChar { it.uppercase() } },
                            url = u, color = color, iconMode = mode, iconUrl = icon.trim(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ChipsRowCompact(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            Text(
                label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onPick(k) }.padding(vertical = 8.dp),
            )
        }
    }
}

fun normalizeUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return t
    return if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else "https://$t"
}
