package com.blacksingularities.openexplorer.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store
import com.blacksingularities.openexplorer.data.Tile
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TILE_COLORS = listOf(
    0L, 0xFF4285F4, 0xFFEA4335, 0xFFFBBC05, 0xFF34A853, 0xFF7C5CFF, 0xFFFF5A00, 0xFF00A884,
    0xFFD6336C, 0xFF24292F, 0xFF0EA5E9, 0xFF8D6E63,
)

fun hostOf(url: String): String = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

fun faviconUrl(url: String): String = "https://www.google.com/s2/favicons?domain=${hostOf(url)}&sz=128"

@Composable
fun tileShape(settings: Settings): Shape = when (settings.tileShape) {
    "circle" -> CircleShape
    "square" -> RoundedCornerShape(6.dp)
    else -> RoundedCornerShape(22.dp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(
    settings: Settings,
    store: Store,
    incognito: Boolean,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    var editing by remember { mutableStateOf<Tile?>(null) }
    val bg = when {
        incognito -> Brush.verticalGradient(listOf(Color(0xFF221A33), MaterialTheme.colorScheme.background))
        settings.homeWallpaper == "gradient" -> Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background),
        )
        settings.homeWallpaper == "aurora" -> Brush.linearGradient(
            if (dark) listOf(Color(0xFF0F1B33), Color(0xFF181430), Color(0xFF0E1F22))
            else listOf(Color(0xFFE7EEFF), Color(0xFFF3EAFE), Color(0xFFE6F7F2)),
        )
        settings.homeWallpaper == "solid" -> Brush.verticalGradient(
            listOf(Color(settings.homeWallpaperColor), Color(settings.homeWallpaperColor)),
        )
        else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
    }
    val onBg = if (settings.homeWallpaper == "solid" && !incognito) {
        if (Color(settings.homeWallpaperColor).luminance() < 0.45f) Color.White else Color.Black
    } else MaterialTheme.colorScheme.onBackground

    Box(modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            if (incognito) {
                Icon(Icons.Filled.VisibilityOff, null, Modifier.size(48.dp), tint = onBg.copy(alpha = 0.8f))
                Spacer(Modifier.height(10.dp))
                Text("Karta incognito", style = MaterialTheme.typography.headlineSmall, color = onBg, fontWeight = FontWeight.SemiBold)
                Text(
                    "Historia, ciasteczka i dane formularzy z tej karty nie zostaną zapisane po jej zamknięciu.",
                    color = onBg.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp).widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(28.dp))
            } else {
                if (settings.homeClock) Clock(onBg)
                if (settings.homeLogo) {
                    Text(
                        "OpenExplorer", color = onBg, fontSize = 30.sp, fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
                    )
                } else Spacer(Modifier.height(18.dp))
            }
            if (settings.homeSearchBar) {
                Surface(
                    onClick = onSearchClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).height(54.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Szukaj w ${settings.engine.name} lub wpisz adres",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }

            TileGrid(settings, store, onBg, onOpen, onOpenNewTab, onEdit = { editing = it })

            if (settings.homeRecent && !incognito && store.history.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                    Text("Ostatnio odwiedzane", color = onBg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    store.history.distinctBy { hostOf(it.url) }.take(4).forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(h.url) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteIcon(h.url, h.title, 0L, "auto", "", Modifier.size(28.dp), RoundedCornerShape(8.dp))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(h.title.ifBlank { hostOf(h.url) }, color = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(hostOf(h.url), color = onBg.copy(alpha = 0.6f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    editing?.let { tile ->
        TileEditor(
            tile = tile,
            isNew = store.tiles.none { it.id == tile.id },
            onDismiss = { editing = null },
            onSave = { store.saveTile(it); editing = null },
        )
    }
}

@Composable
private fun Clock(color: Color) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = color, fontSize = 56.sp, fontWeight = FontWeight.Thin)
    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), color = color.copy(alpha = 0.7f),
        style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileGrid(
    settings: Settings,
    store: Store,
    onBg: Color,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onEdit: (Tile) -> Unit,
) {
    val cols = settings.homeColumns.coerceIn(2, 8)
    val max = cols * settings.homeRows.coerceIn(1, 10)
    val visible = store.tiles.take(max)
    val showAdd = store.tiles.size < max
    val cells: List<Tile?> = visible + if (showAdd) listOf(null) else emptyList()
    val shape = tileShape(settings)
    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cells.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    Box(Modifier.weight(1f)) {
                        if (tile == null) AddTile(shape, onBg) { onEdit(Tile(title = "", url = "")) }
                        else TileView(tile, settings, store, shape, onBg, onOpen, onOpenNewTab, onEdit)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AddTile(shape: Shape, onBg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(shape)
                .border(1.5.dp, onBg.copy(alpha = 0.25f), shape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "Dodaj kafelek", tint = onBg.copy(alpha = 0.6f)) }
        Text("Dodaj", color = onBg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    settings: Settings,
    store: Store,
    shape: Shape,
    onBg: Color,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onEdit: (Tile) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(shape)
                .background(if (settings.tileBackground) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f) else Color.Transparent)
                .combinedClickable(onClick = { onOpen(tile.url) }, onLongClick = { menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            val scale = settings.tileIconScale.coerceIn(30, 100) / 100f
            SiteIcon(tile.url, tile.title, tile.color, tile.iconMode, tile.iconUrl, Modifier.fillMaxSize(scale),
                if (settings.tileBackground) RoundedCornerShape(12.dp) else shape)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Otwórz w nowej karcie") }, onClick = { menu = false; onOpenNewTab(tile.url) })
                DropdownMenuItem(text = { Text("Edytuj") }, onClick = { menu = false; onEdit(tile) })
                val i = store.tiles.indexOf(tile)
                if (i > 0) DropdownMenuItem(text = { Text("Przesuń wcześniej") }, onClick = { menu = false; store.moveTile(i, i - 1) })
                if (i < store.tiles.lastIndex) DropdownMenuItem(text = { Text("Przesuń dalej") }, onClick = { menu = false; store.moveTile(i, i + 1) })
                if (i > 0) DropdownMenuItem(text = { Text("Na początek") }, onClick = { menu = false; store.moveTile(i, 0) })
                DropdownMenuItem(text = { Text("Usuń", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; store.removeTile(tile.id) })
            }
        }
        if (settings.tileLabels) {
            Text(
                tile.title.ifBlank { hostOf(tile.url) }, color = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Logo strony: własny adres ikony, automatyczne logo albo litera na kolorowym tle. */
@Composable
fun SiteIcon(url: String, title: String, color: Long, mode: String, customIcon: String, modifier: Modifier, shape: Shape) {
    val letter = @Composable {
        val c = if (color != 0L) Color(color) else MaterialTheme.colorScheme.primary
        Box(modifier.clip(shape).background(c), contentAlignment = Alignment.Center) {
            Text(
                (title.ifBlank { hostOf(url) }).take(1).uppercase(),
                color = if (c.luminance() > 0.6f) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            )
        }
    }
    if (mode == "letter" || url.isBlank()) {
        letter()
        return
    }
    SubcomposeAsyncImage(
        model = if (mode == "custom" && customIcon.isNotBlank()) customIcon else faviconUrl(url),
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(shape),
        loading = { letter() },
        error = { letter() },
    )
}

@Composable
fun TileEditor(tile: Tile, isNew: Boolean, onDismiss: () -> Unit, onSave: (Tile) -> Unit) {
    var title by remember { mutableStateOf(tile.title) }
    var url by remember { mutableStateOf(tile.url) }
    var color by remember { mutableStateOf(tile.color) }
    var mode by remember { mutableStateOf(tile.iconMode) }
    var icon by remember { mutableStateOf(tile.iconUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nowy kafelek" else "Edytuj kafelek") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SiteIcon(normalizeUrl(url), title, color, mode, icon, Modifier.size(64.dp), RoundedCornerShape(16.dp))
                }
                OutlinedTextField(title, { title = it }, label = { Text("Nazwa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it }, label = { Text("Adres") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(),
                )
                Text("Ikona", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("auto" to "Logo", "letter" to "Litera", "custom" to "Własna").forEachIndexed { i, (k, l) ->
                        SegmentedButton(mode == k, { mode = k }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                    }
                }
                if (mode == "custom") {
                    OutlinedTextField(icon, { icon = it }, label = { Text("Adres obrazka (PNG/SVG/ICO)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
                Text("Kolor", style = MaterialTheme.typography.labelLarge)
                TILE_COLORS.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            val col = if (c == 0L) MaterialTheme.colorScheme.primary else Color(c)
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(col).clickable { color = c },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (color == c) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                else if (c == 0L) Text("A", color = Color.White, fontSize = 12.sp)
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
                    onSave(tile.copy(title = title.ifBlank { hostOf(u).substringBefore('.').replaceFirstChar { it.uppercase() } },
                        url = u, color = color, iconMode = mode, iconUrl = icon.trim()))
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

fun normalizeUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return t
    return if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else "https://$t"
}
