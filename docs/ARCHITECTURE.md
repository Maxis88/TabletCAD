# TabletCAD — architektura MVP

## Zakres

TabletCAD jest aplikacją offline dla tabletów z Androidem 14 lub nowszym. Docelowy
rdzeń modeluje precyzyjne bryły B-Rep, a dokument przechowuje parametryczne drzewo
operacji. Importowany STEP jest edytowany bezpośrednio; aplikacja nie deklaruje
odzyskania historii z programu źródłowego.

## Moduły

1. `app-ui` — dotykowy interfejs Android, obsługa rysika, myszy i klawiatury.
2. `document` — wersjonowany manifest, historia operacji, undo/redo i autosave.
3. `sketch` — geometria 2D, więzy, wymiary i solver.
4. `cad-kernel` — C++/Open CASCADE przez JNI; B-Rep, STEP i operacje bryłowe.
5. `mesh-io` — import i eksport STL ASCII/binary.
6. `dxf-io` — szkice oraz arkusze DXF 2D.
7. `drawing` — asocjacyjne rzuty ISO, przekroje, linie niewidoczne, osie i wymiary.
8. `pdf-export` — wektorowy eksport arkuszy i integracja z Android Print Framework.

## Zasady bezpieczeństwa dokumentu

- Każde przeliczenie działa na migawce dokumentu poza wątkiem UI.
- Wynik zastępuje poprzednią geometrię dopiero po pełnym powodzeniu operacji.
- Autosave jest atomowy: plik tymczasowy, synchronizacja i zmiana nazwy.
- Nieznana wersja formatu otwiera dokument tylko do odczytu.
- Importery mają limity rozmiaru, liczby encji i czasu, aby uszkodzony plik nie
  blokował aplikacji.

## Dokumentacja ISO/EU

- jednostka domyślna: mm;
- formaty A4–A0;
- rzutowanie europejskie;
- rzuty podstawowe i izometryczne;
- przekroje, linie niewidoczne, osie i wymiary;
- eksport PDF i DXF 2D oraz druk systemowy Android.

## Etap bieżący

Wersja `0.12.0-toolbar` zawiera interaktywny interfejs, selekcję topologii,
kamerę orbitującą, lokalny układ płaszczyzny szkicu, JNI oraz Open CASCADE 8.0.1.
Rdzeń tworzy prawdziwą bryłę B-Rep, waliduje jej topologię i raportuje liczbę
ścian, krawędzi oraz wierzchołków. Natywny autotest wykonuje zapis bryły do STEP,
ponowny import i kontrolę poprawności topologii. Edytor parametryczny i pełny
moduł dokumentacji technicznej są kolejnymi funkcjami. Dokument
projektu ma wersjonowany manifest JSON, UUID encji, atomowy autosave i kopię
odzyskiwania. Obecnie przechowuje jedną płaszczyznę szkicu, encje linii,
prostokąta i okręgu oraz parametry bazowego wyciągnięcia.

Warstwa JNI tesselluje `TopoDS_Shape`, przekazuje wierzchołki, normalne i indeksy
ścian do Androida. Widok wykonuje projekcję, odrzucanie tylnych ścian, sortowanie
głębokości i selekcję trójkątów. Ręcznie zdefiniowana geometria pudełka nie jest
już źródłem renderowania ani wyboru ściany.

Warstwa plików używa Android Storage Access Framework. STEP jest tworzony i
ponownie walidowany w OCCT, STL jest zapisywany binarnie z bieżącej tessellacji,
a DXF zawiera encje szkicu w milimetrach. Import STEP trafia najpierw do pliku
tymczasowego, przechodzi walidację B-Rep i dopiero potem atomowo zastępuje model.

Zaokrąglenie jest parametryczną cechą dokumentu (`filletRadius`) wykonywaną przez
`BRepFilletAPI_MakeFillet` na wszystkich krawędziach bryły utworzonej lokalnie.
Wynik przechodzi analizę `BRepCheck_Analyzer` przed pokazaniem i zapisem. Ta sama
rekonstrukcja cech jest używana przez tessellację, odtworzenie dokumentu i eksport
STEP, dzięki czemu plik wymiany odpowiada geometrii widocznej w obszarze roboczym.

Interfejs operacji jest rozdzielony na górny pasek ikon narzędzi oraz dolną oś
cech dokumentu. Oś nie przełącza trybów — pokazuje trwałą kolejność Początek →
Szkic → Wyciągnięcie/Import → Zaokrąglenie. Szkic nie jest ukrywany po zmianie
narzędzia. Wskazanie encji w trybie Edytuj otwiera edycję jej lokalnych
współrzędnych; zapis tworzy migawkę undo, przebudowuje OCCT i dopiero po poprawnej
walidacji utrwala dokument.
