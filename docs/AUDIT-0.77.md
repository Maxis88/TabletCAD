# TabletCAD 0.77 — analiza brył: bezwładność, materiały, odległość i kolizje

0.77.0-analysis, versionCode 86. Schemat .tcad 37 (nowe opcjonalne pole `bodyDensities`).
Etap bez tabletu; jądro sprawdzone na komputerze (OCCT 8.0.1, Linux x86-64).

- Właściwości bryły: oprócz sumy dla modelu — dla każdej bryły objętość, środek,
  masa (gęstość bryły albo dokumentu), tensor bezwładności względem środka masy
  (Ixx, Iyy, Izz, Ixy, Ixz, Iyz w kg·mm², znaki iloczynów jak w OCCT) i momenty główne;
  bez gęstości — momenty geometryczne (mm⁵). Środek masy całego modelu (ważony masą,
  gdy wszystkie bryły mają gęstość; w przeciwnym razie objętością).
- Materiał…: katalog typowych gęstości (stale, żeliwo, aluminium, mosiądz, miedź,
  tytan, PLA, PETG, ABS, PA12, żywica, POM, drewno) albo własna wartość, dla całego
  dokumentu lub pojedynczej bryły; przypisanie bryły można usunąć.
- Sprawdź → Odległość brył: minimalna odległość i najbliższe punkty (BRepExtrema),
  wykrywanie kolizji z objętością części wspólnej.
- Jądro: zapytania 11 (bezwładność per bryła) i 12 (odległość/kolizja) z argumentami
  przekazywanymi przez `nativeSetQueryArguments`.

## Wyniki (komputer)

19 zestawów PASS; AnalysisKernelProbe (29): momenty płyty 100×60×20 równe V(b²+c²)/12,
iloczyny zero, masa stali i aluminium, środek masy dwóch brył, zapis gęstości brył i
odrzucenie ujemnej, odległość 10 mm z punktem na górnej ścianie, kolizja z objętością
wspólną 500 mm³, zgłoszenie nieistniejącej bryły.

## Nie zweryfikowano

- Brak testów na tablecie. Wartości bezwładności dla zaimportowanych STEP liczone dla
  całego kształtu jako jednej bryły.
