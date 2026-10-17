# TabletCAD 0.53 — ikona aplikacji

Wersja 0.53.0-application-icon, versionCode 60.

Grafika przekazana przez użytkownika jest ikoną launchera. Zachowano jej
przezroczystość i proporcje; adaptacyjny zasób ma granatowe tło i margines
bezpieczny dla okrągłych masek. Dodano także zasoby pięciu gęstości ekranów.
Manifest wskazuje ikonę zwykłą i okrągłą.

Weryfikacja: kompilacja Java/C++/zasobów i podpis APK poprawne. Aktualizacja
przez adb install -r na fizycznym HONOR YLE-W09 zakończona powodzeniem.
Nowa ikona widoczna na pulpicie i w docku; dotknięcie ikony otwiera aplikację
z poprzednią bryłą. Android potwierdza versionCode 60. Logcat uruchomionego
procesu: zero dopasowań FATAL EXCEPTION/Fatal signal. Zrzuty i log:
build/regression053/. Nie zmieniano geometrii ani formatu dokumentów;
regresji geometrii nie powtarzano dla samej zmiany zasobów.

Zakres funkcjonalny i ograniczenia pozostają opisane w AUDIT-0.52.md.
Pełny projekt nadal jest rozwijany.
