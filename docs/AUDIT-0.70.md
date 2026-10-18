# TabletCAD 0.70 — masa modelu i gęstość materiału

Wersja 0.70.0-body-mass, versionCode 79; schemat .tcad 35.
Baza lokalna: 7e3c5e9 (0.69). Etap wykonany bez używania tabletu na polecenie użytkownika.

## Zakres

- Sprawdź → Właściwości bryły pokazuje masę w kg po przypisaniu gęstości.
  Gęstość materiału podaje się w kg/m³; przecinek i kropka są obsługiwane.
  Puste pole usuwa przypisanie. Brak wartości nie jest pokazywany jako zerowa masa.
- Objętość pochodzi z istniejącego zapytania BRepGProp::VolumeProperties,
  nie z triangulacji ekranu. Przeliczenie: kg = mm³ × kg/m³ / 1 000 000 000.
  Ten etap nie zmienia JNI, geometrii ani sposobu renderowania.
- Jedna gęstość obejmuje cały model; środek objętości jest wtedy środkiem masy.
  Wyświetlane właściwości są migawką z chwili otwarcia panelu.
- Zapis odbywa się na osobnym kandydacie dokumentu. Bieżący dokument i stosy
  historii są zmieniane po poprawnym zapisie. Zmiana gęstości nie wywołuje
  przebudowy geometrii; późniejsze otwarcie panelu ponawia dotychczasowe zapytanie OCCT.
- Aktywna edycja szkicu i tryb tylko do odczytu blokują zmianę gęstości.
  Walidacja formularza pozostawia błędny tekst do poprawienia.
- Schemat 35 zachowuje UUID i odczyt starszych dokumentów; starsze schematy
  bez pola gęstości otrzymują stan „nie przypisano”. Zapis w nowym formacie
  wymaga 0.70 lub nowszej aplikacji do ponownego otwarcia.
- Walidacja odczytu/zapisu odrzuca ujemne, nieskończone, nieliczbowe wartości.
  Kopie projektu i przywracanie zachowują pole gęstości.

## Weryfikacja

- tools/build-foundation-apk.ps1: PASS, pakiet 79 / 0.70.0-body-mass,
  minSdk 34, targetSdk 36, ARM64. DEX, libtabletcad.so i licencja OCCT obecne;
  podpis APK zweryfikowany przez apksigner.
- tools/test-library-files-host.ps1: 48 kontroli biblioteki PASS oraz
  BodyMassHostTest: 49 kontroli PASS, na rzeczywistym kodeku CadProject i
  org.json. Logi: build/host-tests/library-results.txt i body-mass-results.txt.
- Kontrole masy: jednostki m³/cm³/mm³, małe masy, suma brył o jednakowej
  gęstości, przeliczenie znanej objętości po wycięciu, błędne dane i zakres
  double, polski przecinek, migracja schematów 1/29/34, zachowanie UUID,
  odrzucanie błędnego pola dokumentu, odtworzenie wartości przez SnapshotHistory,
  zachowanie stosów przy błędzie, kopiowanie i odzyskiwanie z kosza.
- APK narzędzi i prób Androida skompilowany i podpisany. Nie uruchamiano go.

## Ograniczenia

Testy JVM sprawdzają arytmetykę na zadanych objętościach, kod dokumentu,
historię przez callback odtwarzania oraz rzeczywiste operacje plikowe biblioteki.
Nie wykonują JNI ani zapisu AtomicSavePair z android.system.Os. Nie potwierdzają
dotykowego formularza, klawiatury, ponownego uruchomienia Activity ani undo/redo
z przebudową OCCT w działającej aplikacji. Nowy UI nie był uruchamiany na urządzeniu.

Brak katalogu materiałów, różnych gęstości dla poszczególnych brył i tensora
bezwładności. Tekst „Materiał” w tabliczce rysunkowej nie ustawia automatycznie
gęstości. STL nadal nie ma dokładnej analizy masy B-Rep. Zapytanie właściwości
OCCT jest nadal synchroniczne; nie jest wykonywane w pętli obrotu ani startu.
Model z nakładającymi się bryłami nie jest automatycznie scalany na potrzeby masy.
Pozostałe luki z STATUS_WYMAGAN_CAD.md pozostają otwarte.
