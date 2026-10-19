# TabletCAD 0.78 — powiązane kopie szkicu (szyki i lustro)

0.78.0-linked-copies, versionCode 87. Schemat .tcad 37 (nowy rodzaj więzu `rigid`
w istniejącej liście więzów). Etap bez tabletu.

- Szyk liniowy, szyk kołowy i lustro w szkicu tworzą kopie powiązane ze źródłem
  więzem „kopia powiązana” ⧉ (przesunięcie, obrót wokół środka albo odbicie względem
  osi zapisane liczbowo). Solver utrzymuje kopie: przesunięcie, zmiana wymiaru lub
  promienia źródła zmienia wszystkie kopie; status więzów liczy kopie jako w pełni
  określone przez źródło. Oś lustra i środek szyku są stałe od chwili utworzenia.
- Kopie geometrii, której nie da się powiązać 1:1 (np. prostokąt obrócony w szyku
  kołowym zamienia się w wielobok, łuki/krzywe), pozostają niezależne — komunikat
  mówi, ile kopii powiązano. Przycięcie, rozbicie lub zmiana rodzaju elementu
  usuwa jego powiązania zamiast zostawić sprzeczny więz. Więz można usunąć jak
  inne (Geometria → Usuń więzy), co uniezależnia kopię.
- Status więzów: obrót punktu promienia okręgu jest odejmowany jako swoboda pozorna
  tylko wtedy, gdy więzy go nie ustalają (k − rząd(J·G)).

## Wyniki (komputer)

20 zestawów PASS w `tools/host-kernel-tests.sh`, w tym LinkedCopiesHostTest (16):
kopie podążają za przesunięciem, promieniem i wymiarem sterującym źródła, szyk kołowy
linii, lustro prostokąta i linii, stopnie swobody, zapis, utrata powiązania po zmianie
rodzaju, odrzucenie niespełnionej i niepoprawnej relacji. Regresje szkicu: RelationProbe,
SketchPatternProbe, ConstructionAndSnapProbe, SketchWorkflowProbe, AxisConstraintProbe.

## Nie zweryfikowano

- Brak testów na tablecie. Edycja parametrów istniejącego szyku (liczba, odstęp) —
  wymaga usunięcia i utworzenia szyku ponownie.
