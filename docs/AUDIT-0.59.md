# TabletCAD 0.59 — odsunięcie szkicu

0.59.0-sketch-offset, versionCode 67, schema dokumentu bez zmian (34).

## Zmiany

- `SketchOffset`: odsunięcie jako nowa, niezależna geometria szkicu.
  - Linia: równoległa kopia po lewej stronie kierunku (ujemna wartość: prawa).
  - Łuk i okrąg: współśrodkowe, promień r ± d; zapadnięcie (r ≤ 0,01) odrzucone.
  - Prostokąt: powiększony/pomniejszony o d z każdej strony.
  - Wielokąt (Kontur): krawędzie przesunięte wzdłuż normalnej zewnętrznej
    (niezależnie od kierunku obiegu), wierzchołki jako przecięcia — ostre
    narożniki, także wklęsłe. Odwrócony bok lub samoprzecięcie → błąd.
  - Kontur linia/łuk: łuki współśrodkowe, styczne połączenia zostają styczne,
    niestyczne narożniki to dokładne przecięcia linia–linia, linia–okrąg,
    okrąg–okrąg. Zanik łuku/odcinka lub samoprzecięcie → błąd z numerem.
  - Splajn: jawnie nieobsługiwany.
- Narzędzie Geometria → Odsunięcie (szkic) z ikoną wektorową i kontekstowym
  panelem wartości (przecinek dziesiętny, „Do wewnątrz”), Cofnij/Ponów.
- `build-ui-tests.ps1`: klasy testów pakowane do jednego JAR przed d8
  (lista plików przekraczała limit długości linii poleceń Windows).

## Weryfikacja

HONOR YLE-W09, Android 16. `SketchOffsetProbe`: dokładne wierzchołki
(wklęsły kształt L, kwadrat CW), pola konturu z zaokrągleniami równe
A ± P·d + πd² (±0,01 mm²), promienie 5±2, przecięcie linia/łuk w punkcie
analitycznym, odrzucenie zapadnięć; wyciągnięcie odsuniętego konturu daje
w OCCT objętość zgodną z analityczną (1e-6 mm³). `SketchOffsetUiProbe`:
narzędzie tylko w szkicu, bez zaznaczenia prosi o element, panel 2,5 mm
tworzy dokładny prostokąt, Cofnij usuwa kopię. Cała `WorkspaceInstrumentation`
`result=PASS`.

## Granice

- Wartość podaje się w panelu; brak jeszcze uchwytu przeciągania odległości.
- Odsunięcie nie tworzy trwałego więzu odległości ze źródłem (kopia niezależna).
- Brak odsunięcia łańcucha otwartych elementów i splajnów.
