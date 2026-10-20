# TabletCAD 0.98 — nazewnictwo krawędzi przez ściany

Pkt 2 `STATUS_WYMAGAN_CAD.md` i 8.2 promptu („uszkodzenie modelu po edycji wcześniejszego
szkicu”): zaokrąglenie lub faza zapisuje dokładną sygnaturę krawędzi (początek, środek, koniec).
Odniesienia do krawędzi bryły bazowej były już mapowane z parametrów szkicu bazowego (0.5x), ale
krawędź utworzona przez późniejszą operację (np. narożnik występu) po edycji tej operacji była
tracona — jądro zgłaszało błąd i zmiany nie dało się zatwierdzić.

## Zmiany

- **`EdgeRepair`**: przy zatwierdzaniu zmiany wcześniejszej operacji dla każdej niezmienionej
  późniejszej operacji krawędziowej sprawdzane jest, czy jej krawędź nadal istnieje w modelu
  zbudowanym do tej operacji. Jeśli nie — krawędź jest rozpoznawana po dwóch ścianach, które ją
  tworzyły w poprzednim modelu (operacja tworząca każdą ścianę i kierunek ściany), i szukana
  między ścianami o tych samych twórcach i kierunkach w nowym modelu; przy kilku kandydatach
  wybierana jest najbliższa. Zapis operacji dostaje nową sygnaturę (także dla członków grup
  krawędzi), a po zatwierdzeniu pojawia się krótka informacja.
- Gdy takiej krawędzi nie ma (np. prostokątny występ zamieniony na okrągły), nic nie jest
  zgadywane — jądro zgłasza utraconą krawędź jak dotąd.
- Odniesienia do krawędzi bryły bazowej nadal mapuje jądro (bez zmian).

## Testy

- Kernel host: `EdgeRepairKernelProbe` (R3 na pionowej krawędzi występu; po przesunięciu i
  poszerzeniu występu bez naprawy jądro zgłasza utratę, z naprawą — ta sama objętość i krawędź w
  nowym narożniku; okrągły występ — brak zgadywania; dokument bez zmian — brak napraw).
