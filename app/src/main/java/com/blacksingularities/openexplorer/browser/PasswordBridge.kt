package com.blacksingularities.openexplorer.browser

import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.Credential
import com.blacksingularities.openexplorer.data.PasswordStore
import com.blacksingularities.openexplorer.data.PasswordTools
import com.blacksingularities.openexplorer.data.Settings
import org.json.JSONObject

/**
 * Menedżer haseł na stronach: most do skryptu w WebView, propozycje logowania, zapisu
 * i silnego hasła. Stan propozycji czyta interfejs (paski i okna haseł).
 */
class PasswordBridge(
    private val browser: BrowserState,
    private val host: BrowserHost,
    private val settings: Settings,
    private val passwords: PasswordStore,
) {
    var loginOffer by mutableStateOf<LoginOffer?>(null)
    var savePrompt by mutableStateOf<SavePrompt?>(null)
    /** Karta, na której jest pole nowego hasła (propozycja silnego hasła). */
    var newPasswordTab by mutableStateOf<Long?>(null)
    /** Ostatnio wpisana nazwa użytkownika dla domeny (logowanie dwuetapowe: najpierw login, potem hasło). */
    private val recentUser = HashMap<String, Pair<String, Long>>()

    private val supported: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    /**
     * Most do skryptu haseł. Wiadomości przyjmowane są tylko z głównej ramki, a domena pochodzi
     * z WebView (nie od strony) – strona może dostać wyłącznie loginy zapisane dla własnej domeny.
     */
    fun attach(wv: WebView, tab: Tab) {
        if (!supported) return
        val ok = runCatching {
            WebViewCompat.addWebMessageListener(wv, Scripts.PASS_BRIDGE, setOf("*")) { _, message, origin, isMainFrame, reply ->
                if (isMainFrame) message.data?.let { onMessage(tab, it, origin, reply) }
            }
        }.isSuccess
        if (ok && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(wv, Scripts.PASSWORDS, setOf("*"))
                tab.passScriptAtStart = true
            }
        }
    }

    fun onPageStarted(tab: Tab) {
        if (loginOffer?.tabId == tab.id) loginOffer = null
        if (newPasswordTab == tab.id) newPasswordTab = null
    }

    /** Starsze WebView nie wstrzykują skryptu na początku dokumentu – wtedy po wczytaniu strony. */
    fun onPageFinished(view: WebView, tab: Tab) {
        if (!tab.passScriptAtStart && supported) view.evaluateJavascript(Scripts.PASSWORDS, null)
    }

    fun onTabSelected() {
        loginOffer = null
        newPasswordTab = null
    }

    private fun onMessage(tab: Tab, data: String, origin: Uri, reply: JavaScriptReplyProxy) {
        val scheme = origin.scheme?.lowercase()
        val host = origin.host?.lowercase()?.removePrefix("www.")
        if ((scheme != "https" && scheme != "http") || host.isNullOrEmpty()) return
        val o = runCatching { JSONObject(data) }.getOrNull() ?: return
        tab.passReply = reply
        tab.passHost = host
        tab.passScheme = scheme
        when (o.optString("t")) {
            "hello" -> reply.postMessage(
                JSONObject().put("t", "cfg")
                    .put("on", settings.passwordManager)
                    .put("eye", settings.revealPasswordButton)
                    .put("show", browser.str(R.string.passwords_show))
                    .put("hide", browser.str(R.string.passwords_hide))
                    .toString(),
            )
            "form" -> {
                if (!settings.passwordManager) return
                if (o.optBoolean("nw") && tab.id == browser.currentId) newPasswordTab = tab.id
                offerLogin(tab, host, scheme)
            }
            "user" -> o.optString("v").takeIf { it.isNotBlank() }?.let { recentUser[host] = it.take(200) to System.currentTimeMillis() }
            "submit" -> onSubmitLogin(tab, host, "$scheme://$host", o.optString("u").trim(), o.optString("p"))
        }
    }

    private fun offerLogin(tab: Tab, host: String, scheme: String) {
        val list = passwords.forHost(host)
        if (list.isEmpty()) return
        val exact = list.filter { it.host == host }
        if (settings.passwordAutofill && !settings.passwordAuthFill && scheme == "https" && exact.size == 1 && !tab.autoFilled) {
            sendFill(tab, exact[0])
            return
        }
        if (tab.id == browser.currentId && !tab.autoFilled) loginOffer = LoginOffer(tab.id, host, list)
    }

    /** Wypełnienie formularza wybranym kontem (po potwierdzeniu tożsamości, jeśli tak ustawiono). */
    fun fillLogin(offer: LoginOffer, c: Credential) {
        val tab = browser.tabs.firstOrNull { it.id == offer.tabId } ?: return
        loginOffer = null
        if (settings.passwordAuthFill) host.authenticate(browser.str(R.string.passwords_fill_auth, offer.host)) { ok -> if (ok) sendFill(tab, c) }
        else sendFill(tab, c)
    }

    private fun sendFill(tab: Tab, c: Credential) {
        val reply = tab.passReply ?: return
        // Strona mogła się w międzyczasie zmienić – hasło trafia tylko do domeny, dla której je zapisano.
        val pageHost = tab.webView?.url?.let { Uri.parse(it).host?.lowercase()?.removePrefix("www.") }
        if (pageHost == null || pageHost != tab.passHost || PasswordTools.baseDomain(pageHost) != PasswordTools.baseDomain(c.host)) return
        reply.postMessage(JSONObject().put("t", "fill").put("u", c.username).put("p", c.password).toString())
        tab.autoFilled = true
        passwords.markUsed(c.id)
    }

    /** Wpisanie wygenerowanego silnego hasła w pola nowego hasła. */
    fun fillGenerated(password: String) {
        val tab = browser.current ?: return
        newPasswordTab = null
        tab.passReply?.postMessage(JSONObject().put("t", "gen").put("p", password).toString())
    }

    private fun onSubmitLogin(tab: Tab, host: String, origin: String, user: String, password: String) {
        if (!settings.passwordManager || !settings.offerSavePasswords || tab.incognito || password.isEmpty()) return
        if (passwords.isNever(host)) return
        val username = user.ifEmpty {
            recentUser[host]?.takeIf { System.currentTimeMillis() - it.second < 10 * 60_000L }?.first.orEmpty()
        }
        val existing = passwords.find(host, username)
            ?: if (username.isEmpty()) passwords.forHost(host).singleOrNull { it.host == host } else null
        if (existing != null && existing.password == password) {
            passwords.markUsed(existing.id)
            return
        }
        val p = savePrompt
        if (p != null && p.host == host && p.username == username && p.password == password) return
        savePrompt = SavePrompt(tab.id, host, origin, username, password, existing)
    }

    fun savePassword(prompt: SavePrompt, username: String, password: String) {
        savePrompt = null
        val existing = prompt.existing
        if (existing != null && existing.username == username) passwords.save(existing.copy(password = password))
        else passwords.upsert(prompt.origin, username, password)
        browser.snack(browser.str(if (existing != null) R.string.passwords_updated else R.string.passwords_saved))
    }

    fun neverSave(prompt: SavePrompt) {
        savePrompt = null
        passwords.addNever(prompt.host)
        browser.snack(browser.str(R.string.passwords_never_saved, prompt.host))
    }
}
