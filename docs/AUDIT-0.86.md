# TabletCAD 0.86 — przesuwanie i pozycjonowanie szkicu, stabilność, wydajność

Zgłoszenia użytkownika (19.10): „po narysowaniu okręgu nie mogę go przesunąć — ruszam obrotem
bryły”, „brakuje funkcji, która pozwoli dobrze wypozycjonować szkic względem innych elementów”,
„projekcja z zaznaczonego elementu bryły, potem wyrównanie do krawędzi albo do środka”,
„zakończenie/anulowanie szkicu i edycja wymiaru trwa”, „czasem nie da się zakończyć ani anulować
szkicu, boczne menu zostaje, a włączenie czegoś innego kończy się awarią”.

## Przyczyny

- `orbitAllowed()` blokował obrót tylko w trybie *Szkic*; w *Edytuj szkic* każdy ruch jednym
  palcem poza konturem obracał kamerę.
- Element dało się chwycić tylko za kontur (±16 dp) — dotknięcie wnętrza okręgu trafiało w gest
  kamery.
- W trybie rysowania dotyk zawsze zaczynał nowy element; nie było sposobu chwycenia istniejącego.
- Wymiary położenia istniały, ale były ukryte w menu Geometria i działały tylko od początku układu.
- Każda zmiana w otwartym szkicu przebudowywała w OCCT niezmienioną bryłę bazową; anulowanie
  przebudowywało ją jeszcze raz. Każdy zapis parsował też dwa poprzednie pliki projektu.
- Nieudane zatwierdzenie zostawiało szkic bez wyjścia, a anulowanie wymagało udanej przebudowy.
  Wyjątek w poleceniu, dotyku lub rysowaniu zamykał aplikację.

## Zmiany

- **Szkic na płaszczyźnie**: jeden palec na pustym miejscu przesuwa widok (pan), nigdy nie obraca;
  obrót tylko narzędziem *Obróć widok* (bez zmian), dwa palce jak dotąd.
- **Chwytanie za wnętrze** (`SketchHit`): dotknięcie wnętrza zamkniętego elementu chwyta
  najmniejszy element zawierający punkt (otwór przed płytą).
- **Przytrzymanie w trybie rysowania** chwyta element pod palcem (wibracja), przełącza na Edytuj
  i przesuwa go; krótkie dotknięcie elementu zaznacza go i podpowiada przytrzymanie.
- **Położenie elementu** (`SketchPosition`, narzędzie *Przekształć → Położenie*, panel boczny
  zaznaczonego elementu, menu Geometria): punkt elementu (środek okręgu/prostokąta, narożnik,
  wierzchołek, koniec), odniesienie — początek układu, punkt lub element szkicu albo **krawędź
  bryły, która jest automatycznie rzutowana** — odległości X/Y z podglądem i opcjonalnymi
  sterującymi wymiarami położenia (widocznymi na szkicu, zastępującymi poprzednie wymiary tego
  punktu). **Wyrównanie** do elementu: środek do środka (współśrodkowo), środek w X/Y, do lewej,
  prawej, górnej, dolnej krawędzi; z blokadą wymiarem.
- **Wydajność**: `KernelCache` zapamiętuje wyniki czystych zapytań jądra (model, krawędzie,
  analiza ścian, właściwości) dla identycznych danych wejściowych — edycja szkicu, edycja
  wymiaru i anulowanie nie przebudowują ponownie tej samej bryły. Anulowanie szkicu przywraca
  dokument bez przebudowy (wyświetlana bryła jest już bryłą bazową). Zapis pomija ponowne
  parsowanie plików, które repozytorium samo zapisało i zweryfikowało.
- **Stabilność**: nieudane zatwierdzenie szkicu otwiera panel *Wróć do szkicu / Odrzuć zmiany
  szkicu*. Wyjątki w poleceniach paska, przyciskach paneli, dotyku i rysowaniu są przechwytywane:
  stan interakcji jest czyszczony, użytkownik dostaje komunikat, a stos trafia do
  `Android/data/pl.tabletcad.app/files/tabletcad-error.txt` (do pobrania przez adb).

## Testy

- Host: `SketchPositionHostTest` (26), `KernelCacheHostTest` (7), pełny zestaw host-kernel.
- Urządzenie (do wykonania): `SketchMoveUiProbe` — chwyt za wnętrze bez obrotu kamery, pan na
  pustym miejscu, przytrzymanie w trybie rysowania, Położenie X25/Y40 z dwoma wymiarami.
  Do sprawdzenia: `tabletcad-crash.txt` / `tabletcad-error.txt` z urządzenia (przyczyna awarii
  po nieudanym zakończeniu szkicu).
