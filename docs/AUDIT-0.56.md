# TabletCAD 0.56 — interakcje szkicu i modelowanie

0.56.0-cad-interaction, versionCode 63, schema dokumentu 34. Baza gałęzi:
fdf4c32 z main, zawierająca roboczą wersję 0.55. Ikona launchera pozostaje
bez zmian. Aplikacja nadal używa natywnego View/Canvas oraz OCCT B-Rep.

## Zmiany

- Biblioteka projektów na starcie; przycisk tworzy od razu osobny pusty
  dokument, zapisując poprzedni. Okno startowe nie otwiera się po odłączeniu
  widoku. Nie ma drugiego formularza przykrywającego stary model.
- Dwukrotne dotknięcie etykiety długości, kąta, szerokości, wysokości lub
  promienia otwiera edycję jej wartości. Zmiana używanego szkicu przebudowuje
  właściwe cechy. Wymiary i symbole więzów są widoczne podczas edycji,
  z przełącznikiem widoczności wymiarów.
- Pomocniczość jest właściwością geometrii, także okręgu, prostokąta,
  wielokąta, konturu, łuku i splajnu. Narzędzie Pomocnicza znika z menu;
  starsze dokumenty nadal czytają poprzedni typ. Geometria pomocnicza nie
  tworzy profilu wyciągnięcia. Operacje mają wektorowe symbole.
- Snap przesuwanej geometrii używa jej punktów i krawędzi, nie położenia
  palca ani etykiet. Uwzględnia krawędzie OCCT leżące na płaszczyźnie szkicu.
  Zoom 0.02–200 zachowuje punkt skupienia gestu, siatka zmienia odstęp.
- Równoległość, prostopadłość, równa długość/promień, współśrodkowość,
  styczność, punkt na linii/okręgu i symetria względem X/Y lub linii.
  Więzy są zapisywane, walidowane i oznaczane w edycji. Sprzeczny układ
  zachowuje poprzedni szkic. Osobne linie można zaokrąglić stycznym łukiem.
- Drzewo ma osobne bryły oraz przypisane części źródłowego szkicu. UUID
  bryły pozostaje stały po edycji. Jedno dotknięcie zaznacza, dwukrotne
  edytuje; szkic ma edycję, nazwę i widoczność. Widoczność brył jest niezależna.
  Historia również rozróżnia wybór od edycji; podświetlenie używa informacji
  OCCT o ścianach wyniku, przekazywanej razem z siatką.
- Dotknięcie wnętrz kilku zamkniętych profili wybiera je bez listy bocznej.
  Nowa bryła, połączenie, wycięcie i część wspólna mają jawny cel. Działanie
  na jednej bryle nie wycina przypadkiem innej zachodzącej bryły. Dostępne
  są symetria i dwa przeciwne zakresy z niezależnymi długościami. Parametry
  można zmieniać z historii; źródłowa płaszczyzna nie dryfuje po kolejnych
  zmianach. Wiele profili zatwierdza się jedną transakcją Cofnij.
- Gwint rozpoznaje rzeczywistą walcową ścianę otworu i proponuje zbliżone
  presety metryczne M2–M48. Można dotknąć kilku otworów, automatycznie dobrać
  preset dla każdego, wybrać głębokość, koniec oraz prawy/lewy zwój.
  OCCT wycina rzeczywistą helisę o profilu V 60°, zachowaną w STEP.
- Zatwierdzanie nowych wyciągnięć i gwintów działa w tle na kopii dokumentu.
  Sprawdzony wynik i dokument są publikowane razem dopiero po poprawnym
  zapisie; nieaktualne wyniki nie zastępują bieżącego projektu.

## Weryfikacja

Fizyczny HONOR YLE_W09, ARM64, 3000×1920, izolowane katalogi danych testów.
APK Java/C++ zbudowany przez tools/build-foundation-apk.ps1; podpis v3.
TKOffset dołączony do powtarzalnego przygotowania OCCT i pakowania 26 bibliotek.

WorkspaceInstrumentation obejmuje ExtrusionPlanProbe, ThreadProbe,
BodyGraphProbe, RelationProbe, ConstructionAndSnapProbe, BooleanModeProbe,
SplineProbe, SketchFilletProbe, DirectionProbe, SymmetricProbe,
SketchWorkflowProbe, SketchTransactionProbe, TimelineProbe, ToolbarProbe,
WorkspaceUiProbe, InteractionFixProbe, ExtrusionManipulatorProbe,
EdgeGroupUiProbe, RecoveryUiProbe i TabletFlowProbe.

TabletFlowProbe wysyła zdarzenia przez Android Instrumentation do widocznego
okna: wybór dwóch profili, wyciągnięcie 4+2 mm, Cofnij/Ponów, wybór i edycja
szkicu z drzewa, dwukrotne dotknięcie szerokości, zmiana 20→25 mm, nowy pusty
projekt oraz wybór ścian dwóch otworów i utworzenie gwintów. Sprawdza dokładne
objętości 4800→5400 mm³, UUID, liczbę brył oraz odtworzenie objętości po
cofnięciu gwintów. Pozostałe próby sprawdzają modele, serializację, STEP,
rollback i wybrane interakcje bezpośrednimi zdarzeniami View.

Lokalne logi i obrazy: build/regression056 (wyniki budowania nie są w Git).

## Granice

- Solver obsługuje wymienione relacje i grupy do 128 współrzędnych. Nie jest
  pełnym solverem dowolnych więzów CAD. Styczność dotyczy linii/okręgów,
  nie dowolnych splajnów. Punkt na linii oznacza jej prostą nośną.
- Gwint jest rzeczywistą przybliżoną geometrią V, bez zaokrąglenia dna,
  klas tolerancji ISO, tabel pasowań ani zapewnienia zgodności produkcyjnej.
  Limit wynosi 200 zwojów. Obsługiwane są pełne walcowe otwory B-Rep,
  nie siatki STL, stożki ani częściowe powierzchnie walcowe.
- Stare compoundy pozostają jednym węzłem istniejącej bryły. Nie zgadujemy
  historycznego przypisania ich dawnych solidów do szkiców. Nowe operacje
  mają jawne cele i UUID. Pochodzenie ścian zachowuje dokładną tożsamość OCCT;
  zmieniona topologia może oznaczyć zmodyfikowaną ścianę jako wynik operacji.
  Nie jest to uniwersalne trwałe nazewnictwo topologiczne.
- Wybór obszarów wyciągania dotyczy gotowych zamkniętych profili. Nie tworzy
  automatycznie wszystkich komórek z dowolnych przecinających się linii.
  Wieloprofilowa część wspólna wymaga osobnych operacji; wieloprofilowe
  tworzenie, łączenie i wycinanie obsługują jedno zatwierdzenie.
- Starsze narzędzia importu, pomiaru i część przebudów nadal mogą blokować
  wątek UI. Nie sprawdzono ergonomii palca/rysika na każdym modelu tabletu;
  automatyzacja widocznego okna nie zastępuje długiej pracy użytkownika.
