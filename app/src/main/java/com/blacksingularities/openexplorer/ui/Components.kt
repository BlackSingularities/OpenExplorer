package com.blacksingularities.openexplorer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.update.UpdateState
import com.blacksingularities.openexplorer.update.Updater
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Czy wykonywać haptykę przy dotyku elementów. */
val LocalHaptics = staticCompositionLocalOf { true }

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    val on = LocalHaptics.current
    return { if (on) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

/** Okrągły, płaski przycisk z ikoną; aktywny = pełny blok w kolorze akcentu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundIcon(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = LocalDims.current.button,
    iconSize: Dp = LocalDims.current.icon,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, label = "roundBg")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(
                enabled = enabled,
                onClick = { haptic(); onClick() },
                onLongClick = onLongClick?.let { { haptic(); it() } },
                onLongClickLabel = label,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label, Modifier.size(iconSize),
            tint = when {
                active -> MaterialTheme.colorScheme.onPrimary
                !enabled -> tint.copy(alpha = 0.3f)
                else -> tint
            },
        )
    }
}

/**
 * Panel z nagłówkiem i przyciskiem zamknięcia: na telefonie na cały ekran, na tablecie
 * jako kolumna przy prawej krawędzi.
 */
@Composable
fun Panel(
    title: String,
    onClose: () -> Unit,
    actions: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClose))
        Column(
            Modifier
                .then(if (wide) Modifier.align(Alignment.CenterEnd).width(440.dp).fillMaxHeight() else Modifier.fillMaxSize())
                .background(MaterialTheme.colorScheme.background)
                .clickable(enabled = false) {}
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                actions()
                RoundIcon(Icons.Rounded.Close, "Zamknij", onClick = onClose)
            }
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                content = content,
            )
        }
    }
}

/** Nagłówek sekcji ustawień. */
@Composable
fun SectionTitle(title: String) {
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp),
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            value, onChange, Modifier.padding(start = 12.dp),
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
fun ClickRow(title: String, subtitle: String? = null, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp), maxLines = 1)
    }
}

@Composable
fun SliderRow(title: String, value: Int, range: IntRange, suffix: String = "", step: Int = 1, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(Modifier.padding(top = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()}$suffix", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        Slider(
            local, { local = it }, valueRange = range.first.toFloat()..range.last.toFloat(), steps = steps,
            onValueChangeFinished = { onChange(local.roundToInt()) },
        )
    }
}

/** Wybór jednej z opcji w postaci rzędu pigułek. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipsRow(title: String?, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (k, label) ->
                val on = k == selected
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(k) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Komunikat z akcją, chowany po kilku sekundach. */
@Composable
fun SnackHost(state: BrowserState, modifier: Modifier = Modifier) {
    val snack = state.snack
    LaunchedEffect(snack?.id) {
        if (snack != null) {
            delay(if (snack.action != null) 4500 else 2500)
            if (state.snack?.id == snack.id) state.snack = null
        }
    }
    AnimatedVisibility(snack != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val s = snack ?: return@AnimatedVisibility
        Row(
            Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.inverseSurface).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium)
            if (s.action != null) {
                Text(
                    s.action, color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { state.snack = null; s.onAction?.invoke() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Baner nowej wersji (jak w OpenPDF): jednym dotknięciem pobiera i instaluje APK. */
@Composable
fun UpdateBanner(updater: Updater, onUpdate: () -> Unit, onInstall: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val u = updater.state
    val visible = u is UpdateState.Available || u is UpdateState.Downloading || u is UpdateState.Ready
    AnimatedVisibility(visible, modifier, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        val (title, action) = when (u) {
            is UpdateState.Available -> "Nowa wersja ${u.info.version}" to "Aktualizuj"
            is UpdateState.Downloading -> "Pobieranie ${u.info.version} · ${(u.progress * 100).roundToInt()}%" to null
            is UpdateState.Ready -> "Wersja ${u.info.version} pobrana" to "Zainstaluj"
            else -> "" to null
        }
        val notes = (u as? UpdateState.Available)?.info?.notes
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (u is UpdateState.Available) "Pobierze się i zainstaluje automatycznie" else "Nie zamykaj aplikacji",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (action != null) {
                    Text(
                        action, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                            .clickable { if (u is UpdateState.Ready) onInstall() else onUpdate() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (u !is UpdateState.Downloading) RoundIcon(Icons.Rounded.Close, "Ukryj", size = 34.dp, iconSize = 18.dp, onClick = onHide)
            }
            if (u is UpdateState.Downloading) {
                LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            if (!notes.isNullOrBlank()) {
                Text(notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
