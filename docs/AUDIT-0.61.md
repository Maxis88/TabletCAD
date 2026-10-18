# TabletCAD 0.61 — szyki szkicu

0.61.0-sketch-patterns, versionCode 69, schema dokumentu bez zmian (34).

## Zmiany

- `SketchPattern`: `transform` (obrót wokół punktu + przesunięcie) dla linii,
  okręgów, prostokątów, wielokątów, splajnów, łuków i konturów linia/łuk.
  Prostokąt obrócony o kąt inny niż wielokrotność 90° staje się dokładnym
  wielokątem. `grid` (kolumny×wiersze z odstępami X/Y), `linear`, `circular`
  (360° dzieli okrąg równo, kąt częściowy rozkłada od pierwszego do ostatniego).
  Limit 200 elementów, zakres ±5000 mm.
- Geometria → Szyk liniowy / Szyk kołowy z ikonami wektorowymi i panelem
  wartości; wszystkie kopie jedną transakcją Cofnij.

## Weryfikacja

HONOR YLE-W09, Android 16. `SketchPatternProbe` (położenia, promienie, kąty,
obrót prostokąta, odrzucenia) i UI w `SketchOffsetUiProbe` (6 otworów na
okręgu podziałowym Ø40, jedno Cofnij). Cała `WorkspaceInstrumentation`
`result=PASS`.

## Granice

- Kopie są niezależne: zmiana źródła nie aktualizuje szyku (brak cechy
  parametrycznej szyku w szkicu ani szyku brył).
- Środek szyku kołowego podaje się liczbowo (brak wskazania punktu dotykiem).
