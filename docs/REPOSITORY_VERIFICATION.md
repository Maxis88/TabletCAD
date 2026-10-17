# Weryfikacja repozytorium — 2026-10-17

Repozytorium: https://github.com/Maxis88/TabletCAD (prywatne).
Zachowano pierwotne 9 commitów do `60a7807` oraz wszystkie wcześniej wersjonowane pliki.
Snapshot bieżącego rozwoju 0.45.0: `6a1dd53`.

## Wykonane sprawdzenia

- Uwierzytelnienie Git, uprawnienie push i pobranie projektu z GitHuba.
- Identyczny SHA i 80 plików początkowego snapshotu w świeżym klonie.
- Kontrola typowych sygnatur tokenów i kluczy prywatnych w źródłach i historii:
  brak trafień. Kontrola wykluczenia lokalnych ustawień, sekretów i buildów.
- Kompilacja 32 plików Java aplikacji i 29 programów Probe z pobranych źródeł.
- SnapshotHistoryProbe wykonany na JVM: wszystkie sprawdzenia PASS.
- Pobranie OCCT V8_0_1 z GitHuba, kontrola przypiętej rewizji i kompilacja od
  zera w osobnym klonie; bez kopiowania istniejących bibliotek ani cache projektu.
- Kompilacja JNI/C++, Java, zasobów i DEX, pakowanie APK i poprawny podpis v3.
- Metadane APK: pl.tabletcad.app, versionCode 52,
  0.45.0-transactional-history, minSdk 34, targetSdk 36.
- Zawartość: classes.dex, 26 bibliotek ARM64 (JNI + 25 OCCT), licencja pod
  prawidłową ścieżką assets/licenses/OCCT-LGPL-2.1.txt.

Test ujawnił i doprowadził do naprawy dwóch błędów skryptu Windows:
limit długości polecenia D8 (klasy są teraz przekazywane jako jeden JAR)
oraz separator ścieżki zasobu licencji (pakowanie przez narzędzie JAR).
Poprawiony skrypt ponownie zbudował i zweryfikował APK w tym samym izolowanym
klonie z OCCT zbudowanym ze źródeł. Końcowe źródła są identyczne z opublikowaną wersją.

## Środowisko i zakres

Windows, JBR z Android Studio, Android SDK 36, Build Tools 36.0.0,
NDK 30.0.16248370 i CMake 4.1.2. Weryfikacja czystego stanu źródeł i zależności
projektu korzystała z już zainstalowanego SDK/JDK; nie był to test instalacji
narzędzi na świeżym systemie operacyjnym. Instrukcja instalacji jest w README.

Nie uruchamiano w tym zadaniu całej regresji ani interfejsu na Androidzie.
Kompilacja 29 programów Probe nie oznacza ich wykonania. Historyczne testy na
urządzeniu są opisane osobno w audycie, nie stanowią wyniku tego zadania.
Nie sprawdzano ścieżki Gradle; obecną ścieżką budowania APK jest skrypt PowerShell.

Lokalny wynik: build/clean-checkout/dist/TabletCAD-0.45.0-transactional-history.apk.
SHA-256: 0EBC12C42F2C9EC1B1E609F6ECBD1CCDE2C279199615C327A92F34930B2E0CDD.
Artefakty i logi pozostają ignorowane przez Git. Klucz debug wygenerowano lokalnie
w testowym klonie i nie przesłano go do repozytorium.
