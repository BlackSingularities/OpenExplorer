package com.blacksingularities.openexplorer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.browser.BrowserState
import com.blacksingularities.openexplorer.browser.LoginOffer
import com.blacksingularities.openexplorer.data.PasswordTools

/** Pole hasła z przyciskiem oka (pokaż / ukryj). */
@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (shown) FontFamily.Monospace else FontFamily.Default),
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                extra?.invoke()
                IconButton(onClick = { shown = !shown }) {
                    Icon(if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, stringResource(if (shown) R.string.passwords_hide else R.string.passwords_show))
                }
            }
        },
    )
}

private val STRENGTH_COLORS = listOf(Color(0xFFE5484D), Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFF30A46C), Color(0xFF30A46C))

/** Opis siły hasła dla wyniku [PasswordTools.strength] (0–4). */
@androidx.annotation.StringRes
private fun strengthLabel(score: Int): Int = when (score) {
    0 -> R.string.strength_very_weak
    1 -> R.string.strength_weak
    2 -> R.string.strength_medium
    3 -> R.string.strength_strong
    else -> R.string.strength_very_strong
}

/** Pasek siły hasła (4 odcinki) z opisem. */
@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val score = PasswordTools.strength(password)
    val color = STRENGTH_COLORS[score]
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (i < score.coerceAtLeast(1)) color else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        Text(
            stringResource(R.string.passwords_strength, stringResource(strengthLabel(score)).lowercase()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Kopiowanie hasła: schowek oznaczony jako poufny (klawiatura i podgląd systemu go nie pokażą)
 * i czyszczony po minucie.
 */
fun copySecret(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(SECRET_LABEL, text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            val cur = cm.primaryClip
            if (Build.VERSION.SDK_INT >= 28 && cur?.description?.label == SECRET_LABEL && cur.getItemAt(0)?.text?.toString() == text) {
                cm.clearPrimaryClip()
            }
        }
    }, 60_000)
}

private const val SECRET_LABEL = "OpenExplorer – hasło"

@Composable
private fun FloatingCard(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(vertical = 6.dp),
    ) { content() }
}

/** „Zaloguj się zapisanym kontem” nad paskiem, gdy strona ma formularz logowania. */
@Composable
fun LoginOfferCard(state: BrowserState) {
    val offer = state.logins.loginOffer
    val visible = offer != null && offer.tabId == state.current?.id
    var last by remember { mutableStateOf<LoginOffer?>(null) }
    if (offer != null) last = offer
    AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val o = last ?: return@AnimatedVisibility
        val noUsername = stringResource(R.string.passwords_no_username)
        FloatingCard {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.passwords_saved_logins, o.host), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                RoundIcon(Icons.Rounded.Close, stringResource(R.string.common_not_now), size = 34.dp, iconSize = 18.dp) { state.logins.loginOffer = null }
            }
            o.accounts.take(4).forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { state.logins.fillLogin(o, c) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (c.username.firstOrNull() ?: '•').uppercase(), color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.username.ifEmpty { noUsername }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge)
                        Text(if (c.host != o.host) c.host else "••••••••", maxLines = 1, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.passwords_fill), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Propozycja silnego hasła przy rejestracji albo zmianie hasła. */
@Composable
fun NewPasswordCard(state: BrowserState) {
    val tabId = state.logins.newPasswordTab
    val visible = tabId != null && tabId == state.current?.id && state.logins.loginOffer == null
    val generated = remember(tabId) { PasswordTools.generate() }
    AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        FloatingCard {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Password, null, Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.passwords_use_strong), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(generated, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.passwords_use), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                        .clickable { state.logins.fillGenerated(generated) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
                RoundIcon(Icons.Rounded.Close, stringResource(R.string.common_not_now), size = 34.dp, iconSize = 18.dp) { state.logins.newPasswordTab = null }
            }
            Text(
                stringResource(R.string.passwords_saved_after_submit),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }
}

/** Pytanie o zapisanie lub zmianę hasła po zalogowaniu się na stronie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePasswordSheet(state: BrowserState) {
    val p = state.logins.savePrompt ?: return
    var user by remember(p) { mutableStateOf(p.username) }
    var pass by remember(p) { mutableStateOf(p.password) }
    val update = p.existing != null && p.existing.username == user
    ModalBottomSheet(
        onDismissRequest = { state.logins.savePrompt = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteLogo(p.origin, p.host, 0L, "auto", "", "hd", false, Modifier.size(40.dp), RoundedCornerShape(12.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(stringResource(if (update) R.string.passwords_update_title else R.string.passwords_save_title), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(p.host, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.passwords_username_label)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PasswordField(pass, { pass = it }, stringResource(R.string.passwords_password_label))
            StrengthMeter(pass, Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.passwords_local_only),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p.existing == null) TextButton(onClick = { state.logins.neverSave(p) }) { Text(stringResource(R.string.passwords_never_for_site)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.logins.savePrompt = null }) { Text(stringResource(R.string.common_not_now)) }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { state.logins.savePassword(p, user.trim(), pass) },
                    enabled = pass.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(stringResource(if (update) R.string.passwords_update else R.string.common_save)) }
            }
        }
    }
}
