# TabletCAD

Tabletowa aplikacja CAD dla Androida 14+. Repozytorium zawiera obecnie etap
`0.9.1-history`: interaktywny interfejs obszaru roboczego, trwały dokument
projektu, JNI/C++ dla
`arm64-v8a` oraz Open CASCADE 8.0.1. Rdzeń tworzy i waliduje rzeczywistą bryłę
B-Rep oraz wykonuje autotest zapisu i ponownego odczytu STEP.

Gotowy APK znajduje się w `dist/TabletCAD-0.9.1-history.apk`. Jest to podpisana
wersja deweloperska dla tabletów ARM64 z Androidem 14 lub nowszym.

Wersja 0.9.1 została zainstalowana i przetestowana dotykowo na HONOR MagicPad4 z MagicOS 10.
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
parametryczne, selektor plików, STL, DXF i arkusze techniczne pozostają kolejnymi
etapami implementacji.

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
