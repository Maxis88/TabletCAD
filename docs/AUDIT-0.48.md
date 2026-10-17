# TabletCAD 0.48 — kontekstowy panel i przewijanie historii

Wersja deweloperska 0.48.0-context-workspace, versionCode 55.
Kontynuacja zweryfikowanych zmian 0.47, opisanych w AUDIT-0.47.md.

## Zmiany

- Prawy panel jest widoczny przy aktywnym edytorze, szkicu, pomiarze lub arkuszu.
  W spoczynku jego szerokość wraca do widoku 3D i paska historii.
- Renderowanie, środek projekcji i hit-testing korzystają z tej samej granicy
  obszaru roboczego. Odzyskana przestrzeń przyjmuje gesty modelu, a formularz
  pozostaje poza obszarem wybierania/obracania geometrii.
- Po zamknięciu panelu komunikaty są nadal dostępne w kompaktowej nakładce
  nad historią, z limitem dwóch linii.
- Historia przewija ikony poziomo. Cofnij/Ponów pozostają nieruchome.
  Wybór po przewinięciu uwzględnia offset i otwiera właściwą cechę.
- Gest rozpoczęty na historii przewija ją i nie obraca kamery. Gesty paneli
  poza obszarem roboczym również nie uruchamiają obrotu.

## Weryfikacja na fizycznym HONOR

- Kompilacja i podpis APK, instalacja przez adb install -r bez kasowania danych.
- Wszystkie 30 dotychczasowych zestawów Probe przeszły po zmianie interfejsu.
- WorkspaceUiProbe przeszedł jako test instrumentowany Androida. Sprawdza
  rysowanie/odtwarzanie stanu Canvas, szerokość viewportu, hit-testing,
  MotionEvent swipe/tap, brak obrotu kamery, właściwe parametry przewiniętej
  cechy, natywny przycisk Anuluj oraz ograniczenia przewijania na obu końcach.
- Normalna aplikacja sprawdzona na ekranie tabletu: otwarcie parametrów
  bazowego wyciągnięcia dotknięciem historii, naciśnięcie Anuluj oraz przesunięcie
  palca na pasku. Nie zatwierdzano zmian w dokumencie użytkownika.
- Zrzuty przed otwarciem i po anulowaniu/przesunięciu są bajtowo identyczne.
  Model zachował orientację, a panel zniknął. Logi/zrzuty: build/regression048/.

Pierwsze próby rysowania w app_process nie przeszły: proces nie posiadał
prawidłowo zainicjalizowanych fontów Androida. Test przeniesiono do osobnego
APK instrumentacji; nie wprowadzano obejść fontów do aplikacji produkcyjnej.

## Nadal otwarte

Historia nie ma jeszcze pełnego systemu podświetlania wszystkich zależnych
ścian/krawędzi, menu długiego przytrzymania ani zmiany kolejności operacji.
Lewe drzewo nie jest pełnym drzewem komponentów/brył. Manipulatory wyciągania,
wybór wielu krawędzi i obliczenia JNI w tle nadal wymagają implementacji.
Pozostają ograniczenia geometrii, solvera i zapisu opisane w audytach 0.46/0.47.
Nie jest to zakończenie całego projektu ani pełna weryfikacja wszystkich gestów.
