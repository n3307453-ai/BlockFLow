package pl.blockflow;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Gotowe schematy generowane w kodzie (do wczytania do schowka i postawienia). */
final class Builtin {
    private Builtin() {}

    static final String[] NAMES = {
            "Domek 7x5x7", "Wieza 5x12x5", "Piramida 15x8x15", "Mur 20x4x1", "Platforma 9x1x9",
            "Zamek 27x16x27", "Statek 9x15x25", "Hotel 17x20x14", "Blok radziecki 36x24x12",
            "Bank 23x17x21", "Latarnia 11x26x11", "Willa 13x11x11", "Most 31x9x7"};

    /** Prosty builder: mapa pozycja -> stan, pozniejsze set() nadpisuje wczesniejsze. */
    private static final class B {
        final Map<Long, BlockState> m = new HashMap<>();
        final int w, h, d;

        B(int w, int h, int d) { this.w = w; this.h = h; this.d = d; }

        static long key(int x, int y, int z) {
            return (((long) x & 0xFFFFF) << 40) | (((long) y & 0xFFFFF) << 20) | ((long) z & 0xFFFFF);
        }

        void set(int x, int y, int z, BlockState s) {
            if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) return;
            m.put(key(x, y, z), s);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) set(x, y, z, s);
        }

        /** Same sciany pionowe (4 boki), bez dna i dachu. */
        void shell(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++)
                        if (x == x0 || x == x1 || z == z0 || z == z1) set(x, y, z, s);
        }

        void carve(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) m.remove(key(x, y, z));
        }

        List<Flow.Cell> cells() {
            List<Flow.Cell> l = new ArrayList<>(m.size());
            for (Map.Entry<Long, BlockState> e : m.entrySet()) {
                long k = e.getKey();
                l.add(new Flow.Cell((int) ((k >> 40) & 0xFFFFF), (int) ((k >> 20) & 0xFFFFF),
                        (int) (k & 0xFFFFF), e.getValue()));
            }
            return l;
        }
    }

    static String load(int idx) {
        B b = switch (idx) {
            case 0 -> domek();
            case 1 -> wieza();
            case 2 -> piramida();
            case 3 -> mur();
            case 4 -> platforma();
            case 5 -> zamek();
            case 6 -> statek();
            case 7 -> hotel();
            case 8 -> blokRadziecki();
            case 9 -> bank();
            case 10 -> latarnia();
            case 11 -> willa();
            default -> most();
        };
        List<Flow.Cell> l = b.cells();
        Flow.setClipboard(l.toArray(new Flow.Cell[0]), b.w, b.h, b.d);
        return "Wczytano: " + NAMES[idx] + " (" + l.size() + " blokow). Teraz: Postaw na celowniku (menu) albo klawisz \\.";
    }

    // ------------------------------------------------------------------ proste
    private static B domek() {
        B b = new B(7, 5, 7);
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        b.fill(0, 0, 0, 6, 0, 6, planks);
        b.shell(0, 1, 0, 6, 3, 6, cobble);
        for (int y = 1; y <= 3; y++) for (int cx : new int[]{0, 6}) for (int cz : new int[]{0, 6}) b.set(cx, y, cz, log);
        b.set(3, 2, 6, glass);
        b.set(0, 2, 3, glass);
        b.set(6, 2, 3, glass);
        b.carve(3, 1, 0, 3, 2, 0);
        b.fill(0, 4, 0, 6, 4, 6, planks);
        return b;
    }

    private static B wieza() {
        B b = new B(5, 12, 5);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        b.fill(0, 0, 0, 4, 0, 4, bricks);
        b.shell(0, 1, 0, 4, 10, 4, bricks);
        b.fill(0, 11, 0, 4, 11, 4, bricks);
        for (int y = 2; y <= 10; y += 4) {
            b.set(2, y, 0, glass); b.set(2, y, 4, glass); b.set(0, y, 2, glass); b.set(4, y, 2, glass);
        }
        b.carve(2, 1, 0, 2, 2, 0);
        return b;
    }

    private static B piramida() {
        B b = new B(15, 8, 15);
        BlockState sand = Blocks.SANDSTONE.defaultBlockState();
        for (int y = 0; y < 8; y++) b.fill(y, y, y, 14 - y, y, 14 - y, sand);
        return b;
    }

    private static B mur() {
        B b = new B(20, 4, 1);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 20; x++) {
                if (y == 3 && x % 2 == 1) continue;
                b.set(x, y, 0, bricks);
            }
        return b;
    }

    private static B platforma() {
        B b = new B(9, 1, 9);
        b.fill(0, 0, 0, 8, 0, 8, Blocks.STONE_BRICKS.defaultBlockState());
        return b;
    }

    // ------------------------------------------------------------------ zamek
    private static BlockState castleMat(int x, int y, int z) {
        return Math.floorMod(x * 7 + y * 13 + z * 17, 6) == 0
                ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static B zamek() {
        B b = new B(27, 16, 27);
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        b.fill(0, 0, 0, 26, 0, 26, cobble);

        // mury oslonowe (grubosc 2) + zebatki
        for (int x = 3; x <= 23; x++)
            for (int z = 3; z <= 23; z++) {
                int dm = Math.min(Math.min(x - 3, 23 - x), Math.min(z - 3, 23 - z));
                if (dm <= 1) for (int y = 1; y <= 6; y++) b.set(x, y, z, castleMat(x, y, z));
                if (dm == 0 && (x + z) % 2 == 0) b.set(x, 7, z, castleMat(x, 7, z));
            }

        // 4 wieze narozne 7x7
        for (int[] t : new int[][]{{0, 0}, {20, 0}, {0, 20}, {20, 20}}) {
            int x0 = t[0], z0 = t[1], x1 = x0 + 6, z1 = z0 + 6;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (edge) for (int y = 1; y <= 9; y++) b.set(x, y, z, castleMat(x, y, z));
                    b.set(x, 10, z, castleMat(x, 10, z));                  // pokrywa
                    if (edge && (x + z) % 2 == 0) b.set(x, 11, z, castleMat(x, 11, z));   // zebatki
                }
            for (int y = 6; y <= 7; y++) {
                b.set(x0 + 3, y, z0, glass); b.set(x0 + 3, y, z1, glass);
                b.set(x0, y, z0 + 3, glass); b.set(x1, y, z0 + 3, glass);
            }
        }

        // glowna baszta 9x9
        for (int x = 9; x <= 17; x++)
            for (int z = 9; z <= 17; z++) {
                boolean edge = x == 9 || x == 17 || z == 9 || z == 17;
                if (edge) for (int y = 1; y <= 11; y++) b.set(x, y, z, castleMat(x, y, z));
                if (x > 9 && x < 17 && z > 9 && z < 17) b.set(x, 6, z, cobble);   // pietro
                b.set(x, 12, z, castleMat(x, 12, z));
                if (edge && (x + z) % 2 == 0) b.set(x, 13, z, castleMat(x, 13, z));
            }
        for (int y : new int[]{3, 4, 8, 9}) {
            b.set(13, y, 9, glass); b.set(13, y, 17, glass); b.set(9, y, 13, glass); b.set(17, y, 13, glass);
        }
        b.carve(12, 1, 9, 14, 3, 9);       // drzwi baszty
        b.carve(12, 1, 3, 14, 4, 4);       // brama w murze
        return b;
    }

    // ------------------------------------------------------------------ statek
    private static int hw(int z) {
        if (z < 5) return 1 + z * 3 / 5;
        if (z > 19) return 1 + (24 - z) * 3 / 5;
        return 4;
    }

    private static B statek() {
        B b = new B(9, 15, 25);
        int cx = 4;
        BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState oak = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState wool = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();

        for (int z = 0; z < 25; z++) {
            int h = hw(z);
            b.fill(cx - Math.max(0, h - 1), 0, z, cx + Math.max(0, h - 1), 0, z, dark);      // stępka
            b.fill(cx - h, 1, z, cx + h, 2, z, dark);                                          // kadlub
            b.fill(cx - h, 3, z, cx + h, 3, z, oak);                                           // poklad
            b.set(cx - h, 4, z, fence);                                                        // burty
            b.set(cx + h, 4, z, fence);
        }
        b.set(cx, 4, 0, fence);                       // dziob
        b.set(cx, 5, 0, fence);

        // maszty i zagle
        for (int mz : new int[]{6, 12}) {
            b.fill(cx, 4, mz, cx, 13, mz, log);
            b.fill(cx - 3, 6, mz + 1, cx + 3, 12, mz + 1, wool);
        }

        // kajuta rufowa
        b.shell(2, 4, 15, 6, 7, 19, dark);
        b.fill(2, 8, 15, 6, 8, 19, oak);
        b.carve(3, 4, 16, 5, 7, 18);
        b.carve(cx, 4, 15, cx, 5, 15);                // drzwi od dziobu
        b.set(2, 5, 17, glass); b.set(6, 5, 17, glass);
        b.set(cx, 5, 19, glass);
        return b;
    }

    // ------------------------------------------------------------------ hotel
    private static B hotel() {
        B b = new B(17, 20, 14);
        BlockState wall = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState quartz = Blocks.POLISHED_ANDESITE.defaultBlockState();   // naroza i attyka
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState slab = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();

        for (int f = 0; f <= 6; f++) b.fill(1, 3 * f, 1, 15, 3 * f, 13, slab);          // stropy / podlogi
        b.shell(1, 1, 1, 15, 18, 13, wall);
        for (int y = 1; y <= 18; y++) for (int cx : new int[]{1, 15}) for (int cz : new int[]{1, 13}) b.set(cx, y, cz, quartz);

        for (int f = 0; f < 6; f++) {                                                     // okna pieter
            int y = 3 * f + 2;
            for (int x = 3; x <= 13; x += 2) { b.set(x, y, 1, glass); b.set(x, y, 13, glass); }
            for (int z : new int[]{4, 7, 10}) { b.set(1, y, z, glass); b.set(15, y, z, glass); }
        }
        for (int y = 1; y <= 3; y++) for (int x : new int[]{3, 4, 5, 11, 12, 13}) b.set(x, y, 1, glass);   // lobby
        b.carve(7, 1, 1, 9, 3, 1);                                                        // wejscie
        b.fill(7, 4, 1, 9, 4, 1, gold);                                                   // szyld
        b.fill(6, 4, 0, 10, 4, 0, dark);                                                  // daszek
        b.fill(6, 1, 0, 6, 3, 0, dark);
        b.fill(10, 1, 0, 10, 3, 0, dark);
        for (int x = 1; x <= 15; x++) { b.set(x, 19, 1, quartz); b.set(x, 19, 13, quartz); }   // attyka
        for (int z = 1; z <= 13; z++) { b.set(1, 19, z, quartz); b.set(15, 19, z, quartz); }
        return b;
    }

    // ------------------------------------------------------------------ blok radziecki
    private static B blokRadziecki() {
        B b = new B(36, 24, 12);
        BlockState panel = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState seam = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState slab = Blocks.COBBLESTONE.defaultBlockState();
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        BlockState brick = Blocks.BRICKS.defaultBlockState();

        for (int f = 0; f <= 5; f++) b.fill(1, 4 * f, 2, 34, 4 * f, 10, slab);           // stropy
        b.shell(1, 1, 2, 34, 19, 10, panel);                                              // sciany 5 pieter
        for (int y = 1; y <= 19; y++)
            for (int x = 1; x <= 34; x += 1) {
                if (x % 6 == 0) { b.set(x, y, 2, seam); b.set(x, y, 10, seam); }          // spoiny pionowe
            }
        for (int f = 0; f <= 5; f++)
            for (int x = 1; x <= 34; x++) {
                int y = 4 * f;
                if (y >= 1 && y <= 19) { b.set(x, y, 2, seam); b.set(x, y, 10, seam); }   // pasy poziome
            }
        for (int f = 0; f < 5; f++) {                                                     // okna
            for (int x = 1; x <= 34; x++) {
                if (x % 6 == 2 || x % 6 == 3) {
                    for (int y = 4 * f + 2; y <= 4 * f + 3; y++) { b.set(x, y, 2, glass); b.set(x, y, 10, glass); }
                }
            }
        }
        for (int x0 : new int[]{4, 16, 28}) {                                             // 3 klatki schodowe
            b.carve(x0, 1, 2, x0 + 1, 3, 2);
            b.fill(x0 - 1, 4, 1, x0 + 2, 4, 1, panel);                                    // daszek
            b.fill(x0 - 1, 4, 2, x0 + 2, 4, 2, seam);
        }
        for (int f = 1; f < 5; f++)                                                       // balkony
            for (int x = 1; x <= 34; x++)
                if (x % 6 == 2 || x % 6 == 3) {
                    b.set(x, 4 * f, 1, panel);
                    b.set(x, 4 * f + 1, 1, pane);
                }
        for (int x = 1; x <= 34; x++) { b.set(x, 21, 2, seam); b.set(x, 21, 10, seam); } // attyka
        for (int z = 2; z <= 10; z++) { b.set(1, 21, z, seam); b.set(34, 21, z, seam); }
        for (int cx : new int[]{9, 25}) b.fill(cx, 21, 5, cx + 1, 23, 6, brick);         // kominy
        return b;
    }

    // ------------------------------------------------------------------ bank
    private static B bank() {
        B b = new B(23, 17, 21);
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();

        b.fill(0, 0, 0, 22, 0, 20, stone);                  // cokol: schody
        b.fill(0, 1, 2, 22, 1, 20, stone);
        b.fill(3, 2, 7, 19, 2, 17, floor);                  // podloga holu
        b.fill(2, 2, 3, 20, 2, 5, quartz);                  // portyk
        b.shell(2, 3, 6, 20, 9, 18, quartz);                // sciany holu
        b.fill(1, 10, 3, 21, 10, 19, stone);                // dach z okapem
        for (int x : new int[]{3, 6, 9, 13, 16, 19}) b.fill(x, 3, 4, x, 9, 4, pillar);   // kolumny
        for (int k = 0; k <= 4; k++) b.fill(1 + 2 * k, 11 + k, 3, 21 - 2 * k, 11 + k, 4, quartz);   // fronton
        b.carve(10, 3, 6, 12, 5, 6);                        // wejscie
        for (int x : new int[]{5, 8, 14, 17}) { b.set(x, 4, 6, glass); b.set(x, 5, 6, glass); }

        b.shell(14, 3, 13, 19, 7, 17, iron);                // skarbiec
        b.fill(14, 8, 13, 19, 8, 17, iron);
        b.carve(15, 3, 14, 18, 7, 16);
        b.fill(15, 3, 14, 18, 4, 16, gold);                 // zloto
        b.carve(16, 3, 13, 17, 5, 13);                      // drzwi skarbca
        return b;
    }

    // ------------------------------------------------------------------ latarnia
    private static B latarnia() {
        B b = new B(11, 26, 11);
        int c = 5;
        BlockState white = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState red = Blocks.BRICKS.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();

        for (int x = 0; x < 11; x++)
            for (int z = 0; z < 11; z++) {
                int d2 = (x - c) * (x - c) + (z - c) * (z - c);
                if (d2 <= 20) b.set(x, 0, z, stone);                                        // fundament
                for (int y = 1; y <= 17; y++) {
                    int r = y < 10 ? 4 : 3;
                    boolean wall = d2 <= r * r + r && d2 > (r - 1) * (r - 1) + (r - 1);
                    if (wall) b.set(x, y, z, (y / 3) % 2 == 0 ? white : red);
                }
                for (int y : new int[]{6, 12}) if (d2 <= 6) b.set(x, y, z, plank);         // pietra
                if (d2 <= 22) b.set(x, 18, z, plank);                                       // galeria
                if (d2 <= 22 && d2 > 15) b.set(x, 19, z, fence);                            // barierka
                for (int y = 19; y <= 22; y++) if (d2 <= 6 && d2 > 2) b.set(x, y, z, glass);
                if (d2 <= 10) b.set(x, 23, z, roof);
                if (d2 <= 4) b.set(x, 24, z, roof);
            }
        b.set(c, 25, c, roof);
        b.set(c, 20, c, lamp);
        b.set(c, 21, c, lamp);
        b.carve(c, 1, 0, c, 2, 2);                                                           // drzwi
        return b;
    }

    // ------------------------------------------------------------------ willa
    private static B willa() {
        B b = new B(13, 11, 11);
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();

        b.fill(0, 0, 1, 12, 0, 10, cobble);
        b.shell(0, 1, 1, 12, 3, 10, plank);
        b.fill(0, 4, 1, 12, 4, 10, plank);
        b.shell(0, 5, 1, 12, 7, 10, plank);
        for (int y = 1; y <= 7; y++) for (int cx : new int[]{0, 12}) for (int cz : new int[]{1, 10}) b.set(cx, y, cz, log);
        for (int x : new int[]{3, 9}) {
            b.set(x, 2, 1, glass); b.set(x, 2, 10, glass);
            b.set(x, 6, 1, glass); b.set(x, 6, 10, glass);
        }
        for (int z : new int[]{4, 7}) {
            b.set(0, 2, z, glass); b.set(12, 2, z, glass); b.set(0, 6, z, glass); b.set(12, 6, z, glass);
        }
        b.set(6, 6, 1, glass);
        b.carve(6, 1, 1, 6, 2, 1);                                   // drzwi
        b.fill(4, 4, 0, 8, 4, 0, plank);                              // balkon
        b.fill(4, 5, 0, 8, 5, 0, fence);
        b.fill(0, 8, 1, 12, 8, 10, dark);                             // dach
        b.fill(2, 9, 3, 10, 9, 8, dark);
        b.fill(4, 10, 5, 8, 10, 6, dark);
        return b;
    }

    // ------------------------------------------------------------------ most
    private static B most() {
        B b = new B(31, 9, 7);
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();

        b.fill(0, 4, 1, 30, 4, 5, plank);
        for (int x = 0; x < 31; x++) { b.set(x, 5, 1, fence); b.set(x, 5, 5, fence); }
        for (int x : new int[]{3, 9, 15, 21, 27}) b.fill(x, 0, 2, x, 3, 4, stone);
        for (int x : new int[]{0, 30}) for (int z : new int[]{1, 5}) {
            b.fill(x, 5, z, x, 7, z, log);
            b.set(x, 8, z, lantern);
        }
        return b;
    }
}
