# TabletCAD 0.80 — podstawowe złożenia: komponenty, wystąpienia, dosuwanie, kolizje, lista części

0.80.0-assembly, versionCode 90. Schemat .tcad 37 (nowe opcjonalne pole `components`,
nowy rekord historii -23). Etap bez tabletu; jądro sprawdzone na komputerze.

- Bryła → Złożenie → Nowy komponent: nazwana grupa brył. Drzewo modelu pokazuje
  komponenty (zwijane, ✎ zarządzanie, ● ukrycie wszystkich brył komponentu) z ich
  bryłami i szkicami pod spodem.
- Wstaw wystąpienie: powiązana kopia komponentu (albo pojedynczej bryły) jako nowe
  bryły (rekord -23 w historii, liczony od bieżącej bryły źródłowej — zmiany źródła są
  przenoszone). Położenie: przesunięcie + obrót wokół osi X/Y/Z przez punkt, z podglądem.
  Zmiana położenia istniejącego wystąpienia składa się z bieżącym (✎ → Zmień położenie).
- Dosuń ściany: płaska ściana wystąpienia na płaską ścianę innej bryły (normalne
  przeciwne, płaszczyzny wspólne, opcjonalny odstęp); obrót, gdy ściany nie są równoległe.
  Położenie zapisywane w rekordzie wystąpienia (nie jest więzem rozwiązywanym przy
  każdej zmianie — po zmianie geometrii można dosunąć ponownie).
- Kolizje: wszystkie pary widocznych brył (limit 24) — kolizje z objętością części
  wspólnej i styki (BRepExtrema + część wspólna B-Rep).
- Lista części: pozycje z liczbą sztuk (wystąpienia liczone jako kolejne sztuki źródła),
  masa całkowita; eksport CSV (UTF-8, średniki).
- Usunięcie wystąpienia usuwa jego rekordy; puste komponenty wystąpień są czyszczone.
  Wystąpienia można wygaszać i przestawiać w historii jak inne operacje.

## Wyniki (komputer)

22 zestawy PASS; AssemblyKernelProbe (23): wystąpienie jako osobna bryła, przeniesienie
wycięcia ze źródła, złożenie obrotu z przesunięciem, dosunięcie (styk bez części wspólnej,
odstęp 5 mm, obrót 90° przy ścianach prostopadłych z analitycznym środkiem), kolizja
nakładających się brył, lista części i CSV, zapis komponentów, odrzucenie bryły w dwóch
komponentach, czyszczenie po usunięciu wystąpienia.

## Nie zweryfikowano / do zrobienia

- Brak testów na tablecie. Brak więzów montażowych rozwiązywanych stale (przegub obrotowy,
  przesuwny) i ruchu złożenia; eksport STEP zapisuje wystąpienia jako zwykłe bryły.
