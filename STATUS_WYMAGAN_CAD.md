# TabletCAD — mapa zakresu i braków

Stan kodu: 0.72.0, Android minSdk 34. Dokument jest mapą dalszej pracy,
nie deklaracją ukończenia pełnego promptu. Szczegółowe sprawdzone scenariusze
znajdują się w README oraz tools/*Probe.java.

0.45.0: odtwarzanie undo/redo z walidacją geometrii i rollback, zachowanie
stosów przy błędzie oraz walidacja odczytywalności JSON przed zapisem.
Aktualny audyt: docs/AUDIT-2026-10-17.md. Nie zamyka to całego etapu stabilizacji.

0.46.0: publikacja siatki dopiero po poprawnej przebudowie, jawny wynik błędu,
odrzucanie utraconego profilu i wspólna transakcja zmian podstawowych operacji,
edycji/kończenia/anulowania szkicu oraz usuwania cechy/encji. Błąd geometrii
lub zapisu zachowuje poprzedni stan i gałąź redo. Bieżący audyt: docs/AUDIT-0.46.md.

## Działające podzbiory

0.72.0: rysowanie okręgu 3 pkt i szczeliny z zapisem rozpoczętej konstrukcji,
przesunięcie/obrót/skala zaznaczonego elementu szkicu, nawigacja do źródła
operacji i bezpośrednie powiązania. Wspólny kod zmiany profilu zachowuje
głębokości, kierunki, ramy i osie oraz prawidłowo zmienia rodzaj/tail geometrii.
Sprzeczne wersje wspólnego profilu blokują odtworzenie szkicu zamiast zgadywania.
130 nowych kontroli JVM + 149 regresji PASS. Nowy test objętości B-Rep
skompilowany, nieuruchomiony na polecenie pracy bez tabletu.
Audyt: docs/AUDIT-0.72.md. Nie jest to pełne nazewnictwo topologiczne.

0.71.0: wspólna biblioteka start/menu, wyszukiwarka danych dokumentu,
sortowanie dat/nazw, filtry typów, odczyt list i miniatur w tle, odrzucanie
spóźnionych wyników. Wykrywanie .bak bez głównego pliku i wspólna blokada
archiwizacji/odczytu/zapisu; rollback archiwizacji bez nadpisywania nowych danych.
52 nowe kontrole JVM + 97 wcześniejszych, kompilacja APK i prób Androida.
Bez weryfikacji nowego UI runtime na polecenie użytkownika. Audyt: docs/AUDIT-0.71.md.

0.70.0: masa całego modelu z objętości OCCT i zapisanej, jednorodnej gęstości
w kg/m³. Schemat .tcad 35 z odczytem starszych projektów. 49 kontroli JVM
nowego zakresu oraz 48 regresji biblioteki, kompilacja APK i testów Androida.
UI bez weryfikacji runtime na polecenie użytkownika. Audyt: docs/AUDIT-0.70.md.

0.69.0: kopia projektu z nowym UUID dokumentu i osobnymi źródłami importu,
przywracanie z kosza (także z .bak), kopia przy kolizji istniejącego UUID,
kopiowanie i przywracanie w tle. 48 testów plikowych JVM oraz kompilacja APK;
interfejs tego etapu bez weryfikacji na Androidzie na polecenie użytkownika.
Audyt: docs/AUDIT-0.69.md.

0.68.0: obracanie szkicu, jednorazowe wyciąganie do równoległej ściany,
odznaczanie, poprawione gwinty M6 × 1, indeksowanie widoczności i zbiorcze
rysowanie podglądu bez zmiany B-Rep, strzałka nowych wyciągnięć i edycji
zakresu oraz odroczona odbudowa modelu przy starcie do biblioteki.
Audyt: docs/AUDIT-0.68-LOCAL.md. Pełne nazewnictwo topologiczne i wszystkie
tryby zakończenia wyciągnięć nadal nie są zaimplementowane.

0.67.0: biblioteka projektów z miniaturami brył (PNG generowane w tle po każdej
zmianie i dla starych projektów przy otwarciu listy), datą zmiany, opisem
i usuwaniem do projects/trash z potwierdzeniem. Audyt: docs/AUDIT-0.67.md.

0.65.0: zaznacz = edytuj. Widoczne szkice wybierane bezpośrednio w widoku bryły
(tylko widoczne fragmenty), ściany otwierają operację źródłową, drzewo i oś
czasu edytują po jednym dotknięciu. Audyt: docs/AUDIT-0.65.md.

0.64.0: przeprojektowany układ obszaru roboczego (pasek 64 dp zamiast 108 dp,
zwijane drzewo do szyny 40 dp, numer wersji w pasku i menu projektu, tokeny
kolorów). Audyt: docs/AUDIT-0.64.md.

0.63.0: wymiary położenia X/Y (dimX/dimY) jako więzy sterujące w obu solverach,
z liniami wymiarowymi na szkicu i edycją dwukrotnym dotknięciem; okrąg
wymiarowany średnicą Ø; początek szkicu na ścianie = rzut początku modelu.
Scenariusz 16.3 (kroki 1–21) przechodzi dotykiem w widocznym oknie (CadScenarioProbe).

0.62.0: pamięć podręczna stanów historii w jądrze (bryła bazowa, w tym odczyt
STEP, i stan po każdej operacji najwyższego poziomu); wznawianie od najdłuższego
niezmienionego prefiksu, serializacja wszystkich obliczeń OCCT. Wyniki
identyczne z obliczeniem od zera. Audyt: docs/AUDIT-0.62.md.

0.61.0: Szyk liniowy (siatka kolumny×wiersze) i kołowy (liczba, kąt, środek)
elementów szkicu jako dokładne przekształcenia sztywne; obrócony prostokąt
staje się równoważnym wielokątem. Kopie niezależne, jedna transakcja Cofnij.

0.60.0: Przedłuż — linia (także pomocnicza) do najbliższego przecięcia
z linią, prostokątem, wielokątem, okręgiem, istniejącą częścią łuku, konturem
linia/łuk lub splajnem (bisekcja na dokładnej krzywej Béziera). Automatycznie
wybierany koniec z krótszym przedłużeniem; zachowane UUID i więzy przez solver.

0.59.0: Odsunięcie szkicu — niezależna kopia o stałej odległości: linia,
łuk (współśrodkowy), okrąg, prostokąt, wielokąt (narożniki ostre, także wklęsłe)
oraz kontur linia/łuk (łuki współśrodkowe, narożniki jako dokładne przecięcia).
Zapadające się wyniki są odrzucane z przyczyną. Audyt: docs/AUDIT-0.59.md.

0.58.0: Widok → Dopasuj (rzeczywiste dopasowanie do granic bryły), Izometryczny,
Przód, Góra, Prawy bok i Rzut prostokątny z wektorowymi ikonami. Odległość
kamery perspektywicznej rośnie z rozmiarem modelu (wcześniej stałe 420 mm
powodowało błędne rzutowanie części > ~400 mm). Audyt: docs/AUDIT-0.58.md.

0.57.0: błędy przebudowy OCCT zwracają indeks operacji i konkretną przyczynę
(Boolean bez efektu, rozłączne dodanie, zaokrąglenie/faza niewykonalne, utracona
krawędź, niepoprawny profil, wyjątek OCCT). Komunikat w UI wskazuje operację
z historii. KernelDiagnosticsProbe sprawdza dokładne objętości części
referencyjnej 100×60×20 z otworem Ø10 i R3, edycję bazy i STEP. Audyt: docs/AUDIT-0.57.md.

0.56.0: edycja etykiet wymiarów/kątów, pomocniczość figur, rozszerzone więzy,
przyciąganie rzeczywistej geometrii, drzewo brył i części szkiców, wieloprofilowe
wyciąganie z dwoma zakresami oraz rzeczywiste gwinty otworów. Nowy dokument
jest pusty, zoom ma zakres 0.02–200. Dokładny zakres i ograniczenia, w tym
stare nierozdzielone compoundy i profil V gwintu: docs/AUDIT-0.56.md.

0.54.0: eksport triangulowanego OBJ przez systemowy wybór pliku, współdzielone
wierzchołki, normalne zgodne z orientacją trójkątów, grupy ścian, wartości
w mm i jawna informacja o jednostkach. Nie obejmuje importu OBJ.


0.52.0: zapis dokumentu, autosave i ich kopii ma wspólny dziennik odzyskiwania,
kopie poprzednich bajtów z SHA-256, blokadę procesu i znacznik zatwierdzenia.
Przerwany zapis jest odzyskiwany przed otwarciem; uszkodzone odzyskiwanie
blokuje zapis i narzędzia, zachowując widok oraz eksport. Testy rzeczywistej
śmierci procesu i ponownego otwarcia przeszły na Androidzie.

0.51.0: kolejne dotknięcia wybierają/odznaczają wiele krawędzi fazowania lub
zaokrąglania. Wspólny wymiar, jedna operacja OCCT i jedna cecha historii,
edycja parametru, zapis i undo/redo. Format dokumentu 30 czyta starsze wersje.
Nadal brak uniwersalnego wyboru obszarem, pętlami i stycznością.

0.50.0: kosztowne podglądy operacji modelujących wykonują się w tle na kopiach
parametrów, z jednym oczekującym żądaniem i odrzucaniem nieaktualnych wyników.
Zamknięcie narzędzia unieważnia także wynik już oczekujący na dostarczenie do UI.
Zatwierdzanie, eksport, import i pomiary nie są jeszcze w całości asynchroniczne.

0.49.0: strzałka wyciągania bazowego/dodania/wycięcia, gest oparty na osi 3D
i promieniu kamery, odwrotny kierunek, długość symetryczna, wymiar przy geometrii,
numeryczna edycja po dotknięciu etykiety oraz rzeczywisty podgląd OCCT.
Zapis/undo/redo dopiero po zatwierdzeniu; nadal brak wszystkich trybów zakończeń.

0.48.0: prawy panel i obszar interakcji są kontekstowe; w spoczynku model
zajmuje pełną dostępną szerokość. Historia przewija się poziomo bez obracania
kamery i poprawnie wybiera operację po przewinięciu. Audyt: docs/AUDIT-0.48.md.

0.47.0: import STEP/STL/DXF używa wspólnej transakcji; odrzucony import
zachowuje dokument, geometrię, historię i nie zostawia nowego źródła.
STEP/STL poprawnie odłączają poprzednią sesję szkicu, a undo ją odtwarza.
DXF wymaga zakończenia aktywnego szkicu. Audyt: docs/AUDIT-0.47.md.

- Natywny interfejs Android i lokalny OCCT przez JNI; brak konta/chmury.
- Precyzyjne profile liniowe i analityczne łuki/okręgi, szkic na płaszczyźnie
  lub płaskiej ścianie, rysowanie dotykiem, przyciąganie do geometrii i osi.
- Linia, polilinia, prostokąt, okrąg, łuk 3-punktowy, linia pomocnicza,
  łączenie konturów, rozbijanie, przycinanie, usuwanie i kopia lustrzana.
- Sterujące wymiary podstawowych elementów, ograniczony solver więzów
  liniowych; nie jest to pełny solver wszystkich więzów z promptu.
- Wyciągnięcia/dodania/wycięcia, kierunek odwrócony i symetryczny,
  parametryczny obrót profilu z dodaniem/wycięciem,
  zaokrąglenie oraz fazowanie wskazanej krawędzi; edycja w historii.
- Robocza edycja używanego szkicu z atomowym zatwierdzeniem geometrii,
  anulowaniem i odtwarzaniem sesji po restarcie.
- Pomiary punktów/wierzchołków oraz dokładne długości/promienie krawędzi.
- Dokładne objętości, pola i środki objętości B-Rep w panelu właściwości.
- Historia, Cofnij/Ponów, wiele lokalnych projektów, automatyczny zapis.
- Odniesienia faz/zaokrągleń do parametrów prostej bazy wyciągnięcia
  lub obrotu prostokąta; brak zgadywania przy utracie krawędzi.
- STEP B-Rep, STL binarny/tekstowy, DXF szkiców i dokumentacji.
- Arkusz techniczny z rzutami, przekrojem i liniami ukrytymi; nie pełny
  swobodny edytor dokumentacji technicznej.
- Cieniowany widok 3D, zaznaczanie ścian/krawędzi, podgląd operacji,
  ukrywanie zasłoniętych fragmentów i izolowanie zakrytego szkicu.

## Najważniejsze pozostałe luki

1. Szkice: elipsa i dalsze prymitywy, rzutowanie
   krawędzi modelu, parametryczne (powiązane) szyki i pełne więzy parametryczne,
   w tym styczność oraz trwała zależność symetrii. Lustro i szyki obecnie kopiują.
   Splajn istnieje; okrąg 3 pkt i szczelina podłączone w 0.72, z runtime
   gestów i nowych ścieżek OCCT nadal do potwierdzenia.
2. Podstawowe bryły: Shell i narzędzie parametrycznych otworów.
   Otwór można obecnie wykonać przez wycięcie okręgu, bez dedykowanego Hole.
3. Referencje topologiczne: od 0.44 powiązania obsługiwanych krawędzi bazowych
   śledzą zmianę parametrów generatora. Nadal brak ogólnego nazewnictwa
   krawędzi/ścian po dowolnych operacjach, aktualizacji wszystkich płaszczyzn
   szkiców i odniesień wycięć. Nie wolno zastępować brakującej referencji
   najbliższą krawędzią.
4. Historia: kompletne drzewo brył/komponentów, zmiana kolejności operacji,
   szersze zarządzanie zależnościami i diagnostyka regeneracji.
5. Parametry użytkownika: nazwy, jednostki, wyrażenia i cykle zależności.
6. Sweep, Loft, szyki brył, lustro brył, podziały, pochylenia i narzędzia
   powierzchniowe; pozostałe operacje modelowania z promptu.
7. Zespoły: komponenty, instancje, wiązania montażowe i kolizje.
8. Analizy OCCT: katalog materiałów, różne gęstości poszczególnych brył,
   bezwładność i odległość minimalna. Masa z własnej jednorodnej gęstości
   jest od 0.70; powierzchnia, objętość i środek objętości są od 0.43.
9. Dokumentacja: dowolne wymiary cech, tolerancje, szczegóły i oznaczenia,
   wszystkie wymagane formaty arkusza oraz szersza edycja rzutów i opisów.
10. IGES/3MF/SVG, import OBJ, pełne ustawienia dokładności i zakresu eksportu,
    weryfikacja we wskazanych zewnętrznych programach CAD.
11. UI/renderowanie: pełny ViewCube (ortografia i standardowe orientacje są od 0.58),
    tryby szkieletowy/przezroczysty, motywy i szersza obsługa urządzeń wejścia.
12. Wydajność: przeniesienie wszystkich kosztownych obliczeń poza wątek UI,
    anulowanie/postęp, testy obciążeniowe i zużycia pamięci/temperatury.
13. Gwinty, biblioteki techniczne, blachy i rozwinięcia: późniejszy zakres.

## Testy akceptacyjne pełnego promptu

- Scenariusz obowiązkowy 16.3 (kroki 1–21) — PASS na HONOR YLE-W09
  (CadScenarioProbe, widoczne okno, rzeczywiste zdarzenia dotyku): szkic
  100 × 60 z wymiarami i położeniem, wyciągnięcie 20 mm, szkic na ścianie,
  otwór Ø10 w 25/30 mm na wylot, R3 na wskazanej krawędzi, zapis, ponowne
  otwarcie, edycja szerokości 100 → 120 z przebudową otworu i R3, eksport STEP
  i ponowny import; objętości B-Rep sprawdzane analitycznie na każdym etapie.

- A — NIEPOTWIERDZONY w całości. Częściowe testy wyciągnięć, wycięć,
  zaokrągleń, zapisu i STEP istnieją; pełna sekwencja czterech otworów,
  zmiany wysokości i zachowania wszystkich odniesień wymaga osobnego testu.
- B — PASS w zautomatyzowanym przebiegu geometrii/parametryki/STEP:
  RootEdgeReferenceProbe tworzy wałek, rowek i fazę, zmienia promień/długość,
  kontroluje analityczną objętość i STEP oraz zapis/edycję źródła.
  Pełna sekwencja dotykowa z rowkiem i fazą nie jest jeszcze potwierdzona;
  w UI sprawdzono zmianę źródła wałka z fazą, edycję, historię i restart.
- C — CZĘŚCIOWY: istnieją rzuty/przekrój/eksport, brak pełnego dowolnego
  wymiarowania dokumentacji i pełnego testu scenariusza z promptu.
- D — NIEWYKONALNY w całości: brak silnika komponentów i więzów montażowych.

Cel całej aplikacji pozostaje nieukończony. Regresje podzbiorów nie są
dowodem ukończenia powyższych testów ani całego zakresu.
