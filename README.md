# OpenExplorer

Szybka, minimalistyczna i rozbudowana przeglądarka internetowa na Androida (8.0+), napisana w Kotlinie i Jetpack Compose na silniku systemowego WebView (Chromium).

## Funkcje

**Strona startowa**
- Kafelki z logo stron w siatce 3×3 albo dowolnej (2–6 kolumn, 1–8 wierszy).
- Edycja kafelka: nazwa, adres, kolor, ikona (logo strony, litera albo własny obrazek), kolejność, otwieranie w nowej karcie.
- Kształt kafelków, wielkość logo, podpisy, tło (zorza, gradient, kolor), zegar, pole wyszukiwania, ostatnio odwiedzane.

**Przeglądanie**
- Karty z miniaturami, karty incognito w osobnym profilu WebView (osobne ciasteczka i pamięć), przywracanie sesji.
- Pasek adresu z podpowiedziami wyszukiwarki, historii i zakładek; pozycja na górze lub na dole, chowanie przy przewijaniu.
- 7 wyszukiwarek i własna, przeciągnij by odświeżyć, wersja desktop, tryb czytania, tłumaczenie strony, wyszukiwanie na stronie, źródło strony, drukowanie / zapis do PDF, skrót strony na ekranie głównym, udostępnianie.
- Menu kontekstowe linków i obrazów, pobieranie plików z wyborem nazwy, wideo na pełnym ekranie, wysyłanie plików, kamera, mikrofon i lokalizacja na żądanie stron.

**Prywatność i bezpieczeństwo**
- Blokowanie reklam i śledzenia (lista wbudowana i pełna lista StevenBlack do pobrania).
- Tylko HTTPS, DNT/GPC, bezpieczne przeglądanie, ostrzeżenia o certyfikatach, blokada wyskakujących okien i ciasteczek stron trzecich, czyszczenie danych.

**Wygląd**: motyw jasny, ciemny, AMOLED, Material You, kolor akcentu, przyciemnianie stron.

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
