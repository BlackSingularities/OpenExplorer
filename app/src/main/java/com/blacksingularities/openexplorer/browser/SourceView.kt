package com.blacksingularities.openexplorer.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Zasób strony widoczny w podglądzie kodu (skrypt, arkusz stylów, obraz…). */
data class SourceResource(val type: String, val url: String)

/** Odpowiedź serwera dla kodu strony. */
data class SourceInfo(
    val status: Int,
    val contentType: String,
    val size: Long,
    val truncated: Boolean,
    val headers: List<Pair<String, String>>,
)

/** Stan pełnoekranowego podglądu kodu jednej strony lub zasobu. */
/** [profile] – profil WebView karty (ciasteczka przy pobieraniu kodu z serwera); null = domyślny. */
class SourceView(val url: String, val title: String, val incognito: Boolean, val hasDom: Boolean = false, val profile: String? = null) {
    /** Kod pobrany z serwera (jak w „view-source:”); null – jeszcze się pobiera. */
    var server by mutableStateOf<String?>(null)
    var serverError by mutableStateOf<String?>(null)
    var info by mutableStateOf<SourceInfo?>(null)
    /** Aktualny DOM po wykonaniu skryptów; null – jeszcze nie odczytany lub niedostępny ([hasDom]). */
    var dom by mutableStateOf<String?>(null)
    var resources by mutableStateOf<List<SourceResource>>(emptyList())
    /** Widok, do którego wraca „Wstecz” (zasób otwarty z listy zasobów). */
    var parent: SourceView? = null
}
