# TabletCAD

Tabletowa aplikacja CAD dla Androida 14+. Repozytorium zawiera obecnie etap
`0.13.0-sketch-plane`: interaktywny interfejs obszaru roboczego, trwały dokument
projektu, JNI/C++ dla
`arm64-v8a` oraz Open CASCADE 8.0.1. Rdzeń tworzy i waliduje rzeczywistą bryłę
B-Rep oraz wykonuje autotest zapisu i ponownego odczytu STEP.

Gotowy APK znajduje się w `dist/TabletCAD-0.13.0-sketch-plane.apk`. Jest to podpisana
wersja deweloperska dla tabletów ARM64 z Androidem 14 lub nowszym.

Wersja 0.13.0 została zainstalowana i przetestowana dotykowo na HONOR MagicPad4 z MagicOS 10.
Naprawia błąd inicjalizacji `WindowInsetsController` występujący przed utworzeniem
widoku dekoracyjnego okna na tym urządzeniu.

## Budowanie

Projekt Gradle używa Android Gradle Plugin 8.6.1 i `compileSdk 36`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat assembleDebug
```

Jeżeli Gradle Wrapper nie jest jeszcze dostępny, można zbudować foundation APK
lokalnym skryptem bez pobierania zależności:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-foundation-apk.ps1
```

## Stan funkcji

Aktywne są: dotykowy obszar roboczy, gest powiększenia/przesunięcia, swobodny
obrót kamery w dwóch osiach, wybór i podświetlanie ścian, wybór płaszczyzny
szkicu XY/XZ/YZ lub płaskiej ściany, numeryczna edycja głębokości,
automatyczne ustawienie kamery prostopadle do płaszczyzny oraz niezależne
elementy szkicu: linia, prostokąt i okrąg,
prostokątny szkic tworzony przeciągnięciem, zmiana głębokości wyciągnięcia,
rzuty prostokątne, podstawowy pomiar, natywny model B-Rep i techniczny
zapis/odczyt STEP. Parametry bryły i encje szkicu mają UUID, są automatycznie
zapisywane w wersjonowanym pliku `.tcad` i odtwarzane po ponownym uruchomieniu.
Zapis używa pliku tymczasowego, synchronizacji i kopii odzyskiwania. Pełny solver szkicu, rozbudowane operacje
parametryczne i pełne arkusze techniczne pozostają kolejnymi etapami implementacji.

Widok 3D korzysta z tessellacji rzeczywistej bryły `TopoDS_Shape` wykonanej przez
OCCT. Selekcja ścian działa na trójkątach tej tessellacji, a normalna wybranej
ściany steruje automatycznym ustawieniem płaszczyzny szkicu.

Zamknięty profil prostokąta lub okręgu na XY/XZ/YZ można wyciągnąć do prawdziwej
bryły B-Rep. Systemowy selektor plików Android zapisuje zweryfikowany STEP,
binarny STL i DXF szkicu 2D. STEP można ponownie otworzyć; import jest walidowany,
tessellowany przez OCCT i zachowywany w dokumencie projektu.

Strzałki historii wykonują rzeczywiste undo/redo całego dokumentu (do 50 migawek)
dla szkicu, płaszczyzny, parametrów, wyciągnięcia i importu. Przywrócenie historii
ponownie przelicza geometrię OCCT i zapisuje odzyskany stan.

Tryb pomiaru wybiera wierzchołki i punkty na widocznych krawędziach tessellacji,
oblicza odległość w przestrzeni 3D i wyświetla asocjacyjną linię wymiarową. Pomiar
pozostaje przywiązany do modelu podczas obrotu kamery. Dla profilu kołowego panel
pokazuje również promień i średnicę.

Tryb modelowania udostępnia rzeczywiste zaokrąglenie wszystkich krawędzi brył
utworzonych w TabletCAD. Promień jest podawany numerycznie w milimetrach,
walidowany przez OCCT, zapisywany w dokumencie, obsługiwany przez undo/redo i
uwzględniany w eksporcie STEP. Nieudana operacja nie zastępuje ostatniej poprawnej
geometrii. Zaokrąglanie dowolnej topologii z importowanego STEP nie jest jeszcze
udostępnione.

Narzędzia wyboru, tworzenia i edycji szkicu, wyciągnięcia, głębokości,
zaokrąglenia, pomiaru i rysunku są dostępne jako kompaktowe ikony na górnym
pasku. Dolny pasek jest osią historii cech modelu z miniaturami początku,
szkicu, wyciągnięcia/importu i zaokrąglenia oraz kontrolkami undo/redo. Encje
szkicu są widoczne wyłącznie podczas tworzenia lub edycji właściwego szkicu.
Każdy szkic zapisuje punkt początku oraz dwie osie lokalnej płaszczyzny, dlatego
jest rzutowany razem z modelem, a nie nakładany na ekran. Zakrzywione ściany są
odrzucane jako płaszczyzny szkicu. Narzędzie Edytuj pozwala wskazać linię,
prostokąt lub okrąg, zmienić jego współrzędne numerycznie albo go usunąć; zmiana
od razu przebudowuje powiązaną geometrię. Drzewo po lewej jest aktywne: otwiera
szkic, wybiera model, edytuje zaokrąglenie i przywraca widok bazowy.
