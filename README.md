# TabletCAD

Natywna aplikacja CAD offline na tablety ARM64 z Androidem 14 lub nowszym.
Wersja źródeł: **0.67.0-project-library**, versionCode **76**.
Główne repozytorium: https://github.com/Maxis88/TabletCAD, gałąź `main`.

Ikona aplikacji w 0.53 wykorzystuje grafikę dostarczoną przez użytkownika,
z adaptacyjną maską Androida i granatowym tłem.

Wersja 0.67: lista projektów z miniaturą bryły, datą zmiany i opisem modelu oraz
usuwanie projektów (do kosza aplikacji, z potwierdzeniem). [Audyt 0.67](docs/AUDIT-0.67.md).

Wersja 0.66: oś czasu jako zaokrąglony dok, komunikaty jako pływające
powiadomienia (czerwony znacznik dla błędów), zaokrąglone okna grup narzędzi;
dotknięcie ściany tylko ją zaznacza, przytrzymanie edytuje operację źródłową.

Wersja 0.65: zaznaczenie oznacza edycję — dotknięcie widocznego szkicu w widoku
bryły otwiera go z zaznaczonym elementem, dotknięcie ściany otwiera parametry
operacji, która ją utworzyła (od 0.66 — przytrzymanie; dotknięcie tylko zaznacza), a pojedyncze dotknięcie szkicu w drzewie lub
operacji na osi czasu od razu ją edytuje. [Audyt 0.65](docs/AUDIT-0.65.md).

Wersja 0.64 porządkuje układ: jeden pasek górny 64 dp (projekt, tryb, numer
wersji, narzędzia, pliki), zwijane drzewo modelu, spójna paleta grafit/morski
akcent. [Audyt 0.64](docs/AUDIT-0.64.md).

Wersja 0.63 dodaje sterujące wymiary położenia X/Y (od początku szkicu lub
między punktami) widoczne na szkicu, wymiar średnicy Ø okręgów i stały początek
szkicu na ścianie bryły. [Audyt 0.63](docs/AUDIT-0.63.md).

Wersja 0.62 przyspiesza przebudowę: jądro wznawia historię od ostatniej
niezmienionej operacji (dopisanie operacji do 25-elementowej historii: 35 ms
zamiast 421 ms na tablecie). [Audyt 0.62](docs/AUDIT-0.62.md).

Wersja 0.61 dodaje szyki szkicu: liniowy/prostokątny i kołowy
([audyt 0.61](docs/AUDIT-0.61.md)).

Wersja 0.60 dodaje Przytnij → Przedłuż: linia rośnie wzdłuż własnego kierunku
do najbliższej geometrii szkicu ([audyt 0.60](docs/AUDIT-0.60.md)).

Wersja 0.59 dodaje dokładne odsunięcie szkicu (Geometria → Odsunięcie) dla linii,
łuków, okręgów, prostokątów, wielokątów i konturów linia/łuk. Szczegóły:
[audyt 0.59](docs/AUDIT-0.59.md).

Wersja 0.58 dodaje widoki standardowe (izometryczny, przód, góra, prawy bok),
przełącznik rzutu prostokątnego, rzeczywiste dopasowanie widoku do bryły oraz
kamerę bezpieczną dla dużych modeli. Szczegóły: [audyt 0.58](docs/AUDIT-0.58.md).

Wersja 0.57 dodaje diagnostykę jądra: nieudana przebudowa podaje numer i nazwę
operacji historii oraz konkretną przyczynę (np. wycięcie nie przecina bryły,
zbyt duży promień zaokrąglenia, utracona krawędź). Szczegóły: [audyt 0.57](docs/AUDIT-0.57.md).

Wersja 0.56 dodaje edycję wymiarów przez dwukrotne dotknięcie, pomocniczość
figur, rozszerzone więzy, drzewo brył i źródłowych szkiców, wybór wielu profili
na rysunku, niezależne kierunki wyciągania oraz rzeczywiste gwinty otworów.
Nowy projekt otwiera pusty dokument. Zakres weryfikacji na tablecie i granice
implementacji opisuje [audyt 0.56](docs/AUDIT-0.56.md).

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
- Wymiana STEP/STL/DXF, eksport triangulowanego OBJ w mm, pomiary krawędzi OCCT, objętość, pole i centroid.

To wersja rozwojowa. Solver szkicu i odniesienia topologiczne mają ograniczony
zakres, część obliczeń blokuje UI. Pełne komponenty i dokumentacja techniczna
pozostają nieukończone. Bieżące ograniczenia: [audyt 0.56](docs/AUDIT-0.56.md)
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
Wynik: `dist/TabletCAD-<versionName>.apk` (np. `dist/TabletCAD-0.57.0-kernel-diagnostics.apk`).
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

Testy renderowania i gestów `WorkspaceUiProbe`, `ExtrusionManipulatorProbe`, `EdgeGroupUiProbe` i `RecoveryUiProbe` wymagają normalnego środowiska
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
