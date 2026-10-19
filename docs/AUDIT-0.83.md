# TabletCAD 0.83 — porządkowanie interfejsu

Ta paczka jeszcze **nie była uruchomiona na tablecie** (praca bez urządzenia na prośbę użytkownika).
W chmurze przeszły kompilacja (Java i jądro), wszystkie 24 zestawy testów oraz kompilacja testów
instrumentacyjnych. Weryfikacja na tablecie jest wymagana przed scaleniem z `main`.

## Panel parametrów (prawy)
- Nowy wspólny styl (`PanelStyle`): nagłówek z tytułem i przyciskiem ✕, główna akcja wypełniona
  kolorem akcentu, Anuluj obok niej, akcje dodatkowe jako obramowane przyciski na całą szerokość,
  akcje usuwające w kolorze ostrzegawczym.
- Pola, przełączniki, suwaki i listy w jednolitym stylu; etykiety szare i mniejsze, pola większe
  (min. 46 dp), elementy dotykowe min. 44 dp. Panel szerszy: 272 dp.
- Menu kontekstowe jako lista wierszy; długie listy krótkich poleceń (np. więzy) w dwóch kolumnach.
- Gest „wstecz” zamyka kolejno: menu narzędzi, panel (jak Anuluj), aktywne narzędzie, zaznaczenie.

## Pasek narzędzi
- Tryb bryły: Wybierz · Szkic · Utwórz · Zmień · Bryły · Powiel · Złożenie · Konstrukcja · Pomiary · Widok.
- Tryb szkicu: dawna, przepełniona grupa Geometria podzielona na Geometria i Przekształć.
- Polecenia niedostępne w bieżącym dokumencie są przygaszone (np. brak bryły, jedna bryła przy
  operacjach na dwóch bryłach, brak otworu przy gwincie); dotknięcie nadal wyjaśnia przyczynę.
- Grupa Widok nie świeci się już stale jako „aktywna” (przełączniki wyświetlania nie są narzędziem).
- „Otwórz plik” → menu **Plik**: import, eksport, nowy projekt, moje projekty, kopia, kosz.

## Widok roboczy
- **Szybkie akcje zaznaczonej ściany** na górze widoku: Szkic na ścianie, Odsuń ścianę, Otwór,
  Płaszczyzna, Edytuj operację, Właściwości; dla ściany walcowej otworu — Gwint.
- Komunikaty stanu znikają po 5 s (błędy po 9 s, trwające obliczenia do końca).
- Zaokrąglanie narożników szkicu: suwak promienia i podgląd na żywo.

## Drzewo modelu
- Wiersze z ikoną typu (bryła, szkic, komponent, konstrukcja), przycisk „⋯” (menu elementu)
  i ikona oka do ukrywania — większe pola dotyku zamiast drobnych znaków ✎ / Aa / ●.
