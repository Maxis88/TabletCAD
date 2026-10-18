# TabletCAD 0.68 — lokalna weryfikacja 2026-10-18

Stan 0.68 został dopuszczony przez użytkownika do zapisania w repozytorium
2026-10-18, po opisanych niżej testach. Zapisy „bez commitów i publikacji”
poniżej dokumentują wcześniejsze etapy przed tą zgodą. Dalsze etapy mają być
sprawdzane lokalnie, bez uruchamiania aplikacji na tablecie.

Stan roboczy 0.68.0-sketch-orbit, versionCode 77, na bazie main 9949458.
Zachowano zastane zmiany. Bez commitowania i publikowania na GitHubie zgodnie
z poleceniem użytkownika; ta wersja nie jest wydaniem zdalnego repozytorium.

## Poprawki tego przebiegu

- Wybór narzędzia rysowania oraz rozpoczęcie nowego szkicu wyłącza sketchOrbit.
  Wcześniej aktywny obrót przechwytywał gest zamiast narysować geometrię.
- Scenariusz wyboru dolnej ściany obraca kamerę pod bryłę przed dotknięciem.
  Poprzednia pozycja kamery wskazywała zasłonięty punkt; wybierana była poprawnie
  widoczna górna ściana, co powodowało niepowodzenie testu.

## Weryfikacja

- APK zbudowany przez tools/build-foundation-apk.ps1; podpis zweryfikowany.
- Aktualizacja na HONOR YLE-W09 przez adb install -r, bez czyszczenia danych.
- Pełny bieżący zestaw WorkspaceInstrumentation: result=PASS, kod -1.
  Log: build/regression068-final.txt (lokalny, ignorowany przez Git).
- Dotyk w oknie aplikacji: obrót szkicu bez rysowania, powrót do narzędzia
  i narysowanie okręgu, wyciąganie do odsłoniętej dolnej ściany na 20 mm,
  odrzucenie ściany bocznej, otwór, R3, zapis i ponowne otwarcie,
  edycja szerokości podstawy oraz eksport i ponowny import STEP.
- Testy pracowały na izolowanych dokumentach. Nie wykonywano destrukcyjnych
  prób na projektach użytkownika.
- Wynik objętości po edycji: 142390.57534261665 mm3;
  ponowny odczyt STEP: 142390.57534261717 mm3.

## Ograniczenia

To weryfikacja bieżącego zestawu, nie całego programu ani wszystkich gestów.
Do powierzchni wyznacza jednorazowo długość do równoległej ściany z jej
triangulacji; nie tworzy trwałego odniesienia topologicznego do celu.
Pozostałe ograniczenia solvera i topologii z wcześniejszych audytów nadal obowiązują.

## Dalsze poprawki: odznaczanie i gwint M6 × 1

- Dotknięcie pustego obszaru widoku czyści zaznaczenie ściany, elementu szkicu,
  podświetlenia bryły/operacji/szkicu, zaznaczenie drzewa i osi czasu oraz zbiory
  wyboru narzędzi (profile wyciągania, krawędzie, otwory do gwintowania).
  Dokument i aktywny cel operacji bryłowej pozostają zachowane.
- Odtworzono na HONOR błąd M6 × 1 w otworze Ø5 mm, długość 10 mm:
  wejście od jednego końca działało, od przeciwnego Boolean nie usuwał materiału.
  Błąd występował również przy przebudowie od zera, bez cache historii.
- Narzędzie gwintu używa teraz kanonicznego kierunku osi. Przy odwróceniu
  przenosi początek na drugi koniec zadanego odcinka; zachowuje zakres osiowy,
  średnice, skok i skrętność. OCCT nadal waliduje narzędzie i wynik odejmowania.
- Rozszerzono ThreadProbe o 12 przypadków: oba końce, prawy/lewy gwint,
  głębokości 3 / 7,25 / 10 mm. Wszystkie przeszły i usuwają materiał z jednej bryły.
  Pełny prawy M6 × 1 daje z obu końców tę samą objętość 8779.580785868622 mm3
  dla testowej płyty 30 × 30 × 10 mm z otworem Ø5 mm.
- TabletFlowProbe sprawdza prawdziwymi dotknięciami odznaczenie dwóch otworów,
  ponowny wybór, domyślne gwintowanie pełnych 10 mm obu otworów oraz undo
  przywracające dokładną objętość sprzed operacji. Zrzut pełnych gwintów
  obejrzano wizualnie: build/thread-full-depth-verified.png.
- CadScenarioProbe sprawdza pusty tap i wyczyszczenie podświetleń modelu.
  Wskazanie bocznej ściany w scenariuszu wyciągania dobiera widoczny trójkąt
  o właściwej normalnej zamiast polegać na zasłanialnym punkcie świata.
- WorkspaceInstrumentation umożliwia opcjonalne -e probe <nazwa> wyłącznie
  spośród istniejących testów; nieznana nazwa zwraca błąd. Domyślnie uruchamia cały zestaw.
- Końcowa pełna regresja na tablecie: result=PASS, kod -1,
  log build/regression069-final.txt. APK Java/C++/DEX zbudowany i podpis sprawdzony,
  zainstalowany przez install -r. Nadal wersja lokalna 0.68.0-sketch-orbit / 77.
- Brak commitów i publikacji. Zachowano zastane pliki i zmiany robocze.

Weryfikacja gwintów dotyczy powyższych przypadków M6 × 1, nie wszystkich
presetów, dowolnych osi i tolerancji produkcyjnych gwintów metrycznych.

## Płynność obrotu bryły z gwintami

Model źródłowy pozostaje parametryczną bryłą B-Rep OCCT. Operacje, pomiary
i eksport STEP nadal korzystają z jądra, a nie z trójkątów wyświetlania.
Bufor podglądu nie jest nową reprezentacją dokumentu ani konwersją na STL.

Usunięto kwadratowe porównywanie każdej krawędzi podglądu ze wszystkimi
fragmentami tej samej powierzchni przy każdej klatce. DisplayMeshTopology
buduje pomocnicze sąsiedztwo raz dla danego bufora; identyfikatory te nie są
utrwalane jako odniesienia topologiczne dokumentu. Zasłanianie korzysta z
siatki przestrzennej na ekranie, a następnie z niezmienionego dokładnego
obliczenia przedziałów widoczności. Bufory projekcji są używane ponownie.

Powierzchnie są przesyłane zbiorczo do Canvas, w kolejności głębokości,
z zachowaniem kolorów i zaznaczeń. Nie są obrysowywane wewnętrzne podziały
powierzchni. Wypełnianie bez AA eliminuje szwy między sąsiednimi fragmentami;
obrysy pozostają wygładzane. Wykorzystana operacja Canvas jest obsługiwana
sprzętowo od API 29; projekt wymaga API 34:
https://developer.android.com/develop/ui/views/graphics/hardware-accel

### Pomiary na HONOR YLE-W09

ThreadRenderProbe, płyta 40 × 30 × 10 mm, dwa pełne gwinty M6 × 1.
Ten sam bufor 5346 elementów podglądu przed i po optymalizacji, bez
zmniejszania dokładności geometrii ani jakości triangulacji OCCT.

- Przed: rysowanie na bitmapowym Canvas 370,8–380,4 ms na klatkę
  (build/thread-render-before.txt).
- Po, końcowy pełny przebieg: 32,9–39,4 ms na klatkę w tym samym teście CPU,
  mediana klatki rzeczywistego obrotu w oknie Android 19,82 ms,
  p95 36,46 ms (build/regression-render-final.txt).
- Są to różne miary: czas bitmapowego renderowania CPU nie jest FPS ekranu.
  Nie deklaruje się stałych 60 FPS ani wyniku dla dowolnie dużego modelu.
- Dodatkowy obrót po dopasowaniu bryły do obszaru roboczego: 33 zmierzone
  klatki, mediana 20,13 ms, p95 34,77 ms; dokument parametryczny identyczny
  przed i po obrocie. Wynik PASS: build/thread-render-fitted.txt.
  Sprawdzony wizualnie zrzut dużej bryły: build/thread-render-fitted.png.

### Regresja

- 1500 deterministycznych zapytań indeksu porównanych z pełnym sprawdzaniem
  zasłaniania: zgodność przedziałów i wyboru punktów, czyszczenie i przebudowa.
- Próbkowane krawędzie podglądu bryły z gwintami: zgodność cache sąsiedztwa
  z dotychczasowym pełnym porównaniem współrzędnych.
- Java/C++/DEX, podpis APK i aktualizacja install -r: poprawne.
- Pełny WorkspaceInstrumentation: result=PASS, kod -1,
  build/regression-render-final.txt; obejmuje geometrię, 12 przypadków M6 × 1,
  wybór, historię, rzeczywiste dotknięcia, zapis i STEP.
- Wszystkie dokumenty testowe izolowane. Bez commitów i publikacji na GitHubie.

## Przywrócenie strzałki i skrócenie startu

Strzałka była podłączona w starszych formularzach, ale pominięta w głównym
wyborze profili i nowszym edytorze zakresu wyciągania. Obie ścieżki korzystają
teraz z manipulatora 3D. Kotwica jest w obrębie wybranego profilu, kierunek
odpowiada dodawaniu/wycinaniu i płaszczyźnie. Edycja zakresu ma asynchroniczny
podgląd z tym samym kandydatem dokumentu co zatwierdzanie; drugi zakres jest
zachowany. Zamknięcie panelu usuwa manipulator i odrzuca spóźnione podglądy.

Sprawdzone na HONOR:

- Rzeczywiste przeciągnięcie strzałki w nowym wyciąganiu 20 → 25 mm,
  bez obrotu kamery; potem powrót do 20 mm i pełny cykl modelowania/STEP.
- Nowszy edytor zakresu na XY/XZ/YZ, z drugim zakresem 0 i 5 mm:
  zmiana pierwszego 20 → 28 mm, prawidłowa objętość OCCT i zachowanie drugiego.
- Zestaw dotychczasowych testów manipulatora: kierunek odwrotny, symetria,
  anulowanie gestu, Cofnij/Ponów i ochrona przed spóźnionym podglądem.
- Logi: build/extrusion-arrow-current.txt, build/extrusion-arrow-window.txt.
  Widoczna strzałka: build/extrusion-arrow-verified.png.

Przy zimnym starcie aplikacja odbudowywała ostatnią bryłę przed pokazaniem
listy projektów. Dla bieżącej biblioteki na tablecie am start -W przekroczył
10-sekundowy limit oczekiwania. Nowe uruchomienie pokazujące bibliotekę
odracza odbudowę do wyboru projektu. Zwykłe konstruktory widoku i ścieżki
odtwarzania używane przez pozostałe narzędzia zachowują dotychczasową semantykę.

Po instalacji: dwa zimne uruchomienia Activity — TotalTime 185 ms i 119 ms,
Status ok / LaunchState COLD. Drugi log: build/startup-after.txt.
To czas do uruchomienia interfejsu/listy, nie czas odbudowy wybranego modelu.
Otwieranie złożonej bryły nadal wykonuje synchroniczną odbudowę OCCT.

Końcowa pełna regresja: result=PASS, kod -1,
build/regression-arrow-startup-final.txt. Obejmuje dodatkowo strzałkę edycji
operacji dodawania i wycinania z niezależnym drugim zakresem oraz izolowany
start z zapisanym otworem i gwintem: konstruktor odracza geometrię, wybór
projektu odtwarza bryłę i zachowuje obie operacje historii. APK zbudowany,
podpis zweryfikowany, zainstalowany przez install -r. Bez commitów i publikacji.
