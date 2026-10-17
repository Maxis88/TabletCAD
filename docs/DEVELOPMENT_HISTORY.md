# TabletCAD

Aktualna kompilacja: **0.45.0-transactional-history** (versionCode 52).
APK: `dist/TabletCAD-0.45.0-transactional-history.apk`.
Naprawiono odtwarzanie historii po błędzie regeneracji i dodano walidację
odczytywalności dokumentu przed zapisem. Bieżący audyt i pozostałe blokery:
`docs/AUDIT-2026-10-17.md`. Starsze sekcje poniżej są zapisem rozwoju wersji.

Tabletowa aplikacja CAD dla Androida 14+. Repozytorium zawiera obecnie etap
`0.29.0-polyline-drawing`: rysowanie polilinii punkt po punkcie, wymiary sterujące podstawowych figur, przycinanie fragmentów szkicu, ukrywanie zasłoniętych krawędzi, kontekstowy interfejs szkicu i modelowania, trwały dokument
projektu, JNI/C++ dla
`arm64-v8a` oraz Open CASCADE 8.0.1. Rdzeń tworzy i waliduje rzeczywistą bryłę
B-Rep oraz wykonuje autotest zapisu i ponownego odczytu STEP.

Gotowy APK znajduje się w `dist/TabletCAD-0.29.0-polyline-drawing.apk`. Jest to podpisana
wersja deweloperska dla tabletów ARM64 z Androidem 14 lub nowszym.

Wersja 0.29.0 została zainstalowana i przetestowana dotykowo na podłączonym tablecie HONOR.
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

Zamknięty profil prostokąta lub okręgu można zaznaczyć i wyciągnąć do prawdziwej
bryły B-Rep. Na płaskiej ścianie istniejącego modelu polecenie Wyciągnij otwiera
wybór `Dodaj materiał` albo `Wytnij materiał` oraz numeryczne pole głębokości.
Operacje są wykonywane przez OCCT jako Fuse/Cut, walidowane i zachowywane w historii.
Systemowy selektor plików Android zapisuje zweryfikowany STEP zawierający wynik
operacji Boolean,
binarny STL i DXF szkicu 2D. STEP można ponownie otworzyć; import jest walidowany,
tessellowany przez OCCT i zachowywany w dokumencie projektu.

Strzałki historii wykonują rzeczywiste undo/redo całego dokumentu (do 50 migawek)
dla szkicu, płaszczyzny, parametrów, wyciągnięcia i importu. Przywrócenie historii
ponownie przelicza geometrię OCCT i zapisuje odzyskany stan.

Od 0.37.0 tryb pomiaru brył korzysta z długości i promieni krawędzi OCCT,
nie odcinków tessellacji. Pozwala też mierzyć odległość między końcami
krawędzi B-Rep. Pomiar pozostaje przywiązany do modelu podczas obrotu kamery.
Szczegóły i ograniczenia opisano w sekcji 0.37.0 poniżej.

Tryb modelowania udostępnia rzeczywiste zaokrąglenie wszystkich krawędzi brył
utworzonych w TabletCAD. Promień jest podawany numerycznie w milimetrach,
walidowany przez OCCT, zapisywany w dokumencie, obsługiwany przez undo/redo i
uwzględniany w eksporcie STEP. Nieudana operacja nie zastępuje ostatniej poprawnej
geometrii. Od 0.36.0 górna ikona zaokrąglania wybiera wskazaną krawędź,
również na imporcie STEP; starsze globalne zaokrąglenia można edytować z historii.
Na płaskich ścianach importowanych brył STEP można tworzyć szkice prostokąta
i okręgu oraz wykonywać kolejne operacje dodania i wycięcia. Źródłowy STEP jest
zachowany w osobnym pliku dokumentu; eksport uwzględnia wynik wszystkich operacji.
Dokument zachowuje kolejne operacje dodania
i wycięcia wraz z lokalnym układem i profilem każdej operacji. Kolejny szkic
zatwierdza poprzednią operację, a OCCT przelicza cały ciąg na bryle bazowej lub
zachowanym wyciągnięciu profilu. Dotknięcie wcześniejszej operacji na dolnej osi
otwiera numeryczną edycję współrzędnych jej profilu i głębokości oraz przebudowuje
dalsze operacje. Niepoprawna zmiana przywraca ostatnią poprawną geometrię.
Rdzeń odrzuca wycięcie bez zmiany objętości i dodanie tworzące oddzielną bryłę.
Zatwierdzone profile przechowują własną geometrię. Szkice zachowane od wersji
0.20 można ponownie otwierać i edytować graficznie; graf zależności między
ścianami pozostaje do implementacji.

Test `tools/NativeSequenceProbe.java` wykonany na fizycznym Androidzie potwierdził
objętość dodania 123880 mm³, sekwencji dodanie/wycięcie 123755 mm³, wynik na
wyciągniętej bazie 39275 mm³, round-trip STEP i serializację historii schematu 8.
Test dotykowy potwierdził oba elementy geometrii po restarcie oraz otwieranie
edycji wcześniejszej cechy. Dolny pasek uwzględnia systemową strefę gestów.

W 0.16.0 test na Androidzie objął zmianę szerokości wcześniejszego profilu
(objętość sekwencji 124255 mm³), odrzucenie rozłącznego dodania i wycięcia poza
modelem. Dotykowo sprawdzono zmianę X1 z 18,500 do 16,500 mm i jej zachowanie
po zimnym uruchomieniu aplikacji.

W 0.17.0 test jądra na tablecie potwierdził wycięcie w zaimportowanym STEP
(123630 mm³), dodanie (123880 mm³) i round-trip zmienionego pliku STEP.
Dotykowo potwierdzono import systemowym selektorem, wybór ściany, wycięcie,
kolejne dodanie oraz odtworzenie obu operacji po restarcie aplikacji.
Schemat 9 przechowuje nazwę niezmiennego źródła STEP, aby kolejne importy
nie nadpisywały źródła poprzedniego modelu.

W 0.18.0 wyciągnięcie przy wielu profilach wyświetla jawny wybór z rozmiarami.
Tryb Edytuj umożliwia przeciąganie całego elementu szkicu po lokalnej płaszczyźnie,
z podglądem konturu i przebudową modelu po puszczeniu. Krótkie dotknięcie nadal
otwiera edycję numeryczną. Powrót do szkicu na ścianie ustawia kamerę prostopadle
na podstawie zachowanego układu płaszczyzny. Test dotykowy potwierdził wybór
profilu, wyrównanie kamery, przesunięcie i trwałość zmiany po restarcie.
Schemat 10 zapisuje UUID profilu dla nowo zatwierdzanych operacji, dzięki czemu
edycja dostępnego szkicu aktualizuje także używającą go wcześniejszą operację.
Stare operacje bez UUID zachowują edycję współrzędnych przez oś historii.

W 0.19.0 pierwsze dotknięcie elementu w trybie Edytuj zaznacza go i pokazuje
uchwyty oraz bieżący rozmiar. Przeciąganie narożników prostokąta zmienia jego
wymiary; uchwyt obwodu okręgu zmienia promień, a uchwyt środka przesuwa okrąg
bez zmiany promienia. Końce linii można przesuwać niezależnie. Ponowne krótkie
dotknięcie zaznaczonego elementu otwiera edycję numeryczną.
Na tablecie potwierdzono zmianę prostokąta do 15,77 × 11,74 mm, zwiększenie
promienia okręgu do około 9,99 mm, przesunięcie środka, wycięcie edytowanego
profilu kołowego w importowanym STEP oraz odtworzenie wyniku po restarcie.

W 0.20.0 zmiana płaszczyzny zachowuje dotychczasowy szkic z UUID, encjami i
lokalnym układem. Narzędzie Edytuj oraz wiersz Szkic otwierają listę bieżącego
i zachowanych szkiców. Nowy szkic można utworzyć także na tej samej płaszczyźnie.
Przełączenie zachowuje historię bryły i automatycznie ustawia kamerę. Powiązane
profile operacji i nowo zachowanego wyciągnięcia bazowego sterują geometrią po
ponownej edycji. Schemat 11 zachowuje archiwum między uruchomieniami.
Test modelu na Androidzie potwierdził round-trip dwóch szkiców XY/XZ z UUID
i układami. Dotykowo potwierdzono utworzenie szkicu XZ, powrót do wcześniejszego
szkicu na ścianie, zachowanie obu po restarcie oraz zmianę promienia wcześniej
wykonanego wycięcia przez uchwyt przywróconego szkicu.

W 0.21.0 Otwórz plik oferuje STEP, STL i DXF. Czytnik STL rozpoznaje wariant
binarny po długości i liczbie trójkątów (także przy nagłówku zaczynającym się od
solid) oraz tekstowy ASCII. Limit wynosi 32 MB / 250000 trójkątów. Importowana
siatka zachowuje źródło w projekcie i odtwarza się po restarcie. Jest dostępna
do oglądania, pomiarów i zapisu STL; Boolean, zaokrąglenie i STEP wymagają B-Rep
i nie są udostępniane dla siatki. Eksport STL oferuje wariant binarny i ASCII.
Czytnik DXF obsługuje płaskie LINE, CIRCLE i proste LWPOLYLINE. Zamknięty
prostokąt osiowy jest odtwarzany jako edytowalny profil; pozostałe polilinie
stają się liniami. Łukowe polilinie i inne encje zgłaszają błąd. Jednostki
mm, cm, m i cale są przeliczane na mm (brak jednostek oznacza mm).
Eksport prostokąta używa zamkniętej LWPOLYLINE.
Test ExchangeProbe na Androidzie potwierdził oba warianty STL, odrzucenie
uszkodzonych danych i NaN, odczyt DXF z prostokątem i okręgiem, przeliczenie
jednostek oraz odrzucenie nieobsługiwanej encji. Dotykowo otwarto DXF i STL,
wykonano eksport ASCII STL, ponowny import i odtworzenie siatki po restarcie.

W 0.22.0 widok Rysunek jest arkuszem A4 poziomo z geometrią bieżącego modelu.
OCCT HLR tworzy rzuty przód/góra/bok lewy z krawędziami widocznymi i ukrytymi.
Przekrój A-A powstaje z BRepAlgoAPI_Section, z kreskowaniem zamkniętych konturów.
Ponowne naciśnięcie Rysunek ustawia położenie przekroju X jako procent zakresu
modelu (0 < X < 100). Parametr zapisuje się w schemacie 13. Arkusz ma ramkę,
tabliczkę, automatyczny dobór skali, osie i automatyczne wymiary gabarytowe.
Ekran i eksport wektorowego PDF używają tego samego renderera; krzywe są
aproksymowane odcinkami do prezentacji. Zmiana geometrii unieważnia arkusz.
Test OCCT na Androidzie potwierdził wszystkie rzuty, ukryte odcinki i przekrój,
w tym zmianę przekroju przez występ z zagłębieniem przy X=18,75%.
Dotykowo wygenerowano arkusz i wyeksportowano PDF; niezależny render Poppler
potwierdził jedną stronę A4 842 × 595 pt i poprawną typografię po korekcie.
Nie jest to jeszcze pełny edytor dokumentacji: brak dowolnych wymiarów cech,
tolerancji, ustawiania wielu przekrojów i arkuszy. W 0.23.0 dodano zapisywane
dane tabliczki: nazwa, numer, autor, materiał i rewizja.

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

## Szkicowanie i modelowanie — 0.24.0

Pasek narzędzi zależy od kontekstu. SZKIC 2D ma wybór/edycję, linię,
prostokąt, okrąg, linię pomocniczą, usuwanie, czyszczenie, ukrywanie bryły
i Zakończ. BRYŁA 3D ma wybór, nowy/istniejący szkic, wyciąganie,
bezpośrednie wycinanie, głębokość, zaokrąglenie, usunięcie ostatniej cechy,
pomiar, dopasowanie i czyszczenie widoku. Rysowanie nie jest dostępne w
trybie bryły; operacje bryłowe nie są dostępne podczas szkicowania.
Parametry i listy wyboru otwierają się w niemodalnym, przewijanym prawym
panelu, nie w oknach zasłaniających model.

Linia pomocnicza jest przerywaną, zapisywaną geometrią szkicu, wyłączoną
z wyboru profili bryłowych. Przyciąganie obejmuje początek układu, końce,
środki, przecięcia i rzut na odcinek. Podczas rysowania działa również
wnioskowanie poziome/pionowe względem początku ruchu. Zielony znacznik
pokazuje korektę pozycji. To przyciąganie geometryczne, nie trwałe więzy
parametryczne ani pełny solver. Szkic ma siatkę metryczną i osie własnej
płaszczyzny; wybrana płaszczyzna nie zmienia się przypadkowym dotknięciem
prawego panelu. XZ jest oglądana dokładnie prostopadle.

Wyciąganie/dodawanie i wycinanie na bryle pokazują podgląd OCCT po zmianie
głębokości w prawym panelu (350 ms po zakończeniu wpisywania). Podgląd
nie zmienia dokumentu ani historii; Zastosuj zapisuje, Anuluj odrzuca.
Można obracać model podczas podglądu. Nie dotyczy to jeszcze podglądu
zaokrąglenia i pierwszego wyciągnięcia bazowego.

Usuń kasuje zaznaczony, nieużywany element szkicu. Wyczyść szkic oferuje
usunięcie nieużywanych elementów albo całego nieużywanego szkicu. Profile
sterujące bryłą są chronione przed przypadkowym skasowaniem; można najpierw
usunąć ostatnią cechę w trybie BRYŁA 3D. Usuwanie cech nie usuwa źródłowego
szkicu. Bryła bazowa nie jest kasowana tą funkcją. Undo/redo obejmuje te
zmiany, ale historia cofania pozostaje tylko w bieżącej sesji.
Wyczyść widok usuwa zaznaczenia i pomiary oraz resetuje przesunięcie/skalę;
nie usuwa geometrii dokumentu. Ukryj bryłę ułatwia szkicowanie bez zasłaniania
linii przez powierzchnie.

Test SketchWorkflowProbe na Androidzie: końce, środki, przecięcia, pozycja
na linii, początek układu, wyrównanie poziome/pionowe, wyłączenie przeciąganej
encji z przyciągania i zapis linii pomocniczej — PASS.
Dotykowo sprawdzono szkic na ścianie, prostokąt i pomocniczą linię, wycięcie
5 mm, podgląd zmiany do 8 mm z obrotem, anulowanie i usunięcie pomocniczej
linii. Potwierdzono usunięcie ostatniej cechy, usunięcie całego nieużywanego
szkicu i odtworzenie obu przez Cofnij. Dwa szybkie naciśnięcia Cofnij cofają
dwie osobne akcje (wyłączono przechwytywanie dwukliku). Sprawdzono również
etykiety i uchwyty zaznaczonego prostokąta z właściwościami w prawym panelu.
Bardziej złożone profile złożone z wielu linii, łuki, przycinanie,
stałe więzy i zaokrąglanie pojedynczych krawędzi wymagały wtedy rozbudowy;
kontury, przycinanie, więzy H/V/stałego punktu i wskazane krawędzie dodano
w kolejnych wersjach opisanych poniżej. Łuki i mieszane kontury dodano w 0.39.0.

## Zamknięte kontury z linii — 0.25.0

W trybie SZKIC 2D narzędzie Połącz kontur łączy osobne linie w zamknięty
wielokąt (3–256 wierzchołków). Kolejność i kierunek rysowania odcinków nie
muszą być zgodne. Można najpierw zaznaczyć jedną linię, aby wskazać właściwy
obwód w szkicu z kilkoma łańcuchami. Bez zaznaczenia używana jest pierwsza
linia szkicu. Końce łączą się z tolerancją 0,01 mm; linie pomocnicze są
pomijane. Otwarte, rozgałęzione i samoprzecinające się obwody są odrzucane.
Połączenie zastępuje zużyte linie pojedynczym Konturem, zachowując jego
rzeczywisty kształt; Cofnij przywraca osobne odcinki.

Kontur ma uchwyty wszystkich wierzchołków. Przeciągnięcie uchwytu zmienia
kształt, a przeciągnięcie krawędzi przesuwa całość. Niepoprawna zmiana jest
odrzucana. Kontur jest profilem wyciągnięcia bazowego oraz operacji dodania
i wycięcia na płaskiej ścianie istniejącej bryły. OCCT tworzy z wierzchołków
rzeczywistą ścianę i graniastosłup B-Rep, również dla wielokątów wklęsłych.
Podgląd, zapis projektu, historia i przebudowa po edycji zachowują wierzchołki.
Schemat 15 używa rekordów cech o zmiennej długości; starsze prostokątne i
kołowe rekordy pozostają obsługiwane. STEP/STL korzystają z wynikowej bryły,
a DXF zapisuje kontur jako zamkniętą LWPOLYLINE, nie jako jego obwiednię.

PolygonProbe na fizycznym Androidzie potwierdził: łączenie odwróconych i
nieuporządkowanych linii, odrzucenie otwarcia/rozgałęzienia/samoprzecięcia,
pomijanie geometrii pomocniczej, zapis i odczyt bazy oraz historii wielokąta,
objętość trójkątnego graniastosłupa 2000 mm³ i wklęsłego L 5000 mm³,
objętości dodania i wycięcia, round-trip STEP i odrzucenie uciętego rekordu
JNI. Dotychczasowy NativeSequenceProbe z prostokątami, STEP i HLR także
przeszedł. Dotykowo narysowano trzy linie, połączono trójkąt, dodano materiał
na 5 mm oraz przesunięto jego wierzchołek z przebudową istniejącej bryły.
Wynik odtworzono po restarcie. Następnie zachowano cechę w historii, powrócono
do jej szkicu i ponownie zmieniono wierzchołek, potwierdzając przebudowę
zatwierdzonej cechy (nie tylko aktywnego podglądu). Głębokość tej cechy
zmieniono w prawym panelu z 5 do 7 mm i odtworzono po kolejnym restarcie.
Osobno sprawdzono natywną bazę wielokątną w XY/XZ/YZ i dodanie na ścianie YZ.

Kontury nie mają jeszcze otworów wewnętrznych ani odcinków łukowych.
Przycinanie, trwałe więzy i pełny solver szkicu pozostają niezaimplementowane.

## Widoczność krawędzi — 0.26.0

Widok bryły ukrywa krawędzie zasłonięte przez bliższe powierzchnie. Wcześniej
wszystkie krawędzie przednio zwróconych trójkątów były nakładane na gotowe
wypełnienia, co powodowało efekt prześwietlenia występów i zagłębień.
HiddenLineVisibility analitycznie przycina odcinek do obszarów trójkątów
zasłaniających, porównując głębokość z poprawną interpolacją perspektywiczną.
Rysowane są tylko niezasłonięte przedziały; krawędź może być widoczna częściowo.
Nie używa się średniej głębokości ściany do wyboru pod dotkniętym punktem.
Pomiar odrzuca zasłonięte wierzchołki i punkty na krawędziach.
W modelowaniu ukryte krawędzie nie są rysowane; linie niewidoczne w arkuszu
technicznym nadal korzystają z oddzielnego HLR i są przerywane.
Od 0.29.1 szkic jest także ukrywany przez zasłaniające go powierzchnie bryły.
Ukryj bryłę udostępnia całą geometrię szkicu; od 0.29.2 wybieranie i uchwyty
korzystają z tej samej kontroli widoczności co rysowanie.

VisibilityProbe na komputerze i fizycznym Androidzie: pełne zasłonięcie,
częściowe przycięcie, widoczność bliższej i współpłaszczyznowej krawędzi,
zmiana kolejności głębokości wzdłuż odcinka, odrzucenie ukrytego punktu
i punkt poza powierzchnią — PASS. Obejrzano model z prostokątnym zagłębieniem,
małym występem i trójkątnym występem przed i po obrocie.

## Przycinanie szkicu — 0.27.0

Ikona nożyczek Przytnij jest dostępna tylko w SZKIC 2D. Dotknięcie usuwa
fragment prostego odcinka pomiędzy kolejnymi przecięciami lub przecięciem
a końcem odcinka. Granice wyznaczają skończone linie, linie pomocnicze,
krawędzie prostokątów i konturów oraz analityczne przecięcia z okręgiem.
Okrąg może wyznaczać granice cięcia linii, ale nie jest jeszcze przycinany
do łuku. Dotknięcie dokładnie przecięcia albo linii bez przecięć nie usuwa
przypadkowo całego elementu, tylko pokazuje wyjaśnienie w prawym panelu.

Pozostałe kawałki linii otrzymują osobne UUID; jeden zachowuje dotychczasowy.
Przycięcie nieużywanego prostokąta lub wielokąta otwiera go jako zbiór
odcinków. Profil powiązany z bryłą jest chroniony przed otwarciem;
pozostaje możliwa edycja jego uchwytów albo wcześniejsze usunięcie zależnej
cechy. Wszystkie zmiany przycinania zapisują się w dokumencie i podlegają
Cofnij/Ponów. Narzędzie nie zmienia układu płaszczyzny ani istniejącej bryły.

TrimProbe na fizycznym Androidzie: usunięcie środkowego i końcowego fragmentu,
unikalne identyfikatory, granice wyznaczane przez okrąg, otwarcie prostokąta,
odrzucenie niejednoznacznego dotknięcia i braku przecięć — PASS. Osiem cięć
nadmiarowych końców czterech linii utworzyło zamknięty obwód, który następnie
połączono i wyciągnięto w OCCT: objętość 4000 mm³. Sprawdzono zapis geometrii.
Dotykowo narysowano trzy przecinające się linie i usunięto środkowy fragment
jednej z nich. Cofnij przywróciło trzy pełne linie, Ponów odtworzyło cztery
odcinki, a ponowne uruchomienie zachowało przycięty szkic.

## Wymiary sterujące figur — 0.28.0

Ikona Wymiar w SZKIC 2D wybiera element dotykiem i otwiera prawy panel:
linia/pomocnicza — długość i kąt względem lokalnej osi X; prostokąt —
szerokość i wysokość; okrąg — promień lub przełączana średnica. Pierwszy
punkt/środek pozostaje na miejscu. Wymiary zmieniają współrzędne rzeczywistej
geometrii i przebudowują powiązane wyciągnięcia/wycięcia, także zatwierdzone.
Zwykła edycja figur również otwiera teraz parametry rozmiaru zamiast wymagać
wpisywania współrzędnych końców. Pozycja numeryczna pozostaje opcjonalna.

Zablokuj wymiary zapisuje trwałe parametry przypisane do UUID encji.
Przesunięcie krawędzi zachowuje rozmiar/kierunek, a uchwyt nie może go
przypadkowo zmienić. Środek zablokowanego okręgu można przesuwać bez zmiany
promienia. Odblokuj lub wyłączenie checkboxa usuwa blokadę; potem znowu
można zmieniać rozmiar uchwytami. Fioletowe linie wymiarowe i wartości są
rysowane na właściwej płaszczyźnie szkicu, także bez zaznaczenia.

Schemat 16 zapisuje wymiary i sprawdza ich zgodność z geometrią. Cofnij,
zapis, restart oraz przywrócenie zachowanego szkicu zachowują blokady.
Historia nie udostępnia surowych współrzędnych dla wymiarowanego profilu;
rozmiar edytuje się w szkicu, a głębokość nadal w historii. Przycinanie i
łączenie zwymiarowanych linii wymaga jawnego odblokowania, aby nie usuwać
po cichu parametrów. Wybrana otwarta linia nie jest zastępowana przypadkowym
zamkniętym profilem podczas wyciągania.

DimensionProbe na Androidzie potwierdził długość/kąt, odwrócony prostokąt,
promień, przesuwanie bez zmiany wielkości, blokadę skalowania, odblokowanie,
JSON, archiwum i historię, odrzucenie sprzecznych/niepoprawnych danych oraz
objętości OCCT 3000 i 8000 mm³ po zmianie parametrów. Dotykowo zmieniono
profil istniejącego wycięcia na 25 × 20 mm, sprawdzono ochronę przed
skalowaniem uchwytem, zadano Ø20 dla okręgu i przesunięto go przy R10 oraz
ustawiono linię L30 pod kątem 30°. Wymiary odtworzono po restarcie i po
przełączeniu między szkicami; historia rozpoznała blokadę zachowanego profilu.

To dokładne więzy pojedynczych podstawowych figur, nie pełny solver szkicu.
Wspólne punkty między encjami dodano w 0.31.0 (opis poniżej). Nadal brak
ogólnej równoległości, styczności, równości oraz wymiarowania dowolnych
krawędzi wieloodcinkowego konturu.

## Polilinia punkt po punkcie — 0.29.0

Polilinia w górnym menu SZKIC 2D zbiera kolejne dotknięcia na bieżącej
płaszczyźnie. Podczas przesuwania palca pokazuje następny odcinek; korzysta
z przyciągania i wnioskowania poziom/pion. Przy co najmniej trzech punktach
dotknięcie pierwszego punktu (znacznik Początek) zamyka obwód. Zamknięcie
tworzy od razu edytowalny Kontur, będący profilem wyciągnięcia/dodania/
wycięcia OCCT. Nie jest wymagane osobne Połącz kontur.

W prawym panelu: Zamknij obwód, Zapisz otwarty łańcuch, Cofnij ostatni punkt,
Anuluj polilinię. Cofnij na dolnej osi też usuwa ostatni punkt rozpoczętego
łańcucha. Otwarty łańcuch zapisuje się jako osobne edytowalne linie, nie
jako bryła. Zamknięcie podlega kontroli samoprzecięć i pola; błędne zamknięcie
nie usuwa punktów, więc można je poprawić. Anulowanie można cofnąć.

Schemat 17 zapisuje rozpoczęte punkty razem z aktualną płaszczyzną. Po
restarcie aplikacja wraca do rysowania niedokończonego łańcucha. Zmiana
narzędzia, przejście do drzewa i operacje plikowe wymagają jawnego zakończenia
lub anulowania draftu; nie gubią go po cichu. Zamknięcie i zapis otwarty
korzystają z historii dokumentu. Polilinia ma limit 256 punktów i odcinki
proste. Od 0.39.0 łuki rysuje się osobnym narzędziem i łączy z liniami
w kontur mieszany; nie są segmentami tego narzędzia Polilinia.

PolylineProbe na fizycznym Androidzie potwierdził zapis niedokończonych
punktów i płaszczyzny, ochronę przed przypadkową zmianą szkicu, otwarty
łańcuch, zamknięty profil, odrzucenie duplikatu/NaN/samoprzecięcia i natywne
wyciągnięcie na XZ o objętości 2000 mm³. Dotykowo rozpoczęto polilinię,
uruchomiono ponownie aplikację po dwóch punktach, dokończono cztery punkty
i zamknięto obwód dotknięciem początku. Z profilu wykonano wycięcie 5 mm.
Sprawdzono panel ochrony przy zmianie narzędzia, cofnięcie punktu i zapis
otwartego łańcucha.

## Trwałe połączenia punktów — 0.31.0

Ikona Więzy w górnym pasku szkicu uruchamia wybór dwóch punktów różnych
elementów: końców linii i linii pomocniczych, narożników prostokąta,
wierzchołków konturu lub środka okręgu. Pierwszy punkt pozostaje na miejscu,
drugi zostaje do niego dołączony. Kółka oznaczają dostępne punkty; fioletowe
oznaczenie pozostaje przy zapisanym połączeniu. Edytuj wraca do przesuwania.

Dokładny liniowy solver propaguje wspólne współrzędne, zachowując blokady
długości/kąta i szerokości/wysokości. Okrąg przesuwany za środek zachowuje
promień. Swobodny wierzchołek może zmienić kształt, a zwymiarowana linia
przesuwa się bez rozciągania. Podgląd szkicu przelicza sąsiadów podczas
przeciągania; po zatwierdzeniu aktualizowane są wszystkie powiązane profile
bryłowe. Sprzeczne więzy, zerowe figury, samoprzecięcia i niepoprawna
przebudowa bryły są odrzucane bez częściowego zapisu geometrii.

Schemat 18 zapisuje więzy razem z bieżącym i zachowanymi szkicami; starsze
projekty nie wymagają migracji ręcznej. Cofnij/Ponów obejmuje więzy.
Geometria pozwala usunąć wszystkie połączenia wybranego elementu bez zmiany
położenia. Usuwanie elementu usuwa jego więzy. Przycinanie/rozbijanie wymaga
najpierw ich usunięcia. Połącz kontur zachowuje połączenia z zewnętrznymi
elementami; połączone końce wewnętrznych linii stają się jednym wierzchołkiem
konturu. Edycja historii nie pozwala obejść więzów zmianą surowych współrzędnych.

To solver liniowy, nie kompletny solver nieliniowy. Kierunki H/V i punkty
stałe dodano w 0.32.0 poniżej. Nadal nie obsługuje styczności, ogólnej
równoległości, prostopadłości ani równości długości dwóch swobodnych elementów.

## Kierunki H/V i punkty stałe — 0.32.0

Więzy w górnym menu otwierają prawy panel: Połącz dwa punkty, Odcinek
poziomy (H), Odcinek pionowy (V), Unieruchom punkt. H/V nadaje się przez
dotknięcie linii, linii pomocniczej lub boku zamkniętej polilinii. Kierunki
odnoszą się do osi lokalnej płaszczyzny szkicu, również na ścianach modelu.
Prostokąty mają już kierunki boków wynikające z ich reprezentacji.

Wolna pojedyncza linia może zostać obrócona do H/V bez zmiany długości,
także o 90 stopni. Zablokowany wymiar/kąt nie jest po cichu usuwany; konflikt
z nowym kierunkiem jest odrzucany. Dotknięcie punktu w trybie Unieruchom
zapisuje jego dokładne współrzędne. Kwadrat oznacza punkt stały, H/V kierunek,
a kółko zbieżność punktów. Zasłonięte przez bryłę punkty nie są wybierane.

Przeciąganie wierzchołka steruje tylko jego dostępnymi współrzędnymi;
pozostałe połączone wierzchołki rozwiązuje solver. Dzięki temu narożnik
konturu z bokami H/V zmienia rozmiar bez łamania kierunków. Stały początek
poziomej/pionowej prowadnicy pozostaje nieruchomy, a jej drugi koniec można
przeciągać wzdłuż kierunku. Przesunięcie całego elementu ze stałym punktem
jest odrzucane. Numeryczna edycja pozostaje dokładna: sprzeczne współrzędne
nie są automatycznie zastępowane innymi.

Geometria → Zarządzaj więzami pokazuje osobne pozycje do usuwania H, V,
punktu stałego i połączenia punktów, a także usunięcie wszystkich więzów
wybranego elementu. Usunięcie więzu nie zmienia bieżącego położenia i jest
objęte Cofnij/Ponów. Połącz kontur zachowuje kierunki i stałe punkty przy
przekształceniu linii w wierzchołki konturu. Schemat 19 zapisuje rodzaj więzu
i współrzędne stałych punktów; wcześniejsze połączenia nadal się odczytują.

AxisConstraintProbe na Androidzie potwierdza kierunki, obrót o 90 stopni,
stałe punkty, projekcję przeciągania, sprzeczne więzy, propagację między
wierzchołkami tego samego konturu, zapis/archiwum i dokładną objętość OCCT
700 mm³ po zmianie konturu. Przeszły też ConstraintProbe, DimensionProbe,
SketchEditingProbe, VisibilityProbe i PolylineProbe. Na tablecie sprawdzono
H/V, wydłużanie i skracanie prowadnicy, zachowanie punktu stałego po restarcie,
usunięcie pojedynczej blokady i przesunięcie elementu z pozostawionym V.
Prowadnicę dodaną do testów usunięto po ich zakończeniu.

## Kierunek wyciągnięcia i operacji Boolean — 0.33.0

Prawy panel wyciągnięcia, dodawania, wycinania i edycji głębokości z historii
ma przełącznik Odwróć kierunek. Wartość w polu pozostaje dodatnia
(0,1–5000 mm). W rekordzie źródłowym znak głębokości koduje kierunek:
dodatni zachowuje dotychczasowy kierunek, ujemny go odwraca. Wycinanie
domyślnie wchodzi w materiał w stronę przeciwną do normalnej płaszczyzny,
zaś dodawanie biegnie zgodnie z nią. Nie zmienia to płaszczyzny ani geometrii
źródłowego szkicu. Nowa operacja zaczyna od domyślnego kierunku; edycja
istniejącej zachowuje jej zapisany kierunek.

Pierwsze wyciągnięcie profilu na płaszczyźnie bazowej otwiera panel długości
i kierunku zamiast od razu tworzyć bryłę ze stałą głębokością. Zmiany pola
lub przełącznika pokazują podgląd B-Rep przed Zastosuj, również przy edycji
wcześniejszych operacji z historii. Anulowanie nie zapisuje podglądu.
Niepoprawny kierunek, brak rzeczywistej zmiany materiału lub niepoprawna
przebudowa dalszych cech pozostawia ostatni prawidłowy dokument.

Natywne prostokąty, okręgi i kontury obsługują oba kierunki na XY/XZ/YZ
i w lokalnym układzie szkicu na ścianie. Usunięto sztuczne naddatki narzędzi
0,01/0,02 mm, które przy złym kierunku mogły tworzyć cienki, pozornie poprawny
wynik. Boolean korzysta z dokładnego zakresu operacji. SimplifyResult OCCT
scala współpłaszczyznowe ściany i zbędne krawędzie po dodawaniu/wycinaniu.
Schemat 20 zachowuje kierunki baz, aktywnych cech i historii; eksport STEP
wykorzystuje tę samą geometrię.

DirectionProbe na fizycznym Androidzie sprawdził wszystkie trzy typy profili
na trzech płaszczyznach, granice odwróconej bryły, objętości prostych brył,
zgodność lustrzanych siatek powierzchni zakrzywionych, dodawanie/wycinanie od
spodu, brak pozornego naddatku, zapis znaków i STEP round-trip. Przeszły
NativeSequenceProbe (w tym HLR/przekrój), PolygonProbe oraz regresja szkiców.
W interfejsie tabletu sprawdzono podgląd odwróconego dodania wypełniającego
istniejące wycięcie, zatwierdzenie, zachowanie kierunku po restarcie oraz
usunięcie operacji testowej. Podgląd błędnego kierunku historycznego wycięcia
zgłosił brak wyniku; zamknięto go bez zapisu. Pierwsze wyciągnięcie bazowe
ma pokrycie natywnymi testami geometrii i kompilacją panelu, ale nie przeszło
jeszcze osobnej pełnej próby dotykowej na nowym dokumencie w 0.33.0;
próbę tę wykonano w 0.34.0 (poniżej).

Wyciągnięcie jednostronne z odwracaniem rozszerzono w 0.35.0 o tryb
symetryczny. Nadal brak zakończenia do wskazanej powierzchni i wyciągnięć
z pochyleniem.

## Biblioteka projektów i pusty dokument — 0.34.0

Dotknięcie nazwy dokumentu w górnym pasku otwiera panel: Nowy pusty projekt,
Moje projekty, Dane i nazwa dokumentu. Bieżący dokument jest zapisywany przed
utworzeniem lub otwarciem innego. Projekty o tej samej nazwie mają niezależne
identyfikatory i pliki. Biblioteka pokazuje aktualny projekt i rodzaj danych.
Nowy projekt zaczyna od pustej sceny; szkice bez bryły można edytować,
wymiarować i zapisywać. Nie jest tworzona ani eksportowana zastępcza kostka.
Schemat 21 rozróżnia pusty dokument od domyślnej bryły starszych projektów.

Każdy projekt ma osobny snapshot document-ID.tcad i kopię .bak w prywatnym
katalogu aplikacji. autosave.tcad zachowuje wybór ostatnio aktywnego projektu.
Zapis używa pliku tymczasowego, synchronizacji danych i atomowej podmiany.
Poprzedni prawidłowy zapis jest kopiowany do .bak; plik nierozpoznawalny jest
zachowywany pod unikalną nazwą unreadable-ID.tcad przed zastąpieniem.
Przy uszkodzeniu zapisu używana jest kopia, a następnie biblioteka projektów.
Identyfikatory nie mogą wskazywać poza katalog projektów, rozmiar JSON jest
ograniczony do 16 MiB. Przełączanie nie nadpisuje zachowanych źródeł STEP/STL.
Projekt z niedostępną geometrią nie zastępuje aktywnego dokumentu.

Przy zmianie projektu odtwarzane są szkice, więzy, parametry, operacje,
tabliczka i niedokończona polilinia. Stos Cofnij/Ponów jest sesyjny i czyści
się przy przełączeniu, aby nie przenosić zmian między dokumentami. Historia
operacji modelu pozostaje zapisana w projekcie. Nie ma jeszcze usuwania
projektów z biblioteki ani przenośnego eksportu całego pakietu projektu.

Pasek funkcji korzysta z jednej listy do rysowania i obsługi dotyku.
Wyciągnięcie bazowe nie znika po rozpoczęciu innego szkicu. Ikona bazy
otwiera jej parametry, ikona cechy właściwą operację, ikona szkicu wybór
bieżących/zachowanych szkiców. Edycja bazy zachowuje aktywne dodanie lub
wycięcie i uwzględnia je w podglądzie/przebudowie. Klikanie cech przy
niedokończonej polilinii wymaga zakończenia lub anulowania jej punktów.

ProjectLibraryProbe na Androidzie potwierdza oddzielne dokumenty, szkice,
więzy, draft, kierunek wyciągnięcia, odzyskiwanie, ochronę źródeł i migrację
starszego autosave. TimelineProbe sprawdza trwałość bazy i indeksowanie
cech po dodaniu ikon importu, szkicu i aktywnej operacji.
W interfejsie tabletu utworzono CAD-test-034, nadano prostokątowi 40 × 20 mm
i wykonano pierwsze odwrócone wyciągnięcie 12 mm. Sprawdzono Cofnij/Ponów,
restart z odtworzeniem parametrów, powrót do Wspornik-A i ponowne otwarcie
testu. Przełączenie zachowało rozpoczętą polilinię. Sprawdzono też zmianę
bazy przy zachowanym aktywnym wycięciu 3 mm i cofnięto dodatkowe próby.
Projekt CAD-test-034 pozostaje jako osobny przykład; Wspornik-A zachowano.

## Wyciągnięcia symetryczne — 0.35.0

Panel głębokości udostępnia „Symetrycznie (długość łączna)” dla pierwszej
bryły, dodawania i wycinania, również podczas edycji historii. Wpisana
wartość jest całkowitą długością: 12 mm daje po 6 mm po obu stronach
płaszczyzny. Tryb nie przesuwa płaszczyzny szkicu; odwracanie kierunku
jest w nim nieaktywne. Podgląd korzysta z tej samej geometrii OCCT co
zatwierdzenie. Schemat 22 zachowuje tryb bazy, aktywnej operacji i historii;
starsze projekty pozostają jednostronne.

SymmetricProbe sprawdza prostokąt, koło i wielokąt na trzech płaszczyznach,
granice i objętości, dodawanie/wycinanie, lokalną płaszczyznę, zapis JSON,
rozpoznanie wycięcia w historii oraz STEP round-trip. Przeszły również
DirectionProbe, NativeSequenceProbe, PolygonProbe, TimelineProbe,
ProjectLibraryProbe, AxisConstraintProbe i VisibilityProbe na Androidzie.
Na tablecie potwierdzono symetryczną bazę 12 mm, podgląd i zatwierdzenie
wycięcia 4 mm oraz odtworzenie obu parametrów po restarcie aplikacji.

W zwykłym widoku bryły zasłonięte fragmenty krawędzi i szkicu są ukrywane,
nie rysowane ciągłą linią przez model. W arkuszu technicznym linie ukryte
są przerywane. Testy widoczności obejmują częściowe zasłonięcie, perspektywę,
szwy triangulacji i brak wybierania zasłoniętych fragmentów szkicu.

## Zaokrąglanie wskazanej krawędzi — 0.36.0

Górna ikona Zaokrąglij otwiera prawy panel promienia. Dotknięcie widocznej
krawędzi wskazuje dokładną krawędź OCCT (nie odcinek siatki renderowania).
Zaznaczenie jest pomarańczowe; podgląd reaguje na wpisywany promień.
Przeciągnięcie nadal obraca bryłę, można wskazać inną krawędź przed
zatwierdzeniem. Ukryte fragmenty nie przechwytują wskazania. Zastosuj dodaje
operację w bieżącym miejscu historii, po dotychczasowych dodaniach/wycięciach.
Ikona operacji na dolnym pasku pozwala zmienić jej promień. Działa też na
bryłach importowanych z STEP. STL pozostaje siatką, bez operacji B-Rep.

Schemat 23 zapisuje operację typu -1 jako 16 liczb: typ, zero, trzy próbki
krawędzi XYZ (początek, środek, koniec), promień, cztery zera. Przebudowa
wyszukuje jedną zgodną krawędź w geometrii wejściowej danej operacji,
nie odwołuje się do niestabilnego indeksu ściany/siatki. Brak lub
niejednoznaczność dopasowania przerywa przebudowę; nie wybiera innej
krawędzi. Jest to konserwatywne dopasowanie geometryczne, nie pełny system
topologicznego nazewnictwa: zmiana położenia/wymiarów krawędzi bazowej może
wymagać usunięcia i ponownego wskazania zaokrąglenia. OCCT może propagować
zaokrąglenie po stycznej kontynuacji wskazanej krawędzi. Wybór wielu
krawędzi naraz i zmienny promień nie są jeszcze dostępne.

EdgeFilletProbe na Androidzie sprawdza dokładne krawędzie prostopadłościanu,
objętość pojedynczego narożnika, odwróconą orientację próbek, kolejność
operacji, dwa niezależne zaokrąglenia, import/eksport STEP, kołową krawędź,
odrzucanie błędnego promienia i utraconej referencji, JSON oraz etykietę
historii. W interfejsie tabletu sprawdzono wybór krawędzi, podgląd R3,
odrzucenie R100 i edycję promienia przez dolny pasek historii. Promień R2
odtworzono po restarcie. Przeszły regresje DirectionProbe, SymmetricProbe,
NativeSequenceProbe, PolygonProbe, TimelineProbe, VisibilityProbe,
ProjectLibraryProbe, ConstraintProbe i AxisConstraintProbe. Test biblioteki
wymaga katalogu jako argumentu (pozostałe testy STEP przyjmują ścieżkę pliku).

## Pomiary wybranej geometrii OCCT — 0.37.0

Ikona Zmierz otwiera prawy panel z wyborem „Krawędź · długość / R / Ø”
lub „Punkt–punkt · wierzchołki”. Pierwszy tryb wskazuje całą krawędź B-Rep,
wyróżnia jej widoczną część na niebiesko i pokazuje długość obliczoną
przez BRepGProp::LinearProperties. Dla koła i łuku promień i środek pochodzą
z BRepAdaptor_Curve, a nie z bieżącego szkicu ani aproksymacji ekranu.
Koło pokazuje obwód, łuk długość łuku oraz R i Ø jego okręgu. Dla pozostałych
krzywych raportowana jest długość z obliczeń OCCT, bez wymyślonego promienia.
Linia wymiarowa jest równoległa do prostej krawędzi; kołowa ma oznaczenie R.
Wartość pozostaje dostępna w panelu także po obrocie ukrywającym krawędź.
Zasłoniętych krawędzi nie można wybierać przez bryłę. Techniczne szwy
zamkniętych powierzchni okresowych są pomijane przy wskazywaniu krawędzi.

Drugi tryb przyciąga do widocznych końców krawędzi B-Rep, pokazuje odległość
3D i bezwzględne składowe ΔX/ΔY/ΔZ. Nie wybiera przypadkowego wierzchołka
tessellacji zakrzywionej ściany. Nie ma jeszcze dokładnego wskazywania
dowolnych punktów wewnątrz krzywej, najkrótszej odległości między dwiema
krzywymi/ścianami ani pomiarów kątów. STL zachowuje oddzielny, wyraźnie
opisany jako przybliżony pomiar punktów siatki; nie udaje geometrii B-Rep.
Pomiary są odczytem sesyjnym, nie trwałymi wymiarami rysunku ani więzami
szkicu. Wyczyść pomiar usuwa bieżące wskazanie, nie geometrię modelu.

MeasurementProbe potwierdza długości 12 krawędzi prostopadłościanu,
zgodność identyfikatorów wskazywania, promień i obwód cylindra na trzech
płaszczyznach, pomijanie szwu, import STEP bez szkicu źródłowego, długość
łuków zaokrąglenia i odległość punktów 3D. Na tablecie wskazano 80 mm na
modelu Wspornik-A, zmierzono dwa jego wierzchołki (80 mm, ΔX=80, ΔY=ΔZ=0)
i łuk w CAD-test-034 (długość 3,142 mm, R2, Ø4). Obrót zachował pomiar łuku.
Przeszły regresje zaokrągleń, kierunków, symetrii, Boolean/STEP/arkusza,
wielokątów, historii, widoczności, biblioteki i więzów szkicu.

## Pogrupowany górny pasek i czytelny szkic — 0.38.0

Górny pasek jest natywną, przewijaną poziomo listą kompaktowych ikon
z podpisami i opisami dostępności. Strefa dotyku ma 64 × 48 dp; sam symbol
jest mniejszy niż wcześniej. Grupy rozwijają ikonowe menu przy górnym pasku,
bez zajmowania prawego panelu parametrów ani dolnej historii. Dotknięcie
poza menu lub Wstecz zamyka menu bez wykonywania operacji na modelu.

W modelowaniu są Wybierz (bezpośrednio), Szkice (nowy/edycja), Modeluj
(wyciągnij/wytnij), Zmień (głębokość/zaokrąglij/usuń cechę), Sprawdź
(pomiar/arkusz) i Widok (dopasuj/wyczyść widok). W szkicu są Edytuj,
Rysuj (linia/polilinia/prostokąt/okrąg), Pomocnicza, Zmień (geometria/
połącz kontur/przytnij), Więzy (więzy/wymiar), Usuń (element/wyczyść szkic),
Widok (pokaż lub ukryj bryłę/dopasuj) i bezpośrednie Zakończ.
Katalog jest wspólny dla natywnych przycisków i awaryjnego hit-testu Canvas.
Nie ma narzędzi brył w menu szkicu ani rysowania figur w menu bryły.

Wejście w edycję szkicu lub wybór płaszczyzny sprawdza geometrię względem
faktycznego kierunku kamery. Jeżeli bryła jest przed płaszczyzną szkicu
(np. szkic XY wewnątrz wyciągnięcia symetrycznego), bryła jest tymczasowo
ukrywana, aby szkic był widoczny i wybieralny. Nie przesuwa to szkicu ani
nie włącza prześwietlania ukrytych krawędzi. Panel wyjaśnia stan; Widok →
Pokaż bryłę pozwala przywrócić bryłę ręcznie. Zakończ przywraca widok bryły.
To izolacja całej bryły podczas edycji, nie przekrój ani przezroczysty podgląd.

ToolbarProbe sprawdza kompletność poleceń, pojedyncze przypisanie do grup,
rozdzielenie kontekstów, bezpośrednie zakończenie szkicu, odporność katalogu
na zmianę zwróconej tablicy i widoczność szkicu na płaszczyznach XY/XZ/YZ.
Na tablecie sprawdzono menu Modeluj, Szkice, Rysuj, Widok i Zmień,
wejście w szkic wewnątrz bazy symetrycznej, odsłonięcie wymiarów 40 × 20 mm,
przełączenie widoczności, narysowanie pomocniczej, cofnięcie próby oraz
powrót do modelowania. Próby wykonano w CAD-test-034; Wspornik-A zachowano.

## Łuki analityczne i kontury mieszane — 0.39.0

W szkicu grupa Krzywe zawiera Okrąg i Łuk. Łuk rysuje się trzema
dotknięciami: początek, koniec, punkt pośredni na wybranej stronie cięciwy.
Trzeci punkt wybiera także łuk większy niż 180°. Niedokończony łuk można
kontynuować albo jawnie anulować w prawym panelu. Cofnij cofa jego ostatni
punkt zamiast wcześniejszej operacji modelu. Punkty niedokończonego łuku
są tymczasowe i nie odtwarzają się po zamknięciu procesu; zapisane łuki
pozostają w projekcie, historii i archiwum szkiców.

Edytuj pozwala przesuwać cały łuk albo jego końce i punkt pośredni.
Panel segmentu pozwala też wpisać współrzędne trzech punktów. Niezmienione
pola zachowują pierwotną dokładność danych, mimo skróconego zapisu na ekranie.
Połącz kontur obsługuje zamknięty, nierozgałęziony łańcuch linii i łuków.
Otwarty łuk nie jest automatycznie zamykany cięciwą i nie jest profilem
do wyciągnięcia. Zmiana wierzchołka zamkniętego konturu przesuwa końce
sąsiednich segmentów, zachowując ich połączenie. Kontur można rozbić z
powrotem na analityczne linie i łuki, o ile nie jest już źródłem operacji.
Łuki przyciągają końce, środek długości i środek okręgu; proste można
przycinać na ich analitycznych przecięciach z łukiem.

Geometria źródłowa nie jest wielokątem: zapisuje segmenty typu linia lub
łuk przez trzy punkty. OCCT buduje łuki GC_MakeArcOfCircle, zamknięty wire,
ścianę i rzeczywisty pryzmat B-Rep. Profile mieszane obsługują pierwsze
wyciągnięcie, dodawanie, wycinanie, odwracanie i symetrię, również na
lokalnej płaszczyźnie ściany. Edycja źródła aktualizuje powiązane operacje.
Próbkowanie służy wyłącznie wyświetlaniu i wskazywaniu; granice konturu
obliczane są z analitycznych ekstremów. Java odrzuca otwarte łańcuchy,
rozgałęzienia, nakładanie i samoprzecięcia, a OCCT sprawdza wynik B-Rep.

Schemat 24 dodaje curves: po 7 liczb na segment (typ, początek XY,
punkt pośredni XY, koniec XY). Typ profilu 1000+liczba segmentów zachowuje
dotychczasowe prefiksy bazy i operacji. Maksimum to 128 segmentów.
Eksport STEP zachowuje łuki; DXF zapisuje encje ARC/LINE, nie aproksymowaną
polilinię. Import ARC obsługuje płaskie XY w standardowym układzie OCS.
Łukowe bulge w LWPOLYLINE nadal nie są obsługiwane.

Ograniczenia tego etapu: brak więzów styczności/promienia między łukami,
wymiarów sterujących łukiem i przycinania samych krzywych (dodane w 0.40.0).
Przed połączeniem linii
z łukiem należy usunąć więzy i blokady wymiarów łączonych linii; solver
liniowy nie udaje rozwiązania nieliniowych więzów. Pozostałe więzy w szkicu
nie powodują zamiany niepowiązanego łuku w prostą. Rozpoczęcie rysowania
łuku nie tworzy niepełnej encji w dokumencie.

ArcProbe na fizycznym Androidzie sprawdza natywne półwalce na XY/XZ/YZ,
symetrię, analityczne promienie i długości krawędzi, Boolean, STEP,
rozbijanie/łączenie, JSON/archiwum oraz DXF ARC+LINE. Objętość liczona
z siatki ma tolerancję tessellacji; dowodem zachowania analitycznych łuków
są promienie i długości krawędzi odczytane bezpośrednio z OCCT.
CurveGeometryProbe sprawdza 300 deterministycznych łuków CW/CCW,
6300 punktów, odwracanie, granice, DXF, kontur kołowy i kapsułę,
przeciąganie, przecięcia i odrzucanie nakładających się krzywych.

Na tablecie utworzono osobny Arc-test-039: łuk trzema dotknięciami,
linia domykająca, Połącz kontur i wyciągnięcie 12 mm. Bryła i szkic
odtworzyły się po restarcie. Zmiana pośredniego Y na 40 mm przebudowała
bryłę: pomiar OCCT zmienił R43,989 na R44,421 mm. Cofnij/Ponów przywróciły
odpowiednie promienie; na końcu cofnięto próbę do pierwotnego łuku.
Sprawdzono też cofnięcie punktu niedokończonego łuku i jawne anulowanie
przed zakończeniem szkicu. Wspornik-A i CAD-test-034 nie były zmieniane.
Dotykowe przeciągnięcie pośredniego punktu również przebudowało bryłę
(pomiar R44,134 mm); tę próbę cofnięto. W interfejsie sprawdzono arkusz
łukowej bryły z trzema rzutami i przekrojem. ArcProbe potwierdził też
cztery widoki/przekrój i skończone współrzędne HLR dla takiego profilu.

Przeszły na Androidzie: ArcProbe, CurveGeometryProbe, ToolbarProbe,
MeasurementProbe, EdgeFilletProbe, DirectionProbe, SymmetricProbe,
NativeSequenceProbe, PolygonProbe, PolylineProbe, TrimProbe,
SketchEditingProbe, SketchWorkflowProbe, DimensionProbe, TimelineProbe,
VisibilityProbe, ProjectLibraryProbe, ConstraintProbe, AxisConstraintProbe
i ExchangeProbe. CurveGeometryProbe działa również lokalnie na JVM.
Przy pakowaniu wielu testów do DEX użyto pliku argumentów D8 (@plik,
jedna pełna ścieżka bez cudzysłowów na wiersz), aby uniknąć limitu
długości polecenia Windows. Plik argumentów musi zawierać każdą nową klasę
testową; nieobecność klasy nie jest błędem funkcji aplikacji.

## Przycinanie łuków i okręgów — 0.40.0

Szkic → Zmień → Przytnij obsługuje teraz linię, łuk, okrąg oraz segment
nieużywanego konturu mieszanego. Dotknięcie wskazuje przedział pomiędzy
rzeczywistymi przecięciami. Łuki i okręgi mogą być przecinane liniami,
prowadnicami, obrysami figur i innymi łukami/okręgami. Obliczenia przecięć
i podziału są analityczne, nie wykonywane na odcinkach podglądu.

Przycięty okrąg staje się jednym otwartym łukiem zachowującym jego promień.
Podział jest cykliczny: kąt 0° nie tworzy sztucznej granicy przycinania.
Usunięcie środkowej części łuku pozostawia dwa niezależne łuki. Przycięcie
nieużywanego konturu mieszanego otwiera go na pozostałe analityczne segmenty.
Cały element bez przecięć usuwa narzędzie Usuń; brak przecięć, pojedyncza
styczność okręgu, dotknięcie punktu przecięcia oraz nakładające się krzywe
nie powodują przypadkowego skasowania geometrii. Wymiary sterujące,
więzy punktów i profile używane przez bryłę nadal są chronione przed
otwarciem obwodu. Edycja takich profili wymaga zachowania zamkniętego
kształtu lub pracy w nowym szkicu — nie jest to jeszcze odroczona edycja
niezamkniętego źródła z przebudową dopiero przy Zakończ.

Dodano przyciąganie do obwodu i punktów kardynalnych okręgu. Zatwierdzenie
pociągnięcia ponawia analityczne przyciąganie, aby zamiana współrzędnych
podglądu na float nie wprowadzała mikroszczeliny między końcem linii
a krzywą. Wycięty łuk można połączyć z linią domykającą i wyciągnąć jako
rzeczywistą bryłę; nie trzeba poprawiać końców numerycznie.

CurveTrimProbe sprawdza podziały przez 0°, oba kierunki łuku, łuki większe
niż 180°, przecięcia dwóch okręgów, otwarcie konturu mieszanego, zachowanie
promienia i tożsamości, 100 kierunków średnicy, błędne wskazania, JSON,
połączenie przyciętego łuku z cięciwą i natywne wyciągnięcie/STEP.
Na tablecie w osobnym Trim-test-040 narysowano okrąg i średnicę,
przycięto górną połowę, potwierdzono przywrócenie okręgu przez Cofnij oraz
łuku przez Ponów. Po poprawce przyciągania powtórzono rysowanie, przycięcie,
Połącz kontur i wyciągnięcie 8 mm. W projekcie pozostawiono wynikowy przykład.
Restart odtworzył bryłę i parametr 8 mm. Próba przycięcia używanego profilu
bazy została odrzucona z komunikatem, bez otwarcia obwodu ani zmiany modelu.
Przeszły CurveTrimProbe i wszystkie 20 zestawów regresyjnych wymienionych
przy 0.39.0, ponownie skompilowane i uruchomione na fizycznym Androidzie.
Wersja nie zmienia schematu projektu 24; wynik przycinania korzysta z
istniejącego zapisu łuków i linii.

### 0.41.0 — robocza edycja szkicu

Edycja zachowuje poprzedni graf bryły do zatwierdzenia przez Zakończ.
Można przyciąć lub rozbić używany profil, ponownie go domknąć i połączyć.
Anuluj szkic (ikona X w górnym menu) przywraca stan sprzed sesji.
Zakończ aktualizuje typ i geometrię powiązanych profili oraz sprawdza
przebudowę zależnych operacji. Niepoprawny wynik pozostawia szkic roboczy
i dotychczasową bryłę. Gdy pochodzenie zamkniętego profilu jest niejednoznaczne,
prawy panel wymaga wskazania zamiennika. Usuwanie całej zawartości używanego
szkicu jest dozwolone w sesji, ale wymaga odbudowy profilu przed zatwierdzeniem.

Schemat 25 zapisuje stan sprzed edycji i pochodzenie fragmentów. Restart
wznawia szkic roboczy. Cofnij/Ponów przechodzi między sesją a zatwierdzonym
modelem. Nawigacja do innych dokumentów podczas sesji wymaga jej zakończenia
lub anulowania. To nie jest automatyczne naprawianie utraconych odniesień
krawędzi zaokrągleń: niezgodna operacja zależna blokuje zatwierdzenie.

SketchTransactionProbe przeszedł na fizycznym Androidzie: zapis/odczyt sesji,
zachowanie stanu do anulowania, blokada otwartego profilu, dziedziczenie
powiązania po przycięciu i połączeniu, przebudowa okręgu w analityczny profil
półwalca, zachowanie głębokości, odrzucenie błędnej tożsamości sesji.
Przeszło również 21 dotychczasowych zestawów regresyjnych. VisibilityProbe
potwierdza ukrywanie zasłoniętych krawędzi i szkiców, częściowe zasłonięcie,
perspektywę i brak wybierania elementów przez bryłę.
W interfejsie, wyłącznie w Trim-test-040: wyczyszczono roboczy profil,
potwierdzono blokadę Zakończ, zrestartowano aplikację i przywrócono profil
oraz bryłę 8 mm przez Anuluj. Potwierdzono także Zakończ i Cofnij/Ponów
odtwarzające odpowiednio menu szkicu i modelowania. Wspornik-A nie był
przedmiotem testowych modyfikacji geometrii.

### 0.42.0 — fazowanie krawędzi i kopia lustrzana szkicu

Bryła → Zmień → Fazuj tworzy rzeczywistą fazę OCCT na wskazanej widocznej
krawędzi. Panel po prawej obsługuje wartość 0,1–1000 mm, podgląd, anulowanie
i zatwierdzenie. Jest to faza o równych odsunięciach na sąsiednich ścianach
(45° dla ścian prostopadłych), nie faza o zadanym dowolnym kącie.
Operacja trafia do historii z własną ikoną i pozwala później zmienić parametr.
Współpracuje z dodaniem, wycięciem, zaokrągleniem i importowanym STEP.
Niepoprawny wynik lub utracona sygnatura krawędzi blokuje zatwierdzenie.
Schemat 26 zapisuje typ operacji -2, sygnaturę krawędzi i wielkość fazy.

Szkic → Zmień → Lustro kopiuje zaznaczony element względem osi X, Y lub
istniejącej linii pomocniczej tego szkicu. Odbicie zachowuje analityczne
okręgi i łuki; ukośnie obrócony prostokąt jest zapisywany jako zamknięty
kontur, a nie jego prostokąt ograniczający. Kopia ma nową tożsamość,
jest edytowalna i może być profilem wyciągnięcia lub wycięcia.
To niezależna kopia: nie tworzy więzu symetrii ani zależności od źródła,
nie kopiuje jego więzów. Odbicie brył 3D pozostaje niewdrożone.

EdgeChamferProbe potwierdza analityczną objętość fazowanej kostki,
niezależność od orientacji krawędzi, dwie fazy w historii, kolejność
z wycięciem/zaokrągleniem, edycję, STEP round-trip, fazę okrągłej krawędzi,
odrzucanie nieprawidłowych parametrów i zapis projektu.
SketchMirrorProbe potwierdza osie główne i 300 ukośnych osi, długości,
pole prostokąta, analityczny promień i orientację łuku, kontur mieszany,
JSON oraz rzeczywiste wyciągnięcie odbitego profilu i STEP round-trip.
Wszystkie 24 zestawy testów uruchomione na fizycznym Androidzie: PASS.

Test interfejsu w CAD-test-034: wybrano krawędź, zatwierdzono fazę 3 mm,
edytowano ją z historii do 2 mm; restart odtworzył parametr 2 mm.
Kopia prostokąta względem osi Y przetrwała zatwierdzenie i restart.
Następnie narysowano ukośną linię pomocniczą i odbito względem niej
prostokąt, sprawdzając poprawny czterowierzchołkowy kontur w widoku.
Ta dodatkowa robocza próba została anulowana; w projekcie pozostają
faza 2 mm i dwa prostokąty. Wspornik-A nie był modyfikowany testowo.

Mapa pozostałego zakresu: STATUS_WYMAGAN_CAD.md. Przejście testów
regresyjnych nie oznacza jeszcze spełnienia całego promptu CAD.

### 0.43.0 — parametryczny obrót profilu i dokładne właściwości bryły

Modeluj → Obrót profilu obsługuje prostokąt, okrąg, zamknięty wielokąt
i analityczny kontur mieszany. Oś wybiera się jako X/Y płaszczyzny szkicu
lub jego linię pomocniczą. Kąt 0,1–360° i odwrócenie kierunku mają podgląd.
W pustym dokumencie powstaje bryła bazowa. Na istniejącej bryle operacja
może dodawać lub wycinać materiał. Całość wykonuje BRepPrimAPI_MakeRevol
OCCT, z kontrolą poprawności, objętości i przecięcia osi z wnętrzem profilu.
Współpłaszczyznowe ściany wynikowe są scalane, aby nie rysować sztucznych
szwów na płaskim przekroju częściowego obrotu.

Obrót jest edytowalny z historii. Zapisuje pełną płaszczyznę, oś, profil,
kąt i tryb dodania/wycięcia. Schemat 27 obsługuje zarówno bazowy obrót,
jak i późniejsze obroty w historii. Edycja źródłowego szkicu przez sesję
roboczą aktualizuje profil obrotu bez nadpisywania osi lub kąta.
Oś pomocnicza jest obecnie zapisana jako geometria osi w chwili wyboru;
nie stanowi jeszcze automatycznej zależności od później przesuniętej linii.
W edycji można wybrać ponownie linie pomocnicze aktywnego szkicu, jeśli
jego układ współrzędnych odpowiada płaszczyźnie operacji.

Sprawdź → Właściwości bryły pokazuje dokładną objętość [mm³], pole [mm²],
środek objętości oraz liczbę brył i ścian. Wyniki pochodzą z BRepGProp,
nie z sumowania trójkątów. Centrum odpowiada środkowi masy tylko dla
jednorodnej gęstości. Nie dodano jeszcze materiałów, masy ani bezwładności.

RevolveProbe na Androidzie: pełne/częściowe/odwrócone obroty, osie X/Y,
ukośna oś i przesunięte płaszczyzny XZ/YZ, walec, torus, kula z profilu
łukowego, rowek i kołnierz, dokładne objętości OCCT, STEP, błędne osie,
zapis/odczyt, historia, edycja źródła i przywrócenie roboczej sesji.
BodyPropertiesProbe: analityczne objętości, pola i środki kostki,
otworu przelotowego i fazowanej bryły oraz odtworzenie właściwości ze STEP.
Wszystkie 26 zestawów testów uruchomionych na fizycznym Androidzie: PASS.

Test interfejsu w osobnym Revolve-test-043: dotykowo narysowano prostokąt,
zakończono szkic i wykonano obrót 360° wokół osi Y. Następnie zmieniono
wymiary źródła na dokładne 10 × 20 mm; zatwierdzenie dało objętość
6283,185307 mm³ i pole 1884,955592 mm². Edycja kąta z historii do 180°
i restart zachowały objętość 3141,592654 mm³, pole 1342,477796 mm²
i środek Z = -4,244132 mm. Pozostałe projekty nie służyły do tych zmian.

Pełny test akceptacyjny B nadal nie jest zaliczony: aktualizacja dowolnych
odniesień zaokrągleń/faz po zmianie wcześniejszego promienia pozostaje
osobnym zadaniem. Samo dodanie Revolve nie rozwiązuje nazewnictwa topologii.

### 0.44.0 — odniesienia krawędzi do parametrów bryły bazowej

Nowe fazy i zaokrąglenia mogą zachowywać wybraną krawędź po zmianie
parametrów bazowego wyciągnięcia prostokąta/okręgu lub obrotu prostokąta.
Powiązanie powstaje tylko wtedy, gdy wybrana krawędź jednoznacznie
odpowiada krawędzi źródłowej bazy. Operacja przechowuje jej sygnaturę
oraz parametry generatora w chwili wyboru. Silnik odtwarza źródłową
krawędź, przelicza jej położenie przez parametry generatora i sprawdza
jednoznaczne występowanie w nowej bazie oraz aktualnym wyniku historii.
Sprawdza również typ krzywej, a dla okręgów promień, środek i płaszczyznę.
Nie używa wyszukiwania najbliższej krawędzi ani zastępowania utraconej
referencji numerem topologicznym z innego modelu.

Schemat 28 zapisuje powiązane zaokrąglenia jako typ -4, fazy jako -5.
Starsze typy -1/-2 pozostają czytelne. Otwarcie i ponowne zatwierdzenie
starszej operacji próbuje nadać jej powiązanie, jeśli obecna baza pozwala
jednoznacznie odtworzyć źródło. Samo otwarcie/anulowanie nie zapisuje zmiany.
Historia obsługuje mieszanie starszych i nowych zapisów o różnej długości.

Ograniczenia: to nie jest pełne nazewnictwo topologii OCCT dla całej
historii. Krawędzie utworzone dopiero przez wycięcia/zaokrąglenia,
importowane STEP, bazy wielokątne/krzywe i obroty innych profili pozostają
przy ścisłej sygnaturze. Zmiana kąta obrotu z zależną fazą/zaokrągleniem
nie jest automatycznie obsługiwana. Utrata krawędzi, niejednoznaczność
lub niezgodna zmiana typu bazy blokują przebudowę zamiast zmieniać inny
element. Istniejące odniesienia ścian szkiców i operacji wycięcia nie
zostały tym mechanizmem automatycznie przebudowane.

RootEdgeReferenceProbe na fizycznym Androidzie: zmiana wszystkich wymiarów
kostki z zachowaniem R3, odwrócenie i symetria wyciągnięcia, płaszczyzny
XZ/YZ, zmiana środka/promienia/długości walca z fazą, wałek obrotowy
z rowkiem i fazą po zmianie promienia i długości, edycja fazy, STEP,
sesja edycji źródła, usunięta/niejednoznaczna krawędź, błędny zapis,
mieszane długości rekordów starszych i nowych operacji. Wszystkie
27 zestawów testów: PASS.

Na tablecie, Revolve-test-043: faza 1 mm przetrwała zmianę profilu
z 10 × 20 na 12 × 30 mm. Objętość wyniosła 13535,028349 mm³.
Edycja fazy do 2 mm, Cofnij/Ponów i restart zachowały poprawny wynik
13429,261397 mm³. W starszym CAD-test-034 ponownie zatwierdzono R2 i fazę
2 mm; zmiana symetrycznego wyciągnięcia z 12 na 16 mm zachowała oba
elementy, bez ponownego wskazywania krawędzi. Wspornik-A nie był przedmiotem
testowych zmian geometrii.
