# TabletCAD 0.73 — szyki, lustra, otwory, powłoka, pochylenie, bryły, jednostki i widoki

0.73.0-solid-tools, versionCode 82. Baza: ba5cd23 (0.72, gałąź codex/draft-0.72).
Schemat .tcad: 36 (odczyt dokumentów 1–35 bez zmian; starsze wersje aplikacji
nie otworzą dokumentów ze schematem 36). Na polecenie użytkownika etap wykonano
bez tabletu; kod jądra sprawdzono na komputerze (OCCT 8.0.1 dla Linux x86-64).

## Modelowanie bryłowe

- Bryła → Powiel → Lustro / Szyk liniowy / Szyk kołowy operacji (rekord -13).
  Kopie powstają z bieżących rekordów źródłowych przy każdej przebudowie, więc
  edycja źródła (np. średnicy otworu) zmienia wszystkie wystąpienia. Można powielać
  wyciągnięcia/wycięcia, obroty profilu, otwory i gwinty; kilka źródeł naraz
  (np. otwór + gwint). Zaokrąglenia i fazowania odnoszą się do konkretnych krawędzi
  i są odrzucane z komunikatem. Lustro: płaszczyzny YZ/XZ/XY z odsunięciem albo
  płaszczyzna zaznaczonej ściany (wartości stałe). Odbicie zachowuje orientację:
  odwraca głębokość wyciągnięcia, kąt obrotu i skrętność gwintu.
  Szyk liniowy: X/Y/Z lub normalna ściany, liczba 2–100, odstęp; szyk kołowy:
  oś X/Y/Z przez wpisany punkt albo oś zaznaczonej ściany walcowej, kąt do 360°.
  Kopia, która nie przecina bryły (np. poza płytą), zgłasza błąd operacji szyku.
- Bryła → Modeluj → Otwór (rekord -14): na płaskiej ścianie, Ø, na wylot lub
  na głębokość, dno płaskie albo stożek wiertła (np. 118°), pogłębienie walcowe
  albo stożkowe. Środek jest rzutowany na bieżącą płaszczyznę wskazanej ściany,
  więc otwór podąża za zmianą parametrów (np. grubości płyty). Gwint można
  dodać do otworu tak jak do wycięcia walcowego.
- Bryła → Modeluj → Powłoka (rekord -12): grubość do wewnątrz lub na zewnątrz,
  z usuniętymi płaskimi ścianami albo bez (zamknięta pustka). Łączenia łukowe.
- Bryła → Bryły → Połącz / Odejmij / Część wspólna brył (rekord -15): operacja
  Boolean między bryłami dokumentu; narzędzie zostaje zużyte albo zachowane.
  Zużyta bryła znika z list wyboru; operacje odwołujące się do niej są odrzucane.
- Bryła → Zmień → Pochyl ściany (rekord -16): kąt ±0,01–60° względem płaszczyzny
  neutralnej XY/XZ/YZ z położeniem; kierunek wyjmowania = normalna płaszczyzny.
- Bryła → Bryły → Podziel bryłę (rekord -18): płaszczyzna YZ/XZ/XY z położeniem
  albo płaszczyzna ściany; część za płaszczyzną zostaje w bryle, część przed nią
  staje się nową bryłą (z własnym UUID), na której można dalej modelować.
- Bryła → Bryły → Przesuń / Obróć / Skaluj bryłę (rekord -17) jako operacje
  historii: wektor przesunięcia, oś X/Y/Z przez punkt i kąt, skala jednorodna
  wokół punktu. Kolejne operacje działają na przekształconej bryle.
- Odwołania do ścian (powłoka, otwór, pochylenie): twórca ściany (operacja historii) +
  normalna; przy kilku kandydatach decyduje zapisany punkt wewnątrz ściany.
  Jądro śledzi rodowód ścian: ściana tylko przycięta przez późniejszą operację
  (ta sama powierzchnia OCCT) dziedziczy twórców przodka. Brak lub niejednoznaczność
  ściany jest zgłaszana; nie ma dopasowania do najbliższej ściany.
- Oś czasu: przytrzymanie operacji → Edytuj / Usuń operację… Usuwanie dowolnej
  operacji pokazuje konsekwencje, przenumerowuje odwołania szyków, powłok i otworów,
  usuwa szyk pozbawiony ostatniego źródła i odmawia usunięcia bryły używanej dalej.
  Wynik przechodzi przez tę samą transakcję co inne zmiany: przy błędzie geometrii
  dokument pozostaje bez zmian, a usunięcie można cofnąć.

## Jednostki i wprowadzanie wartości

- Projekt → Jednostki i dokładność: mm, cm, m, cale oraz 0–6 miejsc po przecinku
  na rysunku. Geometria jest nadal zapisywana dokładnie w milimetrach.
- Pola długości (etykiety „[mm]”) pokazują i przyjmują wartości w jednostce
  projektu; dozwolone są wyrażenia: 125,75 · 100/3 · 2*(10+2,5) · 1in+2mm.
  Liczba bez jednostki oznacza jednostkę projektu, wynik jest sprawdzany
  wymiarowo (np. 10mm*10mm nie jest długością). Pola kątów i liczności
  przyjmują wyrażenia bez jednostek. Pola używają klawiatury tekstowej.
- Wymiary na szkicu, wymiary wyciągnięć i właściwości bryły (objętość, pole,
  środek) są pokazywane w jednostce projektu.

## Widok

- Widok → Cieniowanie / Krawędzie / Przezroczystość. Tryby krawędzi i
  przezroczystości rysują także krawędzie ukryte (przerywane) i ściany tylne.
- Widok → Przekrój: płaszczyzna YZ/XZ/XY z położeniem albo płaszczyzna
  zaznaczonej ściany, z wyborem strony. Przekrój liczony przez OCCT (część
  wspólna z półprzestrzenią dla każdej bryły), więc przecięte ściany są pełne.
  Jest tylko widokiem: nie zmienia dokumentu ani eksportu; w trybie przekroju
  wybór ścian jest wyłączony.
- Nowe wektorowe ikony: lustro/szyk liniowy/szyk kołowy operacji, otwór,
  powłoka, pochylenie, operacje na bryłach, przesunięcie/obrót/skala, przekrój
  i trzy tryby wyświetlania (także na osi czasu).

## Weryfikacja

Bez tabletu, na komputerze (Linux x86-64, OCCT 8.0.1 z tej samej rewizji co APK,
te same funkcje JNI; `tools/host-kernel-tests.sh`):

- Kompilacja Java (android.jar 36) i C++ (`-Wall -Wextra -Werror`) — OK.
- Testy hosta: SolidPatternHostTest 115, UnitsHostTest 39, SketchDesignHostTest 131,
  ProjectLibraryFilesHostTest 48, BodyMassHostTest 50, LibraryWorkspaceHostTest 53 — PASS.
- SolidPatternKernelProbe 68 kontroli PASS: objętości analityczne szyków, luster,
  otworów (wylot, głębokość, stożek 118°, pogłębienia, ściana boczna, gwint w otworze),
  powłok (do wewnątrz, na zewnątrz z łukami, zamknięta, po zmianie grubości płyty),
  pochylenia ±5°, operacji na bryłach, podziału, przekształceń i przekroju; diagnostyka
  błędów wskazuje właściwą operację historii.
- KernelDiagnosticsProbe 10 PASS; SketchDesignKernelProbe 7 PASS (pierwsze uruchomienie
  próby z 0.72) oraz 33 dalsze próby jądra/szkicu bez regresji.
- Znalezione i poprawione: gwint nie wcinał się w otwór zbudowany z przekształconej
  kopii walca (Boolean bez efektu) — narzędzie otworu powstaje teraz bezpośrednio
  w układzie modelu jako pryzmat okręgu, jak zwykłe wycięcie.
- Niezależne od 0.73 (identyczny wynik na 0.72): ToolbarProbe miał nieaktualną listę
  poleceń (zaktualizowano), SketchEditingProbe, ThreadProbe (tolerancja STEP na x86-64),
  BodyPropertiesProbe i EdgeChamferProbe (nie kompilują się z obecnym API) — do przeglądu.

## Ograniczenia

- Szyk/lustro całych brył (nie operacji) i szyk po ścieżce nie są dostępne.
  Szyk nie powiela operacji tworzących nową bryłę ani części wspólnej.
- Otwór umieszcza się jednym dotknięciem i numerycznym położeniem; brak
  wymiarowania środka względem krawędzi ściany (można użyć szkicu i wycięcia).
- Płaszczyzna lustra/przekroju ze ściany jest zapisywana jako stała wartość.
- Rodowód ścian opiera się na wspólnej powierzchni OCCT; ściana utworzona na
  nowo (inna powierzchnia) wymaga ponownego wskazania. Nie jest to pełne
  nazewnictwo topologiczne.
- Gesty, formularze i rysowanie nowych narzędzi nie były uruchomione na Androidzie.
