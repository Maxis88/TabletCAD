# TabletCAD 0.55 — szkicowanie i obsługa operacji

Wersja 0.55.0-sketch-workflow, versionCode 62. Baza: b9cb75f z origin/main.
To częściowe wdrożenie listy 14 wymagań z 2026-10-18. Nie oznacza zakończenia
tej listy ani implementacji pełnego solvera, drzewa brył i gwintów.

## Wdrożone zachowania

- Podczas przeciągania widoczna długość i kąt. Po utworzeniu linii otwiera się
  formularz długości i kąta do osi X. Wymiar może być zablokowany.
- W edycji szkicu widoczne wymiary także niewymiarowanych linii/prostokątów,
  promienie okręgów/łuków i długości boków konturów. Widok → Wymiary przełącza
  widoczność. Wymiary, symbole więzów i uchwyty nie są rysowane po edycji.
- Pojedyncze dotknięcie bieżącego szkicu zaznacza jego geometrię. Dwukrotne
  przechodzi do edycji. Osobne małe przyciski edycji i nazwy. Nazwa jest
  częścią dokumentu i archiwizowanego szkicu, także przy pustej geometrii.
- Rysuj grupuje dotychczasowe narzędzia oraz prostokąt od środka, wielokąt
  i splajn. Szersze menu ma wiele wierszy. Zmień w szkicu nazywa się Geometria;
  Przytnij stanowi osobne narzędzie. Lustro obsługuje osie X/Y oraz dowolną
  linię lub linię pomocniczą szkicu.
- Splajn naturalny stopnia 3 przechodzi przez wskazane punkty. Zachowane są
  punkty źródłowe, UUID, rozpoczęty szkic i rodzaj rozpoczętego narzędzia.
  Przesuwanie punktów i całej krzywej, istniejące więzy punktowe i lustro
  zachowują rodzaj krzywej. DXF zawiera analityczny SPLINE, nie łamaną.
- Zaokrąglanie wszystkich narożników prostokąta/zamkniętego konturu odcinków
  tworzy rzeczywiste styczne łuki. Za duży promień jest odrzucany. Nowa krzywa
  zachowuje UUID profilu, może być wyciągana przez OCCT. Wymiary sterujące
  figury trzeba odblokować; nie są samowolnie usuwane.
- Niższa, ciaśniejsza historia. Pojedyncze dotknięcie wybiera wpis;
  dwukrotne otwiera właściwy edytor, także po przewinięciu.
- Wyciąganie na istniejącym modelu ma wybór nowej bryły, połączenia, wycięcia
  i części wspólnej. Nowa bryła pozostaje odrębnym solidem w compound OCCT,
  również gdy zachodzi na poprzednią. Część wspólna jest operacją Common.
  Podgląd i zapis używają tego samego kodowania operacji. Edycja symetrii
  zachowuje rodzaj operacji. Profil pozostaje widoczny w formularzu wyciągania.
- Formularze parametrów dopasowują wysokość do zawartości z ograniczeniem
  do ekranu; większe formularze pozostają przewijalne.
- Zimny start pokazuje modalną bibliotekę projektów i przycisk Nowy projekt.
  Ikona z wersji 0.53 pozostaje bez zmian.

## Stan wobec wymagań

| Punkt użytkownika | Stan i pozostały zakres |
|---|---|
| 1 | Pomiar na żywo i formularz długości/kąta po linii; brak edycji pola podczas trwającego gestu |
| 2 | Wymiary i symbole w edycji, przełącznik wymiarów; splajn nie ma automatycznych wymiarów krzywizny |
| 3 | Bieżący szkic: wybór, edycja dwuklikiem, zmiana nazwy; pełne drzewo wszystkich szkiców nadal otwarte |
| 4 | Grupowanie i uruchamianie rysowania poprawione; pomocnicze figury i osobne narzędzie kwadratu nadal otwarte |
| 5 | Nazwa, osobne Przytnij, lustro względem X/Y i linii wdrożone |
| 6 | Symbole znikają po edycji; dodatkowe nieliniowe więzy nie zostały wdrożone |
| 7 | Splajn oraz zaokrąglanie wszystkich narożników konturu; wybór dwóch osobnych linii i rzeczywiste gwinty nadal otwarte |
| 8 | Gęstość i dwuklik wdrożone; brak dokładnego podświetlania wyniku każdej operacji na końcowej bryle |
| 9 | Wybór rodzaju operacji oraz test częściowego wycięcia wdrożone; brak wyboru konkretnej bryły docelowej w modelu wielobryłowym |
| 10 | Profil pozostaje widoczny, istniejąca symetria zachowana; wielokrotny wybór obszarów dotykiem i dwa niezależne kierunki nadal otwarte |
| 11 | Pełny graf bryła → wykorzystany zakres szkicu, ukrywanie poszczególnych brył i szkiców nadal otwarte |
| 12 | Ikona zachowana |
| 13 | Dopasowanie formularzy wdrożone; statyczny panel kontekstu szkicu nadal ma stały obszar zawartości |
| 14 | Biblioteka na zimnym starcie wdrożona |

## Dane i ograniczenia

Schemat 31 odczytuje wcześniejsze schematy. Dodaje rodzaj splajnu, nazwę
bieżącego szkicu, rodzaj rozpoczętego łańcucha oraz tryby operacji 4/6
(osobna bryła) i 8/10 (Common). Starsza aplikacja nie odczyta schematu 31.
Splajn jest otwartą krzywą; nie jest traktowany jako zamknięty wielokąt.
Zamknięte profile ze splajnami, przycinanie splajnu i jego STEP są poza
tym wdrożeniem. Ograniczenie liczby punktów: 128; współrzędne ±5000 mm.

Nowe bryły nie otrzymały jeszcze stabilnych identyfikatorów brył i osobnych
celów Boolean. Dalsze operacje na compound dotyczą całego modelu. Nie należy
utożsamiać tego z ukończoną obsługą wielu brył z wymagania 11.

## Weryfikacja

- Budowanie przez tools/build-foundation-apk.ps1: Java, C++, DEX, zasoby,
  podpis APK. Instalacja aktualizacji na fizycznym HONOR YLE-W09 przez adb -r.
- Rozszerzona instrumentacja uruchamia BooleanModeProbe, SplineProbe,
  SketchFilletProbe, DirectionProbe, SymmetricProbe, SketchWorkflowProbe,
  SketchTransactionProbe, TimelineProbe, ToolbarProbe oraz cztery zestawy UI.
- Testy sprawdzają dokładne objętości i liczbę solidów OCCT, STEP round-trip,
  odrzucenie pustego Common, częściowe wycinanie, zachowanie rodzaju operacji,
  interpolację i ciągłość C2 splajnu, zapis, lustro, DXF i promienie narożników.
- WorkspaceUiProbe wykonuje trzy rzeczywiste MotionEvent dla splajnu,
  zapis/odczyt autosave, zakończenie sesji, wybór szkicu i krótki formularz.
  Sprawdza też pojedynczy/dwukrotny wybór historii po przewinięciu.
- Testy korzystają z odrębnych katalogów cache, nie z modeli użytkownika.
  MotionEvent w instrumentacji nie potwierdza wszystkich fizycznych gestów,
  obsługi rysika ani palm rejection.
- Logi i zrzuty lokalne: build/regression055/ (ignorowane przez Git).
