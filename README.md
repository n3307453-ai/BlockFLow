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
`noclip` (przełącznik lotu przez bloki podczas auto-budowy), `unstuck` (wyciąga Cię z bloków).

## Menu i sloty (prawy Shift)
- **Prawy Shift** (lub `/bf menu`) otwiera menu: **10 slotów na własne budowle** (2 strony po 5) i strona z **gotowymi schematami** (domek, wieża, piramida, mur, platforma).
- **Zapisz** zapisuje aktualny schowek (najpierw zaznacz i `copy`), **Wczytaj** wrzuca slot do schowka, **Usuń** czyści slot.
- **Postaw na celowniku** ustawia hologram tam, gdzie patrzysz; dalej `start` jak zwykle.
- Sloty leżą w `config/blockflow/slotN.bfs` (możesz je kopiować między komputerami).

## Jak lata i stawia (creative)
- **Lot bezkolizyjny (domyślny):** mod planuje trasę po wolnych miejscach (prosto, a gdy zablokowane – nad przeszkodami), więc nigdy nie wchodzi w bloki i serwer niczego nie cofa. Prawdziwy noclip przez bloki **nie działa na serwerze vanilla** – serwer sam sprawdza kolizje i cofa gracza. Surowy noclip zostaje jako opcja (`/bf noclip`), ale zwykły serwer go cofnie.
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
