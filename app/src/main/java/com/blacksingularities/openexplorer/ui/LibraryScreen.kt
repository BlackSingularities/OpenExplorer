package com.blacksingularities.openexplorer.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.browser.BrowserState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.blacksingularities.openexplorer.browser.DownloadEngine
import com.blacksingularities.openexplorer.browser.DownloadStatus
import com.blacksingularities.openexplorer.browser.DownloadTask
import com.blacksingularities.openexplorer.data.Bookmark
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScaffold(
    title: String,
    onBack: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    // Na tabletach i w poziomie lista nie rozciąga się na całą szerokość – czytelna kolumna pośrodku.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.fillMaxHeight().widthIn(max = LIBRARY_MAX_WIDTH).fillMaxWidth()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onClick = onBack)
            if (searching) {
                TextField(
                    query, onQuery, placeholder = { Text(stringResource(R.string.common_search)) }, singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            RoundIcon(if (searching) Icons.Filled.Close else Icons.Filled.Search, stringResource(R.string.common_search)) {
                searching = !searching
                if (!searching) onQuery("")
            }
            actions()
        }
        content(Modifier.weight(1f))
    }
    }
}

/** Najszersza kolumna list biblioteki (zakładki, historia, pobrane) na dużych ekranach. */
private val LIBRARY_MAX_WIDTH = 760.dp

@Composable
private fun Empty(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SiteRow(url: String, title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteLogo(url, title, 0L, "auto", "", "small", false, Modifier.size(28.dp), RoundedCornerShape(8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title.ifBlank { url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun BookmarksScreen(state: BrowserState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    LibraryScaffold(stringResource(R.string.library_bookmarks), onBack, query, { query = it }) { m ->
        val q = query.lowercase()
        val list = state.store.bookmarks.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        if (list.isEmpty()) Empty(stringResource(if (q.isEmpty()) R.string.library_bookmarks_empty else R.string.common_nothing_found), m)
        else LazyColumn(m) {
            items(list, key = { it.id }) { b ->
                SiteRow(b.url, b.title, hostOf(b.url), { onOpen(b.url) }) {
                    IconButton(onClick = { editing = b }) { Icon(Icons.Filled.Edit, stringResource(R.string.common_edit)) }
                    IconButton(onClick = { state.store.removeBookmark(b.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.common_delete)) }
                }
            }
        }
    }
    editing?.let { b ->
        var title by remember(b) { mutableStateOf(b.title) }
        var url by remember(b) { mutableStateOf(b.url) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.library_edit_bookmark)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.library_name)) }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.library_address)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { state.store.saveBookmark(b.copy(title = title, url = normalizeUrl(url))); editing = null }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private const val HISTORY_PAGE = 300

@Composable
fun HistoryScreen(state: BrowserState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val q = query.trim()
    // Wpisy z bazy porcjami: kolejne doczytują się po przewinięciu do końca listy.
    var limit by remember(q) { mutableIntStateOf(HISTORY_PAGE) }
    val list by remember(q, limit) { state.store.historyFlow(q, limit) }.collectAsState(null)
    LibraryScaffold(stringResource(R.string.library_history), onBack, query, { query = it }, actions = {
        RoundIcon(Icons.Filled.DeleteSweep, stringResource(R.string.library_clear_history)) { confirm = true }
    }) { m ->
        val entries = list ?: return@LibraryScaffold
        if (entries.isEmpty()) Empty(stringResource(if (q.isEmpty()) R.string.library_history_empty else R.string.common_nothing_found), m)
        else LazyColumn(m) {
            var lastDay = ""
            entries.forEach { h ->
                val day = DateUtils.getRelativeTimeSpanString(h.time, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
                if (day != lastDay) {
                    lastDay = day
                    item { Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                }
                item(key = h.id) {
                    val time = DateUtils.formatDateTime(ctx, h.time, DateUtils.FORMAT_SHOW_TIME)
                    SiteRow(h.url, h.title, "$time · ${hostOf(h.url)}", { onOpen(h.url) }) {
                        IconButton(onClick = { state.store.removeHistory(h) }) { Icon(Icons.Filled.Close, stringResource(R.string.common_delete)) }
                    }
                }
            }
            if (entries.size >= limit) item { LaunchedEffect(limit) { limit += HISTORY_PAGE } }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.library_clear_history_title)) },
            confirmButton = { TextButton(onClick = { state.clearData(cache = false, cookies = false, history = true, storage = false); confirm = false }) { Text(stringResource(R.string.library_clear)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private class DownloadItem(val id: Long, val title: String, val status: Int, val done: Long, val total: Long, val uri: String?, val mime: String?)

private fun queryDownloads(ctx: Context, ids: List<Long>): List<DownloadItem> {
    if (ids.isEmpty()) return emptyList()
    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val out = mutableListOf<DownloadItem>()
    dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
        while (c.moveToNext()) {
            fun col(n: String) = c.getColumnIndex(n)
            out += DownloadItem(
                c.getLong(col(DownloadManager.COLUMN_ID)),
                c.getString(col(DownloadManager.COLUMN_TITLE)) ?: "",
                c.getInt(col(DownloadManager.COLUMN_STATUS)),
                c.getLong(col(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(col(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getString(col(DownloadManager.COLUMN_LOCAL_URI)),
                c.getString(col(DownloadManager.COLUMN_MEDIA_TYPE)),
            )
        }
    }
    return out.sortedBy { ids.indexOf(it.id) }
}

@Composable
fun DownloadsScreen(state: BrowserState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val engine = state.downloads.engine
    var query by remember { mutableStateOf("") }
    // Starsze pobierania przez systemowy DownloadManager (wersje sprzed własnego silnika, Android 8–9).
    var legacy by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    LaunchedEffect(state.store.downloads.size) {
        while (true) {
            legacy = queryDownloads(ctx, state.store.downloads.toList())
            delay(if (legacy.any { it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING }) 700 else 3000)
        }
    }
    LibraryScaffold(stringResource(R.string.library_downloads), onBack, query, { query = it }, actions = {
        if (engine.tasks.any { it.status == DownloadStatus.DONE }) {
            RoundIcon(Icons.Filled.DeleteSweep, stringResource(R.string.download_clear_finished)) { engine.clearFinished() }
        }
        RoundIcon(Icons.Filled.FolderOpen, stringResource(R.string.library_downloads_folder)) {
            runCatching { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }) { m ->
        val q = query.trim()
        val tasks = engine.tasks.filter { q.isEmpty() || it.fileName.contains(q, true) || it.host.contains(q, true) }
        val old = legacy.filter { q.isEmpty() || it.title.contains(q, true) }
        if (tasks.isEmpty() && old.isEmpty()) {
            Empty(stringResource(if (q.isEmpty()) R.string.library_downloads_empty else R.string.common_nothing_found), m)
        } else {
            // Najpierw trwające, potem reszta od najnowszych.
            val sorted = tasks.sortedWith(compareByDescending<DownloadTask> { it.active }.thenByDescending { it.created })
            LazyColumn(m, contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sorted, key = { it.id }) { t -> DownloadRow(state, t) }
                items(old, key = { "dm-" + it.id }) { d -> LegacyDownloadRow(state, d) }
            }
        }
    }
}

/** Wiersz pobierania: ikona typu pliku, nazwa, stan (postęp, prędkość, czas), pauza/wznowienie i menu. */
@Composable
private fun DownloadRow(state: BrowserState, t: DownloadTask) {
    val ctx = LocalContext.current
    val engine = state.downloads.engine
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val done = t.status == DownloadStatus.DONE
    val failed = t.status == DownloadStatus.FAILED
    Row(
        Modifier.fillMaxWidth().clickable { if (done) state.downloads.open(t) else menu = true }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (failed) colors.errorContainer else colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(fileIcon(t.mime, t.fileName), null, Modifier.size(22.dp),
                tint = if (failed) colors.onErrorContainer else colors.onSecondaryContainer)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                downloadStatus(t), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = if (failed) colors.error else colors.onSurfaceVariant,
            )
            when {
                t.status == DownloadStatus.RUNNING && t.total > 0 ->
                    LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                t.active ->
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                t.status == DownloadStatus.PAUSED && t.total > 0 ->
                    LinearProgressIndicator(progress = { t.progress }, color = colors.outline, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        when (t.status) {
            DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                RoundIcon(Icons.Filled.Pause, stringResource(R.string.download_pause)) { engine.pause(t) }
            DownloadStatus.PAUSED ->
                RoundIcon(Icons.Filled.PlayArrow, stringResource(R.string.download_resume)) { engine.resume(t) }
            DownloadStatus.FAILED ->
                RoundIcon(Icons.Filled.Refresh, stringResource(R.string.download_retry)) { engine.resume(t) }
            DownloadStatus.DONE -> Unit
        }
        Box {
            RoundIcon(Icons.Filled.MoreVert, stringResource(R.string.download_more)) { menu = true }
            DropdownMenu(menu, { menu = false }) {
                if (done) {
                    DropdownMenuItem({ Text(stringResource(R.string.download_open)) }, { menu = false; state.downloads.open(t) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.common_share)) }, {
                        menu = false
                        t.uri?.let { u ->
                            runCatching { ctx.startActivity(Intent.createChooser(DownloadEngine.shareIntent(android.net.Uri.parse(u), t.mime), null)) }
                        }
                    }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                }
                DropdownMenuItem({ Text(stringResource(R.string.common_copy_address)) }, {
                    menu = false
                    copy(ctx, t.url)
                    state.snack(ctx.getString(R.string.common_address_copied))
                }, leadingIcon = { Icon(Icons.Filled.Link, null) })
                if (failed || done) {
                    DropdownMenuItem({ Text(stringResource(R.string.download_again)) }, {
                        menu = false
                        engine.enqueue(t.url, t.fileName, t.mime, t.userAgent, t.referer, t.profile, t.incognito)
                    }, leadingIcon = { Icon(Icons.Filled.Download, null) })
                }
                DropdownMenuItem(
                    { Text(stringResource(if (done) R.string.library_remove_from_list else R.string.download_cancel)) },
                    { menu = false; engine.remove(t) },
                    leadingIcon = { Icon(Icons.Filled.Close, null) },
                )
                if (done) {
                    DropdownMenuItem({ Text(stringResource(R.string.download_delete_file), color = colors.error) }, {
                        menu = false
                        engine.remove(t, deleteFile = true)
                    }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = colors.error) })
                }
            }
        }
    }
}

/** Opis stanu: „1,2 MB z 5 MB · 820 KB/s · 5 s”, „Wstrzymano · …”, „5 MB · example.com · 2 min temu”. */
@Composable
private fun downloadStatus(t: DownloadTask): String {
    val progress = if (t.total > 0) stringResource(R.string.library_download_progress, formatSize(t.done), formatSize(t.total)) else formatSize(t.done)
    return when (t.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.library_download_pending)
        DownloadStatus.RUNNING -> buildString {
            append(progress)
            if (t.speed > 0) append(" · ").append(formatSize(t.speed)).append("/s")
            val eta = t.etaSeconds
            if (eta >= 0) append(" · ").append(formatEta(eta))
        }
        DownloadStatus.PAUSED -> stringResource(R.string.library_download_paused) + " · " + progress
        DownloadStatus.FAILED -> stringResource(R.string.library_download_failed) + (t.error?.let { " · $it" } ?: "")
        DownloadStatus.DONE -> formatSize(t.total.coerceAtLeast(t.done)) + " · " + t.host + " · " +
            DateUtils.getRelativeTimeSpanString(t.finished.takeIf { it > 0 } ?: t.created).toString()
    }
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
    else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
}

/** Ikona według typu pliku. */
private fun fileIcon(mime: String?, name: String): ImageVector {
    val m = mime.orEmpty()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        m.startsWith("image/") -> Icons.Filled.Image
        m.startsWith("video/") -> Icons.Filled.Movie
        m.startsWith("audio/") -> Icons.Filled.MusicNote
        m == "application/pdf" || ext == "pdf" -> Icons.Filled.PictureAsPdf
        m == "application/vnd.android.package-archive" || ext == "apk" -> Icons.Filled.Android
        ext in setOf("zip", "rar", "7z", "tar", "gz", "xz") -> Icons.Filled.FolderZip
        else -> Icons.Filled.InsertDriveFile
    }
}

/** Stare pobieranie przez DownloadManager. */
@Composable
private fun LegacyDownloadRow(state: BrowserState, d: DownloadItem) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = d.status == DownloadManager.STATUS_SUCCESSFUL) { openDownload(ctx, d) }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(fileIcon(d.mime, d.title), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (d.status) {
                DownloadManager.STATUS_SUCCESSFUL -> formatSize(d.total.coerceAtLeast(d.done))
                DownloadManager.STATUS_FAILED -> stringResource(R.string.library_download_failed)
                DownloadManager.STATUS_PAUSED -> stringResource(R.string.library_download_paused)
                DownloadManager.STATUS_PENDING -> stringResource(R.string.library_download_pending)
                else -> if (d.total > 0) stringResource(R.string.library_download_progress, formatSize(d.done), formatSize(d.total)) else formatSize(d.done)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.status == DownloadManager.STATUS_RUNNING && d.total > 0) {
                LinearProgressIndicator(progress = { d.done.toFloat() / d.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        RoundIcon(Icons.Filled.Close, stringResource(R.string.library_remove_from_list)) {
            if (d.status != DownloadManager.STATUS_SUCCESSFUL) {
                (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(d.id)
            }
            state.store.removeDownload(d.id)
        }
    }
}

private fun openDownload(ctx: Context, d: DownloadItem) {
    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = dm.getUriForDownloadedFile(d.id) ?: return
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime ?: dm.getMimeTypeForDownloadedFile(d.id))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        android.widget.Toast.makeText(ctx, ctx.getString(R.string.library_no_app_for_file), android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun OfflineScreen(state: BrowserState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    val files = remember(version) {
        state.tools.offlineDir.listFiles { f -> f.extension == "mht" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    LibraryScaffold(stringResource(R.string.library_offline), onBack, query, { query = it }) { m ->
        val list = files.filter { query.isEmpty() || it.name.contains(query, true) }
        if (list.isEmpty()) Empty(stringResource(R.string.library_offline_empty), m)
        else LazyColumn(m) {
            items(list, key = { it.absolutePath }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen("file://" + f.absolutePath) }
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(f.nameWithoutExtension.substringBeforeLast('-'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            DateUtils.getRelativeTimeSpanString(f.lastModified()).toString() + " · " + formatSize(f.length()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RoundIcon(Icons.Filled.Close, stringResource(R.string.common_delete)) { f.delete(); version++ }
                }
            }
        }
    }
}
