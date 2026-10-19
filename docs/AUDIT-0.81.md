# TabletCAD 0.81 — podgląd na żywo, suwaki, odsuwanie ścian

Paczka odpowiada na uwagi z testów 0.80 na tablecie.

## Zmiany

- **Podgląd na żywo każdej operacji.** Wynik jest liczony przez jądro w tle i rysowany na
  modelu w trakcie zmiany parametrów, a nie dopiero po zatwierdzeniu. Dotyczy to operacji:
  - przesuń, obróć i skaluj bryłę,
  - połącz, odejmij i część wspólna brył,
  - podział bryły,
  - lustro oraz szyki brył i operacji,
  - pochylenie, gwint, przejście, przeciągnięcie, powłoka.

  Wspólny mechanizm: `capture()` uruchamia akcję panelu w trybie przechwycenia
  (`previewSink`). Nic nie jest wtedy zatwierdzane, a dokument kandydata trafia do
  `attachLivePreview`.
- **Szkic na żywo.** Zielone, przerywane „duchy” pokazują wynik przed zatwierdzeniem przy:
  - lustrze szkicu (nowy panel: wybór osi + podgląd),
  - szyku szkicu,
  - przesunięciu, obrocie i skalowaniu elementu,
  - odsunięciu konturu,
  - zmianie wymiarów (wynik solvera: przesuwające się elementy).
- **Suwaki.**
  - Szyki szkicu, operacji i brył: liczba kopii oraz odstęp lub kąt.
  - Odsunięcie konturu, kąt pochylenia, grubość powłoki, promień zaokrąglenia i faza,
    głębokość gwintu, odsunięcie i kąt płaszczyzny konstrukcyjnej, odsunięcie ściany.
  - Suwak i pole są sprzężone w obie strony.
- **Płaszczyzna odsunięta od ściany bryły.** Gdy panel jest otwarty, dotknięcie płaskiej
  ściany ustawia ją jako bazę. Wcześniej domyślną bazą była XY, czyli płaszczyzna szkicu.
  Dodatkowo:
  - zielony podgląd płaszczyzny,
  - strzałka przeciągania odległości zaczepiona na wskazanej ścianie.

  To samo działa dla płaszczyzny pod kątem.
- **Nowe narzędzie „Odsuń ścianę”** (Zmień → Odsuń ścianę, rekord −24). Pozwala wypchnąć
  płaską ścianę bryły wzdłuż jej normalnej (dodatnio dodaje materiał, ujemnie wycina).
  Obsługa:
  - strzałka na ścianie, suwak i podgląd na żywo,
  - edycja z historii.

  Ściana jest identyfikowana jak w powłoce i otworze: operacja źródłowa + normalna + punkt.
  Dzięki temu nadąża za edycją wcześniejszych wymiarów.
- Diagnostyka awarii (`tabletcad-crash.txt`) oraz akcje runnera: `exitinfo`, `pull-crash`,
  `git-pull`.

## Testy (chmura)

- Kompilacja Java oraz clang `-Werror` dla jądra: OK.
- `host-kernel-tests.sh`: 23 zestawy PASS, w tym nowy `FaceOffsetKernelProbe` (15 sprawdzeń):
  - objętości po wypchnięciu i wcięciu ściany górnej i bocznej,
  - odmowa usunięcia całej bryły,
  - nieistniejąca ściana jest zgłaszana,
  - wypchnięcie ściany z otworem,
  - przebudowa po zmianie głębokości bazy,
  - referencje i przenumerowanie w historii, zapis i odczyt, etykieta osi czasu.
- Testy na tablecie: do wykonania (wymagany restart runnera).
