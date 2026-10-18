# TabletCAD 0.72 — szkicowanie, edycja brył i zależności profili

0.72.0-sketch-solid-links, versionCode 81. Baza: 12b18ff (0.71).
Schemat .tcad pozostaje 35. Pakiet dotyczy projektowania; nie rozszerza biblioteki,
eksportu ani analiz materiałowych. Na polecenie użytkownika praca bez tabletu.

## Rysowanie

- Rysuj → Okrąg 3 pkt: trzy punkty obwodu wyznaczają analityczny okrąg.
- Rysuj → Szczelina: dwa środki końców i punkt określający połowę szerokości.
  Zamknięty kontur zawiera dwie linie i dwa półokręgi; nie jest aproksymowany
  wielokątem. Korzysta z istniejącej obsługi konturów analitycznych OCCT.
- Punkty konstrukcji są zapisywane w istniejących polach dokumentu. Można
  kontynuować, cofnąć punkt albo anulować konstrukcję. Błędny trzeci punkt
  pozostawia dwa poprawne punkty. Zakończenie szkicu i zmiana narzędzia
  wymagają rozstrzygnięcia niedokończonej konstrukcji.
- Wznowienie dokumentu/historii przywraca właściwe narzędzie i punkty.
  Zapis kandydata musi się powieść przed podmianą dokumentu/stosów historii.
  Wskazywanie punktów nie przebudowuje niezmienionej bryły przy każdym dotknięciu.
- Nowe narzędzia mają podgląd trzeciego punktu i obsługują geometrię pomocniczą.
  Dotychczasowa ścieżka rysowania łuku pozostaje oddzielna.

## Przekształcanie szkicu

- Geometria → Przesuń szkic / Obróć szkic / Skaluj szkic zmienia zaznaczony
  element, z numerycznym wektorem albo środkiem/kątem/współczynnikiem.
  Nie jest to wielokrotne zaznaczenie ani transformacja całego szkicu naraz.
- Zachowane są UUID i status konstrukcyjny, analityczne okręgi/łuki i splajny.
  Prostokąt po obrocie o dowolny kąt staje się równoważnym zamkniętym konturem.
- Przesunięcie zachowuje wymiary sterujące; jawna skala zmienia ich wartości,
  jawny obrót aktualizuje kąt linii. Obrót zwymiarowanego prostokąta do konturu
  wymaga uprzedniego odblokowania wymiarów, aby ich nie zgubić. Obrót o 90°
  zamienia szerokość i wysokość. Nie są usuwane więzy geometryczne.
- Zmiana przechodzi przez istniejący solver edycji szkicu. Niezgodny punkt
  stały albo inne sprzeczne więzy blokują operację. Zakres geometrii ±5000 mm,
  skala 0,001–1000, kontrola zdegenerowanych boków/promieni i samoprzecięć.

## Zależności szkic → operacje → bryły

- ProfileDependencies zastępuje rozproszone przepisywanie tablic profilu.
  Prawidłowo ustawia typ i długość danych przy przejściu prostokąt ↔ kontur
  albo profil analityczny, usuwając nieaktualny ogon danych.
- Uwzględnia bazę zwykłą i zagnieżdżoną bazę dwukierunkową (-10), kolejne
  wyciągnięcia/wycięcia i obroty. Głębokości, kierunki, początki płaszczyzn,
  osie obrotu i przypisania brył nie są zmieniane przez samą zmianę profilu.
- Naprawiona wcześniejsza bezpośrednia edycja szkicu, która w bazie -10
  przepisywała współrzędne do pól długości, a w zwykłych cechach nie zmieniała
  typu profilu przy przejściu na kontur lub nie usuwała starego tailu.
- Zakończenie sesji aktualizuje wszystkich odbiorców profilu w kandydacie,
  z zachowaniem istniejącej transakcyjnej walidacji modelu i zapisu. Utracony
  profil wymaga jawnego przypisania zamiennika; brak zgadywania po odległości.
- Odtwarzanie zapisanego szkicu sprawdza zgodność profilu we wszystkich
  korzystających z niego operacjach. Sprzeczne kopie zgłaszają błąd; nie
  wybiera się już bez ostrzeżenia ostatniej cechy w historii.
- „Powiązania profilu” pokazuje bezpośrednie operacje używające zaznaczonego
  UUID. „Edytuj profil źródłowy” przechodzi z wybranej operacji do odpowiedniego
  szkicu. Przy wspólnym/zwymiarowanym profilu edytor cechy zmienia głębokość,
  a edycja kształtu prowadzi przez źródło, zamiast rozjeżdżać jedną kopię profilu.
  Wyszukiwanie źródła odrzuca kolizję UUID między różnymi szkicami.

## Weryfikacja

- tools/build-foundation-apk.ps1: aplikacja zbudowana, versionCode 81,
  minSdk 34, targetSdk 36, ARM64, podpis zweryfikowany. Kod C++ bez zmian.
- tools/test-sketch-design-host.ps1: SketchDesignHostTest, 130 kontroli PASS
  na rzeczywistym kodzie domenowym i org.json. Log: build/host-tests/sketch-design-results.txt.
- Kontrole obejmują przekształcenia i blokady, analityczne typy, konstrukcje
  trzypunktowe i zapis/wznowienie, wspólne profile wielu cech, zagnieżdżoną
  bazę dwukierunkową, osie obrotu, zmianę typu i długości tablic, kandydat
  kończenia szkicu, jawny zamiennik UUID, brakujące i sprzeczne profile.
- tools/test-library-files-host.ps1: wcześniejsze 149 kontroli PASS:
  48 biblioteki plikowej, 49 masy/dokumentu, 52 biblioteki i zadań. Razem 279.
- tools/build-ui-tests.ps1: APK testów Androida skompilowany i podpisany,
  w tym SketchDesignKernelProbe. Nowa próba sprawdza analityczne objętości
  szczeliny/okręgu, transformacji, wycięcia i odwróconej bazy dwukierunkowej.
  Próba nie została uruchomiona; jej kompilacja nie jest wynikiem PASS geometrii.

## Do potwierdzenia i dalsza praca

Nowe gesty, formularze, wznowienie Activity, zapis Androida i rzeczywiste wyniki
B-Rep nowych ścieżek pozostają niezweryfikowane runtime. Testy JVM sprawdzają
kod szkicu, solvera i opisu operacji, nie wykonują OCCT/JNI ani Android Canvas.
Nie deklaruje się zaliczenia sekwencji projektowej na urządzeniu.

Nie dodano ogólnego nazewnictwa ścian/krawędzi ani trwałych odwołań szkicu
do przebudowywanej ściany. Nie dodano parametrycznych zależności szyku/lustra,
pełnego solvera, Loft/Sweep/Shell ani nowego narzędzia Hole. Przekształcenie
elementu sprawdza istniejące więzy; nie przekształca całego układu więzów jako grupy.
Szkic z historycznie sprzecznymi kopiami profilu jest chroniony błędem i może
wymagać jawnej naprawy danych/cechy. Ograniczenia mapy CAD pozostają otwarte.
