# TabletCAD 0.54 — eksport OBJ

Wersja deweloperska 0.54.0-obj-export, versionCode 61.

Eksportuj → OBJ zapisuje triangulowaną siatkę przez Android Storage Access
Framework, z typem model/obj i rozszerzeniem .obj. Współrzędne zachowują
układ i milimetry dokumentu; OBJ nie ma standardowej deklaracji jednostek,
więc zapisano komentarz i informację w menu. W programie docelowym należy
wybrać mm. Eksport nie zastępuje precyzyjnego STEP ani dokumentu parametrycznego.

Writer scala identyczne wierzchołki, zachowuje orientację trójkątów, oblicza
jednostkowe normalne z geometrii i zapisuje niezależne indeksy v//vn od 1.
Grupy odpowiadają identyfikatorom ścian siatki; nie są komponentami zespołu.
Walidacja całej siatki poprzedza pierwszy bajt wyjścia. Zerowe trójkąty,
puste bufory, NaN i błędny format są odrzucane. Strumień pozostaje własnością
wywołującego. Aktywna robocza edycja szkicu wymaga zakończenia lub anulowania.

Weryfikacja na fizycznym HONOR:
- APK zbudowany, podpis zweryfikowany, aktualizacja adb install -r: Success.
- ObjExportProbe: PASS; parser kontroluje indeksy, współrzędne, orientację,
  normalne, współdzielenie wierzchołków i analityczną objętość pudełka oraz
  pudełka z wycięciem. Sprawdza polskie locale i odrzucanie błędnych danych.
- Cztery istniejące zestawy instrumentacji interfejsu: PASS.
- Rzeczywiste menu OBJ otwiera systemowy dialog z nazwą .obj. Zapisano osobny
  TabletCAD054-verified.obj w Pobranych, bez zmiany modelu użytkownika.
- Plik 39633 bajtów pobrano i niezależnie odczytano skryptem Python,
  sprawdzając wszystkie indeksy i skończone współrzędne. Wyniki i zrzuty:
  build/regression054/. Logcat aplikacji: zero dopasowań awarii.

Nie sprawdzono jeszcze importu w zewnętrznym edytorze 3D. Dokładność siatki
pozostaje taka jak obecnej tessellacji. Eksport działa synchronicznie i zużywa
pamięć na indeks unikalnych wierzchołków. Import OBJ, materiały/tekstury,
ustawienia dokładności eksportu i cały pozostały zakres z AUDIT-0.52.md
pozostają otwarte. Ikona użytkownika z 0.53 jest zachowana.

Odniesienie formatu: Wavefront OBJ specification, oraz opis zachowania indeksów:
https://www.loc.gov/preservation/digital/formats/fdd/fdd000507.shtml
