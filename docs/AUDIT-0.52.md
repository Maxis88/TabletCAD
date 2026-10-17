# TabletCAD 0.52 — wspólna transakcja zapisu i odzyskiwanie

Wersja deweloperska 0.52.0-recoverable-save, versionCode 59.

## Usunięta luka

Dotychczas dokument biblioteki i autosave były zastępowane osobno. Awaria po
pierwszym zastąpieniu mogła pozostawić różne rewizje. AtomicSavePair obejmuje
teraz dokument, autosave i aktualizowane kopie zapasowe jedną odzyskiwalną
transakcją. Nie zmienia formatu dokumentów ani identyfikatorów projektów.

1. Pod wspólną blokadą przygotowuje nowe pliki tymczasowe i kopie poprzednich
   bajtów wszystkich zmienianych plików. Dane i katalog są synchronizowane.
2. Zapisuje dziennik z nazwami, rozmiarami i SHA-256 obu wersji. Dziennik jest
   podmieniany atomowo i synchronizowany przed rozpoczęciem zmian docelowych.
3. Podmienia pliki atomowymi przeniesieniami i synchronizuje katalog.
4. Dopiero potem utrwala znacznik zatwierdzenia związany z SHA-256 dziennika.
5. Bez znacznika ponowne otwarcie weryfikuje wszystkie stare kopie przed zmianą
   jakiegokolwiek celu i odtwarza poprzedni komplet. Z poprawnym znacznikiem
   weryfikuje komplet nowych plików i zachowuje zatwierdzoną rewizję.

Błąd wejścia/wyjścia podczas zapisu wycofuje komplet plików. Błąd samego
odzyskiwania zachowuje dowody i blokuje dalszy zapis. Katalogi/cele są ograniczone
do prywatnego katalogu projektów, z kontrolą nazw i ścieżek kanonicznych.
Zapisujące instancje współdzielą blokadę w JVM i blokadę pliku między procesami.

## Zachowanie aplikacji

Nieczytelny dziennik albo uszkodzona kopia transakcji nie powodują nadpisania
plików ani awarii podczas początkowego autosave. Czytelny dokument jest otwierany
tylko do odczytu. Zablokowane są narzędzia modelowania i szkicu, także natywne
przyciski paska; obrót widoku i eksport pozostają dostępne.

## Weryfikacja na fizycznym HONOR

- 34 zestawy app_process: PASS; cztery zestawy instrumentowane: PASS.
- AtomicSaveProbe wymusza wyjątek po każdej z czterech podmian. Sprawdza
  dokładne poprzednie bajty obu dokumentów i obu kopii zapasowych.
- Symuluje przerwanie procesu przy każdej granicy oraz po trwałym zatwierdzeniu.
- Dodatkowo uruchamia oddzielny proces Androida i rzeczywiście kończy go przez
  Runtime.halt po pierwszej podmianie oraz po zatwierdzeniu. Nowa instancja
  repozytorium odpowiednio przywraca stary komplet lub zachowuje nowy.
- Weryfikuje uszkodzone SHA-256, uszkodzony dziennik i brak nadpisywania dowodów.
- Trzy niezależne instancje zapisują z trzech wątków; dokumenty i kopie kończą
  z odpowiadającymi sobie rewizjami, bez konfliktu nakładających się blokad.
- RecoveryUiProbe sprawdza otwarcie czytelnej bryły przy uszkodzonym dzienniku,
  blokadę narzędzi, obrót, rzeczywisty STL i niezmienione pliki.
- APK zbudowany i podpisany dotychczasowym kluczem, instalacja przez adb install -r.
  Normalna aplikacja uruchomiona: status ok, poprzednia bryła widoczna, logcat
  aktywnego procesu: 0 dopasowań awarii. Dowody: build/regression052/.

## Ograniczenia

Testy dotyczą błędów zapisu i zakończenia procesu, nie fizycznego odcięcia zasilania
tabletu ani uszkodzenia nośnika. Mechanizm polega na fsync i atomowym rename
prywatnego systemu plików Androida. Nie gwarantuje naprawy uszkodzonych kopii;
w takiej sytuacji chroni pliki przed zapisem. Dodatkowe kopie wymagają miejsca
na dysku. Zapis i odzyskiwanie nadal wykonują się synchronicznie.

Nie zakończono całego projektu. Pozostają ograniczenia geometrii, solvera,
komponentów, manipulatorów, asynchroniczności i interfejsu z poprzednich audytów.
