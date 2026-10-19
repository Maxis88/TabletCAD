# TabletCAD 0.83.1 — stały dolny pasek historii

Kontynuacja porządkowania interfejsu 0.83. Historia jest teraz stałą częścią
obramowania aplikacji, na pełną szerokość ekranu, pod drzewem i prawym panelem.

## Zmiany

- Pasek ma stałą wysokość 62 dp i nie zmienia szerokości ani zakresu przewijania
  po otwarciu parametrów, wejściu w szkic lub zwinięciu drzewa.
- Grafitowe tło dochodzi do dolnej krawędzi; usunięto jasny pas pod dawnym dokiem.
- Natywne menu przewijają zawartość nad historią. Wysokość jest aktualizowana
  po zmianie rozmiaru okna i obszaru nawigacji systemowej.
- Cofnij/Ponów, wybór, przytrzymanie i przewijanie historii korzystają z nowych
  współrzędnych; gesty pod drzewem nie przewijają drzewa.
- Okno działa na pełnym ekranie. Systemowe paski można przywołać gestem od krawędzi.
- Przyciski osadzone w formularzach mają wspólny styl PanelStyle, również gdy
  domyślny motyw Androida dostarcza już tło RippleDrawable.
- Wersja APK: 0.83.1-ui, kod 94. Format dokumentów i geometria bez zmian.

## Weryfikacja

- Kompilacja Java/C++/DEX i pakowanie APK: PASS; podpis APK v3 zweryfikowany.
- Aktualizacja istniejącej aplikacji na HONOR YLE-W09: PASS, bez odinstalowania.
- WorkspaceUiProbe na tablecie: PASS, w tym kolor obu dolnych rogów,
  stałe granice historii przy obu panelach, przewijanie pod drzewem,
  wybór właściwej przewiniętej operacji i granice natywnego panelu.
- Scenariusz 16.3: PASS na tablecie, łącznie z Ø10, R3, przebudową i STEP.
  Test edycji wymiaru dostosowano do obsługi jednym dotknięciem z wersji 0.82.
  Stary podwójny gest powodował powtarzalny błąd kroku średnicy.
- Zrzuty widocznego okna z otwartym i zamkniętym panelem ocenione: historia
  w obu przypadkach dochodzi do dolnej krawędzi i obu boków.
- Końcowa pełna regresja instrumentowana: PASS, wszystkie 46 zestawów
  (34 próby geometrii/modelu i 12 prób UI), w tym scenariusz 16.3 oraz renderowanie
  gwintów. Log lokalny: build/instrumentation-final-0.83.1.txt.

Ten etap porządkuje dolny układ istniejącego redesignu. Nie oznacza ukończenia
wszystkich funkcji CAD ani weryfikacji każdego menu i każdej konfiguracji Androida.
