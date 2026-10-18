# TabletCAD 0.62 — pamięć podręczna historii jądra

0.62.0-history-cache, versionCode 71, schema dokumentu bez zmian (34).

## Problem

Każde zatwierdzenie, podgląd, pomiar i zapytanie o krawędzie liczyło całą
historię od zera (bryła bazowa, odczyt STEP, wszystkie operacje Boolean
i zaokrąglenia). Na tablecie płyta z 24 otworami i R3 przebudowywała się
~420 ms, częściowo w wątku interfejsu.

## Zmiany

- `EvalCache` w `native_cad_engine.cpp`: klucz bryły bazowej (wymiary,
  promień, rekord bazy albo ścieżka STEP z rozmiarem i czasem modyfikacji),
  bryła bazowa oraz stan przed każdym rekordem historii najwyższego poziomu
  (kształt, bryły, flagi, pochodzenie ścian).
- Ewaluacja wznawia od najgłębszego stanu, którego prefiks rekordów jest
  identyczny; dalsze stany są odrzucane i liczone ponownie. Edycja wczesnej
  operacji lub bazy unieważnia odpowiednią część.
- Wszystkie wywołania `nativeEvaluateFeatures` są serializowane mutexem
  (podgląd w tle, zatwierdzanie w tle, wątek UI), więc współdzielone kształty
  B-Rep nie są przetwarzane równolegle.

## Weryfikacja (HONOR YLE-W09)

`KernelDiagnosticsProbe`: 25 operacji od zera 421–423 ms; dopisanie jednej
operacji do zapisanej historii 35 ms. Objętość i liczba ścian identyczne
z obliczeniem bez pamięci podręcznej; zmiana pierwszego otworu oraz bazy
przez pamięć podręczną dają wyniki zgodne z obliczeniem od zera. Cała
`WorkspaceInstrumentation` `result=PASS`.

## Granice

- Jedna pozycja pamięci podręcznej (ostatni model); przełączanie między
  różnymi dokumentami liczy od zera.
- Zmiana wczesnej operacji nadal przelicza wszystkie późniejsze.
- Zatwierdzenia edycji szkicu nadal czekają synchronicznie w wątku UI
  (krócej dzięki pamięci podręcznej przy zmianach końcowych).
