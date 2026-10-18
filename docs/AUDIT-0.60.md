# TabletCAD 0.60 — przedłużanie linii szkicu

0.60.0-sketch-extend, versionCode 68, schema dokumentu bez zmian (34).

## Zmiany

- `SketchExtend`: promień z końca linii wzdłuż jej kierunku; granice to inne
  elementy szkicu — odcinki, prostokąty, wielokąty, okręgi, łuki (tylko
  istniejąca część łuku), kontury linia/łuk i splajny (próbkowanie + bisekcja
  na dokładnym odcinku Béziera). Wynik zachowuje UUID, kierunek i pomocniczość.
  Tryb AUTO wybiera koniec z krótszym przedłużeniem. Linia z zablokowanym
  wymiarem długości jest odrzucana z komunikatem.
- Narzędzie Przytnij → Przedłuż (ikona wektorowa). Zmiana przechodzi przez
  `replaceSketchEntity` (solver więzów, przebudowa zależnych brył, Cofnij).

## Weryfikacja

HONOR YLE-W09, Android 16. `SketchExtendProbe` (dokładne punkty końcowe dla
wszystkich typów granic, kierunek skośny, odrzucenia) i rozszerzony
`SketchOffsetUiProbe` (narzędzie w widoku dociąga linię do krawędzi prostokąta).
Cała `WorkspaceInstrumentation` `result=PASS`.

## Granice

- Brak wyboru końca dotknięciem przy końcu linii (AUTO); brak przedłużania łuków.
- Krawędzie bryły rzutowane na szkic nie są jeszcze granicami.
