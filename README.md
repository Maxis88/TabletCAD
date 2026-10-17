# TabletCAD

Natywna aplikacja CAD offline na tablety ARM64 z Androidem 14 lub nowszym.
Wersja źródeł: **0.51.0-multi-edge-treatment**, versionCode **58**.
Główne repozytorium: https://github.com/Maxis88/TabletCAD, gałąź `main`.

## Architektura i funkcje

- Java, Android View/Canvas: MainActivity i CadWorkspaceView obsługują narzędzia,
  kamerę, dotyk, rysowanie i wybór geometrii. Aplikacja nie używa WebView.
- C++20/JNI, NativeCadEngine i Open CASCADE 8.0.1: rzeczywiste bryły B-Rep,
  wyciągnięcie, obrót profilu, dodawanie/wycinanie, fazowanie, zaokrąglanie,
  tessellacja, walidacja topologii oraz STEP.
- CadProject i ProjectRepository: UUID, wersjonowane dokumenty `.tcad`, biblioteka,
  robocze sesje szkicu, historia operacji, migawki undo/redo i zapis z backupem.
- Szkice: płaszczyzny XY/XZ/YZ i płaskie ściany, linie, polilinie, prostokąty,
  okręgi, łuki, kontury, przycinanie, odbicie, podstawowe więzy i wymiary.
- Wymiana STEP/STL/DXF, pomiary krawędzi OCCT, objętość, pole i centroid.

To wersja rozwojowa. Solver szkicu i odniesienia topologiczne mają ograniczony
zakres, część obliczeń blokuje UI. Pełne komponenty i dokumentacja techniczna
pozostają nieukończone. Bieżące ograniczenia: [audyt 0.51](docs/AUDIT-0.51.md)
i [mapa wymagań](STATUS_WYMAGAN_CAD.md). Starszy opis architektury zawiera cele,
nie gwarancje aktualnego zachowania. Zachowano [historię rozwoju](docs/DEVELOPMENT_HISTORY.md).

W 0.46 przebudowa przygotowuje nowy bufor geometrii przed zastąpieniem
poprzedniego. Błędny wynik i utracony profil nie powodują zastąpienia bryły
pustą siatką ani domyślnym pudełkiem. Wspólna transakcja podstawowych zmian
modelu pracuje na kopii dokumentu; nieudana geometria lub zapis zachowuje
poprzedni dokument, siatkę oraz stosy Cofnij/Ponów.

## Budowanie na Windows ze świeżego klonu

Wymagania: Git, PowerShell, JDK 17 lub nowszy (sprawdzana ścieżka używa JBR
Android Studio), Android SDK z pakietami `platforms;android-36`,
`build-tools;36.0.0`, `ndk;30.0.16248370`, `cmake;4.1.2`.
Zainstaluj pakiety przez SDK Manager w Android Studio i zaakceptuj ich licencje.
Pierwsze przygotowanie OCCT wymaga internetu oraz miejsca na kompilację C++.

```powershell
git clone https://github.com/Maxis88/TabletCAD.git
cd TabletCAD
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\tools\prepare-occt.ps1 -Sdk $env:ANDROID_HOME
.\tools\build-foundation-apk.ps1 -Sdk $env:ANDROID_HOME -Jdk 'C:\Program Files\Android\Android Studio\jbr'
```

`prepare-occt.ps1` pobiera tag V8_0_1 i sprawdza rewizję
`b8f597c677811d1f9f4d8a97f5ae2825c0353a42`, następnie buduje wymagane biblioteki
ARM64. Nie nadpisuje lokalnie zmienionych źródeł zależności.
Wynik: `dist/TabletCAD-0.51.0-multi-edge-treatment.apk`.
Skrypt buduje Java/C++/DEX i zasoby, generuje lokalny klucz debug i weryfikuje
podpis APK. Standardowe hasło Android debug służy tylko do lokalnego klucza
deweloperskiego; nie wolno używać go do podpisywania wydań produkcyjnych.
Klucz pozostaje w ignorowanym `.debug/` i nie trafia do Git.

Pliki Gradle są zachowane dla Android Studio. Projekt obecnie nie zawiera
Gradle Wrappera; `gradlew.bat` nie jest dostępnym poleceniem. Powyższy skrypt
jest obecną ścieżką odtwarzalnej kompilacji APK. Sama konfiguracja Gradle nie
przygotowuje OCCT ani pełnego pakowania jego bibliotek.

## Weryfikacja

Kompilacja APK sprawdza Java, C++, zasoby i podpis. `tools/*Probe.java` to
samodzielne testy regresyjne uruchamiane na Androidzie przez `app_process`;
nie są automatycznie wykonywane przez Gradle/JUnit. Wymagają kompilacji z
klasami aplikacji, DEX i dostępu do natywnej biblioteki na urządzeniu.
Szczegóły wcześniejszych testów opisuje audyt. Testowanie interfejsu i geometrii
należy wykonywać na osobnych dokumentach, bez modyfikowania danych użytkownika.

Testy renderowania i gestów `WorkspaceUiProbe`, `ExtrusionManipulatorProbe` i `EdgeGroupUiProbe` wymagają normalnego środowiska
aplikacji, więc działa przez osobny APK instrumentacji, a nie `app_process`.
Po zbudowaniu i zainstalowaniu bieżącej aplikacji:

```powershell
.\tools\build-ui-tests.ps1 -Sdk $env:ANDROID_HOME
adb install -r .\build\ui-tests\TabletCAD-ui-tests.apk
adb shell am instrument -w -r pl.tabletcad.test/pl.tabletcad.test.WorkspaceInstrumentation
```

Oczekiwany wynik: `result=PASS`. Test tworzy dokumenty w odrębnym podfolderze
cache aplikacji. Kod produkcyjny nie ma testowych intencji ani przełączników
podmieniających dane użytkownika. Testowy APK jest podpisywany lokalnym kluczem
debug, tym samym co aplikacja zbudowana skryptem.

## Rozwój i bezpieczeństwo repozytorium

Pracuj na `codex/<opis>`, zapisuj zakończone i sprawdzone zmiany jako czytelne
commity, integruj z `main` po weryfikacji. Nie przepisuj historii przez force push.
Zasady dla Codex: [AGENTS.md](AGENTS.md).

Źródła Java/C++, zasoby Androida, konfiguracja Gradle, narzędzia i dokumentacja
są wersjonowane. SDK/NDK, OCCT pobierany z upstreamu, build, dist, cache,
lokalne ustawienia i dane podpisywania są ignorowane. Nie dodawaj tokenów,
haseł ani prywatnych kluczy. Informacje o OCCT: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Nie przypisano licencji do własnego kodu projektu; licencja OCCT obowiązuje
niezależnie, a skrypt dołącza jej treść do APK.
