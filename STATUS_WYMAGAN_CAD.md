# TabletCAD — mapa zakresu i braków

Stan kodu: 0.56.0, Android minSdk 34. Dokument jest mapą dalszej pracy,
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

1. Szkice: pozostałe prymitywy (elipsa, splajn, slot itd.), rzutowanie
   krawędzi modelu, offset, wydłużanie, szyki i pełne więzy parametryczne,
   w tym styczność oraz trwała zależność symetrii. Lustro obecnie kopiuje.
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
8. Analizy OCCT: materiały, masa, bezwładność i odległość minimalna.
   Powierzchnia, objętość i środek objętości są dostępne od 0.43.
9. Dokumentacja: dowolne wymiary cech, tolerancje, szczegóły i oznaczenia,
   wszystkie wymagane formaty arkusza oraz szersza edycja rzutów i opisów.
10. IGES/3MF/SVG, import OBJ, pełne ustawienia dokładności i zakresu eksportu,
    weryfikacja we wskazanych zewnętrznych programach CAD.
11. UI/renderowanie: pełny ViewCube, ortografia i standardowe orientacje,
    tryby szkieletowy/przezroczysty, motywy i szersza obsługa urządzeń wejścia.
12. Wydajność: przeniesienie wszystkich kosztownych obliczeń poza wątek UI,
    anulowanie/postęp, testy obciążeniowe i zużycia pamięci/temperatury.
13. Gwinty, biblioteki techniczne, blachy i rozwinięcia: późniejszy zakres.

## Testy akceptacyjne pełnego promptu

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
