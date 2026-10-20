# TabletCAD 0.99 — IGES

Pkt 5 `STATUS_WYMAGAN_CAD.md`: brak IGES. Moduł DataExchange OCCT był już konfigurowany, ale
biblioteka `TKDEIGES` nie była budowana.

## Zmiany

- **Import IGES/IGS** (Plik → Importuj → IGES): rdzeń czyta IGES, a gdy plik zawiera tylko
  powierzchnie, skleja je (tolerancja 1 µm) i zamyka w bryły tam, gdzie powłoki są zamknięte;
  wynik jest przekazywany do zwykłego importu STEP (dokładny B-Rep, rzuty, pomiary, operacje).
  Plik z otwartymi powierzchniami jest odrzucany z wyjaśnieniem.
- **Eksport IGES** (Eksportuj → IGES): model zapisany jako STEP i przeniesiony do IGES w trybie
  B-Rep (jednostki mm). W tle z kartą postępu jak inne eksporty.
- **Budowanie**: `tools/prepare-occt.ps1` buduje także `TKDEIGES`; `build-foundation-apk.ps1`
  uruchamia go, gdy tej biblioteki brakuje (jednorazowo, przyrostowo), i oczekuje 27 bibliotek
  OCCT. `CMakeLists.txt` dołącza IGES tylko, gdy biblioteka istnieje (`TABLETCAD_IGES`) — bez
  niej aplikacja buduje się jak dotąd, a polecenia IGES zgłaszają brak modułu.
- Host: `host-kernel-tests.sh` buduje i dołącza `TKDEIGES`.

- **Arkusz A3** (Rysunek → Ustawienia arkusza): format A4/A3, położenie przekroju i tabliczka w
  jednym panelu (przycisk „Ustawienia arkusza…” wcześniej nic nie robił w trybie Rysunek —
  wywoływał ustawienia rysowania szkicu). Na A3 skala rzutów jest dobierana do większych pól
  (np. część 60 mm: A4 1:1, A3 2:1); PDF w rozmiarze A3. Lista skal ma też 5:1 i 10:1.

## Testy

- Kernel host: `IgesKernelProbe` (płyta z otworem: STEP → IGES → STEP, ta sama objętość B-Rep;
  odrzucenie niepoprawnego pliku), `SheetDimensionHostTest` (29: skala A4/A3, zapis formatu), pełny zestaw.
- Laptop/urządzenie (do wykonania): pierwsze budowanie APK dobuduje TKDEIGES (kilka minut);
  import IGES z innego CAD, eksport i otwarcie w innym programie.
