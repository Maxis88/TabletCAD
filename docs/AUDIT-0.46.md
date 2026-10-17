# TabletCAD 0.46.0 — atomowa publikacja geometrii i edycja dokumentu

Kontynuacja audytu z 2026-10-17. Wersja deweloperska, versionCode 53.

## Naprawiony mechanizm

W 0.45 updateNativeModel wpisywało pusty wynik JNI bezpośrednio do nativeMesh.
Każdy edytor musiał rozpoznać taki stan i samodzielnie przywrócić parametry.
Checkpoint wykonywany przed obliczeniami zużywał historię i kasował redo
także wtedy, gdy użytkownik podał niedopuszczalną operację.

W 0.46 wynik powstaje w osobnym buforze. CadMesh sprawdza strukturę rekordu,
skończone wartości i całkowite identyfikatory ścian. To walidacja danych
renderera; nie zastępuje walidacji B-Rep wykonywanej nadal w OCCT.
Pusty wynik jest prawidłowy wyłącznie dla dokumentu bez bryły.
Przy nieudanej przebudowie dotychczasowe nativeMesh/nativePrism, środek modelu
i opis geometrii nie są zastępowane. Wynik metody jest jawnie logiczny;
odtwarzanie historii i zmiana projektu nie badają już pustki starej siatki.

Utracone jawne powiązanie aktywnego profilu jest błędem. Nie wybieramy
innego profilu ani bryły domyślnej w zastępstwie.

## Wspólna transakcja

commitModelChange tworzy kopię dokumentu, wykonuje zmianę, przelicza geometrię
i zapisuje dokument. Dopiero sukces dodaje jedną migawkę do undo i usuwa redo.
Błąd przywraca poprzedni obiekt dokumentu, bufor renderowania, parametry,
opis oraz arkusz. Nie trzeba ponownie przeliczać ostatniej poprawnej bryły.

Podłączone ścieżki:

- głębokość bazowa i kierunkowe/symetryczne wyciągnięcie;
- dodanie i wycięcie materiału oraz edycja zapisanych cech;
- obrót profilu, fazowanie i zaokrąglanie;
- edycja rozwiązanego szkicu i zależnych parametrów;
- usunięcie encji szkicu i ostatniej cechy;
- zatwierdzenie oraz anulowanie roboczej sesji szkicu.

Nie oznacza to objęcia wszystkich importerów i wszystkich poleceń interfejsu
jedną transakcją. Przykładowo import i przełączanie projektów zachowują osobne
ścieżki. Ich pełne ujednolicenie pozostaje zadaniem.

## Testy

Rozszerzony WorkspaceHistoryProbe uruchamia prawdziwy widok Android i OCCT
w odrębnym katalogu. Sprawdza odrzucenie nadmiernego promienia, brak profilu,
tożsamość zachowanego bufora, niezmienione undo/redo i bajty autosave.
Wymusza błąd zapisu przez katalog w miejscu pliku tymczasowego; sprawdza
rollback mimo poprawnie obliczonej bryły. Następnie sprawdza poprawną edycję,
zapis/odczyt, undo/redo, błąd anulowania sesji szkicu oraz jej odtworzenie
i zatwierdzenie. Osobno kontroluje niekompletne bufory, NaN i błędne ID ściany.

Logi lokalnego przebiegu: build/regression046/. APK: dist/TabletCAD-0.46.0-atomic-geometry.apk.
Pliki wynikowe oraz klucze nie należą do repozytorium Git.

Końcowa weryfikacja na podłączonym HONOR: wszystkie 29 zestawów Probe zakończone
kodem 0, APK zbudowany i podpisany dotychczasowym kluczem, aktualizacja przez
adb install -r, zimny start MainActivity ze statusem ok. Skan logcat aktywnego
procesu po uruchomieniu: 0 dopasowań awarii. Nie wykonano pełnego zestawu gestów.

## Nadal otwarte

- Zapis dokumentu biblioteki i aktywnego autosave nie jest pojedynczą
  transakcją dwóch plików. Test błędu zapisu obejmuje awarię przed pierwszą
  podmianą; nie dowodzi atomowości przy awarii między dwoma przeniesieniami.
- Obliczenia JNI nadal są synchroniczne; brak pełnego anulowania i numerowania
  rewizji obliczeń w tle.
- Pełne referencje topologiczne, wybór wielu krawędzi, manipulator wyciągania
  i kontekstowe zagospodarowanie całej przestrzeni widoku nadal wymagają pracy.
- Automatyczna regresja widoku nie potwierdza kompletu gestów na tablecie.
- Stabilizacja całej aplikacji i pełny zakres pierwotnych wymagań pozostają otwarte.
