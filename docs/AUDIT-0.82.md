# TabletCAD 0.82: audyt obsługi dotykowej

Paczka powstała po zgłoszeniu z testów 0.81. Oprócz zgłoszonych błędów zawiera poprawki znalezione
podczas przejścia przez aplikację na tablecie (HONOR YLE-W09): szkic, wymiar, wyciągnięcie, powielanie,
drzewo i konstrukcje.

## Zgłoszone problemy

| Problem | Przyczyna | Poprawka |
|---|---|---|
| Powielanie nie pokazuje opcji | Bryła bazowa nie jest operacją w historii, więc lista „operacji do powielenia” była pusta. Narzędzie kończyło się tylko komunikatem. | Gdy w historii nie ma operacji, otwiera się szyk lub lustro całej bryły. W panelu wyboru operacji jest przycisk „Powiel całą bryłę…”. Domyślny odstęp to szerokość bryły + 10 mm, a domyślne lustro przechodzi przez prawą ścianę bryły. |
| Brak menu po przytrzymaniu elementu w drzewie | Drzewo obsługiwało tylko dotknięcia. | Przytrzymanie otwiera menu kontekstowe. Zestaw opcji zależy od elementu (zob. tabela niżej). |
| Więzy nie łączą dwóch brył | „Dosuń ściany” działało wyłącznie dla wystąpień złożenia. | „Dosuń” działa dla każdej bryły (patrz niżej). |
| Pełna klawiatura przy liczbach | Pola liczbowe miały typ tekstowy, żeby przyjmować wyrażenia. | Wszystkie pola liczbowe otwierają klawiaturę numeryczną. Szczegóły niżej. |

### Menu kontekstowe w drzewie

| Element | Dostępne opcje |
|---|---|
| Płaszczyzna, oś, punkt | edytuj, nowy szkic na płaszczyźnie, zmień nazwę, ukryj/pokaż, usuń z potwierdzeniem |
| Bryła | zmień nazwę (nowość), ukryj/pokaż, pokaż tylko tę bryłę, właściwości i masa, materiał, utwórz komponent, usuń bryłę razem z tworzącą ją operacją |
| Szkic | edytuj, zmień nazwę, ukryj/pokaż, usuń nieużywany szkic |
| Komponent | dotychczasowe akcje komponentu |

### Dosuwanie brył („Dosuń”)

- Działa dla każdej bryły, a nie tylko dla wystąpień złożenia.
- Płaska ściana do płaskiej ściany:
  - styk (normalne przeciwne) albo wyrównanie (jedna płaszczyzna),
  - odstęp ustawiany suwakiem.
- Walcowa ściana do walcowej ściany: osie współosiowo (np. sworzeń w otworze).
- Wynik jest zapisywany w historii jako obrót i przesunięcie bryły, więc można go edytować i cofnąć.
- Podgląd położenia działa na żywo.

### Klawiatura numeryczna

- Klawiatura numeryczna nadal przyjmuje proste wyrażenia: `+ - * / ( )`, przecinek i kropkę.
- Przytrzymanie pola przełącza na pełną klawiaturę: nazwy parametrów i jednostki.
- Pole powiązane z parametrem od razu otwiera pełną klawiaturę.
- ✓ / Enter na klawiaturze zatwierdza panel tak jak jego główny przycisk.

## Znalezione podczas audytu

- **Wymiar szkicu.** Jedno dotknięcie wartości otwiera edycję (wcześniej potrzebne było podwójne).
  Zmiana wartości od razu pokazuje wynik solvera na szkicu.
- **Wyciągnięcie:**
  - jedyny zamknięty profil zaznacza się automatycznie,
  - widok przechyla się, żeby strzałkę długości dało się przeciągnąć,
  - dodany suwak długości.
- **Podgląd na żywo** sam oddala widok, gdy wynik (np. szyk) wychodzi poza ekran.
- **Komunikaty:** pusty dokument podpowiada, od czego zacząć, zamiast „wymaga bryły B-Rep”. Narzędzie, które
  nie może wystartować, daje sygnał haptyczny.
- **Wystąpienie w historii** otwiera edycję położenia komponentu.

## Testy

- Testy w chmurze: 24 zestawy PASS, w tym nowy `MateKernelProbe`. Sprawdza on:
  - styk ścian i przemieszczenie po dosunięciu,
  - obrót o 90° z poprawnym kierunkiem (brak przenikania),
  - odstęp 4 mm i wyrównanie ścian,
  - współosiowość sworznia z otworem.
- Tablet (Android 16): instrumentacja PASS, 1620 sprawdzeń, w tym scenariusz 16.3.
- Ręcznie sprawdzone na tablecie:
  - menu Powiel, w tym szyk całej bryły z podglądem i suwakiem,
  - menu kontekstowe bryły w drzewie,
  - klawiatura numeryczna i podgląd wymiaru,
  - automatyczny wybór profilu,
  - płaszczyzna odsunięta od dotkniętej ściany.
