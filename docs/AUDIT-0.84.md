# TabletCAD 0.84 — nawigacja i porządki (bez tabletu)

Paczka przygotowana bez dostępu do tabletu: kompilacja, testy w chmurze i kompilacja testów
instrumentacyjnych przeszły; uruchomienie na urządzeniu jest wymagane przed scaleniem z `main`.

- **Kostka nawigacyjna** w rogu widoku zamiast nieruchomego znacznika osi: obraca się razem z
  kamerą, dotknięcie ściany ustawia widok (Przód, Tył, Góra, Dół, Prawy, Lewy), przycisk ⌂ wraca
  do widoku izometrycznego; osie X/Y/Z obracają się z modelem. W otwartym szkicu pozostaje
  prosty znacznik osi. Test: `ViewCubeHostTest` (zgodność z rzutowaniem modelu, trafienia).
- **Biblioteka projektów** w stylu paneli: obramowane „Odśwież”, czerwone „Usuń” (przygaszone dla
  otwartego projektu), neutralne tło miniatury zamiast ikony galerii, wyraźniejsze opisy.
- **Panel szkicu**: przyciski płaszczyzn XY/XZ/YZ jako jasne przyciski z ikoną płaszczyzny.
- **Test akceptacyjny A** (`AcceptanceAKernelProbe`): płyta 100×60×20 z czterema otworami Ø6,5
  zwymiarowanymi od krawędzi; po zmianie na 140×80×30 i po zapisie/odczycie oraz zmianie na
  120×70×25 każdy otwór zostaje 10 mm od swoich krawędzi i przechodzi na wylot (objętości
  analityczne).
- `STATUS_WYMAGAN_CAD.md`: lista braków zaktualizowana do stanu 0.84.
