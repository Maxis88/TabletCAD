# TabletCAD — instrukcje rozwoju

Źródłem kodu jest https://github.com/Maxis88/TabletCAD, gałąź integracyjna `main`.
To natywna aplikacja CAD offline na tablety Android, Java + View/Canvas oraz
C++/JNI/Open CASCADE. Nie zastępuj interfejsu WebView ani B-Rep siatką.

## Praca z Git

- Przed zmianami przeczytaj README, bieżący audyt i stan `git status`.
- Zachowuj istniejące zmiany użytkownika i historię. Nie używaj force push.
- Większe zmiany realizuj na `codex/<opis>`, testuj, następnie integruj z `main`.
- Każdą zakończoną i przetestowaną zmianę zapisuj jako commit z czytelnym opisem.
- Nie commituj SDK, cache, wyników budowania, dokumentów użytkownika, kluczy,
  tokenów, haseł ani lokalnych ustawień. Nigdy nie wypisuj poświadczeń w logach.

## Zasady implementacji

- Zachowuj UUID, wersjonowanie i zgodność odczytu dokumentów `.tcad`.
- Waliduj nową geometrię OCCT przed zastąpieniem ostatniego poprawnego stanu.
- Nie zgaduj tożsamości utraconych krawędzi ani ścian; zgłaszaj niejednoznaczność.
- Błędy przebudowy nie mogą niszczyć geometrii, historii ani zapisanych projektów.
- Utrzymuj undo/redo, sesje szkicu, import/eksport i autosave spójne ze zmianą.
- Rozróżniaj aktualną implementację od celów opisanych w starszej architekturze.
- Gdy wymagania mechaniki lub danych są niejasne, pytaj przed implementacją.

## Weryfikacja

- Zbuduj APK zgodnie z README; zmiany JNI wymagają kompilacji C++.
- Uruchamiaj odpowiednie `tools/*Probe.java` na izolowanych danych Androida.
  Są to programy uruchamiane przez `app_process`, nie testy JUnit hosta.
- Zmiany UI sprawdzaj w działającej aplikacji na emulatorze ARM64 lub tablecie.
- Nie używaj projektów użytkownika do destrukcyjnych prób.
- Raportuj osobno kompilację, testy regresji i faktycznie sprawdzone gesty.
- Aktualizuj dokumentację i audyt ograniczeń; nie deklaruj pełnego solvera ani
  pełnego nazewnictwa topologicznego bez implementacji i testów.
