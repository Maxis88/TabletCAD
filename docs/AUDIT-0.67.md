# TabletCAD 0.67 — biblioteka projektów z miniaturami i usuwaniem

0.67.0-project-library, versionCode 76, format dokumentu bez zmian.

## Zmiany

- Lista projektów przy starcie: karta z miniaturą bryły (120 × 82 dp),
  nazwą, datą ostatniej zmiany, opisem modelu (liczba operacji, B-Rep,
  import STEP/STL, szkic bez bryły) i oznaczeniem otwartego projektu;
  tytuł zawiera numer wersji aplikacji.
- `ThumbnailRenderer`: izometryczny, cieniowany podgląd siatki B-Rep
  (algorytm malarza, odrzucanie ścian tylnych) zapisywany jako PNG
  w `projects/thumbnails/<id>.png` (zapis atomowy: plik tymczasowy + rename).
  Podgląd odświeża się w tle 1,2 s po każdej zmianie geometrii; dla projektów
  bez miniatury generuje się w tle przy otwarciu listy.
- Usuwanie: „Usuń” przy projekcie, okno potwierdzenia; dokument, kopia .bak
  i miniatura trafiają do `projects/trash` z sygnaturą czasu (do odzyskania).
  Otwartego projektu nie można usunąć.

## Weryfikacja

HONOR YLE-W09: `LibraryThumbnailProbe` (podgląd rysuje cieniowaną bryłę,
zapis miniatury, blokada usunięcia otwartego projektu, przeniesienie do kosza,
zniknięcie z listy), cała `WorkspaceInstrumentation` `result=PASS`. Zrzut
ekranu: wszystkie projekty z tabletu mają miniatury wygenerowane automatycznie.

## Granice

- Projekty z importem STEP nie dostają miniatury automatycznie (źródłowy STEP
  jest wspólny dla otwartego dokumentu); miniatura powstaje po ich otwarciu.
- Brak interfejsu przywracania z kosza (pliki pozostają w projects/trash).
