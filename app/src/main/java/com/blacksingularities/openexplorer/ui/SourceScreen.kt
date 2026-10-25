package com.blacksingularities.openexplorer.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material.icons.rounded.Wysiwyg
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.browser.SourceResource
import com.blacksingularities.openexplorer.browser.SourceTools
import com.blacksingularities.openexplorer.browser.SourceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Przygotowany do wyświetlenia kod: tekst (być może sformatowany), tokeny kolorowania i wiersze. */
private class Prepared(val text: String, val tokens: IntArray, val rows: SourceTools.Rows, val longest: Int)

private class CodeColors(
    val tag: Color, val attr: Color, val value: Color, val comment: Color, val doctype: Color,
    val keyword: Color, val string: Color, val number: Color, val entity: Color,
    val match: Color, val currentMatch: Color, val gutter: Color, val gutterText: Color,
)

@Composable
private fun codeColors(): CodeColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val cs = MaterialTheme.colorScheme
    return if (dark) CodeColors(
        tag = Color(0xFF7FB4FF), attr = Color(0xFFB5CEA8), value = Color(0xFFF2C38B), comment = Color(0xFF7C8B7C),
        doctype = Color(0xFFA9A9C8), keyword = Color(0xFFD7A6FF), string = Color(0xFFF2C38B), number = Color(0xFF9FE3C6),
        entity = Color(0xFFFF9EA8), match = Color(0x66FFD54F), currentMatch = Color(0xFFFF9800),
        gutter = cs.surfaceContainer, gutterText = cs.onSurfaceVariant.copy(alpha = 0.7f),
    ) else CodeColors(
        tag = Color(0xFF1A56C4), attr = Color(0xFF8A3FB0), value = Color(0xFFB0451B), comment = Color(0xFF5E7D5E),
        doctype = Color(0xFF6B6B8F), keyword = Color(0xFF8E24AA), string = Color(0xFFB0451B), number = Color(0xFF00796B),
        entity = Color(0xFFC2185B), match = Color(0x66FFC107), currentMatch = Color(0xFFFF9800),
        gutter = cs.surfaceContainer, gutterText = cs.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

/** Pełnoekranowy podgląd kodu strony: kolorowanie, numery linii, szukanie, formatowanie, zasoby i nagłówki. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceScreen(state: BrowserState, view: SourceView) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { state.source.close() }

    // Wybrana zakładka: "server", "dom", "resources", "headers"
    var tab by rememberSaveable(view) { mutableStateOf("server") }
    var wrap by rememberSaveable { mutableStateOf(false) }
    var pretty by rememberSaveable { mutableStateOf(false) }
    var colorize by rememberSaveable { mutableStateOf(true) }
    var numbers by rememberSaveable { mutableStateOf(true) }
    var fontSize by rememberSaveable { mutableIntStateOf(12) }
    var searching by remember(view) { mutableStateOf(false) }
    var query by remember(view) { mutableStateOf("") }
    var matchIndex by remember(view) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var goToLine by remember { mutableStateOf(false) }

    val raw: String? = when (tab) {
        "dom" -> view.dom
        else -> view.server
    }
    val kind = remember(view, view.info) { SourceTools.kindOf(view.url, view.info?.contentType ?: "") }
    val canFormat = kind == "html"

    // Ciężka praca (formatowanie, tokeny, wiersze) poza wątkiem głównym.
    var prepared by remember(view, tab) { mutableStateOf<Prepared?>(null) }
    LaunchedEffect(raw, pretty, wrap, colorize, kind) {
        val text = raw ?: return@LaunchedEffect
        prepared = withContext(Dispatchers.Default) {
            val t = if (pretty && canFormat) SourceTools.format(text) else text
            val rows = SourceTools.rows(t, if (wrap) 160 else SourceTools.CHUNK)
            var longest = 0
            for (i in 0 until rows.size) longest = maxOf(longest, rows.end[i] - rows.start[i])
            Prepared(t, if (colorize) SourceTools.tokenize(t, kind) else IntArray(0), rows, longest)
        }
    }
    val p = prepared

    var matches by remember(view) { mutableStateOf(IntArray(0)) }
    LaunchedEffect(p, query) {
        val text = p?.text ?: return@LaunchedEffect
        matches = withContext(Dispatchers.Default) { SourceTools.find(text, query) }
        matchIndex = 0
    }

    val listState = rememberLazyListState()
    fun scrollToRow(row: Int) = scope.launch { listState.animateScrollToItem((row - 3).coerceAtLeast(0)) }
    fun jumpToMatch(i: Int) {
        if (matches.isEmpty() || p == null) return
        matchIndex = ((i % matches.size) + matches.size) % matches.size
        scrollToRow(p.rows.rowAt(matches[matchIndex]))
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Pasek górny
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) { state.source.close() }
            if (searching) {
                TextField(
                    query, { query = it }, placeholder = { Text(stringResource(R.string.source_search_hint)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { jumpToMatch(matchIndex + 1) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    Text(
                        if (matches.isEmpty()) "0" else "${matchIndex + 1}/${matches.size}${if (matches.size >= 10_000) "+" else ""}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RoundIcon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.source_prev), enabled = matches.isNotEmpty()) { jumpToMatch(matchIndex - 1) }
                RoundIcon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.source_next), enabled = matches.isNotEmpty()) { jumpToMatch(matchIndex + 1) }
                RoundIcon(Icons.Rounded.Close, stringResource(R.string.source_close_search)) { searching = false; query = "" }
            } else {
                val defaultTitle = stringResource(R.string.source_title_default)
                val linesLabel = if (p != null) {
                    val lines = p.rows.line[p.rows.size - 1]
                    pluralStringResource(R.plurals.source_lines, lines, lines)
                } else null
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        view.title.ifBlank { defaultTitle }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(
                            hostOf(view.url),
                            linesLabel,
                            view.info?.let { formatSize(it.size) },
                        ).joinToString(" · "),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tab == "server" || tab == "dom") RoundIcon(Icons.Rounded.Search, stringResource(R.string.source_search_hint)) { searching = true }
                Box {
                    RoundIcon(Icons.Rounded.MoreVert, stringResource(R.string.common_more)) { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        @Composable
                        fun item(icon: ImageVector, label: String, checked: Boolean? = null, enabled: Boolean = true, onClick: () -> Unit) =
                            DropdownMenuItem(
                                text = { Text(label) }, enabled = enabled,
                                leadingIcon = { Icon(icon, null) },
                                trailingIcon = checked?.let { { androidx.compose.material3.Checkbox(it, null) } },
                                onClick = { if (checked == null) menu = false; onClick() },
                            )
                        if (canFormat) item(Icons.Rounded.FormatAlignLeft, stringResource(R.string.source_format), pretty) { pretty = !pretty }
                        item(Icons.AutoMirrored.Rounded.WrapText, stringResource(R.string.source_wrap), wrap) { wrap = !wrap }
                        item(Icons.Rounded.Palette, stringResource(R.string.source_colorize), colorize) { colorize = !colorize }
                        item(Icons.Rounded.FormatListNumbered, stringResource(R.string.source_line_numbers), numbers) { numbers = !numbers }
                        HorizontalDivider()
                        item(Icons.Rounded.TextIncrease, stringResource(R.string.source_text_bigger), enabled = fontSize < 22) { fontSize++ }
                        item(Icons.Rounded.TextDecrease, stringResource(R.string.source_text_smaller), enabled = fontSize > 8) { fontSize-- }
                        HorizontalDivider()
                        item(Icons.Rounded.VerticalAlignTop, stringResource(R.string.source_to_top), enabled = p != null) { scope.launch { listState.scrollToItem(0) } }
                        item(Icons.Rounded.VerticalAlignBottom, stringResource(R.string.source_to_bottom), enabled = p != null) {
                            scope.launch { listState.scrollToItem(((p?.rows?.size ?: 1) - 1).coerceAtLeast(0)) }
                        }
                        item(Icons.Rounded.FormatListNumbered, stringResource(R.string.source_go_to_line_menu), enabled = p != null) { goToLine = true }
                        HorizontalDivider()
                        item(Icons.Rounded.ContentCopy, stringResource(R.string.source_copy_all), enabled = p != null) {
                            p?.let {
                                if (it.text.length > 1_000_000) state.snack(ctx.getString(R.string.source_too_big))
                                else { copy(ctx, it.text); state.snack(ctx.getString(R.string.source_copied)) }
                            }
                        }
                        item(Icons.Rounded.Share, stringResource(R.string.source_share), enabled = p != null) {
                            p?.let { shareText(ctx, it.text.take(500_000), view.title) }
                        }
                        item(Icons.Rounded.ContentCopy, stringResource(R.string.common_copy_address)) { copy(ctx, view.url); state.snack(ctx.getString(R.string.common_address_copied)) }
                        item(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.common_open_new_tab)) {
                            state.source.view = null
                            state.newTab(view.url, view.incognito)
                        }
                    }
                }
            }
        }

        // Zakładki
        val serverLabel = stringResource(R.string.source_tab_server)
        val domLabel = stringResource(R.string.source_tab_dom)
        val resourcesLabel = if (view.resources.isNotEmpty()) stringResource(R.string.source_tab_resources_count, view.resources.size)
        else stringResource(R.string.source_tab_resources)
        val headersLabel = stringResource(R.string.source_tab_headers)
        val tabs = buildList {
            add("server" to serverLabel)
            if (view.hasDom) add("dom" to domLabel)
            if (view.hasDom) add("resources" to resourcesLabel)
            add("headers" to headersLabel)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            items(tabs, key = { it.first }) { (key, label) ->
                FilterChip(tab == key, onClick = { tab = key; searching = false; query = "" }, label = { Text(label) })
            }
        }
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "resources" -> ResourceList(view.resources, onOpen = { r ->
                    if (r.type == "image") { state.source.view = null; state.newTab(r.url, view.incognito) } else state.source.viewResource(r.url)
                }, onCopy = { copy(ctx, it.url); state.snack(ctx.getString(R.string.common_address_copied)) })
                "headers" -> HeadersView(view)
                else -> when {
                    tab == "server" && view.serverError != null -> Message(view.serverError!!, if (view.hasDom) stringResource(R.string.source_see_dom) else null) { tab = "dom" }
                    p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    p.text.isEmpty() -> Message(stringResource(R.string.source_empty), null) {}
                    else -> CodeView(p, listState, wrap, numbers, fontSize, matches, matchIndex, query.length)
                }
            }
            if (tab == "server" && view.info?.truncated == true) {
                Text(
                    stringResource(R.string.source_truncated, formatSize(com.blacksingularities.openexplorer.browser.SourceViewer.MAX_SOURCE.toLong())),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (goToLine && p != null) {
        var input by remember { mutableStateOf("") }
        val last = p.rows.line[p.rows.size - 1]
        AlertDialog(
            onDismissRequest = { goToLine = false },
            title = { Text(stringResource(R.string.source_go_to_line)) },
            text = {
                OutlinedTextField(
                    input, { input = it.filter(Char::isDigit).take(9) }, singleLine = true,
                    label = { Text(stringResource(R.string.source_line_number, last)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    goToLine = false
                    input.toIntOrNull()?.let { scrollToRow(p.rows.rowOfLine(it.coerceIn(1, last))) }
                }) { Text(stringResource(R.string.source_go)) }
            },
            dismissButton = { TextButton(onClick = { goToLine = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CodeView(
    p: Prepared,
    listState: androidx.compose.foundation.lazy.LazyListState,
    wrap: Boolean,
    numbers: Boolean,
    fontSize: Int,
    matches: IntArray,
    matchIndex: Int,
    matchLength: Int,
) {
    val colors = codeColors()
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize * 1.45f).sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(fontSize) { measurer.measure("0000000000", style).size.width / 10f }
    val digits = p.rows.line[p.rows.size - 1].toString().length
    val gutterWidth = with(density) { (charWidth * digits).toDp() } + 18.dp
    val hScroll = rememberScrollState()
    val contentWidth = with(density) { (charWidth * (p.longest + 2)).toDp() } + (if (numbers) gutterWidth else 0.dp) + 16.dp

    SelectionContainer {
        Box(Modifier.fillMaxSize().then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier = Modifier.fillMaxHeight().then(if (wrap) Modifier.fillMaxWidth() else Modifier.width(contentWidth)),
            ) {
                items(p.rows.size) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        if (numbers) {
                            Box(
                                Modifier.zIndex(1f)
                                    .graphicsLayer { if (!wrap) translationX = hScroll.value.toFloat() }
                                    .width(gutterWidth).background(colors.gutter).padding(end = 8.dp),
                            ) {
                                androidx.compose.foundation.text.selection.DisableSelection {
                                    Text(
                                        if (p.rows.continuation[row]) "↪" else p.rows.line[row].toString(),
                                        style = style.copy(color = colors.gutterText, textAlign = TextAlign.End),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        Text(
                            remember(p, row, colors, matches, matchIndex) { rowText(p, row, colors, matches, matchIndex, matchLength) },
                            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
                            softWrap = wrap,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Jeden wiersz z kolorami składni i podświetleniem wyników szukania. */
private fun rowText(p: Prepared, row: Int, c: CodeColors, matches: IntArray, matchIndex: Int, matchLength: Int): AnnotatedString {
    val a = p.rows.start[row]
    val b = p.rows.end[row]
    val text = p.text
    return buildAnnotatedString {
        append(text, a, b)
        val tokens = p.tokens
        var t = SourceTools.firstTokenAfter(tokens, a)
        while (t * 3 < tokens.size && tokens[t * 3] < b) {
            val s = maxOf(tokens[t * 3], a) - a
            val e = minOf(tokens[t * 3 + 1], b) - a
            val span = when (tokens[t * 3 + 2]) {
                SourceTools.TAG -> SpanStyle(color = c.tag)
                SourceTools.ATTR -> SpanStyle(color = c.attr)
                SourceTools.VALUE -> SpanStyle(color = c.value)
                SourceTools.COMMENT -> SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
                SourceTools.DOCTYPE -> SpanStyle(color = c.doctype)
                SourceTools.KEYWORD -> SpanStyle(color = c.keyword, fontWeight = FontWeight.SemiBold)
                SourceTools.STRING -> SpanStyle(color = c.string)
                SourceTools.NUMBER -> SpanStyle(color = c.number)
                SourceTools.ENTITY -> SpanStyle(color = c.entity)
                else -> null
            }
            if (span != null && e > s) addStyle(span, s, e)
            t++
        }
        if (matchLength > 0 && matches.isNotEmpty()) {
            // Pierwsze wystąpienie, które może zachodzić na ten wiersz.
            var lo = 0
            var hi = matches.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (matches[mid] + matchLength <= a) lo = mid + 1 else hi = mid
            }
            var m = lo
            while (m < matches.size && matches[m] < b) {
                val s = maxOf(matches[m], a) - a
                val e = minOf(matches[m] + matchLength, b) - a
                val current = m == matchIndex
                addStyle(
                    SpanStyle(background = if (current) c.currentMatch else c.match, color = if (current) Color.Black else Color.Unspecified),
                    s, e,
                )
                m++
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResourceList(resources: List<SourceResource>, onOpen: (SourceResource) -> Unit, onCopy: (SourceResource) -> Unit) {
    if (resources.isEmpty()) return Message(stringResource(R.string.source_no_resources), null) {}
    val labels = mapOf(
        "script" to stringResource(R.string.source_res_script), "style" to stringResource(R.string.source_res_style),
        "frame" to stringResource(R.string.source_res_frame), "image" to stringResource(R.string.source_res_image),
        "fetch" to stringResource(R.string.source_res_fetch), "other" to stringResource(R.string.source_res_other),
    )
    val groups = resources.groupBy { it.type }.toList().sortedBy { labels.keys.indexOf(it.first).let { i -> if (i < 0) 99 else i } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { (type, list) ->
            item(key = "h-$type") {
                Text(
                    "${labels[type] ?: type} · ${list.size}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "$type-${it.url}" }) { r ->
                val icon = when (r.type) {
                    "image" -> Icons.Rounded.Image
                    "frame" -> Icons.Rounded.Web
                    "style" -> Icons.Rounded.Wysiwyg
                    else -> Icons.Rounded.Code
                }
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(r) }, onLongClick = { onCopy(r) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            r.url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { hostOf(r.url) },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            r.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "hint") {
            Text(
                stringResource(R.string.source_res_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun HeadersView(view: SourceView) {
    val info = view.info
    when {
        info == null && view.serverError != null -> Message(view.serverError!!, null) {}
        info == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> SelectionContainer {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp)) {
                item {
                    val type = info.contentType.ifBlank { null } ?: stringResource(R.string.source_unknown_type)
                    Text(stringResource(R.string.source_response), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.source_response_summary, info.status, type, formatSize(info.size)),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Text(view.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
                items(info.headers) { (k, v) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(k, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
    }
}

private fun shareText(ctx: android.content.Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, title)
    runCatching { ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.source_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
