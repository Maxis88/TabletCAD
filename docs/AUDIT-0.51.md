# TabletCAD 0.51 — wiele krawędzi w jednej operacji

Wersja deweloperska 0.51.0-multi-edge-treatment, versionCode 58, schemat dokumentu 30.

## Implementacja

EdgeGroup reprezentuje jedną operację fazowania/zaokrąglenia z jednym wymiarem
i listą 1–256 osobnych odniesień krawędzi. Przechowuje dotychczasowe dokładne
sygnatury lub odniesienia do parametrów wspieranego generatora bazowego.
Nie używa bieżącego indeksu topologii jako trwałej tożsamości.

OCCT rozwiązuje wszystkie odniesienia przed rozpoczęciem operacji, następnie
przekazuje je do jednego BRepFilletAPI_MakeFillet/MakeChamfer i sprawdza cały
wynik BRepCheck_Analyzer. Nieznana/niejednoznaczna krawędź lub niedopuszczalny
wymiar odrzuca całą grupę, bez częściowego zatwierdzania i bez zgadywania.

Interfejs w aktywnym narzędziu przełącza wybrane krawędzie kolejnymi dotknięciami,
podświetla je i pokazuje licznik. Podgląd używa kolejki z wersji 0.50. Zatwierdzenie
dodaje jedną cechę i jedną migawkę undo, a edycja z historii zmienia wspólny wymiar.
Pojedyncze dawne operacje zachowują format i działanie. Czytnik obsługuje schematy
1–30; starsza aplikacja prawidłowo odrzuci nowy schemat zamiast utracić grupę.

## Weryfikacja na tablecie HONOR

- 33 zestawy app_process: PASS. EdgeGroupProbe porównuje dokładne objętości
  czterech równoległych faz/zaokrągleń z wzorami, sprawdza wszystkie 12 krawędzi
  pudełka wraz z narożnikami, zmianę wymiarów generatora, wspólny wymiar, późniejsze
  wycięcie, STEP i serializację jako jedną cechę. Sprawdza odrzucenie uszkodzonych
  zapisów, duplikatów, utraconego członka i nadmiernego promienia.
- Trzy zestawy instrumentowane: PASS. EdgeGroupUiProbe wybiera dwie widoczne
  krawędzie zdarzeniami dotykowymi, odznacza powtórnym dotknięciem, sprawdza podgląd,
  dokładny B-Rep po zapisie, jedną cechę, wspólne undo/redo i edycję wymiaru z historii.
- Siatka dwóch zaokrągleń różni się od analitycznej objętości o około 1,43 mm³
  przy objętości około 23922,74 mm³. Test podglądu dopuszcza 2 mm³, a niezależna
  kontrola dokładnej objętości B-Rep pozostaje na tolerancji 1e-5 mm³.
- APK zbudowany, podpisany tym samym kluczem i zainstalowany przez adb install -r.
- W normalnej aplikacji uruchomiono Fazuj, dotknięto dwóch krawędzi, potwierdzono
  licznik 2, podświetlenie i poprawny podgląd 3 mm. Wybrano Anuluj, bez zapisu modelu
  użytkownika. Sprawdzony logcat: 0 dopasowań awarii. Dowody: build/regression051/.

## Ograniczenia

To wielokrotny wybór w narzędziach obróbki krawędzi, nie kompletny uniwersalny
system zaznaczania. Brak wyboru obszarem, wszystkich granic wskazanej ściany,
pętli/stycznych łańcuchów i wielu ścian. Historyczny edytor grupy zmienia wspólny
wymiar; nie zmienia jeszcze listy członków. Manipulator wymiaru fazy/promienia
pozostaje do dodania. Zależności topologiczne nadal mają ograniczony zakres.
Pozostają ograniczenia poprzednich audytów i projekt pozostaje otwarty.
