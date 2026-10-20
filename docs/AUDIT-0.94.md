# TabletCAD 0.94 — powiązane wymiary arkusza, kąty, tolerancje

Pozostałe problemy z raportu 0.93: wymiary arkusza nie podążały za zmianą modelu, brak kątów i
tolerancji na arkuszu.

## Zmiany

- **Wymiary podążają za modelem**: przy tworzeniu wymiaru zapisywane jest położenie jego punktów
  względem obrysu rzutu. Gdy po zmianie modelu punkt nie jest już wierzchołkiem rzutu, jest
  szukany w miejscu, w które przechodzi jego położenie względne (rzuty rozciągają się razem ze
  zmienionym wymiarem), i przypinany do najbliższego wierzchołka lub środka okręgu (zasięg 8 %
  przekątnej rzutu). Wymiar okręgu szuka okręgu w przesuniętym miejscu. Tylko gdy geometrii nie
  da się odnaleźć, wymiar jest czerwony. Edycja wymiaru zapisuje już przypiętą postać.
- **Kąt na arkuszu**: *Wymiaruj kąt* — wierzchołek i końce dwóch ramion w jednym rzucie; łuk z
  wartością w stopniach, menu: łuk dalej/bliżej, tolerancja, usunięcie.
- **Tolerancje**: każdy wymiar arkusza (liniowy, Ø, R, kąt) ma *Tolerancja…* — odchyłka górna i
  dolna; równe dają zapis „±”, różne „+a/−b”. Drukowane w PDF.
- Format: rekord wymiaru ma 15 wartości; rekordy z 0.90/0.91 (7 wartości) są uzupełniane przy
  odczycie (bez położenia względnego — takie wymiary nie podążają, jak dotąd).

## Testy

- Host: `SheetDimensionHostTest` (26: m.in. szerokość 100 → 120 podąża, otwór przesunięty o
  5 mm zachowuje Ø10, rekord bez kotwic nie jest zgadywany, kąt 90°, zapis tolerancji).
- Urządzenie (do wykonania): wymiar szerokości i otworu, zmiana szkicu bazowego, powrót do arkusza.
