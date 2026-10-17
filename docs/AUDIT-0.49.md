# TabletCAD 0.49 — manipulator wyciągania

Wersja deweloperska 0.49.0-extrusion-manipulator, versionCode 56.

## Zmiany

- Wyciąganie bazowe, dodanie i wycięcie mają strzałkę związaną z faktyczną
  osią natywnej operacji. XZ bazy zachowuje historyczny kierunek +Y; operacje
  na ścianie używają iloczynu U×V i odwracają go dla wycięcia, zgodnie z OCCT.
- AxisDrag wyznacza parametr najbliższego punktu osi względem promienia
  perspektywicznej kamery. Ruch nie jest przypisany do poziomej osi ekranu.
- Kierunek może przejść przez płaszczyznę. Tryb symetryczny zmienia długość
  całkowitą, dwukrotnie względem ruchu końca wyciągnięcia.
- Etykieta przy strzałce otwiera dokładne pole liczbowe. Uchwyt ma dotykowy
  promień 28 dp; przechwytuje cały gest, więc kamera nie obraca się równocześnie.
- Anulowanie gestu lub dołączenie drugiego palca przywraca wymiar z początku
  gestu. Zatwierdzenie i anulowanie narzędzia korzystają z dotychczasowej transakcji.
- Widok niemal równoległy do osi jest lekko odchylany przy otwarciu narzędzia.
  Matematyka jawnie odrzuca niejednoznaczną konfigurację, zamiast zgadywać wymiar.

## Weryfikacja

- AxisDragProbe: 1200 deterministycznych osi, położeń i ustawień kamery;
  dokładne odtworzenie parametru oraz odrzucenie zera/równoległości/błędnej skali.
  Przeszedł na lokalnej JVM oraz Androidzie.
- Wszystkie 31 zestawów app_process przeszły na tablecie HONOR.
- Dwa zestawy instrumentowane PASS: dotychczasowy WorkspaceUiProbe oraz
  ExtrusionManipulatorProbe. Ten drugi sprawdza XY/XZ/YZ przy czterech ustawieniach
  kamery, faktyczne objętości podglądu OCCT, brak mutacji dokumentu/autosave,
  anulowanie, odwrotny kierunek, symetrię, zapis, undo/redo oraz dodanie i wycięcie.
- APK zbudowany, podpisany dotychczasowym kluczem i zainstalowany przez adb install -r.
- W normalnej aplikacji dotknięto operacji w historii i przeciągnięto widoczną
  strzałkę: wymiar zmienił się z 16.000 do 35.489 mm. Dalsze cechy tego modelu
  uniemożliwiły przebudowę, co zostało zgłoszone bez utraty poprzedniej geometrii.
  Wybrano Anuluj; zrzut po anulowaniu był identyczny z wcześniejszym widokiem.
- Sprawdzony logcat procesu: 0 dopasowań awarii. Dowody lokalne: build/regression049/.

## Pozostałe ograniczenia

Podgląd jest ograniczany do kolejnych aktualizacji co około 40 ms, ale obliczenia
OCCT nadal wykonują się na wątku UI. Nie jest to gwarancja płynności dużego modelu.
Trwająca operacja nie jest automatycznie zatwierdzana po puszczeniu strzałki.
Manipulator nie obejmuje jeszcze wszystkich edytorów wcześniejszych konturów,
trybów do powierzchni/przez wszystko ani fazowania/zaokrąglania.
Nadal brak pełnego narzędzia nowa bryła/join/cut/intersect i reprezentacji wielu brył.
Pozostają ograniczenia z poprzednich audytów. Projekt nie jest ukończony.
