# TabletCAD 0.47 — transakcyjny import

Kontynuacja 0.46. Wersja deweloperska 0.47.0-transactional-import, versionCode 54.

## Naprawy

- Publiczne importy STEP i STL zatwierdzają dokument przez commitModelChange.
  Nie modyfikują oryginału przed powodzeniem geometrii i zapisu.
- Źródło importu jest kopiowane pod nową nazwą; przy odrzuceniu transakcji
  nowa kopia jest usuwana. Wcześniejsze źródła zostają dla undo/redo.
- Zastąpienie modelu usuwa sesję szkicu i jej powiązania z nowego dokumentu.
  Migawka undo zachowuje dokładną poprzednią sesję, także niedokończoną.
- DXF dodaje niezależny szkic, zachowując B-Rep, i przechodzi ten sam mechanizm
  zatwierdzenia. Podczas aktywnej sesji szkicu wymaga jej zakończenia lub anulowania.
- Aktywacja innego projektu przy błędzie zapisu przywraca dokładny poprzedni
  bufor i dokument; nie próbuje odczytać ponownie starego źródła, które mogło zniknąć.
- Selektor STEP zapisuje wejście w cache i nie nadpisuje starszego imported.step.
  Ogranicza wejście do 32 MB. Nie dodano limitu czasu obliczeń OCCT.

## Testy

WorkspaceImportProbe wykonuje publiczne importy na fizycznym Androidzie, na
odrębnych danych. Sprawdza uszkodzony STEP/STL, wymuszony błąd zapisu dla trzech
formatów, brak utraty modeli/plików/historii i brak osieroconych kopii importu.
Sprawdza również zastąpienie modelu podczas sesji szkicu, dokładne undo/redo,
niezależny szkic DXF oraz odrzucenie przełączenia projektu bez starego źródła.

## Ograniczenia

Nadal obowiązują otwarte problemy z audytu 0.46: dwufazowy zapis dwóch plików
nie jest wspólną transakcją, JNI jest synchroniczne, nazewnictwo topologiczne
ma ograniczony zakres, brakuje pełnego wyboru wielu krawędzi i manipulatorów.
Ograniczenie rozmiaru wejścia nie ogranicza pamięci ani czasu dekodowania STEP.
Nie wykonano pełnego scenariusza importowania gestami przez selektor Androida.

Logi i wyniki lokalne: build/regression047/. Pliki wynikowe nie są wersjonowane.

Na tablecie HONOR przeszły wszystkie 30 zestawów Probe. APK zbudowany,
podpisany dotychczasowym kluczem i zainstalowany z zachowaniem danych.
MainActivity uruchomiona ze statusem ok. Testy importu wykonano przez publiczne
metody prawdziwego widoku CAD i JNI, bez plików użytkownika.
