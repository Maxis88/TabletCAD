# TabletCAD 0.90 — wymiarowanie arkusza i eksport w tle

Luki z `STATUS_WYMAGAN_CAD.md`: pkt 4 (brak swobodnego wymiarowania arkusza) i pkt 7 (eksport
w wątku UI).

## Zmiany

- **Wymiaruj na arkuszu** (tryb Rysunek, panel boczny): dotknięcie dwóch narożników lub końców
  krawędzi w tym samym rzucie dodaje wymiar. Rodzaj dobierany automatycznie (poziomy, pionowy,
  równoległy do punktów); wartość to rzeczywista odległość w modelu (rzuty prostokątne są w
  skali 1:1 w swoich współrzędnych), niezależna od skali arkusza.
  - Linie pomocnicze, linia wymiarowa ze strzałkami, wartość wzdłuż linii; podgląd pierwszego
    punktu w kolorze akcentu.
  - Dotknięcie wartości: zmiana rodzaju, odsunięcie dalej/bliżej, odwrócenie strony, usunięcie.
    Każda zmiana jest w historii Cofnij.
  - Wymiary są zapisywane w dokumencie (`drawingDimensions`, opcjonalne pole — starsze dokumenty
    otwierają się bez zmian) i trafiają do eksportu PDF.
  - Gdy po zmianie modelu punkt wymiaru nie leży już na wierzchołku rzutu, wymiar jest
    rysowany na czerwono (do usunięcia lub ponownego wskazania) — bez zgadywania.
- **Eksport w tle**: STEP, STL, OBJ, 3MF, DXF, CSV i PDF są zapisywane na wątku roboczym z kartą
  postępu „Eksport …”; dotyk czeka, wynik pokazuje komunikat.

## Testy

- Host: `SheetDimensionHostTest` (11: rodzaje, wartości, mapowanie ekran→papier, zapis i odczyt,
  odrzucenie niepoprawnych rekordów), pełny zestaw host-kernel.
- Urządzenie (do wykonania): dodanie wymiaru w rzucie z przodu i z góry, menu wymiaru, eksport
  PDF z wymiarami, eksport STEP/STL z kartą postępu.
