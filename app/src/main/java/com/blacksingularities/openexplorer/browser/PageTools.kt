package com.blacksingularities.openexplorer.browser

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openexplorer.R
import com.blacksingularities.openexplorer.data.Settings
import org.json.JSONTokener
import java.io.File

/** Narzędzia bieżącej strony z menu: zapis offline, czytanie na głos, zrzut ekranu. */
class PageTools(
    private val browser: BrowserState,
    private val host: BrowserHost,
    private val settings: Settings,
) {
    private val context get() = host.activity
    private val main = Handler(Looper.getMainLooper())

    // ---------- Offline ----------

    val offlineDir: File get() = File(context.filesDir, "offline").apply { mkdirs() }

    fun saveOffline() {
        val tab = browser.current ?: return
        val wv = tab.webView ?: return
        val name = tab.title.ifBlank { tab.host }.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60).trim()
        val file = File(offlineDir, name + "-" + (System.currentTimeMillis() / 1000) + ".mht")
        wv.saveWebArchive(file.absolutePath, false) { path ->
            if (path != null) browser.snack(browser.str(R.string.tools_offline_saved)) else browser.toast(browser.str(R.string.tools_offline_failed))
        }
    }

    // ---------- Czytanie na głos ----------

    private var tts: TextToSpeech? = null
    var speaking by mutableStateOf(false)
        private set

    fun readAloud() {
        if (speaking) {
            tts?.stop()
            speaking = false
            return
        }
        val wv = browser.current?.webView ?: return
        wv.evaluateJavascript(Scripts.PAGE_TEXT) { raw ->
            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty().trim()
            if (text.isEmpty()) {
                browser.toast(browser.str(R.string.tools_no_text))
                return@evaluateJavascript
            }
            val existing = tts
            if (existing != null) speak(existing, text)
            else {
                var created: TextToSpeech? = null
                created = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts = created
                        created?.let { speak(it, text) }
                    } else browser.toast(browser.str(R.string.tools_tts_unavailable))
                }
            }
        }
    }

    private fun speak(engine: TextToSpeech, text: String) {
        engine.setSpeechRate(settings.ttsRate / 100f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "last") main.post { speaking = false }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { speaking = false }
            }
        })
        val chunks = text.chunked(3500)
        chunks.forEachIndexed { i, c ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(c, mode, null, if (i == chunks.lastIndex) "last" else "p$i")
        }
        speaking = true
    }

    // ---------- Zrzut ekranu ----------

    /** Zrzut widocznej części strony do galerii (Obrazy/OpenExplorer). */
    fun screenshot() {
        val tab = browser.current ?: return
        val view = tab.container ?: return
        if (view.width <= 0) return
        try {
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "OpenExplorer-" + System.currentTimeMillis() + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenExplorer")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error(browser.str(R.string.tools_no_space))
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            browser.snack(browser.str(R.string.tools_screenshot_saved), browser.str(R.string.common_share)) {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        browser.str(R.string.common_share),
                    ),
                )
            }
        } catch (e: Exception) {
            browser.toast(browser.str(R.string.tools_screenshot_failed, e.message.orEmpty()))
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
