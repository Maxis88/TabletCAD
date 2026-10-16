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

Wersja `0.8.0-occt-mesh` zawiera interaktywny interfejs, selekcję topologii,
kamerę orbitującą, lokalny układ płaszczyzny szkicu, JNI oraz Open CASCADE 8.0.1.
Rdzeń tworzy prawdziwą bryłę B-Rep, waliduje jej topologię i raportuje liczbę
ścian, krawędzi oraz wierzchołków. Natywny autotest wykonuje zapis bryły do STEP,
ponowny import i kontrolę poprawności topologii. Udostępnienie wyboru pliku STEP
w UI, edytor parametryczny oraz pozostałe formaty są kolejnymi funkcjami. Dokument
projektu ma wersjonowany manifest JSON, UUID encji, atomowy autosave i kopię
odzyskiwania. Obecnie przechowuje jedną płaszczyznę szkicu, encje linii,
prostokąta i okręgu oraz parametry bazowego wyciągnięcia.

Warstwa JNI tesselluje `TopoDS_Shape`, przekazuje wierzchołki, normalne i indeksy
ścian do Androida. Widok wykonuje projekcję, odrzucanie tylnych ścian, sortowanie
głębokości i selekcję trójkątów. Ręcznie zdefiniowana geometria pudełka nie jest
już źródłem renderowania ani wyboru ściany.
