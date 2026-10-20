# TabletCAD 1.0.0-rc4 — audyt zmian

## Zamknięty splajn jako profil
- Przyczyna błędu: splajn był zawsze otwarty (naturalny, z narożnikiem w punkcie zamknięcia) i nie był rozpoznawany jako profil; dotknięcie pierwszego punktu nie zamykało splajnu.
- Teraz: dotknięcie pierwszego punktu (albo „Zamknij splajn” na pasku opcji) tworzy **okresowy** splajn C2 — bez narożnika.
- Jądro: nowy rodzaj odcinka konturu „2” = krzywa Béziera 3. stopnia (Geom_BezierCurve), dokładna geometria, nie łamana.
  Sprawdzone testem: wyciągnięcie (dodanie, wycięcie), obrót profilu (objętość zgodna z regułą Pappusa) i przejście
  splajn → okrąg. Naprawiono przy tym przejście między profilami o różnej liczbie krawędzi (OCCT dopasowuje je teraz
  automatycznie; wcześniej powstawała niepoprawna bryła). Przeciągnięcie po ścieżce — bez osobnego testu.
- Edycja operacji odtwarza splajn źródłowy (punkty przejścia).
- Test: SplineKernelProbe — objętość występu i kieszeni = pole krzywej × głębokość (±0,2 %), zapis/odczyt, brak narożnika.

## Podział bryły powierzchnią (także falistą)
- Podziel bryłę → „Powierzchnia z krzywej”: linia, łuk lub otwarty splajn ze szkicu jest wyciągany prostopadle do szkicu przez całą bryłę (BRepAlgoAPI_Splitter).
- Część po lewej stronie kierunku krzywej zostaje w bryle, druga staje się nową bryłą; „Odwróć strony” zamienia.
- Operacja jest parametryczna: zmiana krzywej w szkicu przelicza podział.
- Krzywa musi przeciąć bryłę na wylot; inaczej czytelny błąd i model bez zmian.
- Test: SurfaceSplitKernelProbe (fala przez płytę, odwrócenie stron, przekątna, odrzucenie krótkiej krzywej, edycja krzywej).
- Ograniczenia: powierzchnia jest walcowa (krzywa × prosty kierunek); powierzchnie swobodne (siatka krzywych) — kolejny etap.

## Wybór ściany
- Zaznaczanie ściany zapamiętuje dokładny punkt dotknięcia (perspektywicznie poprawny), a nie środek trójkąta siatki — otwór i początek szkicu trafiają tam, gdzie dotknięto.
- Widoczne szkice z drzewa nie są już zaznaczane „przez” bryłę: szkic schowany za ścianą (np. profil pod własnym występem) jest pomijany i dotknięcie wybiera ścianę.
- Test: FacePickHostTest. Tablet: do potwierdzenia na urządzeniu.

## Poprawki po rc4 (bez zmiany wersji)
- Znacznik przyciągania pokazuje rodzaj punktu: Punkt, Środek, Przecięcie, Styczna, Prostopadła, Na krzywej, Poziomo/Pionowo, Zamknij.
- Wysokość powierzchni podziału liczona z rzeczywistego zasięgu brył i położenia szkicu (bryły daleko od szkicu też są przecinane).
