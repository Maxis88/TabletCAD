# TabletCAD 0.75 — konstrukcja, pomiary kątów i pól, import OBJ

0.75.0-construction, versionCode 84. Baza: 0.74 (gałąź `claude/cad-0.74`).
Schemat .tcad: 37 (bez zmian względem 0.74 — 0.74 nie zostało jeszcze wydane;
nowe pole `construction` jest opcjonalne). Etap wykonany bez tabletu; jądro
sprawdzone na komputerze (OCCT 8.0.1, Linux x86-64).

## Konstrukcja (Bryła → Konstrukcja)

- Płaszczyzna odsunięta: od XY/XZ/YZ, zaznaczonej płaskiej ściany albo innej
  płaszczyzny konstrukcyjnej, o zadaną odległość wzdłuż normalnej.
- Płaszczyzna pod kątem: obrót płaszczyzny bazowej wokół osi leżącej w niej
  (X/Y/Z, oś konstrukcyjna, oś zaznaczonej ściany walcowej/stożkowej). Oś
  nieleżąca w płaszczyźnie bazowej jest odrzucana.
- Płaszczyzna przez 3 punkty, oś (punkt + kierunek X/Y/Z, dwa punkty, oś ściany
  walcowej/stożkowej), punkt konstrukcyjny. „Wskaż na modelu” wybiera
  wierzchołki B-Rep; punkty współliniowe/pokrywające się są odrzucane.
- Elementy są zapisywane jako dane wejściowe (odniesienie + wartości), geometria
  jest zawsze przeliczana z nich. Ściany i inne elementy są kopiowane liczbowo
  w chwili utworzenia (brak asocjacji z późniejszą zmianą ściany).
- Drzewo modelu: grupa „Konstrukcja” (zwijana), ukrywanie, ✎ edycja, usuwanie.
  Widok 3D: płaszczyzny jako półprzezroczyste prostokąty, osie przerywane, punkty.
- Szkic na płaszczyźnie konstrukcyjnej: przycisk „Konstrukcyjna…” przy wyborze
  płaszczyzny albo dotknięcie płaszczyzny w drzewie w trybie Szkic. Układ szkicu
  jak dla ściany (początek = rzut początku modelu).
- Płaszczyzny konstrukcyjne w lustrze operacji, lustrze bryły i podziale bryły;
  osie w szykach liniowych/kołowych operacji i brył.

## Pomiary

- Zmierz kąt: dwie ściany płaskie, proste krawędzie lub osie ścian walcowych;
  kąt między płaszczyznami, krawędziami albo krawędzią i płaszczyzną, plus kąt
  między normalnymi zewnętrznymi.
- Pole ściany: dokładne pole B-Rep (BRepGProp) i typ powierzchni, promień/średnica
  ścian walcowych i kulistych. Nowe zapytanie jądra (tryb 10) — analiza ścian.

## Import

- OBJ (v/f, wielokąty dzielone na trójkąty, indeksy ujemne, v/vt/vn) jako siatka;
  zapisywana w projekcie jako binarny STL, więc ponowne otwarcie używa ścieżki STL.

## Poprawki

- Strzałka odstępu szyku liniowego podąża za wybranym kierunkiem i przełącznikiem
  „Przeciwny kierunek”.

## Wyniki (komputer)

`tools/host-kernel-tests.sh`: 17 zestawów PASS, m.in. ConstructionHostTest (42:
płaszczyzny, osie, punkty, zapis, drzewo, kąty, OBJ) i ConstructionKernelProbe (15:
pola ścian sumują się do pola bryły, Ø10 na płaszczyźnie pod 30° jako dokładny walec
z osią wzdłuż normalnej, podział bryły płaszczyzną odsuniętą). Dodatkowo ToolbarProbe,
TimelineProbe, BodyGraphProbe.

## Nie zweryfikowano / do zrobienia

- Brak testów na tablecie (APK, testy instrumentowane, w tym nowy CadToolsUiProbe).
- Edycja szyku bryły z osią konstrukcyjną pokazuje oś X (wartość zapisana poprawnie,
  ponowne zatwierdzenie wymaga wskazania osi).
- Elementy konstrukcyjne nie są wybierane dotknięciem w widoku 3D (tylko z drzewa).
