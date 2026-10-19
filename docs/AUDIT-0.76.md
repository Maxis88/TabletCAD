# TabletCAD 0.76 — zarządzanie historią: wygaszanie, kolejność, znacznik

0.76.0-history, versionCode 85. Schemat .tcad 37 (nowe opcjonalne pole `suppressed`).
Etap bez tabletu; jądro sprawdzone na komputerze (OCCT 8.0.1, Linux x86-64).

## Oś czasu (przytrzymanie operacji)

- Wygaś / Przywróć operację: operacja zostaje w historii, ale nie jest liczona.
  W jądrze zastępuje ją rekord-zaślepka (-22) o tym samym numerze, więc odwołania
  innych operacji (twórca ściany otworu/powłoki/pochylenia, źródła szyków) nie
  zmieniają znaczenia. Szyk wygaszonego źródła jest nieaktywny. Wygaszenie bryły
  używanej przez dalsze operacje jest odrzucane z numerem operacji. Stan zapisywany
  w projekcie; na osi czasu operacja jest wyszarzona i przekreślona.
- Przesuń wcześniej / później: zamiana z sąsiednią operacją z przenumerowaniem
  źródeł szyków, twórców ścian, wygaszeń, zakresów wyciągnięć i powiązań parametrów.
  Odwrócenie zależności (np. szyk przed źródłem, otwór przed operacją, która
  utworzyła jego ścianę) jest odrzucane; wynik przechodzi przez przeliczenie jądra
  jak każda zmiana (przy błędzie dokument bez zmian, można cofnąć).
- Pokaż model do tej operacji (znacznik historii): podgląd stanu po wskazanej
  operacji; dalsze operacje wyszarzone, pomarańczowy znacznik na osi. Stan widoku,
  nie jest zapisywany; wybór narzędzia modelowania wraca na koniec historii
  (narzędzia widoku i pomiarów działają na podglądzie).
- Przelicz model.

## Wyniki (komputer)

`tools/host-kernel-tests.sh`: 18 zestawów PASS, w tym HistoryKernelProbe (25):
objętości po wygaszeniu otworu / źródła szyku, po zapisie i odczycie, znacznik
na początku i po pierwszej operacji, zamiana cięć bez zmiany bryły, odmowa przesunięcia
szyku przed źródło i otworu przed twórcę jego ściany, przenumerowanie twórcy ściany,
odmowa wygaszenia bryły używanej dalej, odrzucenie wygaszenia spoza historii.

## Nie zweryfikowano / do zrobienia

- Brak testów na tablecie. Przenoszenie operacji tylko o jedną pozycję (wielokrotne
  wywołanie); brak przeciągania na osi czasu.
- Nowe operacje zawsze dodawane na końcu (bez wstawiania w miejscu znacznika).
