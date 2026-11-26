# BlockFLow (Fabric, Minecraft 26.3)

Mod **tylko kliencki** – działa na każdym serwerze (serwer nie musi go mieć).
Wymaga Fabric API.

- Zaznacz obszar → skopiuj → obróć/odbij → ustaw „hologram” w świecie.
- **Creative:** mod sam lata i stawia bloki (bierze je do slotu 8 paska i potem oddaje Twój przedmiot).
- **Survival:** pokazuje, jakie bloki postawić (kolorowe boxy), lista materiałów z porównaniem do ekwipunku, pasek postępu.

## Szybki start
1. Patrz na róg budynku → `[` (punkt 1), na przeciwległy róg → `]` (punkt 2).
2. `;` – kopiuj (albo prawy Shift → menu → gotowy schemat / slot).
3. Patrz na ziemię, gdzie ma stanąć budynek → `\` (postaw hologram, narożnik).
4. `'` – obrót o 90° (też `/bf rotate 180`), `/bf mirror` – lustro, `/bf move x y z` – przesuń.
5. Creative: `.` – start/pauza auto-budowy. Survival: buduj ręcznie według boxów, `/bf list` pokazuje braki.
6. `=` – wyczyść wszystko.

Klawisze zmienisz w Opcje → Sterowanie → BlockFLow.

## Komendy (`/blockflow` lub `/bf`)
`pos1|pos2 [x y z]`, `copy`, `rotate [deg]`, `mirror`, `place [x y z]`, `move x y z`,
`start`, `pause`/`stop`, `clear`, `status`, `list`,
`layer [n]` (pokaż tylko najniższe n niedokończonych warstw), `range n` (zasięg podglądu),
`speed 1-8` (bloków na tick), `replace` (czy niszczyć przeszkadzające bloki w creative),
`unstuck` (wyciąga Cię z bloków).


## Przesuwanie budowli (xyz) i obrót
- **Strzałki** = przód / tył / lewo / prawo (względem kierunku, w który patrzysz), **PgUp / PgDn** = w górę / w dół. Krok: `/bf krok n` albo przycisk w menu (1/5/10).
- Menu (prawy Shift) → ostatnia strona „przesuwanie i obrót": obrót 90°, lustro, przód/tył/lewo/prawo/góra/dół, start/pauza, warstwy.
- Komendy: `/bf przod|tyl|lewo|prawo|gora|dol [n]`, `/bf move x y z`, `/bf rotate [deg]`, `/bf mirror`.

## Brak podparcia = podpora z rusztowania
Gdy blok wisi w powietrzu, bot buduje z **rusztowania** (scaffolding) kolumnę od ziemi pod blokiem albo obok niego (wybiera najkrótszą, do 48 bloków), stawia blok, a potem zdejmuje rusztowanie od góry. Nie zajmuje pól należących do budowli. Piasek/żwir (spadłyby) są pomijane. Wyłączenie: `/bf podpory`.

## Stawianie 1:1 (symulacja vanilli)
Bot **nie zgaduje** już ustawienia. Dla każdego bloku pyta grę (`BlockItem#getPlacementState`), jaki stan postawiłaby vanilla przy danym kliknięciu (podpierany blok, miejsce kliknięcia, obrót i pochylenie gracza), i wybiera dopiero takie ustawienie, które daje **dokładnie** zadany blok. Dzięki temu poprawnie stawia m.in.:
- **wiszące i stojące latarnie**, łańcuchy, dzwony (podłoga/sufit/ściana),
- **tabliczki stojące i ścienne**, pochodnie stojące i ścienne, sztandary, głowy (wariant stojący/ścienny zależy od kąta patrzenia),
- drzwi (zawias, kierunek, obie połówki), łóżka, schody (połówka), płyty (też podwójne), belki (oś), przyciski/dźwignie (podłoga/ściana/sufit), drabiny, pączki kryształów, tłoki, piece, skrzynie itd.
Gdy po postawieniu wyjdzie inny blok lub stan (np. ścienna zamiast stojącej tabliczki), bot **burzy go i próbuje następnego ustawienia** (do 8 prób), zamiast go zostawiać.
- Tekst **tabliczek** jest kopiowany i wpisywany po postawieniu (też zapisywany w slotach).
- Stany przełączane kliknięciem (otwarte drzwi/furtki, dźwignia, opóźnienie przekaźnika, nuta w bloku nutowym) bot ustawia **kliknięciem pustą ręką**.
- Jeśli symulacja jest niedostępna (zmiana API), w `latest.log` pojawi się wpis „symulacja stawiania niedostepna" i bot wraca do wbudowanych heurystyk.
- Czego **nie da się** skopiować/postawić klientem: woda i lawa, ogień, portale, byty (obrazy, ramki, pojazdy, armor standy), zawartość skrzyń i pieców, wzory sztandarów, typ spawnera. Na końcu budowy bot wypisuje, które typy pominął.

## Menu i sloty (prawy Shift)
- **Prawy Shift** (lub `/bf menu`) otwiera menu: **10 slotów na własne budowle** (2 strony po 5) i strona z **gotowymi schematami** (20 sztuk, **z wyposażeniem**: drzwi, łóżka, szafy, piece, klatki schodowe, dźwignie z lampami, pułapka w piramidzie…: domek wiejski, willa, **zamek**, **galeon**, hotel, blok radziecki, **trzy wieżowce** (radziecki, szklany, art deco), biurowiec, bank, kościół, wiatrak, latarnia morska, most łukowy, piramida, wieża zegarowa, stodoła, mur z basztami, kamienica).
- **Zapisz** zapisuje aktualny schowek (najpierw zaznacz i `copy`), **Wczytaj** wrzuca slot do schowka, **Usuń** czyści slot.
- **Postaw na celowniku** ustawia hologram tam, gdzie patrzysz; dalej `start` jak zwykle.
- Sloty leżą w `config/blockflow/slotN.bfs` (możesz je kopiować między komputerami).

## Jak lata i stawia (creative)
- **Budowa warstwami:** od podłogi w górę, cała warstwa (jeden poziom Y) jest kończona, zanim bot wzniesie się wyżej. Bot leci nad bieżącą warstwą po wolnych miejscach, więc nie wchodzi w postawione bloki i serwer niczego nie cofa (noclip usunięty – na serwerze vanilla i tak by nie działał).
- **Stawianie 1:1:** dla każdego bloku do 3 sposobów (obrót gracza, klikana ściana, oś), sprawdzenie wyniku, w razie błędu burzenie i próba kolejnego. Rusztowanie rośnie w górę.
- **Bezpieczniki:** gdy sam ruszysz się (W/A/S/D/spacja/shift), bot oddaje sterowanie. Bot, który nic nie robi ok. 5 s, pomija blok. Po pauzie/końcu jesteś wyciągany z bloków (`/bf unstuck` ręcznie).

## Ograniczenia
- Stan bloku odtwarzany jest dla: kierunku, osi, połówki, płyt i drzwi. Właściwości liczone przez świat (połączenia płotów, kształt schodów, zasilenie) ustawiają się same. Jeśli po 3 próbach stan nadal się nie zgadza, blok zostaje w najlepszej wersji, a podsumowanie to pokaże.
- Bloki bez podparcia (wiszące w powietrzu) są odkładane na koniec; jeśli nadal nie ma o co oprzeć, są pomijane.
- Nie kopiuje zawartości skrzyń ani encji. Limit: 1 000 000 bloków, 2000 na wymiar.
- **Serwery:** auto-budowa w creative to automatyzacja – sprawdź regulamin serwera/antycheat. Używasz na własną odpowiedzialność.

## Build
Pliki są w katalogu głównym (płasko). `build.yml` przenieś do `.github/workflows/build.yml`
(GitHub: kliknij plik → ołówek → wpisz tę ścieżkę w polu nazwy). Lokalnie: `gradle build` (JDK 25, Gradle 9.5.1).
Wersje (Loader, Loom, Fabric API) w `gradle.properties` – sprawdź na fabricmc.net/develop.
