# TabletCAD 0.93 — gesty z wymagań 6.1

Brakujące pozycje listy 6.1 promptu.

## Zmiany

- **Obrót dwoma palcami** (gest skrętu) obraca widok wokół osi pionowej. Włącza się dopiero po
  wyraźnym skręcie (12°), więc nie zakłóca przesuwania i powiększania dwoma palcami. Nie działa w
  otwartym szkicu patrzącym na płaszczyznę (tam widok pozostaje prostopadły) ani na arkuszu.
- **Dwukrotne dotknięcie** w widoku modelu: na pustym miejscu dopasowuje widok do całego modelu, na
  bryle ustawia widok i punkt obrotu na tej bryle (pozostałe bryły nadal widoczne).
- **Przytrzymanie elementu szkicu** w trybie Edytuj otwiera jego menu (położenie, więzy,
  geometria pomocnicza, rozbijanie, usuwanie…); ruch palca przed upływem czasu przytrzymania
  rozpoczyna zwykłe przesuwanie. Przytrzymanie w trybie rysowania nadal chwyta element (0.86).
- Bez zmian: jeden palec — wybór i narzędzie, przeciągnięcie po pustym miejscu — obrót (model)
  lub przesuwanie (szkic), dwa palce — przesuwanie, ściskanie — powiększenie, przytrzymanie ściany
  — edycja operacji.

## Testy

- Pełny zestaw host-kernel (logika gestów działa na zdarzeniach Androida i będzie sprawdzona na
  urządzeniu).
- Urządzenie (do wykonania): skręt dwoma palcami bez przypadkowego powiększenia, dwukrotne
  dotknięcie pustego miejsca i bryły, przytrzymanie okręgu w trybie Edytuj.
