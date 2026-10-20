# TabletCAD 0.95 — punkt szkicu, szybsze „Zastosuj”

## Zmiany

- **Narzędzie Punkt** (Rysuj → Punkt; wymagane w 4.1 promptu): dotknięcie stawia punkt w miejscu
  przyciągniętym do geometrii. Punkt jest zawsze geometrią pomocniczą (nie tworzy profilu), ma
  jeden punkt więzów — można go łączyć z końcami linii, kłaść na linii lub okręgu, wymiarować
  jego położenie (panel Położenie otwiera się po dotknięciu punktu w trybie Edytuj), przesuwać,
  kopiować lustrzanie i w szykach. Rysowanie przyciąga do punktów. Eksport DXF zapisuje POINT.
  Stopnie swobody: 2 na punkt. Przycinanie i odsunięcie punktu są odrzucane z komunikatem.
- **Podgląd = zatwierdzenie**: podglądy operacji (wyciągnięcie, zaokrąglenie, fazowanie, obrót,
  szyki, edycje historii) liczą model tym samym zapytaniem jądra co zatwierdzenie (z
  odniesieniami ścian), a wynik jest zapamiętywany — „Zastosuj” po gotowym podglądzie tylko
  podmienia model zamiast przebudowywać historię w wątku interfejsu.

## Testy

- Host: `SketchPointHostTest` (12: pomocniczość, kotwica, łączenie z końcem linii, punkt na linii,
  położenie X/Y z wymiarami, stopnie swobody, JSON, przyciąganie, przesunięcie), pełny zestaw.
- Urządzenie (do wykonania): punkt jako środek otworu (Położenie od krawędzi), łączenie z okręgiem.
