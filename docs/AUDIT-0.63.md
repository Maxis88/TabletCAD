# TabletCAD 0.63 — wymiary położenia i pełny scenariusz 16.3

0.63.0-position-dimensions, versionCode 72. Format: nowe rodzaje więzów `dimX`
i `dimY` w istniejącej liście `coincidences` (starsze dokumenty bez zmian;
starsze wersje aplikacji nie odczytają dokumentu zawierającego te więzy).

## Braki wykryte przez scenariusz

- Nie było sposobu zwymiarowania położenia punktu (np. środka otworu względem
  krawędzi) — tylko długości, promienie i kąty pojedynczych elementów.
- Okrąg pokazywał wyłącznie promień R.
- Początek szkicu na ścianie bryły leżał w przypadkowym punkcie dotknięcia, więc
  współrzędne szkicu ściany nie były powtarzalne.

## Zmiany

- Więzy `dimX`/`dimY`: ten sam punkt = współrzędna od początku szkicu;
  dwa punkty = różnica współrzędnych (b − a). Solver liniowy (union-find
  z przesunięciami i przypięciami) i nieliniowy (residua) obsługują oba warianty.
- Linie wymiarowe X/Y na szkicu (z linią odniesienia do początku lub drugiego
  punktu), wartość edytowana dwukrotnym dotknięciem z zachowaniem znaku.
- Tworzenie: Więzy → „Wymiar położenia X ↔ / Y ↕” (punkt, potem drugi punkt
  albo początek układu) oraz Geometria → „Wymiaruj położenie … (X/Y od początku)”.
- Okrąg: etykieta „Ø” i edycja średnicy.
- Szkic na płaskiej ścianie: początek = rzut początku modelu na płaszczyznę
  ściany; osie jak dotąd. Dla górnej ściany bryły wyciągniętej z XY
  współrzędne szkicu odpowiadają współrzędnym X/Y szkicu bazowego.

## Weryfikacja (HONOR YLE-W09, widoczne okno, rzeczywiste zdarzenia dotyku)

`CadScenarioProbe`: nowy projekt → szkic XY → prostokąt rysowany palcem →
wymiary 100 × 60 wpisane w etykiety → wymiary położenia narożnika 0/0 →
wyciągnięcie 20 mm (120000 mm³) → dotknięcie górnej ściany → szkic na ścianie
z początkiem (0,0,20) → okrąg rysowany palcem → Ø10 → położenie środka 25/30 →
wycięcie na wylot: 120000 − 500π mm³ (dokładnie) → dotknięcie pionowej
krawędzi i R3: −(9 − 9π/4)·20 mm³ → zapis, nowy obszar roboczy, ponowne
otwarcie z repozytorium (geometria i historia bez zmian) → podwójne dotknięcie
szkicu bazowego w drzewie, szerokość 100 → 120, Zakończ: +24000 mm³, otwór
i R3 przebudowane → eksport STEP (objętość zgodna) → ponowny import STEP jako
B-Rep (objętość i liczba brył zgodne). Cała `WorkspaceInstrumentation`
`result=PASS`.

## Granice

- Wymiar położenia między szkicem ściany a krawędziami bryły korzysta z
  wspólnego początku; brak rzutowania krawędzi bryły jako odniesień szkicu.
- Brak wymiaru odległości punktu od linii (ukośnej) i wymiaru wyrównanego.
