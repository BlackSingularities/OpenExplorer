# OpenExplorer

Szybka, minimalistyczna i rozbudowana przeglądarka internetowa na Androida (8.0+), napisana w Kotlinie i Jetpack Compose na silniku systemowego WebView (Chromium).

## Funkcje

**Strona startowa**
- Loga stron w siatce dowolnej wielkości (1–10 kolumn, 1–12 wierszy albo wszystkie) – same loga bez teł, obramowań i podpisów albo klasyczne kafelki.
- Duże logo stron, ikona albo litera; własne logo z galerii; przeciąganie kafelków; szybkie dodawanie często odwiedzanych.
- Zaokrąglenie, wielkość logo, odstępy, szerokość, podpisy, szarość, zegar, pole wyszukiwania, tło (w tym obraz z galerii z przyciemnieniem i rozmyciem).
- Gotowe układy (Same loga, Kafelki, Gęsta siatka, Zen) i podgląd na żywo w ustawieniach.

**Przeglądanie**
- Karty z miniaturami, karty incognito w osobnym profilu WebView (osobne ciasteczka i pamięć), przywracanie sesji.
- Pasek adresu z podpowiedziami wyszukiwarki, historii i zakładek; pozycja na górze lub na dole, płynne chowanie razem z przewijaną stroną, dopisywanie adresów podczas pisania, link ze schowka, przycisk „do góry”.
- 7 wyszukiwarek i własna, przeciągnij by odświeżyć, wersja desktop, tryb czytania, tłumaczenie strony, wyszukiwanie na stronie, źródło strony, drukowanie / zapis do PDF, skrót strony na ekranie głównym, udostępnianie.
- Menu kontekstowe linków i obrazów, pobieranie plików z wyborem nazwy, wideo na pełnym ekranie, wysyłanie plików, kamera, mikrofon i lokalizacja na żądanie stron.

**Hasła**
- Własny menedżer haseł (bez Google i Edge): zapisywanie po zalogowaniu, uzupełnianie jednym dotknięciem, generator silnych haseł.
- Szyfrowanie AES-256 kluczem z Android Keystore, odblokowanie odciskiem palca lub PIN-em, ochrona przed zrzutami ekranu.
- Import i eksport CSV (Edge, Chrome, Firefox, Bitwarden), wykrywanie słabych i powtórzonych haseł, przycisk z okiem przy polach haseł.

**Prywatność i bezpieczeństwo**
- Blokowanie reklam i śledzenia (lista wbudowana i pełna lista StevenBlack do pobrania).
- Tylko HTTPS, DNT/GPC, bezpieczne przeglądanie, ostrzeżenia o certyfikatach, blokada wyskakujących okien i ciasteczek stron trzecich, czyszczenie danych.

**Wygląd**: kompaktowy, czarno-biały interfejs jak w OpenPDF (albo akcent / Material You), AMOLED, pasek adresu na górze lub dole, pasek nawigacji, gest zmiany kart.

**Narzędzia**: panel strony z wyjątkami dla domeny, czytanie na głos, strony offline, zrzuty ekranu, kody QR, rozmiar tekstu dla strony, skróty wyszukiwania (!yt, !w, !gh…), ukrywanie banerów cookies, przywracanie zamkniętych kart.

**Inne**: zakładki, historia z wyszukiwaniem, lista pobranych, kopia zapasowa (JSON), domyślna przeglądarka, automatyczne aktualizacje.

## Wydania i aktualizacje

Każdy merge do gałęzi `main` buduje podpisany plik APK i publikuje wydanie na GitHub. Numer wersji pochodzi z pliku `VERSION`; gdy wydanie o tym numerze już istnieje, dostaje przyrostek z numerem budowania. Aplikacja sama sprawdza nowe wydania, pobiera APK i otwiera instalator.

Podpis wydań wymaga sekretów repozytorium: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`.

## Budowanie

```bash
gradle :app:assembleDebug
```

Wymaga JDK 17 i Android SDK (compileSdk 36).

## Licencja

MIT

---

**Projekt i wykonanie: [redzikowski.com](https://redzikowski.com)**

<a href="https://buycoffee.to/adamredzikowski" target="_blank"><img src="https://buycoffee.to/static/img/share/share-button-dark--pl.png" style="width: 351px; height: 92px" width="351" height="92" alt="Postaw kawę dla adamredzikowski na buycoffee.to"></a>
