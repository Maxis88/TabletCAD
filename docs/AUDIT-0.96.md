# TabletCAD 0.96 — trwałe dosunięcia wystąpień, rzetelność testów

## Zmiany

- **Dosunięcie wystąpienia jest trwałe** (luka z pkt 3 `STATUS_WYMAGAN_CAD.md`): dla wystąpienia
  złożenia z jedną bryłą *Dosuń ściany* zapisuje rekord −25 (jak dla zwykłych brył), a nie
  jednorazowe położenie. Ściany wystąpienia są tworzone przez rekord wystąpienia, więc są
  odnajdywane po każdej przebudowie: zmiana źródła (np. szersza płyta) zmienia wystąpienie i
  zachowuje dosunięcie. Wystąpienia wielobryłowe — jak dotąd przesunięcie całego wystąpienia.
- **Usuwanie wystąpienia** usuwa najpierw jego dosunięcia (przesuwające je i trzymające inne
  bryły przy nim, `Assembly.removeMates`), zamiast odmowy; komunikat podaje ich liczbę.
- **Testy**: `host-kernel-tests.sh` zgłasza błąd, gdy sonda się nie kompiluje — wcześniej
  uruchamiana była stara skompilowana wersja, co mogło ukryć regresję.

## Testy

- Kernel host: `AssemblyKernelProbe` (30: wystąpienie dosunięte do płyty, po poszerzeniu płyty
  wystąpienie szersze i nadal przylega, zapis, odmowa usunięcia przy istniejącym dosunięciu,
  usunięcie dosunięcia i wystąpienia), pełny zestaw.
