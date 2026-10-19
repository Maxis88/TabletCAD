# TabletCAD 0.88 — grupy elementów szkicu

Rozwinięcie pozycjonowania z 0.86/0.87: szkic zwykle składa się z kilku elementów (płyta i
otwory, kontur i rowek), które trzeba ustawić razem względem bryły lub innych elementów.

## Zmiany

- **Zaznacz wiele elementów** (panel szkicu w trybie Edytuj): dotknięcia dodają i odejmują
  elementy (podświetlenie jak zaznaczenie), licznik w panelu.
- **Wspólne przeciąganie**: przeciągnięcie jednego z zaznaczonych elementów przesuwa wszystkie
  (podgląd przerywaną linią, przyciąganie jak dla pojedynczego elementu).
- **Przesuń o X/Y…** z podglądem; **Położenie i wyrównanie zaznaczenia…**: środek lub narożnik
  wspólnego obrysu w odległości X/Y od początku układu, punktu lub elementu szkicu albo
  rzutowanej krawędzi lub ściany bryły; wyrównanie obrysu zaznaczenia (środek, środek X/Y,
  krawędzie). Narzędzie *Położenie* przy kilku zaznaczonych elementach otwiera wariant grupowy.
- **Usuń zaznaczone** (panel lub narzędzie Usuń) z potwierdzeniem; elementy używane przez bryłę
  poza edycją szkicu są pomijane i zgłaszane.
- Bezpieczeństwo więzów (`SketchPosition.moveGroup`): po przesunięciu więzy są rozwiązywane
  ponownie; jeżeli element grupy jest trzymany wymiarem od początku układu, unieruchomieniem lub
  wymiarem do elementu spoza grupy, ruch jest odrzucany z wyjaśnieniem zamiast cichej deformacji.
  Wymiary między elementami grupy przesuwają się razem z nią.

## Testy

- Host: `SketchPositionHostTest` (31: przesunięcie płyty z otworem zwymiarowanym od jej narożnika,
  odmowa dla elementu trzymanego wymiarami od początku, wyśrodkowanie grupy, wyrównanie do linii,
  odmowa odniesienia z grupy), pełny zestaw host-kernel.
- Urządzenie (do wykonania): `SketchMoveUiProbe` krok 5 — zaznaczenie dwóch okręgów dotknięciami i
  wspólne przeciągnięcie.
