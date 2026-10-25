# Zmiany

## 1.4.0

Kontenery kart, własne style i skrypty dla stron, przygotowanie do F-Droid.

- Kontenery kart (np. „Praca”, „Prywatne”): każdy to osobny profil WebView z własnymi ciasteczkami i danymi stron – np. dwa konta w tym samym serwisie naraz. Kontener widać na liście kart (etykieta i filtr) i w pasku adresu (kolorowa kropka); „Otwórz w kontenerze” w menu linku. Kontenery dodaje się, zmienia i usuwa (razem z danymi) w Ustawieniach → Karty i pobieranie. Na WebView bez obsługi wielu profili funkcja jest ukryta.
- Własny CSS i JavaScript dla domeny w ustawieniach strony, wstrzykiwane po wczytaniu strony; przy skrypcie wyraźne ostrzeżenie. W kartach incognito własny kod nie działa.
- Kopia zapasowa obejmuje także ustawienia stron (z własnym kodem) i kontenery.
- Pobieranie plików, podgląd kodu i „Wyczyść dane strony” używają ciasteczek profilu karty – także w incognito.
- Wariant „fdroid” bez wbudowanego aktualizatora i uprawnień do instalowania pakietów; opisy w formacie fastlane. Build nie wymaga sekretów, a bez parametrów wersja pochodzi z pliku VERSION.

## 1.3.2

Bezpieczniejsze aktualizacje i HTTPS, zgodność z Androidem 16.

- Pobrana aktualizacja jest sprawdzana skrótem SHA-256 z GitHub i musi być podpisana tym samym kluczem co zainstalowana aplikacja.
- Gdy strona nie działa przez https://, zamiast błędu pojawia się pytanie, czy otworzyć ją bez szyfrowania (zgoda obowiązuje do zamknięcia aplikacji).
- Pobrana pełna lista blokowanych domen odświeża się sama raz w tygodniu przez Wi-Fi.
- Aplikacja jest celowana w Androida 16 (API 36).
- Usunięty martwy wpis z wbudowanej listy blokowania reklam.
- Nowe testy jednostkowe i raport lint przy każdym budowaniu w CI.
- Angielska wersja językowa – włącza się sama na telefonach z językiem angielskim.
- Skrót „!w” szuka w Wikipedii, a „!t” tłumaczy na język telefonu; poza polskim „!a” i „!am” prowadzą do amazon.com.
- Domyślne kafelki dopasowane do języka (Amazon i X zamiast Allegro i Onetu poza polskim), etykieta przycisku z okiem na stronach w języku telefonu.
- Napisy interfejsu przeniesione do zasobów, z poprawną odmianą liczebników („1 hasło”, „2 hasła”, „5 haseł”).

## 1.3.1

Przezroczysta ikona aplikacji.

- Przezroczysty znak aplikacji w ikonie, na ekranie startowym i w pozostałych miejscach z logo.
- Zachowana przezroczystość w klasycznych i adaptacyjnych ikonach Androida; odświeżona ikona tematyczna.

## 1.3.0

Nowy podgląd kodu strony (menu → Kod strony).

- Pełnoekranowy podgląd zamiast małego okienka, z kolorowaniem składni HTML, JavaScriptu i CSS oraz numerami linii (przyklejonymi przy przewijaniu w bok).
- Dwie wersje kodu: „Z serwera” (oryginał, jak view-source:) i „Po wczytaniu” (aktualny DOM po wykonaniu skryptów).
- Szukanie w kodzie z licznikiem wyników i przechodzeniem między nimi, „Przejdź do linii”, skok na początek i koniec.
- „Uporządkuj wcięcia” dla zminifikowanego HTML, zawijanie długich wierszy, zmiana wielkości tekstu, wyłączanie kolorów i numerów.
- Lista zasobów strony (skrypty, style, ramki, obrazy, zapytania) – dotknięcie otwiera kod zasobu.
- Nagłówki odpowiedzi serwera, kod statusu, typ i rozmiar.
- Kopiowanie i udostępnianie kodu; obsługa „view-source:adres” w pasku adresu.

## 1.2.0

Własny menedżer haseł, płynniejsze przewijanie i czytelniejsze ustawienia.

**Hasła**
- Wbudowany menedżer haseł – niezależny od Google, Edge i innych usług. Proponuje zapisanie hasła po zalogowaniu, uzupełnia loginy jednym dotknięciem, obsługuje logowanie dwuetapowe (najpierw login, potem hasło).
- Hasła są szyfrowane AES-256 kluczem z Android Keystore, który nie opuszcza telefonu; plik jest wyłączony z kopii w chmurze.
- Ekran „Hasła” (menu → Hasła) odblokowuje się odciskiem palca, twarzą lub PIN-em, nie trafia na zrzuty ekranu i blokuje się po wyjściu z aplikacji.
- Wyszukiwanie, filtry słabych i powtórzonych haseł, miernik siły, generator silnych haseł (także na stronach rejestracji), notatki.
- Import haseł z CSV (Edge, Chrome, Firefox, Bitwarden…) i eksport do CSV.
- Kopiowane hasło jest oznaczone jako poufne i znika ze schowka po minucie.
- Opcjonalnie: uzupełnianie bez pytania i potwierdzanie odciskiem palca przed każdym uzupełnieniem.
- Systemowe autouzupełnianie (np. okienka Edge) domyślnie wyłączone – można je włączyć w ustawieniach.
- Przycisk z okiem przy polach haseł na stronach (gdy strona nie ma własnego) i w polach haseł aplikacji.

**Przewijanie i pasek adresu**
- Paski płynnie odjeżdżają razem z przewijaną stroną zamiast zmieniać jej rozmiar – koniec szarpania przy przewijaniu.
- Przycisk „do góry” po przewinięciu długiej strony; szybsze rysowanie fragmentów poza ekranem.
- „Wstecz” przy otwartym pasku adresu zamyka pasek i wraca do strony (także gdy klawiatura chowa się przyciskiem „Wstecz”).
- Dopisywanie adresów podczas pisania („you” → „youtube.com”), podpowiedź skopiowanego linku, przełączanie na otwarte karty z podpowiedzi.

**Inne**
- Zamykanie kart przesunięciem w bok na liście kart, płynne przesuwanie pozostałych.
- „Wyjdź” w menu; „Usuwaj dane po wyjściu” działa też wtedy, gdy system sam zamknął aplikację.
- Jaśniejsze nazwy i opisy wielu opcji oraz pozycji menu, nowa kategoria „Hasła i autouzupełnianie”.

## 1.1.2

Dopracowane automatyczne aktualizacje.

- Sprawdzanie przy każdym powrocie do aplikacji (najwyżej co godzinę), a nie tylko przy pierwszym uruchomieniu.
- Sprawdzanie w tle co kilka godzin, także gdy aplikacja jest zamknięta; nowa wersja pobiera się sama.
- Cicha instalacja w tle na Androidzie 12+; w pozostałych przypadkach powiadomienie „dotknij, aby zainstalować”.
- Wznawianie przerwanego pobierania, weryfikacja pobranego pliku (rozmiar i pakiet).
- Po nadaniu zgody na instalację aktualizacja dokańcza się sama po powrocie do aplikacji.
- Czytelne błędy z przyciskiem „Ponów”; anulowanie okna systemu nie gubi pobranego pliku.
- Ukrycie banera nie pomija już wersji na zawsze.
- Po aktualizacji aplikacja uruchamia się ponownie albo pokazuje powiadomienie.
- Nowe opcje: pobieraj automatycznie, tylko przez Wi-Fi, instaluj w tle bez pytania.

## 1.1.1

- Naprawiono niewidoczne teksty i ikony w ciemnym motywie (ustawienia, lista kart, podpowiedzi, zakładki, historia).
- Licznik kart na pasku znów pokazuje liczbę kart.
- Ustawienia podzielone na kategorie z podstronami; podgląd strony startowej przypięty u góry i reagujący na żywo na suwaki.
- „Wstecz” w ustawieniach wraca do listy kategorii.
- Siatka kafelków nie psuje się przy dużej liczbie kolumn i dużych odstępach.

## 1.1.0

Kompaktowy, minimalistyczny interfejs w stylu OpenPDF i znacznie więcej możliwości.

**Strona startowa**
- Same loga bez teł, obramowań i podpisów – albo dowolne połączenie: tło kafelków, podpisy, przycinanie, szarość.
- Siatka dowolnej wielkości: 1–10 kolumn, 1–12 wierszy lub wszystkie kafelki, odstępy, szerokość, zaokrąglenie 0–50%, wielkość logo.
- Duże logo stron (apple-touch-icon, Google 256 px, DuckDuckGo), ikona albo litera; własne logo z galerii.
- Przeciąganie kafelków w trybie porządkowania, szybkie dodawanie często odwiedzanych stron.
- Tło: brak, zorza, gradient, kolor albo obraz z galerii z przyciemnieniem i rozmyciem.
- Zegar z regulacją rozmiaru, pole wyszukiwania (pigułka, minimalne, brak), położenie, ostatnio odwiedzane.
- Gotowe układy: Same loga, Kafelki, Gęsta siatka, Zen. Podgląd na żywo w ustawieniach.

**Interfejs**
- Czarno-biała paleta jak w OpenPDF (domyślna), akcent albo Material You; gęstość kompaktowa lub zwykła.
- Okrągłe, płaskie przyciski, kompaktowe menu, opcjonalny pasek nawigacji, gest przesunięcia paska adresu między kartami, wibracje.
- Komunikaty z akcją „Cofnij” po zamknięciu karty; przywracanie zamkniętych kart.

**Nowe funkcje**
- Panel strony (dotknij kłódki): wyjątki dla domeny – reklamy, JavaScript, wersja desktop, przyciemnianie, czyszczenie danych strony.
- Rozmiar tekstu zapamiętywany dla strony, czytanie strony na głos, zapis stron offline, zrzut ekranu, kod QR adresu i linku.
- Skróty wyszukiwania: !yt, !w, !gh, !m, !a i inne.
- Ukrywanie banerów cookies i miejsc po reklamach.
- Aktualizacje jak w OpenPDF: baner, jedno dotknięcie, cicha instalacja (Android 12+) i ponowne uruchomienie.

## 1.0.0

Pierwsze wydanie OpenExplorer – wielofunkcyjnej przeglądarki na Androida.

- Strona startowa z kafelkami stron (siatka 3×3 lub dowolna od 2×1 do 6×8), logo stron, edycja, kolory, własne ikony, kolejność.
- Karty z miniaturami, karty incognito w osobnym profilu, przywracanie sesji.
- Pasek adresu z podpowiedziami (wyszukiwarka, historia, zakładki), na górze lub na dole.
- Blokowanie reklam i śledzenia, tylko HTTPS, DNT/GPC, bezpieczne przeglądanie, blokada wyskakujących okien.
- Zakładki, historia, pobrane pliki, tryb czytania, tłumaczenie, wersja desktop, wyszukiwanie na stronie, źródło strony, drukowanie do PDF, skróty na ekranie głównym.
- Motywy: jasny, ciemny, AMOLED, Material You, kolor akcentu, ciemne strony.
- Kopia zapasowa ustawień, kafelków i zakładek.
- Automatyczne aktualizacje z wydań GitHub.
