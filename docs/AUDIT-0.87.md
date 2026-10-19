# TabletCAD 0.87 — rzutowanie ścian i przebudowa w tle

Kontynuacja zgłoszeń z 19.10: „projekcja rzutu z zaznaczonego elementu bryły, a później opcja
wyrównania do krawędzi albo do środka” oraz „zakończenie szkicu trwa”.

## Zmiany

- **Rzutuj ścianę**: w trybie rzutowania dotknięcie ściany (a nie krawędzi) rzutuje wszystkie
  krawędzie ograniczające tę ścianę (`EdgeProjection.faceEdges`: każdy odcinek krawędzi leży na
  trójkącie tej ściany; tolerancja 1 µm dla ścian płaskich). Rzutowane elementy są
  unieruchomione jak dotąd.
- **Przycisk w panelu szkicu**: „Rzutuj z bryły (krawędź lub ściana)” uruchamia rzutowanie bez
  menu, „Zakończ rzutowanie” je kończy.
- **Położenie względem ściany**: przy wskazywaniu odniesienia dotknięcie ściany bryły rzutuje jej
  obrys i używa go jako odniesienia; wyrównanie (środek do środka, środek X/Y, krawędzie) działa
  na obrysie całej ściany (`SketchPosition.align` dla grupy elementów), z blokadą wymiarami od
  narożnika obrysu.
- **Zakończ szkic w tle**: kosztowna przebudowa OCCT po zatwierdzeniu szkicu wykonuje się na
  wątku roboczym (wypełnia `KernelCache`), a potem na wątku UI zatwierdzenie tylko podmienia
  wynik. Widok pokazuje kartę „Przebudowa bryły ze szkicu…” z animacją; dotyk, polecenia i
  przycisk Wstecz czekają. Widoki niepodłączone do okna (eksport, testy) zatwierdzają od razu.
- Anulowanie szkicu: zapis przywracanego dokumentu następuje przed podmianą — nieudany zapis
  zostawia szkic roboczy i historię bez zmian.

## Testy

- Host: `FaceProjectionHostTest` (krawędzie ściany górnej i przedniej prostopadłościanu,
  wyśrodkowanie okręgu na obrysie z wymiarami, dosunięcie do krawędzi), pełny zestaw host-kernel.
- Sondy urządzenia dostosowane do przebudowy w tle (`CadScenarioProbe.settle`, `TabletFlowProbe`).
- Urządzenie (do wykonania): rzutowanie ściany w szkicu na ścianie bocznej, Położenie względem
  ściany, czas zakończenia szkicu z kartą postępu.
