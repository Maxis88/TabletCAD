# TabletCAD 0.74 — parametry, elipsa, przejście, przeciągnięcie, szyki brył, status więzów

0.74.0-parametric-tools, versionCode 83. Baza: 16e3686 (0.73, `main`).
Schemat .tcad: 37 (odczyt dokumentów 1–36 bez zmian; 0.73 i starsze nie otworzą
dokumentu 37, bo zgubiłyby parametry i nowe operacje). Na polecenie użytkownika etap
wykonano bez tabletu; wyniki jądra sprawdzono na komputerze (OCCT 8.0.1, Linux x86-64,
te same funkcje JNI). Instalacja i testy na tablecie: do wykonania (patrz niżej).

## Parametry użytkownika

- Projekt → Parametry: nazwane wartości długości lub liczby, wyrażenia odwołujące
  się do innych parametrów (np. `pol = szer/2 + 1mm`). Kolejność obliczeń
  topologiczna; cykl, nieznana nazwa i niezgodny wymiar są zgłaszane.
- Pola długości przyjmują nazwy parametrów (np. `2*szer`). Wyrażenie z nazwą
  wpisane w głębokość wyciągnięcia, wymiar szkicu albo średnicę/głębokość otworu
  jest zapisywane jako powiązanie; zmiana parametru przelicza szkic (solver),
  zależne profile i operacje. Wartość spoza zakresu jest odrzucana z nazwą pozycji.
- Usunięcie operacji usuwa jej powiązania i przenumerowuje późniejsze.

## Szkic

- Elipsa (środek, koniec półosi wielkiej, punkt półosi małej) — dokładny profil
  `gp_Elips` w wyciągnięciu, wycięciu i bazie; przesuwanie, obrót, skala, lustro,
  szyk. Odsunięcie, przycinanie, obrót profilu i rozbijanie elipsy są odrzucane
  zamiast przybliżania.
- Status więzów (pastylka w lewym dolnym rogu widoku szkicu): niedowiązany
  (liczba stopni swobody), w pełni związany, nadmiernie związany (liczba
  zbędnych więzów), sprzeczny. Liczony z rzędu jakobianu wszystkich zapisanych
  więzów i wymiarów sterujących; łuki/krzywe są wskazywane jako bez analizy.
- Nowy więz: współliniowość ⫽. Istniejące wcześniej styczność i symetria są zapisywane
  trwale i rozwiązywane przy każdej edycji.
- Geometria → Rzutuj: dokładne krawędzie B-Rep na płaszczyznę szkicu — wszystkie
  krawędzie leżące w płaszczyźnie albo wskazywane dotykiem. Linie → linie,
  okręgi równoległe do szkicu → okręgi/łuki; rzuty unieruchomione. Krawędzie
  dające punkt lub elipsę/krzywą są pomijane z podaniem przyczyny.

## Bryła

- Modeluj → Przejście (loft, rekord -19): 2–8 zamkniętych przekrojów z zapisanych
  szkiców, prostokreślne albo gładkie; nowa bryła / połącz / wytnij. Przekroje są
  odczytywane ze szkiców przy każdej przebudowie (edycja przekroju zmienia bryłę);
  usunięty przekrój jest zgłaszany.
- Modeluj → Przeciągnij (sweep, rekord -20): zamknięty profil po ścieżce z linii
  i łuków innego szkicu (łańcuch sprawdzany).
- Bryły → Lustro bryły / Szyk liniowy bryły / Szyk kołowy bryły (rekord -21):
  kopie całej bryły złączone z oryginałem; lustro na siebie jest zgłaszane.
- Otwór: podgląd na żywo, strzałka głębokości przeciągana na modelu, położenie
  środka wymiarowane od najbliższej prostej krawędzi ściany (A) i prostopadłej (B);
  odległości liczone z krawędzi OCCT. Położenie jest zapisywane jako punkt na
  ścianie (nie jest ponownie mierzone od krawędzi po zmianie modelu).
- Powłoka: podgląd na żywo i strzałka grubości. Szyk/lustro operacji: podgląd
  na żywo i strzałka odstępu szyku liniowego.

## Narzędzia testowe

- Naprawione stare próby: SketchEditingProbe (konstrukcja jako flaga elementu),
  ThreadProbe (tolerancja względna 2e-5 objętości po STEP — helisa jest B-spline),
  BodyPropertiesProbe i EdgeChamferProbe (kompilacja). Wszystkie cztery dołączone do
  `tools/host-kernel-tests.sh`.
- Runner: akcje `awake` / `awake-off`; `uitests` utrzymuje ekran włączony podczas
  testów instrumentowanych (`svc power stayon usb`) i przywraca ustawienie po teście.

## Wyniki (komputer, OCCT 8.0.1 x86-64)

`tools/host-kernel-tests.sh`: 15 zestawów PASS — 7 testów JVM (SketchDesign 131,
SolidPattern 115, Units 39, CadTools 64, ProjectLibraryFiles 48, BodyMass 50,
LibraryWorkspace 53) i 8 prób jądra (KernelDiagnostics 10, SolidPatternKernel 68,
SketchDesignKernel 7, CadToolsKernel 37, Thread 22, BodyProperties 29, EdgeChamfer 28,
SketchEditing 16). Objętości sprawdzane analitycznie (np. ostrosłup ścięty przejścia,
stożek ścięty, Pappus dla przeciągnięcia po łuku, otwór Ø6,5 w wymiarowanym położeniu).

## Nie zweryfikowano / do zrobienia

- Brak instalacji APK i testów na tablecie (odłożone przez użytkownika): budowa APK,
  testy instrumentowane, scenariusz pełnego cyklu, gesty nowych uchwytów i rzutowania.
- Strzałka odstępu szyku startuje ze środka modelu i nie odwraca się po zaznaczeniu
  „Przeciwny kierunek” (wartość jest poprawna, rysunek uchwytu nie).
- Wymiar położenia otworu nie jest asocjacyjny względem krawędzi.
- Status więzów nie analizuje łuków (krzywe bez punktów kotwiczenia).
