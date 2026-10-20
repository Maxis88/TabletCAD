# TabletCAD 0.89 — kąty i szyk po ścieżce w szkicu

Luki z `STATUS_WYMAGAN_CAD.md` (pkt 1) i z promptu (7.2 „Kąt”, 4.1 „Szyk”).

## Zmiany

- **Kąt między liniami** (relacja `angle`, `NonlinearConstraints`): zorientowany kąt od pierwszej
  linii do drugiej (modulo 180°), rozwiązywany przez ten sam solver co pozostałe relacje
  (zasiew: druga linia obraca się wokół początku, zachowując długość). Analiza stopni swobody
  liczy go jako jeden więz.
  - Wejście: *Więzy → Kąt między liniami ∠* (dwie linie) albo w edytorze wymiarów linii
    *Kąt do innej linii…*; wartość 0–180° z podglądem na żywo.
  - **Widoczny na szkicu**: łuk między liniami w ich przecięciu z wartością „∠ 30°”; dotknięcie
    wartości otwiera edycję. Zmiana pierwszej linii obraca drugą.
  - Zapis w dokumencie jak inne relacje (bez zmiany schematu).
- **Szyk po ścieżce** (*Przekształć → Szyk po ścieżce*, `SketchPathPattern`): zaznaczony element
  powielany w równych odstępach wzdłuż linii, łańcucha, łuku/konturu, splajnu, okręgu lub elipsy
  (otwarta ścieżka: od początku do końca; zamknięta: równo dookoła). Opcje: przeniesienie
  elementu na początek ścieżki, obracanie zgodnie z kierunkiem ścieżki. Podgląd na żywo; kopie są
  niezależnymi elementami (niepowiązanymi parametrycznie).
- Edytor wymiarów elementu ma też *Położenie…* (panel z 0.86).

## Testy

- Host: `SketchAngleHostTest` (20: rozwiązanie 45°, zachowanie długości i linii bazowej, edycja
  na 120°, obrót bazy, JSON, stopnie swobody, zakres, wymaganie linii; szyk po linii co 25 mm,
  promienie, rozkład po okręgu z obrotem, przeniesienie na początek), pełny zestaw host-kernel.
- Urządzenie (do wykonania): kąt z menu Więzy i z edytora linii, edycja dotknięciem łuku;
  szyk po ścieżce na łuku i okręgu.
