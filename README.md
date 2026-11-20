# BlockFLow (Fabric, Minecraft 26.3)

Mod **tylko kliencki** – działa na każdym serwerze (serwer nie musi go mieć).
Wymaga Fabric API.

- Zaznacz obszar → skopiuj → obróć/odbij → ustaw „hologram” w świecie.
- **Creative:** mod sam lata i stawia bloki (bierze je do slotu 8 paska i potem oddaje Twój przedmiot).
- **Survival:** pokazuje, jakie bloki postawić (kolorowe boxy), lista materiałów z porównaniem do ekwipunku, pasek postępu.

## Szybki start
1. Patrz na róg budynku → `[` (punkt 1), na przeciwległy róg → `]` (punkt 2).
2. `;` – kopiuj.
3. Patrz na ziemię, gdzie ma stanąć budynek → `\` (postaw hologram, narożnik).
4. `'` – obrót o 90° (też `/bf rotate 180`), `/bf mirror` – lustro, `/bf move x y z` – przesuń.
5. Creative: `.` – start/pauza auto-budowy. Survival: buduj ręcznie według boxów, `/bf list` pokazuje braki.
6. `=` – wyczyść wszystko.

Klawisze zmienisz w Opcje → Sterowanie → BlockFLow.

## Komendy (`/blockflow` lub `/bf`)
`pos1|pos2 [x y z]`, `copy`, `rotate [deg]`, `mirror`, `place [x y z]`, `move x y z`,
`start`, `pause`/`stop`, `clear`, `status`, `list`,
`layer [n]` (pokaż tylko najniższe n niedokończonych warstw), `range n` (zasięg podglądu),
`speed 1-8` (bloków na tick), `replace` (czy niszczyć przeszkadzające bloki w creative).

## Ograniczenia
- Auto-budowa stawia bloki „jak gracz”: pozycja bloku jest dokładna, ale **stan** (np. połówka płyty, kierunek schodów) odtwarzany jest tylko przybliżeniem; postęp liczy się po *rodzaju* bloku.
- Bloki bez podparcia (wiszące w powietrzu) są odkładane na koniec; jeśli nadal nie ma o co oprzeć, są pomijane.
- Nie kopiuje zawartości skrzyń ani encji. Limit: 1 000 000 bloków, 2000 na wymiar.
- **Serwery:** auto-budowa w creative to automatyzacja – sprawdź regulamin serwera/antycheat. Używasz na własną odpowiedzialność.

## Build
Pliki są w katalogu głównym (płasko). `build.yml` przenieś do `.github/workflows/build.yml`
(GitHub: kliknij plik → ołówek → wpisz tę ścieżkę w polu nazwy). Lokalnie: `gradle build` (JDK 25, Gradle 9.5.1).
Wersje (Loader, Loom, Fabric API) w `gradle.properties` – sprawdź na fabricmc.net/develop.
