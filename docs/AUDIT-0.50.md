# TabletCAD 0.50 — obliczenia podglądu w tle

Wersja deweloperska 0.50.0-background-preview, versionCode 57.

## Zmiany

GeometryPreviewWorker wykonuje jedno bieżące obliczenie i przechowuje najwyżej
jedno najnowsze oczekujące żądanie. Nowe parametry zastępują poprzednie żądanie,
zamiast tworzyć rosnącą kolejkę. Każdy wynik jest sprawdzany względem numeru
rewizji również przy dostarczaniu na wątek interfejsu.

Wyciąganie bazowe, dodanie/wycięcie, edycja cech, obrót profilu, fazowanie
i zaokrąglanie przekazują do podglądu kopie tablic i skalarnych parametrów.
Worker nie odczytuje ani nie modyfikuje bieżącego dokumentu i kontrolek UI.
Użytkownik otrzymuje stan obliczania, a wynik trafia na główny Handler Androida.

Zmiana pola unieważnia poprzedni wynik natychmiast, także podczas opóźnienia
przed uruchomieniem następnego podglądu. Zamknięcie narzędzia unieważnia wynik
już oczekujący na UI. Odłączenie widoku zamyka worker i blokuje nowe zadania
z opóźnionych callbacków do czasu ponownego przyłączenia.

## Weryfikacja

- PreviewWorkerProbe: osobny wątek, zastępowanie oczekujących żądań, brak
  dostarczenia starego wyniku, obsługa wyjątku, anulowanie/close już zakolejkowanego
  wyniku. Przeszedł lokalnie i na fizycznym Androidzie.
- Wszystkie 32 zestawy app_process przeszły na tablecie.
- Oba zestawy instrumentowane przeszły. Test gestów czeka poza wątkiem UI na
  rzeczywisty podgląd z OCCT; weryfikuje objętości i niezmieniony dokument.
- Instrumentacja potwierdziła niezależne kopie parametrów oraz możliwość
  zamknięcia panelu, gdy zadanie nadal jest celowo zablokowane w tle.
  Późny wynik zamkniętego narzędzia nie jest publikowany.
- APK zbudowany i podpisany tym samym kluczem, zainstalowany przez adb install -r.
  Końcowa instrumentacja po zmianie cyklu życia: PASS. Zimny start MainActivity: ok.
- Normalna aplikacja na tablecie: otwarcie wyciągania, uzyskanie podglądu 16 mm
  i zamknięcie przez Anuluj bez zapisu. Dowody lokalne: build/regression050/.

## Ograniczenia

Unieważnienie wyniku nie przerywa wnętrza trwającego algorytmu OCCT. Bieżące
zadanie kończy się w tle, a jego wynik jest odrzucany; najnowsze czeka najwyżej
za tym jednym zadaniem. Nie jest to twarde anulowanie natywnego obliczenia.

Zatwierdzanie operacji, odtwarzanie dokumentu, pomiary, import/eksport i część
odczytów krawędzi nadal mogą wykonywać kosztowne wywołania na wątku UI.
Nie przeprowadzono pełnych testów pamięci i wydajności dużych projektów.
Nie deklarujemy pełnej asynchroniczności aplikacji ani ukończenia projektu.
