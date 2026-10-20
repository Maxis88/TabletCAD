# TabletCAD 0.92 — rysik

Wymaganie 6.2 promptu („obsługa palca i rysika, rozróżnianie rysika i dłoni, precyzyjny wybór,
ograniczenie przypadkowych dotknięć”) nie było dotąd realizowane — wszystkie wskazania były
traktowane jak palec.

## Zmiany

- **Rozpoznanie rysika** (`MotionEvent.getToolType`; końcówka gumki traktowana jak rysik). Do
  pierwszego użycia rysika aplikacja działa dokładnie jak wcześniej.
- **Odrzucanie dłoni** (`StylusFilter`): po użyciu rysika ignorowany jest dotyk palca/dłoni w
  trakcie kreski rysika i przez 350 ms po jej zakończeniu, a także kontakt większy niż 14 mm;
  palce opierające się o ekran w trakcie kreski są odfiltrowywane (dociera tylko wskaźnik rysika).
  Gdy rysik oderwie się przy leżącej dłoni, reszta gestu jest ignorowana.
- **Precyzja**: promień wskazywania elementów i przyciągania jest dla rysika 0,6× promienia palca
  (mniej przypadkowych trafień w sąsiednie elementy).
- **Podgląd nad ekranem** (hover): w otwartym szkicu krzyżyk i znacznik przyciągania pokazują,
  gdzie trafi rysik, zanim dotknie ekranu.
- **Przycisk rysika + przeciągnięcie** przesuwa widok w każdym trybie (rozpoczęta kreska jest
  anulowana).
- **Palec nawiguje**: po użyciu rysika w narzędziach rysowania palec przesuwa widok zamiast
  rysować (komunikat przy pierwszym użyciu); przełącznik „Palec rysuje” w panelu szkicu.

## Testy

- Host: `StylusHostTest` (10: decyzje dla palca, rysika, dłoni, okna czasowego, gumki,
  promienia), pełny zestaw host-kernel.
- Urządzenie (do wykonania, wymaga rysika): rysowanie z dłonią na ekranie, hover, przycisk
  rysika, przełącznik „Palec rysuje”. Tablet bez rysika: brak zmian w działaniu.
