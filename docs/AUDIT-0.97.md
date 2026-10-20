# TabletCAD 0.97 — import 3MF

Luka z pkt 5 `STATUS_WYMAGAN_CAD.md`: 3MF był tylko eksportowany. Dla użytkownika drukującego w
3D to częsty format plików ze slicerów i bibliotek modeli.

## Zmiany

- **Plik → Importuj → 3MF** (`CadExchange.read3mf`): pakiet ZIP, część modelu
  `3D/3dmodel.model` (albo inna `.model`), obiekty z siatkami i komponentami (zagnieżdżenie do 8
  poziomów), elementy `build/item` z przekształceniami 3×4, jednostki modelu (mikron, mm, cm, m,
  cal, stopa) przeliczane na mm. Każdy obiekt jest osobną grupą ścian.
- Import jak STL/OBJ: siatka trójkątów (nie B-Rep), zachowana w projekcie; limity 250 000
  trójkątów i 96 MB modelu; deklaracje DTD i encje są odrzucane (bezpieczny parser XML).
- Odrzucane z konkretnym komunikatem: plik nie-ZIP, brak modelu, brak elementów build, nieznana
  jednostka, indeks trójkąta poza listą wierzchołków.

## Testy

- Host: `ThreeMfHostTest` (10: zgodność z eksportem 3MF dwóch brył, cale i przesunięcie,
  obrócony komponent, pięć przypadków odrzucenia), pełny zestaw host-kernel.
- Urządzenie (do wykonania): import pliku 3MF z PrusaSlicer/Bambu Studio/Cura.
