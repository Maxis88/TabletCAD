# TabletCAD

Tabletowa aplikacja CAD dla Androida 14+. Repozytorium zawiera obecnie etap
`0.6.1-sketch-workplane`: interaktywny interfejs obszaru roboczego, JNI/C++ dla
`arm64-v8a` oraz Open CASCADE 8.0.1. Rdzeń tworzy i waliduje rzeczywistą bryłę
B-Rep oraz wykonuje autotest zapisu i ponownego odczytu STEP.

Gotowy APK znajduje się w `dist/TabletCAD-0.6.1-sketch-workplane.apk`. Jest to podpisana
wersja deweloperska dla tabletów ARM64 z Androidem 14 lub nowszym.

Wersja 0.6.1 została zainstalowana i przetestowana dotykowo na HONOR MagicPad4 z MagicOS 10.
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
zapis/odczyt STEP. Pełny solver szkicu, rozbudowane operacje
parametryczne, selektor plików, STL, DXF i arkusze techniczne pozostają kolejnymi
etapami implementacji.
