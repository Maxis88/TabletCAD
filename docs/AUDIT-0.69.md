# TabletCAD 0.69 — kopie projektów i odzyskiwanie z kosza

0.69.0-project-recovery, versionCode 78. Format .tcad bez zmiany schematu.
Baza: opublikowana na main wersja 0.68, commit 6c1f0de.
Na polecenie użytkownika ten etap wykonywano bez używania tabletu.

## Funkcje

- Projekt → Utwórz kopię projektu: osobny UUID dokumentu i nazwa; zachowane
  wewnętrzne identyfikatory szkiców, encji, brył i odniesień w obrębie dokumentu.
  Źródła importów są kopiowane pod nowymi nazwami, a miniatura kopiowana
  opcjonalnie. Kopia nie jest automatycznie aktywowana; pojawia się w bibliotece.
- Projekt → Kosz oraz Kosz w ekranie startowym: nazwa, data usunięcia,
  oznaczenie odzyskiwania z backupu i kolizji z istniejącą biblioteką.
- Przywróć: oryginalne UUID i bajty .tcad. Przywróć jako kopię: nowy UUID,
  podana nazwa i osobne źródła importu. Samo istnienie pliku .bak również
  blokuje nadpisanie istniejącego projektu.
- Nieczytelny główny zapis używa zgodnej tożsamościowo kopii .bak.
  Obsługiwane są też wpisy kosza zawierające wyłącznie .bak.
- Nieczytelne archiwa pozostają zachowane. Brak źródła lub błędna ścieżka
  blokuje opublikowanie dokumentu w bibliotece.
- Kopiowanie i przywracanie mają osobny wątek i okno postępu. Zniszczenie
  widoku zamyka jego okno; ukończenie zadania nie otwiera okien starej Activity.

## Ochrona danych

ProjectLibraryFiles publikuje nowy dokument dopiero po zapisaniu i fsync plików
źródłowych i dokumentu tymczasowego. Przeniesienie nie używa REPLACE_EXISTING.
Przed publikacją odczytuje wynik tym samym czytnikiem CadProject.fromJson,
co otwieranie projektów. Błędy przed publikacją usuwają tylko nowe pliki
utworzone w danej operacji. Aktualny autosave nie jest celem przywracania
ani publikowania kopii; interfejs przed kopiowaniem zapisuje bieżący projekt.

Zapis w koszu pozostaje fizycznie zachowany także po odzyskaniu. Znacznik
.restored ukrywa ukończony wpis z listy, bez niszczenia archiwalnych bajtów.
Jeżeli znacznik nie powstanie, istniejący UUID chroni przed ponownym nadpisaniem.
Nie ma funkcji trwałego opróżniania kosza.

Przy aktywnej transakcji szkicu kopia jest odrzucana z poleceniem zakończenia
lub anulowania edycji; nie powstaje baseline ze starym UUID projektu.
Odtwarzanie oryginalnego dokumentu z kosza zachowuje jego sesję szkicu.
Tryb tylko do odczytu repozytorium blokuje kopiowanie i przywracanie.

Nowe operacje biblioteki i istniejący AtomicSavePair używają wspólnego monitora
procesu oraz tej samej blokady plikowej. Nie zmieniono formatu ani protokołu
dziennika autosave.

## Sprawdzone lokalnie

- APK Java/C++/DEX zbudowany tools/build-foundation-apk.ps1, podpis sprawdzony.
- tools/test-library-files-host.ps1: 48 sprawdzeń PASS na JVM, na izolowanych
  plikach, z prawdziwym czytnikiem .tcad. Testy nie wykonują atrap metod Androida.
- Kolizje UUID oraz .bak, nazwy i Unicode, zachowanie wewnętrznych UUID,
  traversal ścieżek, uszkodzone zapisy, odzyskiwanie z backupu, brak źródeł.
- Niezależność bajtów źródeł STEP i STL, sprzątanie po wymuszonym błędzie,
  odrzucenie aktywnej sesji szkicu, równoczesne odzyskiwanie tego samego wpisu,
  synchronizacja z istniejącym odzyskiwaniem AtomicSavePair.
- Bajty oryginału, autosave i jego backupu pozostają niezmienione w testach
  bezpośrednich operacji plikowych. Log: build/host-tests/library-results.txt.
- APK testów Androida kompiluje się; nie został instalowany ani uruchamiany.

## Granice

Interfejs 0.69 i zachowanie cyklu Activity nie były sprawdzone runtime na Androidzie.
Nie wykonano testu śmierci procesu ani odcięcia zasilania nowej ścieżki publikacji.
Źródła importów w testach plikowych mają kontrolowane bajty; testy te potwierdzają
kopiowanie i odniesienia, a nie semantyczny STEP/STL round-trip w OCCT.
Archiwa i źródła pozostają zachowane, aby umożliwić dalsze odzyskiwanie.
Odczyt listy kosza jest synchroniczny; duże biblioteki mogą wymagać później
asynchronicznego ładowania listy. Pełne ograniczenia CAD z wcześniejszych audytów
pozostają aktualne.
