# TabletCAD 0.57 — diagnostyka jądra CAD

0.57.0-kernel-diagnostics, versionCode 64, schema dokumentu bez zmian (34).
Baza: e2df37e (main, 0.56.0).

## Zmiany

- `native_cad_engine.cpp`: lokalny dla wątku rekord błędu (`feature`, `message`).
  Każda ścieżka niepowodzenia `evaluateRecords`, operacji Boolean, zaokrągleń,
  faz, gwintów, obrotu profilu, bryły bazowej, importu i zapisu STEP podaje
  konkretną przyczynę. Wyjątki `Standard_Failure` są opisywane `what()`.
  Indeks `-1` oznacza bryłę bazową, `-2` etap wyjścia (STEP/triangulacja).
- JNI `nativeLastError`, Java `NativeCadEngine.lastFailure()`,
  `evaluateModelChecked()` i `KernelException` (feature, reason). Błąd odczytuje
  ten sam wątek, który liczył (synchroniczna przebudowa i `commitProjectAsync`).
- `CadWorkspaceView.kernelFailureMessage`: komunikat „Operacja „Wycięcie” (nr 3):
  … Poprzednia geometria została zachowana.” Nazwy pochodzą z `CadTimeline`.
  Zachowanie transakcji bez zmian: nieudany wynik nie zastępuje modelu.
- Narzędzie `tools/TabletCAD-TestRunner.ps1`: lokalny wykonawca z białą listą
  akcji (build, ADB install/launch/instrumentacja, logcat, zrzut ekranu,
  tap/swipe, wybrane operacje Git bez force). Kolejka w `.debug/runner`.
- Stare ręczne łatki i pliki `.bak` przeniesione do ignorowanego `tmp/legacy-patches`.

## Weryfikacja

Fizyczny HONOR YLE-W09, Android 16 (SDK 36), ARM64, 1920×3000.
`KernelDiagnosticsProbe` (dokładne właściwości B-Rep OCCT):

- płyta 100×60×20 = 120000 mm³; otwór przelotowy Ø10 = 120000 − 2000π;
  zaokrąglenie R3 pionowej krawędzi = −(9 − 9π/4)·20; tolerancja 1e-6 mm³,
- edycja szkicu bazy 100 → 120 mm zachowuje otwór i R3 (+24000 mm³),
- eksport STEP i ponowny odczyt: ta sama objętość (1e-4 mm³),
- diagnostyka: wycięcie poza bryłą (#1), rozłączne dodanie (#0), R70 (#1),
  utracona krawędź (#0), błąd trzeciej operacji (#2); po błędzie poprawny model
  przelicza się bez pozostałości stanu błędu.

Cała `WorkspaceInstrumentation` (w tym TabletFlowProbe z dotykiem widocznego
okna) zakończona `result=PASS` na tablecie.

## 0.57.1 — przyczyny w podglądach

Podglądy wyciągnięcia, wycięcia, obrotu, zaokrąglenia/fazy i wieloprofilowego
wyciągania pokazują przyczynę z jądra zamiast ogólnego „Brak poprawnego wyniku”.
TabletFlowProbe w widocznym oknie zatwierdza wycięcie poza bryłą i sprawdza
komunikat „Operacja „Wycięcie” (nr 3): … nie przecina bryły … Poprzednia
geometria została zachowana”, niezmienioną historię i dokładną objętość.

## Granice

- Nie każda przyczyna jest rozróżniona: część walidacji rekordów zwraca ogólne
  „Niepoprawne parametry operacji w historii”.
