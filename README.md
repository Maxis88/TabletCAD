# TabletCAD

Natywna aplikacja CAD offline na tablety ARM64 z Androidem 14 lub nowszym.
Wersja źródeł: **1.0.0-rc7**, versionCode **116**.
Główne repozytorium: https://github.com/Maxis88/TabletCAD, gałąź `main`.

Ikona aplikacji w 0.53 wykorzystuje grafikę dostarczoną przez użytkownika,
z adaptacyjną maską Androida i granatowym tłem.

Wersja 1.0.0-rc7 — wyciąganie obszaru między konturami: dotknięcie między zagnieżdżonymi profilami (np. kwadrat w kwadracie, płyta z otworami) zaznacza pierścień/ramkę jako jeden obszar; jądro tworzy jedną płaską ścianę B-Rep z wyspami (dokładne okręgi, łuki, elipsy) i wyciąga ją jako jedną operację (rekord -30, także pierwsza bryła, łączenie, wycięcie, część wspólna; zaznaczenie ramki i jej wyspy daje cały kontur). Edycja wymiarów któregokolwiek konturu przelicza operację. Nowe okno **Opcje** (Plik / Projekt → Opcje): ukrywanie gwintów modelowanych w widoku i pomiarach (ściana gwintowanego otworu ma wtedy pomarańczowe paski; eksport zawsze zawiera gwint), tło obszaru roboczego, kolor akcentu, kolor bryły, siatka, kostka widoku, wymiary w szkicu, jednostki nowych projektów. W drzewie modelu jeden przełącznik dla wszystkich szkiców („Wszystkie szkice”) i dla wszystkich elementów konstrukcyjnych (oko przy „Konstrukcja”). Sondy `RegionKernelProbe` (27 sprawdzeń) i `RegionUiProbe`.
Wersja 1.0.0-rc4 — zamknięty splajn jako gładki profil (wyciągnięcie i wycięcie; krzywe Béziera w jądrze OCCT); podział bryły powierzchnią z krzywej szkicu, także falistej; wybór ściany w punkcie dotknięcia i bez zaznaczania szkiców ukrytych pod bryłą.
Wersja 1.0.0-rc3 — przełącznik rodzaju wyciągnięcia (Auto/Nowa/Połącz/Wytnij/Wspólna) przy strzałce; przyciąganie do przecięć okręgów, punktów styczności i prostopadłej z początku linii.
Wersja 1.0.0-rc2 — pasek opcji narzędzia pod górnym menu (nad oboma panelami bocznymi), prawy panel tylko dla pełnych formularzy, małe okienka przy edytowanej operacji, przełączniki Odwróć/Symetrycznie przy strzałce wyciągnięcia; szkice na ścianie, otwory, gwinty i fazy podążają za przesuniętą operacją ([docs/AUDIT-1.0.0-rc2.md](docs/AUDIT-1.0.0-rc2.md)).
Wersja 1.0.0-rc1 — sterujący wymiar odległości punkt–linia i punkt–punkt; kandydat do wydania 1.0 —
lista kontrolna testów na tablecie w [docs/AUDIT-1.0.0-rc1.md](docs/AUDIT-1.0.0-rc1.md).

Wersja 0.99 — import i eksport IGES (wymaga jednorazowego dobudowania modułu OCCT TKDEIGES przez
`tools/prepare-occt.ps1`, uruchamianego automatycznie przez build). [Audyt 0.99](docs/AUDIT-0.99.md) — testy na tablecie do wykonania.

Wersja 0.98 — zaokrąglenia i fazy podążają za krawędzią po edycji wcześniejszej operacji (nazewnictwo
krawędzi przez ściany). [Audyt 0.98](docs/AUDIT-0.98.md) — testy na tablecie do wykonania.

Wersja 0.97 — import 3MF (siatki z drukarek 3D i slicerów). [Audyt 0.97](docs/AUDIT-0.97.md) — testy na tablecie do wykonania.

Wersja 0.96 — trwałe dosunięcia wystąpień złożenia, test-runner nie uruchamia nieskompilowanych sond.
[Audyt 0.96](docs/AUDIT-0.96.md) — testy na tablecie do wykonania.

Wersja 0.95 — punkt szkicu (narzędzie Punkt), podgląd operacji liczony tym samym zapytaniem co zatwierdzenie.
[Audyt 0.95](docs/AUDIT-0.95.md) — testy na tablecie do wykonania.

Wersja 0.94 — wymiary arkusza podążają za zmianą modelu, kąty i tolerancje na arkuszu.
[Audyt 0.94](docs/AUDIT-0.94.md) — testy na tablecie do wykonania.

Wersja 0.93 — gesty: obrót widoku dwoma palcami, dwukrotne dotknięcie dopasowuje widok lub pokazuje bryłę,
przytrzymanie elementu szkicu otwiera jego menu. [Audyt 0.93](docs/AUDIT-0.93.md) — testy na tablecie do wykonania.

Wersja 0.92 — obsługa rysika: odrzucanie dłoni, precyzyjne wskazywanie, podgląd przyciągania nad ekranem,
przycisk rysika przesuwa widok, palec nawiguje. [Audyt 0.92](docs/AUDIT-0.92.md) — testy na tablecie do wykonania.

Wersja 0.91 — średnice i promienie na arkuszu, wymiary od środków otworów, szybsze zatwierdzanie
po podglądzie. [Audyt 0.91](docs/AUDIT-0.91.md) — testy na tablecie do wykonania.

Wersja 0.90 — wymiarowanie arkusza rysunkowego dotykiem (poziome, pionowe, równoległe, w PDF), eksport
w tle z postępem. [Audyt 0.90](docs/AUDIT-0.90.md) — testy na tablecie do wykonania.

Wersja 0.89 — sterujący wymiar kąta między liniami (widoczny łuk z wartością), szyk po ścieżce w szkicu.
[Audyt 0.89](docs/AUDIT-0.89.md) — testy na tablecie do wykonania.

Wersja 0.88 — zaznaczanie wielu elementów szkicu: wspólne przeciąganie, przesunięcie o X/Y, położenie
i wyrównanie zaznaczenia, usuwanie. [Audyt 0.88](docs/AUDIT-0.88.md) — testy na tablecie do wykonania.

Wersja 0.87 — rzutowanie całej ściany bryły jednym dotknięciem, wyrównanie do obrysu ściany, przebudowa
po zakończeniu szkicu w tle z postępem. [Audyt 0.87](docs/AUDIT-0.87.md) — testy na tablecie do wykonania.

Wersja 0.86 — przesuwanie elementów szkicu bez obracania widoku, panel Położenie (odległości i wyrównanie
do punktów, elementów i rzutowanych krawędzi bryły), szybsze anulowanie i edycja szkicu, ochrona przed
awariami poleceń. [Audyt 0.86](docs/AUDIT-0.86.md) — testy na tablecie do wykonania.

Wersja 0.85 — kostka nawigacyjna, trwałe dosunięcia brył (płaszczyzny i współosiowość), test akceptacyjny A,
ikony wektorowe zamiast znaków. [Audyt 0.85](docs/AUDIT-0.85.md).

Wersja 0.84 — wspólne prawe panele ze stałymi akcjami, szkice źródłowe podążające za transformacjami bryły, automatyczny wybór Połącz/Wytnij, stały zoom i przekroje podczas edycji. [Audyt 0.84](docs/AUDIT-0.84.md).

Wersja 0.83.1 — stała historia na całą szerokość przy dolnej krawędzi, panele nad historią i pełny ekran z nawigacją systemową dostępną gestem. [Audyt 0.83.1](docs/AUDIT-0.83.1.md).

Wersja 0.83 — nowy wygląd paneli, pasek narzędzi, szybkie akcje
ściany, menu Plik, gest wstecz. [Audyt 0.83](docs/AUDIT-0.83.md).

Wersja 0.82 — audyt obsługi dotykowej: menu kontekstowe drzewa, dosuwanie dowolnych brył (styk, wyrównanie,
współosiowość), klawiatura numeryczna, edycja wymiaru jednym dotknięciem. [Audyt 0.82](docs/AUDIT-0.82.md).

Wersja 0.81 — podgląd na żywo wszystkich operacji, suwaki szyków i parametrów,
odsuwanie ściany bryły i płaszczyzna od wskazanej ściany. [Audyt 0.81](docs/AUDIT-0.81.md).

Wersja 0.80 — złożenia: komponenty w drzewie, powiązane wystąpienia, dosuwanie ścian,
kolizje i lista części z CSV. Wersje 0.74–0.80 przetestowane na tablecie (testy
instrumentowane i scenariusz 16.3 PASS). [Audyt 0.80](docs/AUDIT-0.80.md).

Wersja 0.79.1 — asocjacyjne położenie otworu od krawędzi, łuki w statusie więzów,
promień okręgu zachowany przy ruchu środka.

Wersja 0.79 — druk 3D (eksport z wybraną dokładnością siatki, 3MF z bryłami, widok
nawisów, połóż na stole) i poprawki: wstawianie operacji przy znaczniku historii,
wybór elementów konstrukcyjnych dotykiem, edycja powiązanego szyku, lustro za osią.
[Audyt 0.79](docs/AUDIT-0.79.md).

Wersja 0.78 — powiązane kopie szkicu: szyki i lustro w szkicu podążają za źródłem
(więz ⧉). [Audyt 0.78](docs/AUDIT-0.78.md).

Wersja 0.77 — analiza: bezwładność i masa każdej bryły, katalog materiałów i gęstość
per bryła, odległość minimalna i kolizje brył. [Audyt 0.77](docs/AUDIT-0.77.md).

Wersja 0.76 — historia: wygaszanie operacji, zmiana kolejności z przenumerowaniem
odwołań, podgląd modelu do wybranej operacji (znacznik), przeliczenie modelu.
[Audyt 0.76](docs/AUDIT-0.76.md).

Wersja 0.75 — konstrukcja: płaszczyzny odsunięte, pod kątem i przez 3 punkty,
osie i punkty konstrukcyjne (drzewo, widok 3D, szkic na płaszczyźnie, użycie
w lustrach, szykach i podziale), pomiar kątów i dokładnego pola ścian, import OBJ.
[Audyt 0.75](docs/AUDIT-0.75.md).

Wersja 0.74 — parametry użytkownika (wyrażenia, powiązania z wymiarami szkicu,
głębokościami i otworami), elipsa jako dokładny profil, Przejście (loft) i
Przeciągnij (sweep) z asocjacyjnymi przekrojami, lustro/szyki całych brył,
status więzów szkicu (niedowiązany / w pełni / nadmiernie / sprzeczny),
współliniowość, rzutowanie krawędzi B-Rep na szkic, podgląd na żywo i uchwyty
na modelu dla otworu, powłoki i szyku, położenie otworu od krawędzi ściany.
Schemat .tcad 37. Sprawdzone na komputerze (15 zestawów PASS); bez testu na
tablecie. [Audyt 0.74](docs/AUDIT-0.74.md).

Wersja 0.73 — narzędzia bryłowe: parametryczne lustro oraz szyk liniowy
i kołowy operacji (kopie śledzą zmiany źródeł), Otwór na płaskiej ścianie
(na wylot/na głębokość, stożek wiertła, pogłębienie walcowe/stożkowe),
Powłoka z usuwanymi ścianami, Pochylenie ścian, Połącz/Odejmij/Część wspólna
brył, Podziel bryłę, Przesuń/Obróć/Skaluj bryłę, usuwanie
dowolnej operacji z osi czasu z listą konsekwencji. Jednostki projektu
(mm/cm/m/cale), dokładność wyświetlania i wyrażenia w polach (100/3, 1in+2mm).
Widoki: cieniowany, krawędzie, przezroczystość i przekrój OCCT.
Schemat .tcad 36. Jądro sprawdzone na komputerze przez
`tools/host-kernel-tests.sh` (OCCT 8.0.1 dla Linux x86-64, te same funkcje JNI);
interfejs nie był uruchamiany na tablecie. [Audyt 0.73](docs/AUDIT-0.73.md).

Wersja 0.72 — pakiet projektowania: okrąg przez trzy punkty, szczelina,
numeryczne przesunięcie/obrót/skala zaznaczonego elementu szkicu, przejście
z operacji do profilu źródłowego i lista jego bezpośrednich powiązań.
Poprawiona aktualizacja typu/payloadu profilu we wszystkich zależnych
wyciągnięciach i obrotach, również w bazie dwukierunkowej na ścianie.
130 nowych kontroli kodu szkicowania i zależności oraz 149 wcześniejszych
regresji PASS; APK aplikacji i prób Androida zbudowane. Gesty i wynikowe
bryły OCCT nowych ścieżek nie były uruchamiane na urządzeniu.
[Audyt projektowania 0.72](docs/AUDIT-0.72.md).

Wersja 0.71: wspólna biblioteka przy starcie i w menu Moje projekty,
wyszukiwanie po nazwie i danych rysunkowych (także bez polskich znaków),
sortowanie po dacie/nazwie i filtry typów. Listy biblioteki oraz kosza czytane
w tle, miniatury dekodowane poza UI z ograniczoną kolejką i pamięcią podręczną.
Wykrywanie projektów zawierających tylko .bak, oznaczenie kopii odzyskiwania
oraz bezpieczniejsze przenoszenie do kosza z cofaniem po błędzie.
149 kontroli JVM i kompilacja APK przeszły; UI bez uruchomienia na Androidzie.
[Audyt pakietu 0.71](docs/AUDIT-0.71.md).

Wersja 0.70: Sprawdź → Właściwości bryły → Gęstość materiału: obliczanie masy
całego modelu na podstawie objętości OCCT i własnej gęstości w kg/m³.
Jedna gęstość obejmuje wszystkie bryły. Zapis .tcad (schemat 35), undo/redo,
kopiowanie i odzyskiwanie zachowują przypisanie. Starsze projekty można odczytać;
starsze aplikacje nie otworzą dokumentów zapisanych w schemacie 35.
Kompilacja APK i 97 kontroli JVM przeszły; UI tego etapu bez testu na Androidzie.
[Audyt 0.70](docs/AUDIT-0.70.md).

Wersja 0.69: Projekt → Utwórz kopię projektu oraz Kosz → Przywróć / Przywróć
jako kopię. Kopie mają osobny dokument i niezależne źródła importu STEP/STL.
Odzyskiwanie obsługuje kopie .bak i odrzuca nadpisanie istniejącego projektu.
Operacje kopiowania i przywracania wykonują się w tle. Ten etap zweryfikowano
kompilacją i 48 testami plikowymi na komputerze; interfejs 0.69 nie był
uruchamiany na tablecie. [Audyt 0.69](docs/AUDIT-0.69.md).

Wersja 0.68: obrót podczas szkicowania, wyciąganie do równoległej ściany,
odznaczanie pustym dotknięciem, naprawa pełnych gwintów M6 × 1,
szybsze renderowanie brył, przywrócona strzałka wyciągania i szybki start
do biblioteki projektów. Zakres testów na HONOR i ograniczenia:
[audyt 0.68](docs/AUDIT-0.68-LOCAL.md).

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

Testy plikowe biblioteki 0.69 można uruchomić na Windows po zbudowaniu aplikacji:

```powershell
.\tools\test-library-files-host.ps1 -Sdk $env:ANDROID_HOME
```

Używają właściwego czytnika dokumentów i operacji plikowych, z przypiętą biblioteką
org.json dla JVM (pobieraną do ignorowanego build/ i sprawdzaną SHA-256).
Nie uruchamiają Androida ani OCCT i nie zastępują weryfikacji interfejsu.

## Weryfikacja

### Bez tabletu (Linux, od 0.73)

```bash
tools/host-kernel-tests.sh --android-jar <SDK>/platforms/android-36/android.jar \
    --json-jar build/host-tests/lib/json-20240303.jar
```

Skrypt buduje przypięty OCCT 8.0.1 dla x86-64, kompiluje `native_cad_engine.cpp`
do `libtabletcad.so` i uruchamia na JVM komputera testy hosta oraz próby jądra
(`*KernelProbe`, `KernelDiagnosticsProbe`), które korzystają tylko z modelu dokumentu
i `NativeCadEngine`. Sprawdza to rzeczywiste wyniki B-Rep (objętości analityczne),
ale nie zastępuje testu interfejsu, gestów ani biblioteki ARM64 na urządzeniu.

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
