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

## Testy na tablecie (20.10.2026, Honor YLE-W09, Android 16)
- APK 1.0.0-rc4 (gałąź claude/cad-0.86, commit 68a393c) zbudowany przenośnym JDK 21, zainstalowany przez ADB.
- Instrumentacja na urządzeniu: **53/53 PASS** (39 sond jądra i logiki, w tym EdgeRepair, Spline, SurfaceSplit; 14 sond UI z prawdziwymi zdarzeniami dotyku).
- Logcat crash: pusty.
- Błędy znalezione i naprawione podczas testów:
  - „Wyciągnij”, narzędzia bryłowe i Gwint uruchomione z otwartego szkicu tylko zatwierdzały szkic (zatwierdzanie w tle od 0.87) — teraz narzędzie otwiera się po przebudowie.
  - Napis Ø wewnątrz małego okręgu przejmował dotyk: przeciągnięcie, przytrzymanie (podczas rysowania) i zaznaczanie wielu elementów nie działały — naprawione.
  - Przeciągnięcie zaznaczonego okręgu za środek przesuwało tylko jego, nie całe zaznaczenie — naprawione.
  - Budowanie: brak jar.exe/javac.exe w C:\Program Files\Android — skrypty pakują APK bez jar.exe i używają third_party\jdk.
- Nie sprawdzone automatycznie (do ręcznej oceny): wygoda paska opcji, położenie małych okienek przy klawiaturze, rysowanie rysikiem.

## Poprawki 20.10 po uwagach z tabletu
- Wielokąt (i kwadrat, prostokąt od środka) widoczny na żywo podczas rysowania.
- Wielokąt foremny: przeciągnięcie wierzchołka skaluje i obraca cały wielokąt (zostaje foremny).
- Opcje wyciągnięcia stoją obok strzałki, nie na niej (CompactPlacement.beside).
- Linie wymiarowe można odsuwać od elementu przeciągnięciem wartości (zapis w projekcie: dimensionOffsets).
- Okręgi: Ø zamiast R w szkicu i w pomiarze krawędzi.
- Dotknięcie ściany zaznacza tylko tę ścianę (bez podświetlania całej operacji).
- Przycinanie linii i łuków na elipsach (dokładnie) i splajnach (TrimCurvesHostTest); przycinanie samej elipsy — jeszcze nie.
- Nowe: podział bryły powierzchnią przez kilka krzywych z różnych szkiców (rekord -27, SurfaceSplitKernelProbe).

## Wydajność (20.10)
- Przyczyna ~1,5 s podglądu na tablecie: wyszukiwanie otworów klasyfikowało punkty względem całej bryły (z gwintem bardzo kosztowne). Teraz rozpoznanie po orientacji ściany walcowej — model z gwintem M10: 750 ms → <1 ms; krok podglądu 800 → 90 ms (PreviewSpeedProbe). Potwierdzone przez użytkownika na tablecie.
- Czasy etapów zapisywane w logcat (TabletCAD-perf); na Honorze logcat jest szyfrowany.
- Auto-wyciągnięcie: Połącz, gdy narzędzie tylko częściowo zachodzi na materiał (AutoExtrusionKernelProbe).
- Wyciągnij przy zaznaczonej ścianie = odsunięcie ściany. Kontrast cieniowania (ShadeModel). Małe okienka bez opisów.

## 1.0.0-rc5

- **Napis** (Szkic → Rysuj → Napis): treść, wysokość liter, układ płasko / po okręgu (na zewnątrz,
  wewnątrz), kąt, promień, 4 czcionki systemowe (offline), odstęp liter. Kontury liter z wyspami
  (O, A, B…) — rekord jądra -28. Edycja: dotknięcie napisu w trybie Edytuj.
- **Wyciągnięcie napisu**: Połącz (wypukły), Wytnij (grawer), Nowa bryła, Część wspólna; głębokość
  z suwakiem, podgląd, kierunek, symetria. Operacja śledzi edycję napisu w szkicu.
  Ograniczenie: napis nie może być pierwszą bryłą pustego projektu (najpierw np. płytka).
- **Gwinty**: OCCT po cichu pomijał wycięcie gwintu na pełną długość dla niektórych skoków (M3/M4)
  — ponowienie z obróconym początkiem gwintu i weryfikacja usuniętej objętości (ThreadVariantsProbe).
- **Wiązanie kątowe** ścian płaskich (MateKernelProbe).
- Host: 46 zestawów PASS. Tablet: NIEPRZEPROWADZONE (AndroidTextProbe — czcionki urządzenia — tylko na tablecie).

## 1.0.0-rc6 (seria)

- **Przycinanie elipsy**: elipsa przecięta w ≥2 miejscach → łuk eliptyczny (nowy element „Łuk eliptyczny”);
  łuk dalej przycinany (także na dwie części), przedłużanie linii do elipsy/łuku, przesuwanie, obrót, skala,
  szyki, lustro, obrys (splajn), edycja liczbowa (środek, półosie, obrót, t₀, t₁), zapis w projekcie.
- **Profil z łukiem eliptycznym**: Połącz kontur łączy łuk eliptyczny z liniami i łukami; w profilu łuk jest
  zastępowany łukami kołowymi z odchyłką ≤ 0,0001 mm (objętość wyciągnięcia ≤ 0,01 % od dokładnej).
- **DXF – import**: ELLIPSE (pełna i łuk), POINT, SPLINE (punkty dopasowania, postać Béziera z eksportu
  TabletCAD dokładnie, inne B-splajny przez interpolację), LWPOLYLINE z wybrzuszeniami (łuki).
  Eksport łuku eliptycznego jako ELLIPSE z parametrami.
- **Obrys** całego zamkniętego łańcucha linii/łuków z jednego elementu; zastąpienie oryginału.
- **Napis**: strzałka głębokości na modelu, domyślnie grawer (wycięcie z bryły).
- **Suwaki** bez sztywnych limitów (zakres z rozmiaru modelu, rozszerzanie przy końcu).
- **Obrót elipsy** (dokładna elipsa w jądrze); obrót jako nowa bryła obsługiwany w jądrze.
- Host: 49 zestawów PASS (EllipseArcKernelProbe, SliderRangeHostTest, SketchOffsetCurvesHostTest).
- **Obrót profilu → Nowa bryła** (podgląd i zapis jako osobna bryła).
- **Łuk eliptyczny: wymiary na szkicu** a, b i rozwarcie ∠ (dotknięcie → zmiana wartości).
- **Podział CadWorkspaceView (początek)**: TextTools (napisy) i OutlineTools (obrys) jako osobne moduły.
- **Podział CadWorkspaceView**: 9256 → ok. 6550 linii. Moduły: TextTools, OutlineTools, ThreadTools, RevolveTools,
  EdgeTools, MateTools, BodyTools, ShellTools, SketchPatternTools, FeaturePatternTools, HoleTools, DatumTools,
  ExtrusionDialogs, SheetTools, MaterialTools, AssemblyTools, SketchPositionTools, SketchConstraintTools,
  SketchMirrorTools, ProjectFileTools, FeatureEditorTools, MeasureTools. Zachowanie bez zmian (przeniesienie kodu);
  wejścia używane przez sondy UI (refleksja) pozostały w widoku jako delegaty.
