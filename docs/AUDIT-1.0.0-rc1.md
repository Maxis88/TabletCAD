# TabletCAD 1.0.0-rc1 — kandydat do wydania 1.0

## Zmiana w tej wersji

- **Odległość między elementami** (7.2 promptu): *Więzy → Odległość punkt–linia / punkt–punkt*.
  Najpierw punkt (koniec linii, środek okręgu, punkt szkicu…), potem linia (odległość mierzona
  prostopadle, punkt zostaje po tej samej stronie) albo punkt innego elementu (odległość
  bezpośrednia). Wartość z podglądem; wymiar jest widoczny na szkicu z linią wymiarową od punktu
  do stopy na linii, dotknięcie zmienia wartość. Element odniesienia (linia, drugi punkt) zostaje
  na miejscu; przy edycji kąta (0.89) pierwsza linia również zostaje na miejscu.
- Test: `SketchAngleHostTest` (27, w tym środek otworu 15 mm od krawędzi, zmiana na 22,5 mm,
  dwa środki 40 mm, stopnie swobody, JSON, odmowa zera).

## Stan względem promptu (skrót; szczegóły w `STATUS_WYMAGAN_CAD.md`)

| Obszar | Stan |
|---|---|
| 4.1 Narzędzia SZKIC/BRYŁA/KONSTRUKCJA/POMIARY/PLIK/WIDOK | wszystkie pozycje listy mają działające polecenia (Punkt od 0.95) |
| 5 Ikony | wektorowe ikony dla wszystkich poleceń, etykiety po przytrzymaniu |
| 6.1 Gesty | jeden/dwa palce, ściskanie, skręt (0.93), dwukrotne dotknięcie (0.93), przytrzymanie |
| 6.2 Rysik | rozpoznanie, odrzucanie dłoni, precyzja, hover, przycisk (0.92) |
| 7 Szkice | wymiary: długość, poziomy/pionowy, położenie, kąt (0.89), promień/średnica, odległość (rc1); więzy z listy 7.3; status związania; przyciąganie; edycja, przesuwanie, położenie i wyrównanie (0.86–0.88) |
| 8 Bryły | wyciągnięcia (wszystkie warianty), obrót, przeciągnięcie, przejście, otwory, gwinty, zaokrąglenia/fazy (krawędzie podążają — 0.98), powłoka, pochylenie, podział, Boolean, szyki, lustra, transformacje, dosuwanie |
| 9–11 Drzewo, historia, panel | drzewo z menu, historia z edycją/usuwaniem/tłumieniem/znacznikiem, panel kontekstowy |
| 12 Jednostki | mm, cm, m, cal; dokładność wyświetlania; wyrażenia i parametry |
| 13 Pliki | natywny format z historią; STEP, IGES (0.99), STL, OBJ, 3MF (import 0.97), DXF; PDF arkusza A4/A3 z wymiarami (0.90–0.94); autozapis i odzyskiwanie |
| 15 Wydajność | pamięć wyników jądra, przebudowa i eksport w tle, podgląd = zatwierdzenie |

## Lista kontrolna testów na tablecie (przed wydaniem 1.0)

1. Budowanie APK na laptopie: pierwszy raz dobuduje bibliotekę OCCT `TKDEIGES` (kilka minut).
2. Pełny zestaw sond (`TabletCAD-TestRunner.ps1`, w tym `SketchMoveUiProbe` i scenariusz 16.3).
3. Pobrać `tabletcad-crash.txt` i `tabletcad-error.txt` z `Android/data/pl.tabletcad.app/files/`.
4. Ręcznie:
   - przesuwanie okręgu palcem i za wnętrze, przytrzymanie w trybie rysowania i w trybie Edytuj;
   - Położenie i wyrównanie (do punktu, elementu, rzutowanej krawędzi i ściany), grupy;
   - kąt i odległość sterujące, szyk po ścieżce, punkt szkicu;
   - Zakończ/Anuluj szkic (karta postępu, panel przy błędzie);
   - zaokrąglenie na występie, potem przesunięcie występu (krawędź podąża);
   - dosunięcie wystąpienia i zmiana źródła;
   - Rysunek: wymiary liniowe, Ø/R, kąt, tolerancje, A3, PDF;
   - import/eksport IGES, 3MF;
   - gesty: skręt, dwukrotne dotknięcie; rysik (jeśli dostępny).
5. Po zielonych testach: scalenie `claude/cad-0.86` do `main`, oznaczenie wersji 1.0.0.
