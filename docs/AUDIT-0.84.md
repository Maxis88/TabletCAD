# TabletCAD 0.84 — panele, szkice źródłowe i podgląd modelowania

Wersja `0.84.0-context-panels`, versionCode 95. Kontynuacja redesignu 0.83.

## Prawe panele

- Formularze mają wspólną kolumnę 272 dp, stały nagłówek i przyciski końcowe.
  Przewijana jest zawartość, nie przyciski Zastosuj/Wykonaj/Anuluj.
- Konteksty szkicu, wyboru płaszczyzny, pomiaru i arkusza korzystają z natywnych
  kontrolek zamiast dawnych paneli Canvas ze sztywnymi współrzędnymi dotyku.
- W szkicu są bezpośrednie Zakończ/Anuluj oraz opcje potrzebne bieżącemu narzędziu.
  Usunięto z domyślnych paneli informacje o rdzeniu i niepowiązanych operacjach.
- Listy poleceń mają jedną kolumnę. Pola drugiego kierunku, pogłębienia,
  głębokości otworu/gwintu oraz wyboru bryły są ukrywane, gdy nie mają zastosowania.
- Obszar klawiatury skraca panel; nie zmienia kamery CAD. Historia pozostaje
  pełnej szerokości pod panelami.

## Szkice źródłowe

- Położenie wyświetlanego szkicu wynika z jawnego przypisania profilu do bryły
  oraz późniejszych operacji Przesuń/Obróć/Skaluj tej bryły.
- Profil pozostaje w swoim historycznym układzie. Transformacja służy rysowaniu
  i odwrotnemu mapowaniu dotyku; nie jest dopisywana drugi raz do generatora B-Rep.
- Widok drzewa uwzględnia transformację każdego profilu osobno. W podglądzie
  niezatwierdzonej operacji obrys korzysta z tego samego dokumentu kandydującego co bryła.
  Edycja szkicu
  na wskazanej bryle korzysta z jej układu i nie wyświetla profili innych brył
  pod błędną wspólną transformacją.
- Uchwyt głębokości edycji historii ma tę samą pozycję i kierunek co przekształcona
  bryła; przeciągnięcie po skalowaniu wraca do jednostek profilu źródłowego.
- Wygaśnięte transformacje i operacje za znacznikiem historii nie są stosowane.
  Dokumenty zachowują schemat 37; nie zmieniono tożsamości profili ani formatu.
- To obsługa źródłowych profili i jawnych transformacji, nie automatyczne
  odrysowywanie końcowego obrysu po otworach/fazach i nie pełne nazewnictwo topologiczne.

## Automatyczne wyciąganie

- Domyślnie panel rozpoznaje operację w tle na podstawie dokładnego przecięcia
  narzędzia wyciągnięcia z wybraną bryłą: wspólna objętość oznacza Wytnij,
  styk bez wspólnej objętości oznacza Połącz.
- Rozpoznany tryb i geometria są pokazywane przed zatwierdzeniem. Zastosuj jest
  aktywne dopiero dla aktualnego, poprawnego podglądu; sprawdzana jest też
  niezmienność dokumentu. Kierunek fizycznego narzędzia jest zachowany przy
  konwersji zapisu z Nowa bryła na Wytnij.
- Pierwszy model pustego dokumentu tworzy bryłę. Gdy istnieją bryły, lecz narzędzie
  nie styka się z wybranym celem, użytkownik wybiera inny cel lub ręcznie Nowa bryła.
- Nowa bryła pozostaje jawną opcją. W istniejących projektach przecinające się
  osobne bryły nie są automatycznie scalane. Do wspólnych krawędzi służy Połącz bryły.
- Próba jądra sprawdza rzeczywiste krawędzie u obu nasad części wystającej przez
  płytę oraz zaokrąglenie i fazę na każdej stronie. Nie dodajemy fikcyjnych linii
  na powierzchni niepołączonych brył.

## Kamera i widoki

- Usunięto automatyczne oddalanie podglądu. Aktualizacja geometrii nie zmienia
  zoomu, punktu obrotu ani odległości perspektywicznej kamery.
- Otwarcie panelu nie przesuwa środka projekcji. Dopasuj jest jawną akcją;
  zakończenie szkicu i zmiana orientacji nie wykonują dopasowania.
- Bryła oraz Bryła z liniami niewidocznymi są dostępne także w szkicu.
  Drugi tryb zachowuje nieprzezroczyste ściany i kreskuje ukryte krawędzie.
- Przekrój OCCT można włączyć podczas edycji parametrów lub szkicu bez zamykania
  operacji. Dostępne są osie X/Y/Z, płaszczyzna szkicu, zmiana strony i suwak
  położenia. Przekrój nie zmienia dokumentu, historii ani eksportu.
- Polecenia widoku są obsługiwane przed zamykaniem narzędzi i sesji szkicu.

## Weryfikacja

- Kompilacja aplikacji i APK testów: PASS; APK podpisany lokalnym kluczem debug v3.
- `SourceSketchKernelProbe`: PASS na HONOR YLE-W09 — transformacje, zapis/odczyt,
  edycja bez podwójnego skalowania, wygaśnięcie i znacznik historii, dokładne
  objętości Połącz/Wytnij oraz fazowanie i zaokrąglanie obu nasad połączenia.
- `ContextPanelsUiProbe`: test widocznego okna na izolowanych dokumentach —
  stałe akcje, odwrócenie projekcji szkicu, brak zoom-out, przekrój w otwartym
  formularzu oraz zatwierdzenie automatycznego wycięcia i ręcznej nowej bryły.
- Pełny przebieg instrumentacji: PASS, 48 zestawów i 1664 sprawdzenia
  (build/instrumentation-0.84-full-pass.txt).
- Po końcowym rozdzieleniu środka geometrii od kamery i powiązaniu obrysu z dokumentem
  podglądu powtórzono właściwe regresje: ContextPanelsUiProbe, WorkspaceUiProbe,
  EdgeGroupUiProbe, ViewCameraProbe, ConstructionKernelProbe, AssemblyKernelProbe
  i CadScenarioProbe: PASS. Logi: build/final-*-0.84.txt.
- Zrzuty widocznego okna: ocenione panele szkicu, stałe przyciski, natywne menu
  przekroju, przekrój podczas edycji oraz automatyczny podgląd wycięcia.

Nie jest to deklaracja ukończenia pełnego zakresu CAD ani sprawdzenia każdej
kombinacji parametrów wszystkich narzędzi na wszystkich tabletach.
