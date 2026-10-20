package com.blacksingularities.openexplorer.ui

import android.app.KeyguardManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.data.Credential
import com.blacksingularities.openexplorer.data.PasswordTools

/**
 * Menedżer haseł. Wejście wymaga potwierdzenia tożsamości (odcisk palca / PIN), ekran jest
 * chroniony przed zrzutami (FLAG_SECURE w aktywności), a po dłuższym wyjściu z aplikacji blokuje się ponownie.
 */
@Composable
fun PasswordsScreen(
    state: BrowserState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    authenticate: (String, (Boolean) -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    val store = state.passwords
    val secureDevice = remember { ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
    fun unlock() = authenticate("Odblokuj hasła") { ok -> if (ok) state.passwordsUnlocked = true }
    LaunchedEffect(Unit) { if (!state.passwordsUnlocked) unlock() }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var editing by remember { mutableStateOf<Credential?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var exportConfirm by remember { mutableStateOf(false) }
    var neverList by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(store.exportCsv().toByteArray()) }
            state.snack("Wyeksportowano ${store.items.size} haseł – usuń plik, gdy nie będzie potrzebny")
        }.onFailure { state.toast("Błąd zapisu: ${it.message}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("pusty plik")
            val n = store.importCsv(text)
            if (n > 0) state.snack("Zaimportowano $n haseł – usuń plik CSV z telefonu")
            else state.toast("Nie znaleziono haseł w pliku (oczekiwane kolumny: url, username, password)")
        }.onFailure { state.toast("Nie udało się wczytać pliku: ${it.message}") }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", onClick = onBack)
            if (searching) {
                TextField(
                    query, { query = it }, placeholder = { Text("Szukaj strony lub loginu") }, singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text("Hasła", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            if (state.passwordsUnlocked) {
                RoundIcon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Szukaj") {
                    searching = !searching
                    if (!searching) query = ""
                }
                RoundIcon(Icons.Rounded.Add, "Dodaj hasło") { adding = true }
                Box {
                    RoundIcon(Icons.Rounded.MoreVert, "Więcej") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Importuj z pliku CSV") }, leadingIcon = { Icon(Icons.Rounded.Download, null) },
                            onClick = { menu = false; importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Eksportuj do pliku CSV") }, leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                            enabled = store.items.isNotEmpty(), onClick = { menu = false; exportConfirm = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Strony bez zapisywania (${store.never.size})") }, leadingIcon = { Icon(Icons.Rounded.Block, null) },
                            onClick = { menu = false; neverList = true },
                        )
                    }
                }
            }
        }

        if (!state.passwordsUnlocked) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Lock, null, Modifier.size(32.dp)) }
                Text("Hasła są zablokowane", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
                Text(
                    "Potwierdź, że to Ty – odciskiem palca, twarzą albo kodem blokady ekranu.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
                Button(onClick = { unlock() }, modifier = Modifier.padding(top = 20.dp)) { Text("Odblokuj") }
            }
            return@Column
        }

        val reused = remember(store.items.toList()) { store.items.groupingBy { it.password }.eachCount() }
        val all = store.items.toList()
        val weak = all.filter { PasswordTools.strength(it.password) <= 1 }
        val repeated = all.filter { (reused[it.password] ?: 0) > 1 }
        val q = query.trim().lowercase()
        val list = when (filter) {
            "weak" -> weak
            "reused" -> repeated
            else -> all
        }.filter { q.isEmpty() || it.host.contains(q) || it.username.lowercase().contains(q) }
            .sortedWith(compareBy<Credential> { it.host }.thenBy { it.username.lowercase() })

        LazyColumn(Modifier.weight(1f)) {
            item {
                store.loadError?.let { err ->
                    Notice(Icons.Rounded.Warning, err)
                }
                if (!secureDevice) {
                    Notice(Icons.Rounded.Warning, "Telefon nie ma blokady ekranu – każdy, kto go weźmie, zobaczy hasła. Ustaw PIN, wzór lub odcisk palca w ustawieniach systemu.")
                }
                Text(
                    if (all.isEmpty()) "Hasła zapisane w przeglądarce pojawią się tutaj. Możesz też zaimportować je z Edge, Chrome lub innego menedżera (menu ⋮ → Importuj z pliku CSV)."
                    else "${all.size} zapisanych loginów · zaszyfrowane, przechowywane tylko na tym telefonie",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                if (all.isNotEmpty()) {
                    ChipsRow(
                        null,
                        listOf("all" to "Wszystkie · ${all.size}", "weak" to "Słabe · ${weak.size}", "reused" to "Powtórzone · ${repeated.size}"),
                        filter,
                    ) { filter = it }
                }
            }
            items(list, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = c }.padding(start = 16.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteLogo(c.origin, c.host, 0L, "auto", "", "small", false, Modifier.size(30.dp), RoundedCornerShape(8.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(c.host, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            c.username.ifEmpty { "(bez nazwy użytkownika)" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (PasswordTools.strength(c.password) <= 1 || (reused[c.password] ?: 0) > 1) {
                        Icon(Icons.Rounded.Warning, "Słabe lub powtórzone hasło", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { copySecret(ctx, c.password); state.snack("Skopiowano hasło – schowek wyczyści się za minutę") }) {
                        Icon(Icons.Rounded.ContentCopy, "Kopiuj hasło", Modifier.size(20.dp))
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }

    editing?.let { c ->
        CredentialDialog(
            state, c,
            onDismiss = { editing = null },
            onOpen = { editing = null; onOpen(c.origin) },
        )
    }
    if (adding) {
        CredentialDialog(state, null, onDismiss = { adding = false }, onOpen = {})
    }
    if (exportConfirm) {
        AlertDialog(
            onDismissRequest = { exportConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text("Eksportować hasła?") },
            text = { Text("Plik CSV nie jest zaszyfrowany – każdy, kto go otworzy, zobaczy wszystkie hasła. Przenieś go od razu do innego menedżera i usuń.") },
            confirmButton = {
                TextButton(onClick = {
                    exportConfirm = false
                    authenticate("Eksport haseł") { ok -> if (ok) exportLauncher.launch("OpenExplorer-hasla.csv") }
                }) { Text("Eksportuj") }
            },
            dismissButton = { TextButton(onClick = { exportConfirm = false }) { Text("Anuluj") } },
        )
    }
    if (neverList) {
        AlertDialog(
            onDismissRequest = { neverList = false },
            title = { Text("Strony bez zapisywania haseł") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (store.never.isEmpty()) Text("Brak – przy pytaniu o zapis możesz wybrać „Nigdy dla tej strony”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    store.never.toList().sorted().forEach { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h, Modifier.weight(1f))
                            RoundIcon(Icons.Rounded.Close, "Usuń z listy", size = 36.dp, iconSize = 18.dp) { store.removeNever(h) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { neverList = false }) { Text("Zamknij") } },
        )
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 12.dp))
    }
}

/** Podgląd i edycja loginu ([c] = null – nowy login). */
@Composable
private fun CredentialDialog(state: BrowserState, c: Credential?, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val store = state.passwords
    var site by remember(c) { mutableStateOf(c?.origin?.removePrefix("https://") ?: "") }
    var user by remember(c) { mutableStateOf(c?.username ?: "") }
    var pass by remember(c) { mutableStateOf(c?.password ?: "") }
    var note by remember(c) { mutableStateOf(c?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val origin = PasswordTools.normalizeOrigin(site)
    val others = if (pass.isEmpty()) 0 else store.items.count { it.password == pass && it.id != c?.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text(if (c == null) "Nowy login" else c.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    site, { site = it }, label = { Text("Strona (np. example.com)") }, singleLine = true,
                    isError = site.isNotEmpty() && origin == null, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    user, { user = it }, label = { Text("Nazwa użytkownika lub e-mail") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (user.isNotEmpty()) IconButton(onClick = { copy(ctx, user); state.snack("Skopiowano nazwę użytkownika") }) {
                            Icon(Icons.Rounded.ContentCopy, "Kopiuj nazwę użytkownika", Modifier.size(20.dp))
                        }
                    },
                )
                PasswordField(pass, { pass = it }, "Hasło") {
                    IconButton(onClick = { pass = PasswordTools.generate() }) { Icon(Icons.Rounded.Autorenew, "Wygeneruj silne hasło") }
                    if (pass.isNotEmpty()) IconButton(onClick = { copySecret(ctx, pass); state.snack("Skopiowano hasło – schowek wyczyści się za minutę") }) {
                        Icon(Icons.Rounded.ContentCopy, "Kopiuj hasło", Modifier.size(20.dp))
                    }
                }
                StrengthMeter(pass)
                if (others > 0) {
                    Text("To samo hasło jest używane na $others innych stronach – lepiej użyć innego.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(note, { note = it }, label = { Text("Notatka (opcjonalnie)") }, modifier = Modifier.fillMaxWidth())
                if (c != null) {
                    Text(
                        buildString {
                            append("Zapisano ")
                            append(DateUtils.getRelativeTimeSpanString(c.created).toString().lowercase())
                            if (c.lastUsed > 0) append(" · ostatnio użyte " + DateUtils.getRelativeTimeSpanString(c.lastUsed).toString().lowercase())
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = onOpen) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                            Text("Otwórz stronę", Modifier.padding(start = 6.dp))
                        }
                        TextButton(onClick = { confirmDelete = true }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = origin != null && pass.isNotEmpty(),
                onClick = {
                    origin?.let { o ->
                        if (c == null) {
                            if (store.find(PasswordTools.hostOf(o), user.trim()) != null) store.upsert(o, user.trim(), pass)
                            else store.save(Credential(origin = o, username = user.trim(), password = pass, note = note))
                        } else store.save(c.copy(origin = o, username = user.trim(), password = pass, note = note))
                        state.snack("Zapisano")
                        onDismiss()
                    }
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć login?") },
            text = { Text("${c.username.ifEmpty { "Login" }} na ${c.host} zostanie usunięty z tego telefonu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.remove(c.id)
                    onDismiss()
                    state.snack("Usunięto login", "Cofnij") { store.save(c) }
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}
