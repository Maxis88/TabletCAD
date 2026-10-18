# TabletCAD 0.64 — przeprojektowany obszar roboczy

0.64.0-workspace-redesign, versionCode 73, format dokumentu bez zmian.

## Problemy dotychczasowego układu

- Dwa rzędy górnego paska (108 dp): tytuł i pliki w pierwszym, narzędzia
  w drugim — 44 dp wysokości zajęte przez pusty pasek tytułowy.
- Drzewo modelu zawsze 224 dp, bez możliwości zwinięcia (wymóg 9).
- Brak numeru wersji w aplikacji (nie dało się sprawdzić, co jest zainstalowane;
  numery w nazwach projektów testowych były mylone z wersją).
- Kolory ciemnych elementów rozproszone w kodzie, panel właściwości ucięty
  w połowie wysokości.

## Zmiany

- Jeden pasek 64 dp: blok projektu (TabletCAD, plakietka trybu SZKIC 2D /
  BRYŁA 3D, nazwa dokumentu ▾, numer wersji z PackageManager), grupy narzędzi
  w tym samym rzędzie, Otwórz plik / Eksportuj po prawej. Obszar roboczy
  zaczyna się od 66 dp (wcześniej 110 dp).
- Drzewo modelu zwijane chevronem „‹” do szyny 40 dp; dotknięcie szyny
  przywraca drzewo. Obszar gestów modelu, oś czasu i dopasowanie widoku
  korzystają z bieżącej szerokości panelu.
- Tokeny kolorów: CHROME, CHROME_RAISED, CHROME_TEXT, DIVIDER; separator
  paneli 1 dp; prawy panel na pełną wysokość, treść wyrównana z drzewem.
- Wersja widoczna w pasku i w tytule menu projektu.
- Testy korzystają z metod układu (`treeRowCenterY`, `leftWidth`, `viewTop`)
  zamiast stałych pikseli.

## Weryfikacja

HONOR YLE-W09: cała `WorkspaceInstrumentation` `result=PASS` (w tym scenariusz
16.3 dotykiem i TabletFlowProbe na nowym układzie); ViewCameraProbe sprawdza
pasek 66 dp, odczyt wersji, zwijanie i przywracanie drzewa. Zrzuty ekranu
z tabletu potwierdzają wyrównanie paneli.
