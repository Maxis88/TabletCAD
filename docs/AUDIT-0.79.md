# TabletCAD 0.79 — poprawki znalezionych problemów i pakiet „druk 3D”

0.79.0-print, versionCode 88. Schemat .tcad 37 (nowe opcjonalne pola: znacznik
historii, ustawienia eksportu siatki). Etap bez tabletu; jądro sprawdzone na komputerze.

## Poprawki problemów zgłoszonych w 0.75–0.78

- Znacznik historii działa jak w Fusion: operacje dodane, gdy znacznik stoi wcześniej,
  są wstawiane w jego miejscu (przy zatwierdzeniu przenoszone z przenumerowaniem
  odwołań), znacznik przesuwa się za nie. Znacznik jest zapisywany z projektem;
  „Wróć na koniec historii” przelicza cały model. Wybór narzędzia nie przesuwa już
  znacznika.
- Elementy konstrukcyjne wybiera się dotknięciem w widoku 3D (osie i punkty — blisko
  linii/punktu, płaszczyzny — brzeg albo wnętrze, gdy pod spodem nie ma ściany);
  ponowne dotknięcie otwiera edycję; w trybie Szkic dotknięcie płaszczyzny zaczyna
  szkic na niej.
- Geometria → Edytuj szyk: zaznaczony element powiązanego szyku otwiera jego
  parametry (liczba, odstęp, kąt, środek); kopie są tworzone na nowo z tym samym
  powiązaniem. Kopia będąca profilem operacji blokuje zmianę z komunikatem.
- Lustro linii i okręgów względem linii szkicu używa więzu symetrii z odwołaniem do
  tej linii — kopia podąża za przesuniętą osią (symetria okręgów: środek + równy promień).
- Edycja szyku/lustra bryły z osią/płaszczyzną konstrukcyjną pokazuje to odniesienie
  albo „Bieżące odniesienie (zapisane)” zamiast osi X.
- Zaokrąglij szkic przeniesione do grupy Przytnij (limit 12 poleceń w grupie).

## Druk 3D

- Eksport STL/OBJ/3MF z siatki eksportowej o zadanej dokładności (Eksportuj → Jakość
  siatki eksportu: zgrubna 0,1 mm/20°, standardowa 0,02 mm/10°, dokładna 0,005 mm/5°,
  bardzo dokładna 0,001 mm/2°, własna). Wcześniej eksport używał siatki ekranu
  (odchyłka 0,15 mm). Triangulacja na kopii bryły — widok się nie zmienia; ukryte bryły
  nie są eksportowane.
- 3MF: milimetry, każda bryła jako osobny obiekt z nazwą, wspólne wierzchołki.
- Sprawdź → Nawisy: ściany wymagające podpór (bardziej poziome niż zadany kąt od pionu,
  domyślnie 45°, kierunek druku +Z) na czerwono; ściany leżące na stole pominięte.
- Zmień → Połóż na stole: obrót bryły tak, by zaznaczona płaska ściana leżała na Z = 0
  (operacje Obrót bryły + Przesunięcie bryły w historii, edytowalne).

## Wyniki (komputer)

21 zestawów PASS, w tym PrintKernelProbe (21): dokładniejsza siatka ma więcej trójkątów
i objętość bliższą B-Rep (błąd < 2·10⁻⁵), siatka ekranu bez zmian, 3MF z dwoma obiektami
(nazwy XML, 8 wierzchołków i 12 trójkątów kostki, objętość 500 mm³ z orientacji),
reguła nawisu 45°/60°, położenie płyty boczną ścianą (Z od 0 do 100), zapis ustawień;
HistoryKernelProbe (32) z wstawianiem operacji przy znaczniku; LinkedCopiesHostTest (23)
z metadanymi szyku i symetrią okręgów.

## Nie zweryfikowano / do zrobienia

- Brak testów na tablecie.
- Położenie otworu od krawędzi nadal nie jest asocjacyjne; łuki bez analizy stopni swobody.
- Przy przesuwaniu osi lustra promienie okręgów symetrycznych mogą się zmienić (równe
  sobie), jeśli nie mają wymiaru sterującego.
