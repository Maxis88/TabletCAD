# TabletCAD 0.65 — zaznaczenie oznacza edycję

0.65.0-select-to-edit, versionCode 74, format dokumentu bez zmian.

## Zmiana zachowania (na życzenie użytkownika)

Wcześniej zaznaczenie i edycja były rozdzielone: pojedyncze dotknięcie w drzewie
lub na osi czasu tylko podświetlało, edycja wymagała drugiego dotknięcia,
a szkiców widocznych w widoku bryły nie dało się w ogóle wskazać.

- Widok bryły (tryb Wybierz): dotknięcie widocznej linii szkicu otwiera ten
  szkic w edytorze z dotkniętym elementem zaznaczonym i jego panelem parametrów.
  Wybierane są tylko fragmenty niezasłonięte bryłą (ta sama widoczność co
  przy rysowaniu); przy nakładaniu wygrywa szkic podświetlony w drzewie.
- Dotknięcie ściany: ściana zostaje zaznaczona (Szkic nadal jej użyje), a panel
  pokazuje parametry operacji, która utworzyła ścianę (pochodzenie ścian z OCCT);
  ściana bryły bazowej otwiera edycję wyciągnięcia bazowego.
- Drzewo: jedno dotknięcie wiersza szkicu otwiera edycję szkicu.
- Oś czasu: jedno dotknięcie zaznacza operację i otwiera jej edytor.
- Przewijanie osi czasu liczy się od bieżącej szerokości drzewa (poprawka
  stałej z poprzedniego układu).

## Weryfikacja

HONOR YLE-W09, cała `WorkspaceInstrumentation` `result=PASS`. Scenariusz 16.3:
krok 8 — dotknięcie górnej ściany otwiera parametry wyciągnięcia, a szkic
powstaje na tej ścianie; krok 17 — po obróceniu modelu jedno dotknięcie
krawędzi szkicu bazowego otwiera go z zaznaczonym prostokątem, po czym edycja
100 → 120 przebudowuje bryłę dokładnie. Zaktualizowano TabletFlowProbe
i WorkspaceUiProbe do nowego zachowania.
