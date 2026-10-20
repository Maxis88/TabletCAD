# TabletCAD 0.91 — okręgi na arkuszu i szybsze zatwierdzanie

## Zmiany

- **Średnica i promień na arkuszu**: w trybie *Wymiaruj* dotknięcie obrysu okręgu lub łuku w
  rzucie dodaje wymiar Ø (okrąg zamknięty) albo R (łuk) z odnośnikiem w kierunku dotknięcia.
  Okręgi są rozpoznawane w rzutach (`TechnicalSheet.circles`: spójne łańcuchy widocznych
  odcinków leżące na jednym okręgu z dokładnością 0,5 %); prostokąty i inne kontury nie są
  mylone z okręgami. Menu wymiaru: średnica/promień, obrót odnośnika o ±45°, usunięcie.
- **Środki otworów jako punkty wymiaru**: dotknięcie środka okręgu wybiera środek — położenie
  otworu można zwymiarować od krawędzi płyty (wymiar poziomy/pionowy od środka).
- Wymiar okręgu, którego otwór zniknął z modelu, jest rysowany na czerwono.
- **Zatwierdzanie po podglądzie**: podglądy operacji (wyciągnięcie, zaokrąglenie, fazowanie, obrót,
  szyki…) liczą siatkę tym samym zapytaniem jądra co sprawdzenie przy „Zastosuj”; to zapytanie
  jest teraz zapamiętywane (`KernelCache`, tryb 0 bez pliku eksportu), więc ponowne sprawdzenie
  po podglądzie nie przelicza historii od nowa.
- `TechnicalSheet` tworzy pędzel dopiero przy rysowaniu — zapytania geometryczne arkusza są
  testowalne bez ekranu.

## Testy

- Host: `SheetDimensionHostTest` (18: rozpoznanie otworu Ø10 i nierozpoznanie prostokąta, środek,
  obrys, wartość Ø, aktualność, narożniki), pełny zestaw host-kernel.
- Urządzenie (do wykonania): Ø otworu w rzucie z góry, położenie otworu od krawędzi, PDF.
