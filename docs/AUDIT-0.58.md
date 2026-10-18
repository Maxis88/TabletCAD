# TabletCAD 0.58 — widoki standardowe i bezpieczna kamera

0.58.0-standard-views, versionCode 66, schema dokumentu bez zmian (34).

## Zmiany

- Grupa Widok (tryb modelu): Dopasuj, Izometryczny, Przód, Góra, Prawy bok,
  Rzut prostokątny, Wyczyść widok. Nowe ikony wektorowe: sześcian z wypełnioną
  ścianą widzianą z danego kierunku; rzut prostokątny jako dwa równoległe rzuty.
- Konwencja kamery bez zmian (oś Y modelu jest pionowa w widoku z przodu,
  zgodnie z istniejącymi płaszczyznami szkicu XY/XZ/YZ).
- Dopasuj liczy powiększenie z promienia obwiedni siatki B-Rep i wolnego obszaru
  roboczego (wcześniej tylko zoom=1, co nie mieściło dużych ani nie powiększało
  małych części).
- Kamera perspektywiczna: odległość max(420 mm, 4×promień modelu). Wcześniej
  stała 420 mm sprawiała, że wierzchołki modeli większych niż ~400 mm trafiały
  za oko kamery (dzielenie przez wartość ≤ 0, zniekształcony obraz i wybór).
  Rzut prostokątny używa odległości 10⁶ mm. Ta sama odległość trafia do
  `AxisDrag` (strzałka wyciągania), rzutowania szkicu, pomiarów i siatki.

## Weryfikacja

HONOR YLE-W09, Android 16. `ViewCameraProbe` (wątek UI, izolowane pliki):
model 2000×1000×500 mm mieści się po Dopasuj, wszystkie wierzchołki przed kamerą;
w rzucie prostokątnym proporcje widoków przód 2:1, góra 4:1, prawy bok 1:2
(±0,2 %); orientacja izometryczna −45°/35,264°; mała część zachowuje 420 mm;
`AxisDrag` odwraca rzut dla 420 i 5000 mm. Cała `WorkspaceInstrumentation`
`result=PASS` (w tym dotyk strzałki wyciągania dla wielu orientacji kamery).

## Granice

- Brak interaktywnego ViewCube; brak widoków Tył/Dół/Lewy w menu (obsługiwane
  przez kod `applyStandardView`, nieudostępnione, aby nie przeładować menu).
- (Naprawione w 0.61.1) Odrzucanie ścian tylnych używało przybliżenia
  ortograficznego normalnej.

## 0.61.1 — poprawki widoku

- Odrzucanie ścian tylnych liczone względem rzeczywistego punktu oka
  perspektywy: wcześniej ściany widoczne pod ostrym kątem (np. ścianka otworu
  widziana przez otwór, boki brył przy brzegu ekranu) znikały z widoku
  i z wyboru. ViewCameraProbe: ścianka otworu widoczna w perspektywie
  (≈730 px²), krawędziowo w rzucie prostokątnym (<1 px²).
- Dopasuj w dokumencie bez bryły skaluje i centruje sam szkic (wcześniej
  liczony od początku układu — odległy szkic zostawał mały i poza środkiem).
  W trybie szkicu z bryłą uwzględnia obie geometrie.
- build-ui-tests.ps1 czyści katalogi klas/DEX przed kompilacją testów.
