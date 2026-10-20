# TabletCAD 1.0.0-rc2 — audyt zmian

## Operacje podążają za zmienioną wcześniejszą operacją (EdgeRepair)

Po edycji wcześniejszej operacji (np. przesunięcie wyciągnięcia) niezmienione późniejsze operacje są
przypinane ponownie do „tej samej” geometrii:

| Operacja zależna | Jak jest odnajdywana | Test |
|---|---|---|
| Zaokrąglenie / fazowanie (pojedyncze i grupy) | krawędź między ścianami o tych samych operacjach-twórcach i normalnych | EdgeRepairKernelProbe |
| Gwint | walec tej samej operacji, ten sam promień, oś równoległa (najbliższy) | EdgeRepairKernelProbe |
| Otwór bez wymiarów od krawędzi | ta sama ściana (twórca + normalna), odsunięcie od najbliższego narożnika | EdgeRepairKernelProbe |
| Otwór zwymiarowany od krawędzi | HoleAssociation (bez zmian) | HoleAssociation probes |
| **Nowe:** profil narysowany na ścianie | ściana płaska, na której leżała płaszczyzna szkicu; początek układu szkicu przesuwany o ruch ściany, razem z zapisanym szkicem źródłowym | EdgeRepairKernelProbe (kieszeń Ø4 na przesuniętym występie, ta sama objętość) |

Ograniczenia: nic nie jest zgadywane — gdy ściana/krawędź nie istnieje lub jest niejednoznaczna,
operacja zostaje bez zmian i jądro zgłasza błąd przebudowy jak dotąd. Obrót ściany nie jest śledzony
(tylko przesunięcie i zmiana wymiarów).

## Interfejs

- **Pasek opcji narzędzia** (44 dp) pod górnym menu, na całą szerokość ekranu — lewe drzewo i prawy
  panel zaczynają się pod nim, więc nie mogą go zasłonić. Zawiera: wybór płaszczyzny szkicu,
  polecenia rysowanej polilinii, zaznaczenie wielu elementów, operacje zaznaczonego elementu,
  rzutowanie, przełącznik rysika, opcje pomiaru i arkusza, szybkie akcje zaznaczonej ściany
  (dotychczas pływający pasek w widoku).
- **Prawy panel** pojawia się wyłącznie dla pełnych formularzy (np. wyciągnięcie, otwór, szyk).
  W szkicu, pomiarze i arkuszu obszar roboczy jest teraz pełnej szerokości.
- **Małe okienka przy operacji** (248 dp, `PropertyBuilder.setCompact`) dla edycji z 1–3 polami:
  wartości wymiarów szkicu, odległości, kąty, półosie elipsy, zaokrąglenie i fazowanie krawędzi,
  odsunięcie ściany, pochylenie, powłoka, gwint, menu kontekstowe. Okienko staje obok dotkniętego
  miejsca (`CompactPlacement`), nigdy na nim, pod drzewem, pod paskiem opcji ani pod historią;
  klawiatura je przesuwa.
- Przy strzałce wyciągnięcia: przełączniki **Odwróć** i **Symetrycznie**.

## Testy

- Host: 37 zestawów PASS (w tym nowy CompactPlacementHostTest, EdgeRepairKernelProbe 23 sprawdzenia).
- Tablet: NIEPRZEPROWADZONE (zgodnie z ustaleniem — testy po zakończeniu prac). Do sprawdzenia:
  pasek opcji w każdym trybie, położenie małych okienek przy klawiaturze, przesunięcie występu
  z kieszenią/otworem/gwintem/fazą narysowaną na jego ścianie.
