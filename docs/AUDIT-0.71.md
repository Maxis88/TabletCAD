# TabletCAD 0.71 — pakiet biblioteki, wydajności i odzyskiwania

0.71.0-library-workspace, versionCode 80. Baza lokalna: 53b1896 (0.70).
Schemat .tcad pozostaje 35; geometrii OCCT i JNI nie zmieniano.
Na polecenie użytkownika etap wykonano bez używania tabletu.

## Funkcje użytkowe

- Biblioteka przy starcie i Projekt → Moje projekty korzystają z tego samego
  natywnego widoku. Dostępne są nazwy, daty, typ modelu, miniatury,
  oznaczenie otwartego dokumentu, usuwanie do kosza, nowy projekt i kosz.
- Wyszukiwanie obejmuje nazwę, numer rysunku, autora i materiał. Wszystkie
  słowa zapytania muszą wystąpić; wielkość liter i polskie znaki są pomijane.
  „sruba lozka” odnajduje „Śruba łóżka”. Limit zapytania: 160 znaków.
- Sortowanie: ostatnio zmienione, najstarsze zmiany, nazwa A–Z, nazwa Z–A.
  Nazwy są porównywane po normalizacji znaków, bez pełnej kolacji językowej.
  Przy jednakowym kluczu kolejność ustala UUID.
- Filtry: wszystkie, bryły natywne, import STEP, siatki STL, szkice bez bryły,
  puste projekty. Filtr łączy się z wyszukiwaniem; licznik pokazuje wynik/ogółem.
- Odśwież oraz jawny komunikat błędu pozwalają ponowić odczyt biblioteki i kosza.
  Projekty czytane z .bak są oznaczone na liście i można je normalnie otworzyć.

## Wydajność i cykl życia

- Odczyt listy, walidacja dokumentów, odczyt dat oraz przygotowanie opisów
  kosza odbywają się poza wątkiem UI. Otwarcie z menu zapisuje najpierw
  migawkę bieżącego dokumentu w tym samym zadaniu w tle; nawigacja jest
  zablokowana do zakończenia zadania, aby stary zapis nie wyprzedził nowego projektu.
- Klasa LatestTaskRunner szereguje odczyty i sprawdza generację przed pracą
  oraz przed dostarczeniem odpowiedzi. Zamknięte widoki nie dostają wyników;
  starsze, oczekujące odczyty są pomijane. Trwający odczyt może się zakończyć.
- Wiersze listy nie odczytują plików ani nie dekodują PNG. Dekoder ma jeden
  wątek, kolejkę 24 zadań i cache 24 obrazów. Pliki powyżej 4 MiB albo
  wymiarów 8192 × 8192 są pomijane; obrazy są próbkowane do maks. 320 × 240.
  Zamknięcie biblioteki kończy obsługę kolejki i zwalnia cache.
- Usunięto zbiorcze odtwarzanie geometrii starszych projektów wyłącznie dla
  brakujących miniatur. Brakujący obraz pokazuje ikonę zastępczą. Miniatura
  powstaje nadal po otwarciu/przebudowie projektu przez dotychczasowy mechanizm.
- Katalog jest przetwarzany po jednym serializowanym dokumencie; nie jest
  utrzymywana druga kopia bajtów wszystkich dokumentów podczas budowania listy.
  Wyszukiwanie korzysta z przygotowanych tekstów w pamięci, bez ponownego odczytu plików.

## Odzyskiwanie i kosz

- Skan obejmuje zarówno document-ID.tcad, jak i document-ID.tcad.bak;
  wpisy deduplikowane po ID. Najpierw poprawny główny zapis, następnie zgodna
  tożsamościowo kopia. Data odpowiada faktycznie użytemu plikowi.
- Pliki nieczytelne, za duże i z błędnym UUID pozostają zachowane, bez wpisu
  w bibliotece. Błąd katalogu jest zgłaszany zamiast pokazywania pustej biblioteki
  w nowym widoku. Starsze wewnętrzne API listProjects zachowuje dotychczasowy fallback.
- Archiwizacja działa w tle i używa tej samej blokady procesu/pliku co zapis,
  przywracanie, kopiowanie i skan katalogu. Otwarty dokument jest chroniony.
- Można przenieść do kosza także projekt zawierający wyłącznie .bak.
  Backup i miniatura są przenoszone przed głównym dokumentem; istniejące
  archiwa nie są nadpisywane. Zwykły błąd powoduje próbę cofnięcia przeniesień.
- Rollback nie nadpisuje nowego pliku, który pojawił się w pierwotnej ścieżce:
  w takim przypadku starsza kopia pozostaje w archiwum, a operacja zgłasza błąd.
  Źródła STEP/STL nie są usuwane przez archiwizację.

## Weryfikacja

- APK aplikacji zbudowany, podpis zweryfikowany; aapt2 potwierdza wersję 80,
  minSdk 34, targetSdk 36 i ARM64. DEX, biblioteka JNI oraz licencja OCCT obecne.
- tools/test-library-files-host.ps1: 149 kontroli PASS:
  48 biblioteki plikowej, 49 masy/dokumentu oraz 52 LibraryWorkspaceHostTest.
- Nowe kontrole: skan backup-only/uszkodzeń/obcego UUID/limitu 16 MiB,
  odczyt repozytorium, brak modyfikacji źródeł/autosave podczas skanu,
  wyszukiwanie, sorty, wszystkie filtry i katalog 5000 wpisów,
  archiwizacja z rollback oraz kolizją rollback, zgodność z przywracaniem,
  blokowanie skanu podczas archiwizacji i generacje zadań/obsługa błędów/ponowienie.
- Logi: build/host-tests/library-results.txt, body-mass-results.txt,
  library-workspace-results.txt. APK prób Androida skompilowany i podpisany,
  bez instalacji i uruchomienia.

## Otwarte ograniczenia

Brak runtime weryfikacji nowego UI, klawiatury, dotyku, dekodowania PNG i cyklu
Activity na Androidzie. Testy JVM nie wykonują JNI ani zapisu korzystającego
z android.system.Os. Nie wykonano testu śmierci procesu/utraty zasilania podczas
archiwizacji; rollback po wyjątku nie jest dziennikiem odpornym na awarię zasilania.
Nie deklaruje się konkretnego czasu startu ani FPS na podstawie tego pakietu.

Pierwszy odczyt autosave w konstruktorze oraz właściwa aktywacja i przebudowa
wybranego modelu nadal są synchroniczne. Listy trzymają odczytane modele w pamięci,
więc bardzo duża biblioteka nadal może wymagać indeksu metadanych i stronicowania.
Brakująca miniatura pojawia się dopiero po otwarciu/przebudowie projektu.
Wyszukiwanie i sortowanie działają w pamięci UI. Pozostała mapa CAD pozostaje otwarta.
