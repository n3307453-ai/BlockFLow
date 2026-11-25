package pl.blockflow;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gotowe schematy generowane w kodzie. Bloki sa wyszukiwane po id (np. "white_concrete"), wiec
 * zmiany nazw stalych w klasie Blocks nie psuja kompilacji; brakujacy blok zamieniany jest na kamienne cegly.
 */
final class Builtin {
    private Builtin() {}

    static String[] names() {
        return new String[]{
                "Domek wiejski", "Willa nowoczesna", "Zamek", "Statek (galeon)", "Hotel",
                "Blok radziecki", "Wiezowiec radziecki", "Wiezowiec szklany", "Wiezowiec art deco", "Biurowiec",
                "Bank", "Kosciol", "Wiatrak", "Latarnia morska", "Most lukowy",
                "Piramida", "Wieza zegarowa", "Stodola", "Mur z basztami", "Kamienica"};
    }

    // ------------------------------------------------------------------ bloki
    private static final Map<String, BlockState> CACHE = new HashMap<>();

    static BlockState blk(String id) {
        BlockState c = CACHE.get(id);
        if (c != null) return c;
        Block b = Schem.blockById("minecraft:" + id);
        if (b == null) b = Schem.blockById("minecraft:stone_bricks");
        if (b == null) b = Schem.blockById("minecraft:stone");
        BlockState s = b.defaultBlockState();
        CACHE.put(id, s);
        return s;
    }

    static BlockState stair(String id, Direction facing, boolean top) {
        BlockState s = blk(id);
        if (s.hasProperty(StairBlock.FACING)) s = s.setValue(StairBlock.FACING, facing);
        if (s.hasProperty(BlockStateProperties.HALF)) s = s.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        return s;
    }

    static BlockState slab(String id, boolean top) {
        BlockState s = blk(id);
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE))
            s = s.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
        return s;
    }

    static BlockState axis(String id, Direction.Axis a) {
        BlockState s = blk(id);
        if (s.hasProperty(BlockStateProperties.AXIS)) s = s.setValue(BlockStateProperties.AXIS, a);
        return s;
    }

    // ------------------------------------------------------------------ builder
    static final class B {
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

        BlockState get(int x, int y, int z) { return m.get(key(x, y, z)); }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) set(x, y, z, s);
        }

        /** Cztery sciany pionowe (bez podlogi i dachu). */
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

        void disk(int cx, int cz, int y, int r, BlockState s) {
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    int d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (d2 <= r * r + r) set(x, y, z, s);
                }
        }

        void ring(int cx, int cz, int y, int r, BlockState s) {
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    int d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (d2 <= r * r + r && d2 > (r - 1) * (r - 1) + (r - 1)) set(x, y, z, s);
                }
        }

        void cylinder(int cx, int cz, int y0, int y1, int r, BlockState s) {
            for (int y = y0; y <= y1; y++) ring(cx, cz, y, r, s);
        }

        /** Stozek (stopniowo malejace tarcze), r -> 0. */
        void cone(int cx, int cz, int y, int r, BlockState s) {
            for (int k = 0; k <= r; k++) disk(cx, cz, y + k, r - k, s);
        }

        /** Dach dwuspadowy ze schodow. alongX = kalenica rownolegla do osi X. */
        void gable(int x0, int z0, int x1, int z1, int y, String stairId, BlockState ridge, BlockState gableFill, boolean alongX) {
            int a0 = alongX ? z0 : x0, a1 = alongX ? z1 : x1;       // os poprzeczna do kalenicy
            int b0 = alongX ? x0 : z0, b1 = alongX ? x1 : z1;       // os kalenicy
            for (int k = 0; a0 + k <= a1 - k; k++) {
                int lo = a0 + k, hi = a1 - k;
                for (int t = b0; t <= b1; t++) {
                    if (lo == hi) {
                        put(alongX, t, y + k, lo, ridge);
                    } else {
                        put(alongX, t, y + k, lo, stair(stairId, alongX ? Direction.SOUTH : Direction.EAST, false));
                        put(alongX, t, y + k, hi, stair(stairId, alongX ? Direction.NORTH : Direction.WEST, false));
                        if (gableFill != null && (t == b0 || t == b1))
                            for (int a = lo + 1; a < hi; a++) put(alongX, t, y + k, a, gableFill);
                    }
                }
            }
        }

        private void put(boolean alongX, int t, int y, int a, BlockState s) {
            if (alongX) set(t, y, a, s); else set(a, y, t, s);
        }

        /** Dach czterospadowy (ostroslup) ze schodow. */
        void hip(int x0, int z0, int x1, int z1, int y, String stairId, BlockState top) {
            for (int k = 0; x0 + k <= x1 - k && z0 + k <= z1 - k; k++) {
                int xa = x0 + k, xb = x1 - k, za = z0 + k, zb = z1 - k;
                for (int x = xa; x <= xb; x++)
                    for (int z = za; z <= zb; z++) {
                        boolean edge = x == xa || x == xb || z == za || z == zb;
                        if (!edge) continue;
                        if (xa == xb || za == zb) { set(x, y + k, z, top); continue; }
                        boolean corner = (x == xa || x == xb) && (z == za || z == zb);
                        if (corner) { set(x, y + k, z, top); continue; }
                        Direction f = z == za ? Direction.SOUTH : z == zb ? Direction.NORTH : x == xa ? Direction.EAST : Direction.WEST;
                        set(x, y + k, z, stair(stairId, f, false));
                    }
            }
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

    static boolean alt(int x, int y, int z, int mod) {
        return Math.floorMod(x * 7 + y * 13 + z * 17, mod) == 0;
    }

    static B generate(int idx) {
        return switch (idx) {
            case 0 -> domek();
            case 1 -> willa();
            case 2 -> zamek();
            case 3 -> statek();
            case 4 -> hotel();
            case 5 -> blokRadziecki();
            case 6 -> stalinka();
            case 7 -> szklany();
            case 8 -> artDeco();
            case 9 -> biurowiec();
            case 10 -> bank();
            case 11 -> kosciol();
            case 12 -> wiatrak();
            case 13 -> latarnia();
            case 14 -> most();
            case 15 -> piramida();
            case 16 -> wiezaZegarowa();
            case 17 -> stodola();
            case 18 -> mur();
            default -> kamienica();
        };
    }

    static String load(int idx) {
        B b = generate(idx);
        List<Flow.Cell> l = b.cells();
        Flow.setClipboard(l.toArray(new Flow.Cell[0]), b.w, b.h, b.d);
        return "Wczytano: " + names()[idx] + " (" + l.size() + " blokow, " + b.w + "x" + b.h + "x" + b.d
                + "). Teraz: Postaw na celowniku (menu) albo klawisz \\.";
    }

    // =================================================================== 0 DOMEK WIEJSKI
    private static B domek() {
        B b = new B(13, 10, 13);
        BlockState grass = blk("grass_block"), pathB = blk("dirt_path"), cobble = blk("cobblestone");
        BlockState wall = blk("birch_planks"), post = blk("dark_oak_log"), floor = blk("oak_planks");
        BlockState pane = blk("glass_pane"), fence = blk("oak_fence"), brick = blk("stone_bricks");
        int x0 = 2, x1 = 10, z0 = 3, z1 = 11;
        b.fill(0, 0, 0, 12, 0, 12, grass);
        for (int z = 0; z <= 2; z++) b.set(6, 0, z, pathB);
        b.fill(x0, 0, z0, x1, 0, z1, floor);
        b.shell(x0, 1, z0, x1, 1, z1, cobble);                       // podmurowka
        b.shell(x0, 2, z0, x1, 4, z1, wall);
        for (int y = 1; y <= 4; y++)
            for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {6, z0}, {6, z1}, {x0, 7}, {x1, 7}})
                b.set(p[0], y, p[1], post);                          // slupy
        b.fill(x0, 5, z0, x1, 5, z1, blk("dark_oak_planks"));
        for (int x : new int[]{4, 8}) { b.set(x, 3, z0, pane); b.set(x, 3, z1, pane); }
        for (int z : new int[]{5, 9}) { b.set(x0, 3, z, pane); b.set(x1, 3, z, pane); }
        b.carve(6, 1, z0, 6, 3, z0);                                 // drzwi
        b.set(6, 4, z0, blk("dark_oak_planks"));
        b.set(5, 1, 2, blk("cobblestone_stairs")); b.set(7, 1, 2, blk("cobblestone_stairs"));
        b.gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 5, "spruce_stairs", blk("spruce_planks"), wall, true);
        b.fill(8, 2, 9, 8, 8, 9, brick);                             // komin
        b.set(8, 9, 9, blk("stone_brick_wall"));
        for (int i = 0; i <= 12; i++) {                              // plot
            if (i != 6) b.set(i, 1, 0, fence);
            b.set(i, 1, 12, fence);
            b.set(0, 1, i, fence); b.set(12, 1, i, fence);
        }
        b.set(3, 1, 1, blk("poppy")); b.set(9, 1, 1, blk("dandelion")); b.set(1, 1, 8, blk("poppy"));
        furnishDomek(b);
        return b;
    }

    // =================================================================== 1 WILLA
    private static B willa() {
        B b = new B(19, 14, 17);
        BlockState conc = blk("white_concrete"), dark = blk("dark_oak_planks"), glass = blk("glass");
        BlockState slab = blk("smooth_stone"), qp = blk("quartz_pillar"), grass = blk("grass_block");
        BlockState rail = blk("glass_pane"), gray = blk("gray_concrete"), lamp = blk("sea_lantern");
        b.fill(0, 0, 0, 18, 0, 16, grass);
        b.fill(2, 0, 2, 16, 0, 14, blk("stone_bricks"));
        b.fill(2, 1, 2, 16, 1, 14, slab);                                   // podloga parteru
        b.shell(2, 2, 2, 16, 5, 14, conc);                                   // parter
        for (int x = 3; x <= 15; x++) for (int y = 3; y <= 4; y++) {          // przeszklona fasada frontu
            if ((x - 3) % 4 != 0) b.set(x, y, 2, glass);
        }
        for (int y = 2; y <= 5; y++) for (int x : new int[]{2, 6, 10, 14, 16}) b.set(x, y, 2, qp);
        b.carve(8, 2, 2, 10, 4, 2);                                          // wejscie
        for (int z = 4; z <= 12; z += 4) for (int y = 3; y <= 4; y++) { b.set(2, y, z, glass); b.set(16, y, z, glass); }
        b.fill(2, 6, 2, 16, 6, 14, slab);                                    // strop parteru
        b.shell(2, 7, 2, 10, 10, 14, conc);                                  // pietro (lewa czesc)
        for (int x = 3; x <= 9; x++) for (int y = 8; y <= 9; y++) b.set(x, y, 2, glass);
        for (int z = 4; z <= 12; z += 2) { b.set(2, 8, z, glass); b.set(2, 9, z, glass); b.set(10, 8, z, glass); b.set(10, 9, z, glass); }
        b.fill(3, 7, 3, 9, 7, 13, dark);                                     // podloga pietra
        b.fill(1, 11, 1, 11, 11, 15, slab);                                  // dach pietra z okapem
        b.shell(1, 12, 1, 11, 12, 15, gray);                                 // attyka
        for (int x = 11; x <= 16; x++) for (int z = 2; z <= 14; z++) {        // taras na parterze
            if (x == 16 || z == 2 || z == 14) b.set(x, 7, z, rail);
        }
        b.fill(11, 7, 2, 16, 7, 2, rail);
        b.fill(12, 6, 3, 15, 6, 13, dark);                                   // deski tarasu
        for (int x = 3; x <= 15; x += 3) b.set(x, 6, 1, lamp);               // lampy pod okapem
        b.fill(8, 2, 1, 10, 2, 1, dark);                                     // wiatka
        b.fill(7, 1, 0, 11, 1, 0, blk("stone_brick_slab"));
        for (int i = 0; i < 6; i++) b.set(1 + i * 3 % 14, 1, 16, blk("oak_leaves"));
        furnishWilla(b);
        return b;
    }

    // =================================================================== 2 ZAMEK
    private static BlockState cm(int x, int y, int z) {
        return alt(x, y, z, 7) ? blk("mossy_stone_bricks") : alt(x, y, z, 5) ? blk("cracked_stone_bricks") : blk("stone_bricks");
    }

    private static void tower(B b, int cx, int cz, int r, int y0, int y1, int roofH) {
        for (int y = y0; y <= y1; y++) {
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    int d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (d2 <= r * r + r && d2 > (r - 1) * (r - 1) + (r - 1)) b.set(x, y, z, cm(x, y, z));
                }
        }
        for (int y = y0 + 7; y <= y1; y += 8) b.disk(cx, cz, y, r - 1, blk("oak_planks"));      // pietra
        // blanki (szczeliny strzelnicze)
        for (int y = y0 + 3; y <= y1 - 2; y += 6) {
            b.carve(cx - r, y, cz, cx - r, y + 1, cz); b.carve(cx + r, y, cz, cx + r, y + 1, cz);
            b.carve(cx, y, cz - r, cx, y + 1, cz - r); b.carve(cx, y, cz + r, cx, y + 1, cz + r);
        }
        // wystajaca korona i dach
        b.ring(cx, cz, y1 + 1, r + 1, blk("stone_bricks"));
        b.disk(cx, cz, y1 + 1, r, blk("stone_bricks"));
        for (int x = cx - r - 1; x <= cx + r + 1; x++)
            for (int z = cz - r - 1; z <= cz + r + 1; z++) {
                int d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                int rr = r + 1;
                if (d2 <= rr * rr + rr && d2 > (rr - 1) * (rr - 1) + (rr - 1) && (x + z) % 2 == 0) b.set(x, y1 + 2, z, cm(x, y1, z));
            }
        BlockState roof = blk("deepslate_tiles");
        b.cone(cx, cz, y1 + 2, r, roof);
        for (int k = 0; k <= roofH; k++) b.set(cx, y1 + 2 + r + 1 + k, cz, blk("oak_fence"));
        b.set(cx + 1, y1 + 2 + r + roofH, cz, blk("red_wool"));
        b.set(cx + 2, y1 + 2 + r + roofH, cz, blk("red_wool"));
        b.set(cx + 1, y1 + 2 + r + roofH - 1, cz, blk("red_wool"));
    }

    private static B zamek() {
        B b = new B(45, 36, 45);
        int cx = 22;
        BlockState cobble = blk("cobblestone"), glass = blk("glass_pane"), bars = blk("iron_bars");
        b.fill(0, 0, 0, 44, 0, 44, blk("grass_block"));
        b.fill(6, 0, 6, 38, 0, 38, blk("cobblestone"));
        b.fill(cx - 1, 0, 6, cx + 1, 0, 38, blk("gravel"));                 // sciezki dziedzinca
        b.fill(6, 0, cx - 1, 38, 0, cx + 1, blk("gravel"));
        for (int z = 0; z <= 5; z++) b.fill(cx - 2, 0, z, cx + 2, 0, z, blk("oak_planks"));  // most do bramy

        // mury oslonowe (grubosc 3) + krenelaz
        for (int x = 6; x <= 38; x++)
            for (int z = 6; z <= 38; z++) {
                int dm = Math.min(Math.min(x - 6, 38 - x), Math.min(z - 6, 38 - z));
                if (dm > 2) continue;
                for (int y = 1; y <= 9; y++) b.set(x, y, z, cm(x, y, z));
                if (dm == 0) { b.set(x, 10, z, cm(x, 10, z)); if ((x + z) % 2 == 0) b.set(x, 11, z, cm(x, 11, z)); }
                if (dm == 2) b.set(x, 10, z, blk("stone_brick_wall"));
            }

        // narozne baszty
        for (int[] c : new int[][]{{6, 6}, {38, 6}, {6, 38}, {38, 38}}) tower(b, c[0], c[1], 4, 1, 17, 4);
        // baszty srodkowe na murach
        for (int[] c : new int[][]{{cx, 38}, {6, cx}, {38, cx}}) tower(b, c[0], c[1], 3, 1, 13, 3);

        // brama (barbakan) w polnocnej scianie
        for (int x = cx - 6; x <= cx + 6; x++) for (int z = 2; z <= 10; z++)
            for (int y = 1; y <= 16; y++) if (x == cx - 6 || x == cx + 6 || z == 2 || z == 10) b.set(x, y, z, cm(x, y, z));
        b.fill(cx - 6, 17, 2, cx + 6, 17, 10, blk("stone_bricks"));
        for (int x = cx - 6; x <= cx + 6; x++) for (int z = 2; z <= 10; z++)
            if ((x == cx - 6 || x == cx + 6 || z == 2 || z == 10) && (x + z) % 2 == 0) b.set(x, 18, z, cm(x, 18, z));
        b.carve(cx - 2, 1, 2, cx + 2, 7, 10);                                // przejazd
        for (int x = cx - 2; x <= cx + 2; x++) { b.set(x, 8, 2, blk("stone_brick_stairs")); }
        for (int x = cx - 2; x <= cx + 2; x++) for (int y = 5; y <= 7; y++) b.set(x, y, 4, bars);   // krata
        b.carve(cx - 1, 1, 6, cx + 1, 3, 6);
        for (int y = 12; y <= 13; y++) { b.set(cx - 4, y, 2, glass); b.set(cx + 4, y, 2, glass); }
        b.set(cx - 3, 9, 1, blk("lantern")); b.set(cx + 3, 9, 1, blk("lantern"));

        // wielka baszta (donjon)
        int kx0 = 15, kx1 = 29, kz0 = 17, kz1 = 31;
        for (int y = 1; y <= 23; y++)
            for (int x = kx0; x <= kx1; x++)
                for (int z = kz0; z <= kz1; z++)
                    if (x == kx0 || x == kx1 || z == kz0 || z == kz1) b.set(x, y, z, cm(x, y, z));
        for (int y : new int[]{8, 15, 22}) b.fill(kx0 + 1, y, kz0 + 1, kx1 - 1, y, kz1 - 1, blk("oak_planks"));
        b.fill(kx0 - 1, 24, kz0 - 1, kx1 + 1, 24, kz1 + 1, blk("stone_bricks"));   // wysuniety krenelaz
        for (int x = kx0 - 1; x <= kx1 + 1; x++) for (int z = kz0 - 1; z <= kz1 + 1; z++)
            if ((x == kx0 - 1 || x == kx1 + 1 || z == kz0 - 1 || z == kz1 + 1) && (x + z) % 2 == 0) b.set(x, 25, z, cm(x, 25, z));
        for (int x : new int[]{18, 22, 26}) for (int y : new int[]{4, 11, 18}) {
            for (int dz : new int[]{kz0, kz1}) { b.set(x, y, dz, glass); b.set(x, y + 1, dz, glass); }
        }
        for (int z : new int[]{21, 24, 27}) for (int y : new int[]{4, 11, 18}) {
            for (int dx : new int[]{kx0, kx1}) { b.set(dx, y, z, glass); b.set(dx, y + 1, z, glass); }
        }
        b.carve(cx - 1, 1, kz0, cx + 1, 4, kz0);                             // drzwi donjona
        b.fill(cx - 3, 12, kz0 - 1, cx - 3, 17, kz0 - 1, blk("red_wool"));   // proporce
        b.fill(cx + 3, 12, kz0 - 1, cx + 3, 17, kz0 - 1, blk("red_wool"));
        // narozne wiezyczki donjonu + wieza centralna
        for (int[] c : new int[][]{{kx0, kz0}, {kx1, kz0}, {kx0, kz1}, {kx1, kz1}}) {
            b.fill(c[0] - 1, 1, c[1] - 1, c[0] + 1, 29, c[1] + 1, blk("stone_bricks"));
            for (int y = 1; y <= 29; y++) b.set(c[0], y, c[1], cm(c[0], y, c[1]));
            b.hip(c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2, 30, "deepslate_tile_stairs", blk("deepslate_tiles"));
            b.set(c[0], 33, c[1], blk("oak_fence")); b.set(c[0], 34, c[1], blk("red_wool"));
        }
        for (int y = 25; y <= 29; y++) b.shell(cx - 2, y, 22, cx + 2, y, 26, blk("stone_bricks"));   // wiezyczka na dachu donjonu
        b.hip(cx - 3, 21, cx + 3, 27, 30, "deepslate_tile_stairs", blk("deepslate_tiles"));
        b.set(cx, 34, 24, blk("oak_fence")); b.set(cx, 35, 24, blk("red_wool"));
        // studnia na dziedzincu
        b.shell(10, 1, 12, 12, 1, 14, cobble);
        for (int[] p : new int[][]{{10, 12}, {12, 12}, {10, 14}, {12, 14}}) { b.fill(p[0], 2, p[1], p[0], 4, p[1], blk("oak_fence")); }
        b.fill(10, 5, 12, 12, 5, 14, blk("oak_planks"));
        b.set(11, 0, 13, blk("cobblestone"));
        furnishZamek(b);
        return b;
    }

    // =================================================================== 3 STATEK
    private static int shipHw(int z, int len) {
        double t = z / (double) (len - 1);
        double sh;
        if (t < 0.22) sh = Math.pow(t / 0.22, 0.8);
        else if (t > 0.88) sh = 0.62 + 0.38 * Math.pow((1 - t) / 0.12, 0.6);
        else sh = 1.0;
        return (int) Math.round(6 * sh);
    }

    private static B statek() {
        int len = 47, cx = 7;
        B b = new B(17, 32, len);
        BlockState dark = blk("dark_oak_planks"), spruce = blk("spruce_planks"), oak = blk("oak_planks");
        BlockState fence = blk("oak_fence"), glassP = blk("glass_pane"), wool = blk("white_wool");
        BlockState lantern = blk("lantern"), bars = blk("iron_bars");
        double[] frac = {0.35, 0.6, 0.8, 0.92, 1.0, 1.0, 1.0};
        for (int z = 0; z < len; z++) {
            int hw = shipHw(z, len);
            for (int y = 0; y <= 6; y++) {
                int hy = (int) Math.round(hw * frac[y]);
                if (hy < 0) continue;
                BlockState side = (y == 4) ? spruce : dark;
                for (int dx = -hy; dx <= hy; dx++) {
                    boolean edge = Math.abs(dx) == hy;
                    if (y == 0 || (y == 1 && !edge)) { b.set(cx + dx, y, z, y == 0 ? dark : oak); continue; }
                    if (edge) b.set(cx + dx, y, z, side);
                    else if (y == 5) b.set(cx + dx, y, z, oak);        // poklad glowny
                }
                if (y == 6) { b.set(cx - hy, 6, z, fence); b.set(cx + hy, 6, z, fence); }
            }
            // porty armatnie
            if (z >= 10 && z <= 38 && z % 4 == 2) {
                int hy = (int) Math.round(hw * frac[3]);
                b.carve(cx - hy, 3, z, cx - hy, 3, z); b.carve(cx + hy, 3, z, cx + hy, 3, z);
                b.set(cx - hy, 3, z, bars); b.set(cx + hy, 3, z, bars);
            }
        }
        // kasztel dziobowy
        for (int z = 1; z <= 9; z++) {
            int hw = shipHw(z, len);
            for (int y = 6; y <= 7; y++) { b.set(cx - hw, y, z, dark); b.set(cx + hw, y, z, dark); }
            b.fill(cx - hw, 8, z, cx + hw, 8, z, oak);
            b.set(cx - hw, 9, z, fence); b.set(cx + hw, 9, z, fence);
        }
        b.set(cx, 9, 0, fence); b.set(cx, 9, 1, fence); b.set(cx, 8, 0, oak);
        // kasztel rufowy (podwyzszony)
        for (int z = 33; z <= 46; z++) {
            int hw = shipHw(z, len);
            for (int y = 6; y <= 9; y++) { b.set(cx - hw, y, z, dark); b.set(cx + hw, y, z, dark); }
            b.fill(cx - hw, 10, z, cx + hw, 10, z, oak);
            b.set(cx - hw, 11, z, fence); b.set(cx + hw, 11, z, fence);
            if (z % 3 == 0) { b.set(cx - hw, 8, z, glassP); b.set(cx + hw, 8, z, glassP); }
        }
        for (int dx = -4; dx <= 4; dx++) for (int y = 6; y <= 11; y++) {   // plaska rufa z oknami
            boolean win = (y == 8 || y == 9) && Math.abs(dx) % 2 == 1;
            b.set(cx + dx, y, 46, win ? glassP : dark);
        }
        b.fill(cx - 4, 12, 46, cx + 4, 12, 46, blk("dark_oak_planks"));
        b.set(cx - 3, 12, 45, lantern); b.set(cx + 3, 12, 45, lantern);
        // maszty, reje i zagle
        int[][] masts = {{12, 6, 26}, {22, 6, 30}, {32, 10, 24}};   // z, y0, wysokosc szczytu
        for (int[] mm : masts) {
            int z = mm[0], y0 = mm[1], top = mm[2];
            for (int y = y0; y <= top; y++) b.set(cx, y, z, blk("oak_log"));
            int[] ys = {top - 2, top - 9, top - 16};
            int[] hl = {5, 6, 7};
            for (int s = 0; s < 3; s++) {
                int y = ys[s];
                if (y < y0 + 3) continue;
                for (int dx = -hl[s]; dx <= hl[s]; dx++) b.set(cx + dx, y, z, axis("oak_log", Direction.Axis.X));
                int low = y - 6;
                if (low < y0 + 2) low = y0 + 2;
                for (int yy = low; yy < y; yy++)
                    for (int dx = -hl[s] + 1; dx <= hl[s] - 1; dx++) {
                        int zz = Math.abs(dx) <= hl[s] - 3 ? z - 2 : z - 1;
                        b.set(cx + dx, yy, zz, wool);
                    }
            }
            b.set(cx + 1, top, z, blk("red_wool")); b.set(cx + 2, top, z, blk("red_wool"));
            b.set(cx, top + 1, z, fence);
        }
        // bukszpryt i galion
        b.set(cx, 7, 3, axis("oak_log", Direction.Axis.Z)); b.set(cx, 8, 2, axis("oak_log", Direction.Axis.Z));
        b.set(cx, 9, 0, blk("gold_block")); b.set(cx, 10, 0, blk("gold_block"));
        furnishStatek(b);
        return b;
    }

    // =================================================================== 4 HOTEL
    private static B hotel() {
        B b = new B(29, 46, 23);
        BlockState conc = blk("white_concrete"), glass = blk("light_blue_stained_glass"), qp = blk("quartz_pillar");
        BlockState slab = blk("smooth_quartz"), dark = blk("gray_concrete"), lamp = blk("sea_lantern");
        BlockState gold = blk("gold_block"), pane = blk("glass_pane"), stone = blk("polished_andesite");
        b.fill(0, 0, 0, 28, 0, 22, stone);
        // podium (lobby, 2 kondygnacje)
        b.fill(2, 1, 4, 26, 1, 20, blk("smooth_stone"));
        for (int y = 2; y <= 6; y++)
            for (int x = 2; x <= 26; x++)
                for (int z = 4; z <= 20; z++) {
                    boolean edge = x == 2 || x == 26 || z == 4 || z == 20;
                    if (!edge) continue;
                    boolean pier = (x - 2) % 4 == 0 || (z - 4) % 4 == 0;
                    b.set(x, y, z, pier ? qp : glass);
                }
        b.fill(2, 7, 4, 26, 7, 20, slab);
        b.carve(12, 2, 4, 16, 5, 4);                                           // wejscie
        b.fill(8, 8, 0, 20, 8, 3, slab);                                       // daszek
        for (int x : new int[]{8, 20}) b.fill(x, 1, 1, x, 7, 1, qp);
        for (int x = 10; x <= 18; x += 4) b.set(x, 7, 2, lamp);
        // wieza (10 pieter po 4)
        int x0 = 5, x1 = 23, z0 = 7, z1 = 17;
        for (int f = 0; f < 10; f++) {
            int y0 = 8 + f * 4;
            b.fill(x0, y0, z0, x1, y0, z1, slab);
            for (int y = y0 + 1; y <= y0 + 4; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        if (!(x == x0 || x == x1 || z == z0 || z == z1)) continue;
                        boolean pier = ((x == x0 || x == x1) && (z - z0) % 3 == 0) || ((z == z0 || z == z1) && (x - x0) % 3 == 0);
                        boolean band = y == y0 + 4;
                        b.set(x, y, z, pier ? qp : band ? conc : glass);
                    }
            // balkony od frontu
            if (f % 2 == 0)
                for (int x = x0 + 3; x <= x1 - 3; x += 3) {
                    b.set(x, y0, z0 - 1, slab); b.set(x + 1, y0, z0 - 1, slab);
                    b.set(x, y0 + 1, z0 - 1, pane); b.set(x + 1, y0 + 1, z0 - 1, pane);
                }
        }
        int topY = 8 + 10 * 4;                                                 // 48 -> poza h
        // korona
        b.fill(x0, 44, z0, x1, 44, z1, conc);
        b.shell(x0, 45, z0, x1, 45, z1, dark);
        for (int x = x0; x <= x1; x += 3) { b.set(x, 45, z0, lamp); b.set(x, 45, z1, lamp); }
        for (int y = 8; y <= 44; y++) { b.set(x0, y, z0, gold); b.set(x1, y, z0, gold); }   // zlote listwy narozne
        furnishHotel(b);
        return b;
    }

    // =================================================================== 5 BLOK RADZIECKI
    private static B blokRadziecki() {
        B b = new B(38, 27, 14);
        BlockState panel = blk("light_gray_concrete"), seam = blk("gray_concrete"), glass = blk("glass");
        BlockState slab = blk("cobblestone"), pane = blk("glass_pane"), brick = blk("bricks");
        BlockState band = blk("brown_concrete"), roofC = blk("gray_concrete"), step = blk("stone_brick_stairs");
        b.fill(0, 0, 0, 37, 0, 13, blk("polished_andesite"));
        for (int f = 0; f <= 5; f++) b.fill(1, 4 * f + 1, 2, 36, 4 * f + 1, 12, slab);
        for (int y = 2; y <= 21; y++)
            for (int x = 1; x <= 36; x++)
                for (int z = 2; z <= 12; z++)
                    if (x == 1 || x == 36 || z == 2 || z == 12) {
                        int f = (y - 2) / 4, r = (y - 2) % 4;
                        BlockState s = panel;
                        if (x % 6 == 0 && (z == 2 || z == 12)) s = seam;                  // spoiny plyt
                        if (r == 3) s = (z == 2 || z == 12) ? band : panel;               // pas miedzy pietrami
                        b.set(x, y, z, s);
                    }
        for (int f = 0; f < 5; f++)
            for (int x = 1; x <= 36; x++)
                if (x % 6 == 2 || x % 6 == 3)
                    for (int y = 4 * f + 3; y <= 4 * f + 4; y++) { b.set(x, y, 2, glass); b.set(x, y, 12, glass); }
        for (int f = 0; f < 5; f++) for (int z : new int[]{5, 9}) for (int y = 4 * f + 3; y <= 4 * f + 4; y++) { b.set(1, y, z, glass); b.set(36, y, z, glass); }
        for (int x0 : new int[]{4, 16, 28}) {                                              // 3 klatki schodowe
            b.carve(x0, 2, 2, x0 + 1, 4, 2);
            b.fill(x0 - 1, 5, 0, x0 + 2, 5, 2, panel);                                      // daszek
            b.fill(x0 - 1, 6, 0, x0 + 2, 6, 0, seam);
            b.set(x0, 1, 1, step); b.set(x0 + 1, 1, 1, step);
            for (int y = 2; y <= 4; y++) { b.set(x0 - 1, y, 1, seam); b.set(x0 + 2, y, 1, seam); }
        }
        for (int f = 1; f < 5; f++)                                                         // przeszklone balkony
            for (int x = 1; x <= 36; x++)
                if (x % 6 == 2 || x % 6 == 3) {
                    b.set(x, 4 * f + 1, 1, panel); b.set(x, 4 * f + 2, 1, pane); b.set(x, 4 * f + 3, 1, pane); b.set(x, 4 * f + 4, 1, pane);
                }
        b.fill(1, 22, 2, 36, 22, 12, roofC);
        b.shell(1, 23, 2, 36, 23, 12, seam);
        for (int cx : new int[]{9, 26}) b.fill(cx, 23, 6, cx + 1, 25, 7, brick);           // kominy
        for (int x : new int[]{14, 22}) { b.fill(x, 23, 7, x, 26, 7, blk("iron_bars")); }   // anteny
        furnishBlok(b);
        return b;
    }

    // =================================================================== 6 WIEZOWIEC RADZIECKI
    private static void tier(B b, int x0, int z0, int x1, int z1, int y0, int y1, BlockState wall, BlockState pil, BlockState glass, int floorEvery, BlockState floor) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (!(x == x0 || x == x1 || z == z0 || z == z1)) continue;
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    int sx = (x == x0 || x == x1) ? z - z0 : x - x0;
                    boolean pier = corner || sx % 4 == 0;
                    boolean win = !pier && (y - y0) % floorEvery >= 1 && (y - y0) % floorEvery <= floorEvery - 2;
                    b.set(x, y, z, pier ? pil : win ? glass : wall);
                }
        for (int y = y0; y <= y1; y += floorEvery) b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, floor);
        b.fill(x0, y1 + 1, z0, x1, y1 + 1, z1, floor);
    }

    private static B stalinka() {
        B b = new B(39, 86, 39);
        int cx = 19;
        BlockState wall = blk("sandstone"), pil = blk("cut_sandstone"), glass = blk("glass_pane"), floor = blk("smooth_stone");
        BlockState gold = blk("gold_block"), lamp = blk("sea_lantern"), brick = blk("stone_bricks");
        b.fill(0, 0, 0, 38, 0, 38, blk("polished_andesite"));
        // skrzydla niskie (z balustradami)
        tier(b, 2, 2, 36, 36, 1, 20, wall, pil, glass, 5, floor);
        b.shell(2, 22, 2, 36, 22, 36, blk("stone_brick_wall"));
        for (int[] c : new int[][]{{2, 2}, {36, 2}, {2, 36}, {36, 36}}) {                  // narozne pawilony
            b.fill(c[0] - 1, 1, c[1] - 1, c[0] + 1, 26, c[1] + 1, wall);
            b.hip(c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2, 27, "stone_brick_stairs", brick);
            b.set(c[0], 30, c[1], blk("iron_bars")); b.set(c[0], 31, c[1], gold);
        }
        // korpus glowny
        tier(b, 8, 8, 30, 30, 21, 44, wall, pil, glass, 6, floor);
        b.shell(8, 46, 8, 30, 46, 30, blk("stone_brick_wall"));
        tier(b, 12, 12, 26, 26, 45, 60, wall, pil, glass, 5, floor);
        b.shell(12, 62, 12, 26, 62, 26, blk("stone_brick_wall"));
        tier(b, 15, 15, 23, 23, 61, 70, wall, pil, glass, 5, floor);
        // zwienczenie
        tier(b, 17, 17, 21, 21, 71, 74, brick, pil, glass, 4, floor);
        b.hip(16, 16, 22, 22, 76, "stone_brick_stairs", brick);
        for (int y = 80; y <= 84; y++) b.set(cx, y, cx, y == 84 ? gold : blk("iron_bars"));
        b.set(cx, 85, cx, blk("gold_block"));
        // wejscie
        b.carve(cx - 2, 1, 2, cx + 2, 6, 2);
        b.fill(cx - 3, 7, 1, cx + 3, 7, 3, pil);
        for (int x : new int[]{cx - 3, cx + 3}) b.fill(x, 1, 1, x, 6, 1, blk("quartz_pillar"));
        for (int y = 8; y <= 20; y++) { b.set(cx, y, 2, lamp); }
        furnishStalinka(b);
        return b;
    }

    // =================================================================== 7 WIEZOWIEC SZKLANY
    private static B szklany() {
        B b = new B(25, 80, 25);
        BlockState glass = blk("light_blue_stained_glass"), mull = blk("light_gray_concrete"), floor = blk("smooth_stone");
        BlockState conc = blk("white_concrete"), lamp = blk("sea_lantern");
        b.fill(0, 0, 0, 24, 0, 24, blk("polished_andesite"));
        // lobby z kolumnami
        for (int y = 1; y <= 6; y++)
            for (int x = 2; x <= 22; x++)
                for (int z = 2; z <= 22; z++)
                    if (x == 2 || x == 22 || z == 2 || z == 22) b.set(x, y, z, ((x + z) % 4 == 0) ? blk("quartz_pillar") : glass);
        b.fill(2, 7, 2, 22, 7, 22, floor);
        b.carve(11, 1, 2, 13, 4, 2);
        for (int x : new int[]{9, 15}) b.fill(x, 1, 0, x, 7, 0, blk("quartz_pillar"));
        b.fill(9, 8, 0, 15, 8, 2, conc);
        // trzy poziomy wiezy z cofnieciami
        int[][] ts = {{2, 22, 8, 44}, {5, 19, 45, 64}, {8, 16, 65, 74}};
        for (int[] t : ts) {
            int a = t[0], c = t[1], y0 = t[2], y1 = t[3];
            for (int y = y0; y <= y1; y++)
                for (int x = a; x <= c; x++)
                    for (int z = a; z <= c; z++) {
                        if (!(x == a || x == c || z == a || z == c)) continue;
                        boolean mullion = (x - a) % 4 == 0 && (z == a || z == c) || (z - a) % 4 == 0 && (x == a || x == c);
                        boolean slabLine = (y - y0) % 4 == 3;
                        b.set(x, y, z, slabLine || mullion ? mull : glass);
                    }
            for (int y = y0 + 3; y <= y1; y += 4) b.fill(a + 1, y, a + 1, c - 1, y, c - 1, floor);
            b.fill(a, y1 + 1, a, c, y1 + 1, c, conc);
        }
        // korona i iglica
        b.shell(8, 76, 8, 16, 76, 16, mull);
        for (int x = 8; x <= 16; x += 2) { b.set(x, 76, 8, lamp); b.set(x, 76, 16, lamp); }
        for (int y = 77; y <= 79; y++) b.set(12, y, 12, blk("iron_block"));
        b.set(12, 79, 12, lamp);
        furnishSzklany(b);
        return b;
    }

    // =================================================================== 8 ART DECO
    private static B artDeco() {
        B b = new B(31, 104, 31);
        BlockState stone = blk("smooth_quartz"), pil = blk("quartz_pillar"), glass = blk("glass"), gold = blk("gold_block");
        BlockState floor = blk("smooth_stone"), dark = blk("gray_concrete"), lamp = blk("sea_lantern"), cut = blk("chiseled_quartz_block");
        b.fill(0, 0, 0, 30, 0, 30, blk("polished_andesite"));
        int[][] tiers = {{1, 29, 1, 42}, {4, 26, 43, 66}, {7, 23, 67, 84}, {10, 20, 85, 96}};
        for (int[] t : tiers) {
            int a = t[0], c = t[1], y0 = t[2], y1 = t[3];
            for (int y = y0; y <= y1; y++)
                for (int x = a; x <= c; x++)
                    for (int z = a; z <= c; z++) {
                        if (!(x == a || x == c || z == a || z == c)) continue;
                        int s = (x == a || x == c) ? z - a : x - a;
                        boolean pier = s % 3 == 0 || (x == a || x == c) && (z == a || z == c);
                        boolean spandrel = (y - y0) % 4 == 3;
                        BlockState st = pier ? pil : spandrel ? stone : glass;
                        if (spandrel && s % 3 == 1) st = cut;
                        b.set(x, y, z, st);
                    }
            for (int y = y0; y <= y1; y += 4) b.fill(a + 1, y, a + 1, c - 1, y, c - 1, floor);
            // zlote zwienczenie kondygnacji + cofniecia
            for (int x = a; x <= c; x++) for (int z = a; z <= c; z++)
                if (x == a || x == c || z == a || z == c) { b.set(x, y1 + 1, z, gold); if ((x + z) % 2 == 0) b.set(x, y1 + 2, z, stone); }
            b.fill(a + 1, y1 + 1, a + 1, c - 1, y1 + 1, c - 1, dark);
        }
        // iglica schodkowa
        int c = 15;
        for (int k = 0; k < 4; k++) { int r = 3 - k; b.fill(c - r, 98 + k, c - r, c + r, 98 + k, c + r, k % 2 == 0 ? gold : stone); }
        for (int y = 102; y <= 103; y++) b.set(c, y, c, blk("iron_bars"));
        // wejscie i lampy
        b.carve(c - 2, 1, 1, c + 2, 6, 1);
        for (int x : new int[]{c - 3, c + 3}) b.fill(x, 1, 0, x, 7, 0, pil);
        b.fill(c - 3, 8, 0, c + 3, 8, 1, gold);
        for (int y = 9; y <= 40; y += 4) { b.set(c, y, 1, lamp); }
        furnishArtDeco(b);
        return b;
    }

    // =================================================================== 9 BIUROWIEC
    private static B biurowiec() {
        B b = new B(37, 32, 25);
        BlockState glass = blk("gray_stained_glass"), band = blk("gray_concrete"), conc = blk("white_concrete");
        BlockState floor = blk("smooth_stone"), lamp = blk("sea_lantern"), pil = blk("quartz_pillar");
        b.fill(0, 0, 0, 36, 0, 24, blk("polished_andesite"));
        for (int y = 1; y <= 26; y++)
            for (int x = 3; x <= 33; x++)
                for (int z = 3; z <= 21; z++) {
                    if (!(x == 3 || x == 33 || z == 3 || z == 21)) continue;
                    boolean ribbon = (y - 1) % 4 >= 1 && (y - 1) % 4 <= 2;
                    boolean pier = (x - 3) % 6 == 0 && (z == 3 || z == 21);
                    b.set(x, y, z, pier ? pil : ribbon ? glass : band);
                }
        for (int y = 1; y <= 25; y += 4) b.fill(4, y, 4, 32, y, 20, floor);
        b.fill(3, 27, 3, 33, 27, 21, conc);
        b.shell(3, 28, 3, 33, 28, 21, band);
        b.fill(12, 28, 8, 24, 31, 16, blk("light_gray_concrete"));                      // maszynownia
        for (int x = 13; x <= 23; x += 2) b.set(x, 30, 8, blk("iron_bars"));
        // atrium wejsciowe z daszkiem
        b.carve(15, 1, 3, 21, 5, 3);
        b.fill(12, 6, 0, 24, 6, 3, conc);
        for (int x : new int[]{12, 24}) b.fill(x, 1, 0, x, 5, 0, pil);
        for (int x = 14; x <= 22; x += 4) b.set(x, 5, 1, lamp);
        // lewe skrzydlo z tarasem
        b.fill(3, 17, 22, 12, 17, 24, floor);
        b.shell(3, 18, 22, 12, 18, 24, blk("glass_pane"));
        furnishBiurowiec(b);
        return b;
    }

    // =================================================================== 10 BANK
    private static B bank() {
        B b = new B(31, 24, 29);
        BlockState stone = blk("stone_bricks"), quartz = blk("quartz_block"), pil = blk("quartz_pillar");
        BlockState floor = blk("polished_andesite"), glass = blk("glass_pane"), iron = blk("iron_block"), gold = blk("gold_block");
        BlockState lamp = blk("sea_lantern");
        b.fill(0, 0, 0, 30, 0, 28, stone);
        b.fill(0, 1, 3, 30, 1, 28, stone);
        for (int s = 0; s < 3; s++) for (int x = 8; x <= 22; x++) b.set(x, s, 2 - s, stair("quartz_stairs", Direction.SOUTH, false));
        b.fill(8, 0, 2, 22, 0, 2, stone); b.fill(8, 0, 1, 22, 0, 1, stone);
        b.fill(3, 2, 7, 27, 2, 27, floor);
        b.fill(3, 2, 4, 27, 2, 6, quartz);                                               // portyk
        b.shell(3, 3, 7, 27, 12, 26, quartz);                                            // hol
        for (int x : new int[]{5, 8, 11, 19, 22, 25}) for (int y = 5; y <= 9; y++) b.set(x, y, 7, glass);
        b.carve(13, 3, 7, 17, 7, 7);
        for (int x = 12; x <= 18; x++) b.set(x, 8, 7, stair("quartz_stairs", Direction.SOUTH, true));
        for (int x : new int[]{4, 8, 12, 18, 22, 26}) b.fill(x, 3, 5, x, 13, 5, pil);     // kolumny portyku
        b.fill(2, 14, 4, 28, 14, 27, quartz);                                             // belkowanie
        b.fill(2, 15, 4, 28, 15, 27, stone);
        for (int k = 0; k <= 6; k++) b.fill(2 + 2 * k, 16 + k, 4, 28 - 2 * k, 16 + k, 5, k == 6 ? gold : quartz);   // fronton
        b.fill(3, 16, 6, 27, 16, 26, blk("smooth_stone"));
        // skarbiec
        b.shell(18, 3, 18, 26, 9, 26, iron); b.fill(18, 10, 18, 26, 10, 26, iron);
        b.carve(19, 3, 19, 25, 9, 25);
        b.fill(20, 3, 20, 24, 5, 24, gold);
        b.carve(21, 3, 18, 23, 6, 18);
        for (int x : new int[]{21, 23}) b.set(x, 7, 18, blk("iron_bars"));
        // lampy w holu
        for (int x = 8; x <= 24; x += 4) b.set(x, 12, 12, lamp);
        furnishBank(b);
        return b;
    }

    // =================================================================== 11 KOSCIOL
    private static B kosciol() {
        B b = new B(21, 40, 45);
        BlockState stone = blk("stone_bricks"), dark = blk("deepslate_bricks"), pil = blk("stone_brick_wall");
        BlockState floor = blk("polished_andesite"), roofS = blk("deepslate_tile_stairs");
        BlockState g1 = blk("light_blue_stained_glass"), g2 = blk("yellow_stained_glass"), g3 = blk("red_stained_glass");
        BlockState gold = blk("gold_block"), lamp = blk("lantern");
        b.fill(0, 0, 0, 20, 0, 44, blk("grass_block"));
        b.fill(4, 0, 10, 16, 0, 40, floor);
        // nawa
        for (int y = 1; y <= 13; y++)
            for (int x = 4; x <= 16; x++)
                for (int z = 10; z <= 40; z++)
                    if (x == 4 || x == 16 || z == 10 || z == 40) b.set(x, y, z, cm(x, y, z));
        // ostrolukowe okna (witraze) i przypory
        for (int z = 14; z <= 36; z += 4) {
            BlockState g = ((z / 4) % 3 == 0) ? g1 : ((z / 4) % 3 == 1) ? g2 : g3;
            for (int xx : new int[]{4, 16}) {
                for (int y = 4; y <= 9; y++) b.set(xx, y, z, g);
                b.set(xx, 10, z, g);
                int dxo = xx == 4 ? -1 : 1;
                for (int y = 1; y <= 11; y++) b.set(xx + dxo, y, z + 2, blk("stone_bricks"));       // przypory
                b.set(xx + dxo * 2, 1, z + 2, blk("stone_bricks")); b.set(xx + dxo * 2, 2, z + 2, blk("stone_bricks"));
            }
        }
        b.gable(3, 9, 17, 41, 14, "deepslate_tile_stairs", blk("deepslate_tiles"), stone, false);
        // wieza zachodnia
        for (int y = 1; y <= 28; y++)
            for (int x = 6; x <= 14; x++)
                for (int z = 0; z <= 9; z++)
                    if (x == 6 || x == 14 || z == 0 || z == 9) b.set(x, y, z, cm(x, y, z));
        for (int y : new int[]{9, 18}) b.fill(7, y, 1, 13, y, 8, blk("oak_planks"));
        b.carve(9, 1, 0, 11, 5, 0);                                                     // portal
        for (int x = 9; x <= 11; x++) b.set(x, 6, 0, stair("stone_brick_stairs", Direction.SOUTH, true));
        for (int y = 12; y <= 15; y++) { b.set(10, y, 0, g1); b.set(10, y, 9, g1); }     // rozeta
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++)
            if (dx * dx + dy * dy <= 5 && !(dx == 0 && dy == 0)) b.set(10 + dx, 15 + dy, 0, ((dx + dy) % 2 == 0) ? g2 : g3);
        for (int y = 22; y <= 26; y++) {                                              // otwory dzwonnicy
            for (int x : new int[]{8, 10, 12}) { b.carve(x, y, 0, x, y, 0); b.carve(x, y, 9, x, y, 9); }
            for (int z : new int[]{3, 5, 7}) { b.carve(6, y, z, 6, y, z); b.carve(14, y, z, 14, y, z); }
        }
        b.fill(5, 29, 0, 15, 29, 10, dark);
        for (int x = 5; x <= 15; x++) for (int z = 0; z <= 10; z++)
            if ((x == 5 || x == 15 || z == 0 || z == 10) && (x + z) % 2 == 0) b.set(x, 30, z, stone);
        b.hip(6, 0, 14, 9, 30, "deepslate_tile_stairs", blk("deepslate_tiles"));
        for (int y = 35; y <= 38; y++) b.set(10, y, 4, y == 38 ? gold : blk("iron_bars"));
        b.set(9, 37, 4, gold); b.set(11, 37, 4, gold);
        // apsyda
        for (int y = 1; y <= 12; y++) b.cylinder(10, 40, y, y, 6, cm(10, y, 40));
        b.carve(4, 1, 40, 16, 12, 40);
        b.cone(10, 40, 13, 6, blk("deepslate_tiles"));
        // wnetrze
        for (int z = 14; z <= 36; z += 4) { b.set(5, 12, z, lamp); b.set(15, 12, z, lamp); }
        b.fill(9, 1, 38, 11, 1, 40, blk("quartz_block"));     // oltarz
        b.set(10, 2, 39, gold);
        furnishKosciol(b);
        return b;
    }

    // =================================================================== 12 WIATRAK
    private static B wiatrak() {
        B b = new B(23, 34, 23);
        int c = 11;
        BlockState brick = blk("bricks"), white = blk("white_concrete"), wood = blk("dark_oak_planks"), sail = blk("white_wool");
        BlockState oak = blk("oak_planks"), fence = blk("oak_fence");
        b.fill(0, 0, 0, 22, 0, 22, blk("grass_block"));
        b.disk(c, c, 0, 6, blk("stone_bricks"));
        for (int y = 1; y <= 18; y++) {
            int r = y <= 8 ? 5 : y <= 14 ? 4 : 3;
            b.ring(c, c, y, r, y <= 5 ? brick : white);
            if (y % 6 == 0) b.disk(c, c, y, r - 1, oak);                                      // pietra
        }
        b.ring(c, c, 9, 5, blk("stone_brick_wall"));
        b.carve(c, 1, c - 5, c, 3, c - 5);                                                    // drzwi
        for (int y : new int[]{7, 12}) { b.set(c - 4, y, c, blk("glass_pane")); b.set(c + 4, y, c, blk("glass_pane")); b.carve(c, y, c + 4, c, y, c + 4); b.set(c, y, c + 4, blk("glass_pane")); }
        // czapa
        b.disk(c, c, 19, 4, wood);
        b.cone(c, c, 20, 4, wood);
        b.set(c, 25, c, blk("oak_fence")); b.set(c, 26, c, blk("lightning_rod"));
        // piasta i skrzydla (plaszczyzna X-Y z przodu, z = c-6)
        int hubY = 21, zf = c - 5 - 1;
        b.fill(c - 1, hubY - 1, zf, c + 1, hubY + 1, zf, wood);
        b.set(c, hubY, zf - 1, blk("dark_oak_log"));
        for (int i = 2; i <= 10; i++) {
            b.set(c + i, hubY, zf, oak); b.set(c - i, hubY, zf, oak);
            b.set(c, hubY + i, zf, oak); b.set(c, hubY - i, zf, oak);
            if (i >= 3) {
                b.set(c + i, hubY + 1, zf, sail); b.set(c + i, hubY + 2, zf, sail);
                b.set(c - i, hubY - 1, zf, sail); b.set(c - i, hubY - 2, zf, sail);
                b.set(c - 1, hubY + i, zf, sail); b.set(c - 2, hubY + i, zf, sail);
                b.set(c + 1, hubY - i, zf, sail); b.set(c + 2, hubY - i, zf, sail);
            }
        }
        b.fill(c - 3, 19, c + 4, c + 3, 19, c + 4, wood);                                     // ogon (stabilizator)
        for (int i = 0; i < 4; i++) b.set(c, 19, c + 5 + i, fence);
        furnishWiatrak(b);
        return b;
    }

    // =================================================================== 13 LATARNIA
    private static B latarnia() {
        B b = new B(17, 40, 17);
        int c = 8;
        BlockState white = blk("white_concrete"), red = blk("red_concrete"), stone = blk("stone_bricks");
        BlockState plank = blk("dark_oak_planks"), fence = blk("oak_fence"), glass = blk("glass"), lamp = blk("sea_lantern");
        BlockState roof = blk("deepslate_tiles");
        b.fill(0, 0, 0, 16, 0, 16, blk("grass_block"));
        b.disk(c, c, 0, 6, stone);
        b.disk(c, c, 1, 5, stone);
        for (int y = 2; y <= 27; y++) {
            int r = y <= 10 ? 4 : y <= 20 ? 3 : 3;
            if (y > 10) r = 3;
            b.ring(c, c, y, r, ((y - 2) / 4) % 2 == 0 ? white : red);
            if ((y - 2) % 7 == 6) b.disk(c, c, y, r - 1, plank);
        }
        for (int y : new int[]{6, 12, 18}) {                                                  // okna
            b.carve(c, y, c - 4 + (y > 10 ? 1 : 0), c, y + 1, c - 4 + (y > 10 ? 1 : 0));
        }
        b.carve(c, 2, c - 4, c, 3, c - 4);                                                     // drzwi
        // galeria
        b.disk(c, c, 28, 5, plank);
        b.ring(c, c, 29, 5, fence);
        for (int a = 0; a < 8; a++) { int ox = (int) Math.round(5 * Math.cos(a * Math.PI / 4)), oz = (int) Math.round(5 * Math.sin(a * Math.PI / 4)); b.set(c + ox, 30, c + oz, fence); }
        // latarnia
        for (int y = 29; y <= 33; y++) b.ring(c, c, y, 2, glass);
        b.set(c, 30, c, lamp); b.set(c, 31, c, lamp); b.set(c, 32, c, lamp);
        b.disk(c, c, 34, 3, roof);
        b.cone(c, c, 35, 2, roof);
        b.set(c, 38, c, blk("iron_block")); b.set(c, 39, c, blk("lightning_rod"));
        // domek latarnika
        b.fill(11, 1, 3, 15, 1, 8, blk("oak_planks"));
        b.shell(11, 2, 3, 15, 5, 8, white);
        for (int y = 2; y <= 5; y++) for (int[] p : new int[][]{{11, 3}, {15, 3}, {11, 8}, {15, 8}}) b.set(p[0], y, p[1], blk("dark_oak_log"));
        b.carve(11, 2, 6, 11, 3, 6);
        b.set(13, 3, 3, blk("glass_pane")); b.set(15, 3, 5, blk("glass_pane")); b.set(13, 3, 8, blk("glass_pane"));
        b.gable(10, 2, 16, 9, 6, "spruce_stairs", blk("spruce_planks"), white, false);
        furnishLatarnia(b);
        return b;
    }

    // =================================================================== 14 MOST
    private static B most() {
        B b = new B(65, 20, 11);
        BlockState stone = blk("stone_bricks"), wall = blk("stone_brick_wall"), road = blk("gravel"), lantern = blk("lantern");
        int deck = 12;
        b.fill(0, 0, 0, 64, 0, 10, blk("polished_andesite"));
        for (int x = 0; x <= 64; x++) for (int y = 1; y <= deck; y++) for (int z = 2; z <= 8; z++) b.set(x, y, z, cm(x, y, z));
        // trzy luki (polokragle) i przyczolki
        int[] centers = {12, 32, 52};
        for (int xc : centers)
            for (int x = xc - 9; x <= xc + 9; x++)
                for (int y = 1; y <= 11; y++) {
                    double d = Math.hypot(x - xc, y - 1);
                    if (d < 9.0) for (int z = 2; z <= 8; z++) b.carve(x, y, z, x, y, z);
                }
        // opaska luku (rzad ciemniejszych cegiel)
        for (int xc : centers)
            for (int x = xc - 10; x <= xc + 10; x++)
                for (int y = 1; y <= 12; y++) {
                    double d = Math.hypot(x - xc, y - 1);
                    if (d >= 9.0 && d < 10.2) { b.set(x, y, 2, blk("deepslate_bricks")); b.set(x, y, 8, blk("deepslate_bricks")); }
                }
        // jezdnia i balustrady
        b.fill(0, deck + 1, 2, 64, deck + 1, 8, stone);
        b.fill(0, deck + 1, 4, 64, deck + 1, 6, road);
        for (int x = 0; x <= 64; x++) { b.set(x, deck + 2, 2, wall); b.set(x, deck + 2, 8, wall); b.set(x, deck + 3, 2, wall); }
        for (int x = 0; x <= 64; x += 8) for (int z : new int[]{2, 8}) { b.fill(x, deck + 2, z, x, deck + 5, z, wall); b.set(x, deck + 6, z, lantern); }
        for (int x = 4; x <= 60; x += 8) for (int z : new int[]{2, 8}) b.set(x, deck + 4, z, wall);
        // podjazdy
        for (int z = 3; z <= 7; z++) { b.set(0, deck + 2, z, stone); b.set(64, deck + 2, z, stone); }
        return b;
    }

    // =================================================================== 15 PIRAMIDA
    private static B piramida() {
        B b = new B(31, 20, 31);
        BlockState a = blk("sandstone"), c = blk("cut_sandstone"), s = blk("smooth_sandstone"), gold = blk("gold_block");
        b.fill(0, 0, 0, 30, 0, 30, blk("sand"));
        int n = 29;
        for (int y = 0; y < 15; y++) {
            int lo = 1 + y, hi = 29 - y;
            if (lo > hi) break;
            for (int x = lo; x <= hi; x++) for (int z = lo; z <= hi; z++) {
                BlockState st = (x == lo || x == hi || z == lo || z == hi) ? (y % 2 == 0 ? c : s) : a;
                b.set(x, y + 1, z, st);
            }
        }
        b.set(15, 16, 15, gold); b.set(15, 17, 15, gold);
        // wejscie, korytarz i komora
        b.carve(14, 1, 1, 16, 3, 14);
        for (int y = 1; y <= 3; y++) { b.set(13, y, 3, blk("stone_bricks")); }
        b.carve(10, 1, 11, 20, 6, 21);
        b.fill(10, 1, 11, 20, 1, 21, blk("polished_andesite"));
        b.fill(13, 2, 14, 17, 2, 18, gold);
        b.set(15, 3, 16, blk("gold_block"));
        for (int[] p : new int[][]{{10, 11}, {20, 11}, {10, 21}, {20, 21}}) for (int y = 2; y <= 6; y++) b.set(p[0], y, p[1], blk("quartz_pillar"));
        b.set(12, 4, 12, blk("lantern")); b.set(18, 4, 20, blk("lantern"));
        for (int x : new int[]{12, 18}) for (int y = 1; y <= 4; y++) { b.set(x, y, 0, blk("sandstone_wall")); }
        furnishPiramida(b);
        return b;
    }

    // =================================================================== 16 WIEZA ZEGAROWA
    private static B wiezaZegarowa() {
        B b = new B(15, 42, 15);
        BlockState brick = blk("bricks"), stone = blk("stone_bricks"), white = blk("quartz_block"), black = blk("black_concrete");
        BlockState glass = blk("glass_pane"), gold = blk("gold_block");
        b.fill(0, 0, 0, 14, 0, 14, blk("polished_andesite"));
        for (int y = 1; y <= 22; y++)
            for (int x = 2; x <= 12; x++)
                for (int z = 2; z <= 12; z++)
                    if (x == 2 || x == 12 || z == 2 || z == 12) {
                        boolean corner = (x == 2 || x == 12) && (z == 2 || z == 12);
                        b.set(x, y, z, corner ? stone : brick);
                    }
        for (int y = 4; y <= 20; y += 5) for (int z : new int[]{2, 12}) { b.set(7, y, z, glass); b.set(7, y + 1, z, glass); b.set(2, y, 7, glass); b.set(12, y, 7, glass); b.set(2, y + 1, 7, glass); b.set(12, y + 1, 7, glass); }
        b.carve(6, 1, 2, 8, 4, 2);
        for (int x = 6; x <= 8; x++) b.set(x, 5, 2, stair("stone_brick_stairs", Direction.SOUTH, true));
        b.fill(1, 23, 1, 13, 23, 13, stone);                                              // gzyms
        // cyferblatowa kondygnacja
        for (int y = 24; y <= 33; y++)
            for (int x = 2; x <= 12; x++)
                for (int z = 2; z <= 12; z++)
                    if (x == 2 || x == 12 || z == 2 || z == 12) b.set(x, y, z, stone);
        for (int face = 0; face < 4; face++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = -3; dy <= 3; dy++) {
                    int d2 = dx * dx + dy * dy;
                    if (d2 > 11) continue;
                    int x, z;
                    x = face == 0 ? 7 + dx : face == 1 ? 12 : face == 2 ? 7 - dx : 2;
                    z = face == 0 ? 2 : face == 1 ? 7 + dx : face == 2 ? 12 : 7 - dx;
                    boolean hand = (dx == 0 && dy >= 0 && dy <= 2) || (dy == 0 && dx >= 0 && dx <= 1) || d2 >= 9 && (dx % 2 == 0 && dy % 2 == 0 || d2 == 9 && (dx == 0 || dy == 0));
                    b.set(x, 29 + dy, z, hand ? black : white);
                }
        b.fill(1, 34, 1, 13, 34, 13, stone);
        for (int x = 1; x <= 13; x++) for (int z = 1; z <= 13; z++)
            if ((x == 1 || x == 13 || z == 1 || z == 13) && (x + z) % 2 == 0) b.set(x, 35, z, stone);
        b.hip(2, 2, 12, 12, 35, "deepslate_tile_stairs", blk("deepslate_tiles"));
        for (int y = 40; y <= 41; y++) b.set(7, y, 7, y == 41 ? gold : blk("iron_bars"));
        for (int[] p : new int[][]{{2, 2}, {12, 2}, {2, 12}, {12, 12}}) for (int y = 36; y <= 37; y++) b.set(p[0], y, p[1], blk("iron_bars"));
        furnishWieza(b);
        return b;
    }

    // =================================================================== 17 STODOLA
    private static B stodola() {
        B b = new B(21, 17, 29);
        BlockState red = blk("red_terracotta"), white = blk("white_concrete"), wood = blk("dark_oak_planks");
        BlockState hay = blk("hay_block"), glassP = blk("glass_pane"), stone = blk("cobblestone");
        b.fill(0, 0, 0, 20, 0, 28, blk("dirt"));
        b.fill(2, 0, 2, 18, 0, 26, blk("coarse_dirt"));
        b.shell(2, 1, 2, 18, 1, 26, stone);
        b.shell(2, 2, 2, 18, 8, 26, red);
        for (int y = 1; y <= 8; y++) for (int[] p : new int[][]{{2, 2}, {18, 2}, {2, 26}, {18, 26}}) b.set(p[0], y, p[1], white);
        for (int z = 6; z <= 22; z += 4) for (int y = 1; y <= 8; y++) { b.set(2, y, z, white); b.set(18, y, z, white); }
        for (int z = 4; z <= 24; z += 4) { b.set(2, 4, z, glassP); b.set(2, 5, z, glassP); b.set(18, 4, z, glassP); b.set(18, 5, z, glassP); }
        // wrota z krzyzami
        b.carve(7, 1, 2, 13, 7, 2);
        for (int y = 1; y <= 7; y++) for (int x = 7; x <= 13; x++) {
            boolean frame = x == 7 || x == 13 || y == 1 || y == 7 || x == 10;
            boolean cross = Math.abs((x - 7) - (y - 1)) == 0 || Math.abs((13 - x) - (y - 1)) == 0;
            b.set(x, y, 2, frame || cross ? white : wood);
        }
        b.gable(1, 1, 19, 27, 9, "dark_oak_stairs", blk("dark_oak_planks"), red, false);
        b.carve(9, 10, 2, 11, 11, 2); b.fill(9, 10, 2, 11, 11, 2, hay);
        b.set(10, 12, 2, glassP);
        // lukarna na kalenicy
        b.fill(9, 15, 12, 11, 16, 14, white);
        b.set(10, 16, 13, glassP);
        b.fill(8, 17 - 1, 11, 12, 17 - 1, 15, wood);
        // snopki
        for (int[] p : new int[][]{{4, 1, 4}, {5, 1, 4}, {4, 2, 4}, {16, 1, 20}, {15, 1, 20}}) b.set(p[0], p[1], p[2], hay);
        furnishStodola(b);
        return b;
    }

    // =================================================================== 18 MUR Z BASZTAMI
    private static B mur() {
        B b = new B(41, 24, 11);
        BlockState cobble = blk("cobblestone");
        b.fill(0, 0, 0, 40, 0, 10, blk("grass_block"));
        for (int x = 7; x <= 33; x++)
            for (int z = 3; z <= 7; z++) {
                int dz = Math.min(z - 3, 7 - z);
                if (dz <= 1) for (int y = 1; y <= 10; y++) b.set(x, y, z, cm(x, y, z));
                else for (int y = 1; y <= 9; y++) b.set(x, y, z, cm(x, y, z));
                if (z == 3 || z == 7) { b.set(x, 11, z, cm(x, 11, z)); if (x % 2 == 0) b.set(x, 12, z, cm(x, 12, z)); }
            }
        for (int x : new int[]{12, 20, 28}) for (int y : new int[]{4, 7}) { b.carve(x, y, 3, x, y + 1, 3); }
        // brama
        b.carve(18, 1, 3, 22, 7, 7);
        for (int x = 17; x <= 23; x++) b.set(x, 8, 3, stair("stone_brick_stairs", Direction.SOUTH, true));
        for (int x = 18; x <= 22; x++) for (int y = 5; y <= 7; y++) b.set(x, y, 4, blk("iron_bars"));
        b.fill(16, 11, 2, 24, 11, 8, blk("stone_bricks"));
        // baszty
        for (int[] t : new int[][]{{3, 5}, {37, 5}}) {
            tower(b, t[0], t[1], 3, 1, 17, 3);
        }
        for (int x = 8; x <= 32; x += 12) { b.set(x, 13, 5, blk("lantern")); }
        furnishMur(b);
        return b;
    }

    // =================================================================== 19 KAMIENICA
    private static B kamienica() {
        B b = new B(19, 30, 17);
        BlockState wall = blk("smooth_sandstone"), trim = blk("quartz_block"), glass = blk("glass_pane"), pil = blk("quartz_pillar");
        BlockState bars = blk("iron_bars"), floor = blk("oak_planks"), dark = blk("gray_concrete");
        b.fill(0, 0, 0, 18, 0, 16, blk("polished_andesite"));
        int x0 = 2, x1 = 16, z0 = 2, z1 = 14;
        for (int y = 1; y <= 24; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++)
                    if (x == x0 || x == x1 || z == z0 || z == z1) b.set(x, y, z, wall);
        for (int y = 1; y <= 24; y += 5) b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, floor);
        // parter: witryna sklepowa i wejscie
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int y = 2; y <= 4; y++) b.set(x, y, z0, (x - x0) % 4 == 0 ? pil : glass);
        b.carve(8, 1, z0, 10, 4, z0);
        b.fill(x0, 5, z0 - 1, x1, 5, z0, trim);                                         // gzyms nad parterem
        // pietra z oknami, pilastrami i balkonami
        for (int f = 1; f <= 3; f++) {
            int y0 = 1 + f * 5;
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                boolean win = (x - x0) % 3 == 1;
                for (int y = y0 + 1; y <= y0 + 3; y++) b.set(x, y, z0, win ? ((y == y0 + 3) ? trim : glass) : (((x - x0) % 3 == 0) ? pil : wall));
                if (win) { b.set(x, y0 + 4, z0, trim); }
            }
            if (f % 2 == 1) for (int x = x0 + 4; x <= x1 - 4; x++) { b.set(x, y0, z0 - 1, trim); b.set(x, y0 + 1, z0 - 1, bars); }
            for (int z = z0 + 2; z <= z1 - 2; z += 3) for (int y = y0 + 1; y <= y0 + 2; y++) { b.set(x0, y, z, glass); b.set(x1, y, z, glass); }
        }
        // gzyms koronujacy i dach mansardowy
        b.fill(x0 - 1, 21, z0 - 1, x1 + 1, 21, z1 + 1, trim);
        b.gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 22, "stone_brick_stairs", dark, wall, true);
        for (int x : new int[]{6, 12}) { b.set(x, 22, z0 - 1, trim); b.fill(x, 23, z0, x, 24, z0, glass); b.set(x, 25, z0 - 1, trim); }
        b.fill(14, 22, 11, 15, 28, 12, blk("bricks"));
        furnishKamienica(b);
        return b;
    }

    // =================================================================== POMOCNICZE (wyposazenie)
    static String dn(Direction d) {
        return switch (d) {
            case NORTH -> "north"; case SOUTH -> "south"; case EAST -> "east"; case WEST -> "west";
            case UP -> "up"; default -> "down";
        };
    }

    /** Blok z ustawionymi wlasciwosciami (pary nazwa, wartosc). */
    static BlockState st(String id, String... kv) {
        BlockState s = blk(id);
        for (int i = 0; i + 1 < kv.length; i += 2) s = Props.set(s, kv[i], kv[i + 1]);
        return s;
    }

    static void door(B b, int x, int y, int z, String id, Direction f, boolean hingeRight, boolean open) {
        String h = hingeRight ? "right" : "left", o = open ? "true" : "false";
        b.set(x, y, z, st(id, "facing", dn(f), "half", "lower", "hinge", h, "open", o));
        b.set(x, y + 1, z, st(id, "facing", dn(f), "half", "upper", "hinge", h, "open", o));
    }

    /** Lozko: stopa w (x,y,z), glowa o jedno pole dalej w kierunku f. */
    static void bed(B b, int x, int y, int z, String color, Direction f) {
        b.set(x, y, z, st(color + "_bed", "facing", dn(f), "part", "foot"));
        b.set(x + f.getStepX(), y, z + f.getStepZ(), st(color + "_bed", "facing", dn(f), "part", "head"));
    }

    static void chest(B b, int x, int y, int z, Direction f) { b.set(x, y, z, st("chest", "facing", dn(f))); }
    static void furnace(B b, int x, int y, int z, String id, Direction f) { b.set(x, y, z, st(id, "facing", dn(f))); }
    static void facing(B b, int x, int y, int z, String id, Direction f) { b.set(x, y, z, st(id, "facing", dn(f))); }

    static void ladder(B b, int x, int y0, int y1, int z, Direction f) {
        for (int y = y0; y <= y1; y++) b.set(x, y, z, st("ladder", "facing", dn(f)));
    }

    static void trapdoor(B b, int x, int y, int z, String id, Direction f, boolean top, boolean open) {
        b.set(x, y, z, st(id, "facing", dn(f), "half", top ? "top" : "bottom", "open", open ? "true" : "false"));
    }

    static void lever(B b, int x, int y, int z, String face, Direction f, boolean powered) {
        b.set(x, y, z, st("lever", "face", face, "facing", dn(f), "powered", powered ? "true" : "false"));
    }

    static void button(B b, int x, int y, int z, String id, String face, Direction f) {
        b.set(x, y, z, st(id, "face", face, "facing", dn(f)));
    }

    static void gate(B b, int x, int y, int z, String id, Direction f, boolean open) {
        b.set(x, y, z, st(id, "facing", dn(f), "open", open ? "true" : "false"));
    }

    static void table(B b, int x, int y, int z, String fence, String plate) {
        b.set(x, y, z, blk(fence));
        b.set(x, y + 1, z, blk(plate));
    }

    static void chair(B b, int x, int y, int z, Direction f) { b.set(x, y, z, stair("oak_stairs", f, false)); }

    static void candle(B b, int x, int y, int z, String color, int n) {
        b.set(x, y, z, st(color + "_candle", "candles", String.valueOf(n)));
    }

    static void hangingLantern(B b, int x, int y, int z) { b.set(x, y, z, st("lantern", "hanging", "true")); }

    /** Biegi schodow (stopnie rosna o 1 na kazde pole) od poziomu plyty s do plyty top; dir: EAST lub WEST. */
    static void flight(B b, int x, int z, int s, int top, Direction dir, String stairId) {
        int h = top - s, sx = dir == Direction.EAST ? 1 : -1;
        for (int i = 0; i < h; i++) {
            int xx = x + sx * i;
            b.carve(xx, s + 2 + i, z, xx, s + 3 + i, z);                       // wolna przestrzen nad stopniem
            b.set(xx, s + 1 + i, z, stair(stairId, dir, false));
        }
    }

    /** Klatka schodowa zygzakiem miedzy plytami s0, s0+hh, ...; x,z = narozne pole, szerokosc 3, dlugosc hh+2. */
    static void stairCore(B b, int x, int z, int s0, int flights, int hh, String stairId) {
        for (int k = 0; k < flights; k++) {
            int s = s0 + k * hh;
            if (k % 2 == 0) flight(b, x, z, s, s + hh, Direction.EAST, stairId);
            else flight(b, x + hh + 1, z + 2, s, s + hh, Direction.WEST, stairId);
        }
    }

    /** Pietro biurowe: biurka (fence + plyta), krzesla, lampy; w obszarze x0..x1, z0..z1 na poziomie y. */
    static void office(B b, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0 + 1; x <= x1 - 1; x += 3)
            for (int z = z0 + 1; z <= z1 - 1; z += 4) {
                b.set(x, y, z, blk("spruce_trapdoor"));
                b.set(x + 1, y, z, blk("spruce_trapdoor"));
                chair(b, x, y, z + 1, Direction.NORTH);
                b.set(x, y + 1, z, blk("light_gray_carpet"));
                b.set(x + 1, y + 1, z, blk("lantern"));
            }
        for (int x = x0 + 1; x <= x1 - 1; x += 6) b.set(x, y + 2, z0, blk("glowstone"));
    }

    // =================================================================== WYPOSAZENIE BUDYNKOW
    /** Kolejne klatki schodowe po liscie poziomow plyt (rozne wysokosci), zygzakiem; hmax = najdluzszy bieg. */
    static void stairChain(B b, int x, int z, int[] levels, int hmax, String stairId) {
        for (int k = 0; k + 1 < levels.length; k++) {
            int s = levels[k], t = levels[k + 1];
            if (k % 2 == 0) flight(b, x, z, s, t, Direction.EAST, stairId);
            else flight(b, x + hmax + 1, z + 2, s, t, Direction.WEST, stairId);
        }
    }

    private static void officeEx(B b, int x0, int z0, int x1, int z1, int y, int ex0, int ez0, int ex1, int ez1) {
        for (int x = x0 + 1; x <= x1 - 2; x += 3)
            for (int z = z0 + 1; z <= z1 - 2; z += 4) {
                if (x + 1 >= ex0 && x <= ex1 && z + 1 >= ez0 && z <= ez1) continue;
                b.set(x, y, z, blk("spruce_trapdoor"));
                b.set(x + 1, y, z, blk("spruce_trapdoor"));
                chair(b, x, y, z + 1, Direction.NORTH);
                b.set(x + 1, y + 1, z, blk("lantern"));
            }
    }

    private static void furnishDomek(B b) {
        door(b, 6, 1, 3, "oak_door", Direction.SOUTH, false, false);
        b.set(6, 3, 3, blk("glass_pane"));
        bed(b, 3, 1, 10, "red", Direction.NORTH);
        chest(b, 4, 1, 10, Direction.NORTH);
        b.set(5, 1, 10, st("barrel", "facing", "up"));
        furnace(b, 8, 1, 9, "furnace", Direction.NORTH);
        furnace(b, 8, 1, 10, "blast_furnace", Direction.NORTH);
        furnace(b, 9, 1, 10, "smoker", Direction.NORTH);
        b.set(9, 1, 9, blk("crafting_table"));
        table(b, 6, 1, 7, "oak_fence", "oak_pressure_plate");
        chair(b, 5, 1, 7, Direction.WEST);
        chair(b, 7, 1, 7, Direction.EAST);
        for (int z = 5; z <= 6; z++) for (int y = 1; y <= 2; y++) b.set(3, y, z, blk("bookshelf"));
        b.set(6, 1, 5, blk("red_carpet")); b.set(6, 1, 6, blk("red_carpet"));
        hangingLantern(b, 6, 4, 7);
        // latarnia ogrodowa z dzwignia i lampa (mechanizm)
        b.fill(11, 1, 2, 11, 2, 2, blk("oak_fence"));
        b.set(11, 3, 2, blk("redstone_lamp"));
        lever(b, 12, 3, 2, "wall", Direction.EAST, true);
        b.set(1, 1, 1, blk("composter"));
    }

    private static void furnishWilla(B b) {
        door(b, 8, 2, 2, "dark_oak_door", Direction.SOUTH, true, false);
        door(b, 9, 2, 2, "dark_oak_door", Direction.SOUTH, false, false);
        for (int y = 2; y <= 4; y++) b.set(10, y, 2, blk("glass"));
        b.set(8, 4, 2, blk("glass")); b.set(9, 4, 2, blk("glass"));
        // salon
        for (int x = 5; x <= 9; x++) b.set(x, 2, 5, stair("quartz_stairs", Direction.SOUTH, false));
        b.set(7, 2, 7, blk("spruce_trapdoor"));
        for (int x = 5; x <= 9; x++) for (int z = 6; z <= 8; z++) b.set(x, 2, z, blk("gray_carpet"));
        b.fill(5, 3, 13, 9, 4, 13, blk("black_concrete"));                         // telewizor na scianie
        // kuchnia
        furnace(b, 11, 2, 13, "furnace", Direction.NORTH);
        furnace(b, 12, 2, 13, "smoker", Direction.NORTH);
        b.set(13, 2, 13, st("barrel", "facing", "up"));
        b.set(14, 2, 13, blk("cauldron"));
        b.set(15, 2, 13, blk("crafting_table"));
        table(b, 13, 2, 8, "spruce_fence", "spruce_pressure_plate");
        table(b, 14, 2, 8, "spruce_fence", "spruce_pressure_plate");
        chair(b, 13, 2, 7, Direction.NORTH); chair(b, 14, 2, 7, Direction.NORTH);
        chair(b, 13, 2, 9, Direction.SOUTH); chair(b, 14, 2, 9, Direction.SOUTH);
        // schody na pietro (zawijasem wzdluz sciany)
        flight(b, 9, 12, 1, 7, Direction.WEST, "quartz_stairs");
        // sypialnia
        bed(b, 4, 8, 5, "blue", Direction.NORTH);
        b.set(3, 8, 4, st("barrel", "facing", "up")); b.set(5, 8, 4, st("barrel", "facing", "up"));
        b.set(3, 9, 4, blk("lantern")); b.set(5, 9, 4, blk("lantern"));
        chest(b, 3, 8, 8, Direction.EAST); chest(b, 3, 8, 9, Direction.EAST);
        table(b, 8, 8, 4, "dark_oak_fence", "dark_oak_pressure_plate");
        chair(b, 8, 8, 5, Direction.SOUTH);
        b.set(9, 8, 13, blk("cauldron"));
        for (int z = 6; z <= 8; z++) b.set(6, 8, z, blk("white_carpet"));
        // taras
        table(b, 13, 7, 8, "oak_fence", "oak_pressure_plate");
        chair(b, 12, 7, 8, Direction.WEST); chair(b, 14, 7, 8, Direction.EAST);
        b.set(15, 7, 4, blk("lantern"));
    }

    private static void furnishZamek(B b) {
        int cx = 22;
        // drzwi donzonu (2 skrzydla) + dokonczenie otworu
        door(b, 21, 1, 17, "dark_oak_door", Direction.SOUTH, true, false);
        door(b, 22, 1, 17, "dark_oak_door", Direction.SOUTH, false, false);
        for (int y = 1; y <= 4; y++) b.set(23, y, 17, cm(23, y, 17));
        for (int y = 3; y <= 4; y++) { b.set(21, y, 17, blk("iron_bars")); b.set(22, y, 17, blk("iron_bars")); }
        // wielka sala: dywan, stoly, laweczki, kominek, tron
        for (int z = 18; z <= 28; z++) b.set(cx, 1, z, blk("red_carpet"));
        for (int xc : new int[]{18, 26}) for (int z = 20; z <= 27; z++) {
            table(b, xc, 1, z, "spruce_fence", "spruce_pressure_plate");
            b.set(xc - 1, 1, z, stair("oak_stairs", Direction.WEST, false));
            b.set(xc + 1, 1, z, stair("oak_stairs", Direction.EAST, false));
        }
        for (int z = 21; z <= 23; z++) b.set(28, 1, z, blk("stone_bricks"));
        furnace(b, 27, 1, 22, "furnace", Direction.WEST);
        for (int x = 20; x <= 24; x += 2) hangingLantern(b, x, 7, 24);
        b.set(cx, 1, 29, stair("quartz_stairs", Direction.NORTH, false));             // tron
        b.set(cx, 2, 30, blk("gold_block"));
        // schody miedzy pietrami (3 biegi)
        flight(b, 16, 30, 0, 8, Direction.EAST, "stone_brick_stairs");
        flight(b, 27, 28, 8, 15, Direction.WEST, "stone_brick_stairs");
        flight(b, 16, 30, 15, 22, Direction.EAST, "stone_brick_stairs");
        // sypialnie (pietro 2)
        bed(b, 17, 9, 19, "red", Direction.EAST); bed(b, 17, 9, 22, "red", Direction.EAST);
        bed(b, 27, 9, 19, "red", Direction.WEST); bed(b, 27, 9, 22, "red", Direction.WEST);
        chest(b, 17, 9, 21, Direction.EAST); chest(b, 27, 9, 21, Direction.WEST);
        for (int x = 20; x <= 24; x++) b.set(x, 9, 20, blk("blue_carpet"));
        // biblioteka (pietro 3)
        for (int x = 17; x <= 27; x++) for (int y = 16; y <= 18; y++) b.set(x, y, 18, blk("bookshelf"));
        facing(b, 22, 16, 21, "lectern", Direction.SOUTH);
        b.set(22, 16, 25, blk("enchanting_table"));
        b.set(20, 16, 25, blk("brewing_stand"));
        b.set(24, 16, 25, blk("cauldron"));
        for (int x = 18; x <= 26; x += 4) hangingLantern(b, x, 21, 22);
        // dziedziniec: kowal
        b.set(10, 1, 20, blk("anvil")); b.set(11, 1, 20, st("grindstone", "face", "floor", "facing", "north"));
        furnace(b, 12, 1, 20, "blast_furnace", Direction.SOUTH);
        b.set(9, 1, 20, st("barrel", "facing", "up"));
    }

    private static void furnishStatek(B b) {
        int cx = 7;
        // przegroda i drzwi kajuty kapitana
        for (int dx = -6; dx <= 6; dx++) for (int y = 6; y <= 9; y++) b.set(cx + dx, y, 37, blk("dark_oak_planks"));
        door(b, cx, 6, 37, "spruce_door", Direction.SOUTH, false, false);
        bed(b, cx - 3, 6, 43, "red", Direction.SOUTH);
        b.set(cx - 4, 6, 43, st("barrel", "facing", "up")); b.set(cx - 2, 6, 44, st("barrel", "facing", "up"));
        chest(b, cx + 3, 6, 44, Direction.NORTH); chest(b, cx + 4, 6, 44, Direction.NORTH);
        table(b, cx, 6, 41, "dark_oak_fence", "dark_oak_pressure_plate");
        chair(b, cx - 1, 6, 41, Direction.WEST); chair(b, cx + 1, 6, 41, Direction.EAST);
        facing(b, cx + 3, 6, 40, "lectern", Direction.WEST);
        for (int z = 39; z <= 45; z++) b.set(cx - 5, 6, z, blk("bookshelf"));
        hangingLantern(b, cx, 9, 41); hangingLantern(b, cx, 9, 44);
        // koło sterowe (przy rufie na pokladzie)
        facing(b, cx, 11, 40, "lectern", Direction.SOUTH);
        b.set(cx, 12, 40, blk("lantern"));
        // ladownia: beczki i skrzynie, luk z trapdoor
        for (int z = 14; z <= 32; z += 2) { b.set(cx - 3, 2, z, st("barrel", "facing", "up")); b.set(cx + 3, 2, z, st("barrel", "facing", "up")); }
        for (int z = 15; z <= 31; z += 4) { chest(b, cx - 2, 2, z, Direction.EAST); chest(b, cx + 2, 2, z, Direction.WEST); }
        trapdoor(b, cx, 5, 20, "oak_trapdoor", Direction.NORTH, false, false);
        // pochodnie na bukszprycie/dziobie
        b.set(cx - 1, 10, 1, blk("lantern")); b.set(cx + 1, 10, 1, blk("lantern"));
    }

    private static void furnishHotel(B b) {
        for (int f = 0; f <= 8; f++) {
            int y0 = 8 + 4 * f;
            int y = y0 + 1;
            for (int x = 6; x <= 15; x++) for (int yy = y; yy <= y + 2; yy++) { b.set(x, yy, 11, blk("smooth_quartz")); b.set(x, yy, 13, blk("smooth_quartz")); }
            for (int x : new int[]{9, 13}) for (int yy = y; yy <= y + 2; yy++) for (int z : new int[]{8, 9, 10, 14, 15, 16}) b.set(x, yy, z, blk("smooth_quartz"));
            for (int xc : new int[]{7, 11, 15}) {
                door(b, xc, y, 11, "spruce_door", Direction.NORTH, false, false);       // pokoje polnocne
                door(b, xc, y, 13, "spruce_door", Direction.SOUTH, false, false);       // pokoje poludniowe
                bed(b, xc, y, 9, "red", Direction.NORTH);
                b.set(xc - 1, y, 8, st("barrel", "facing", "up")); b.set(xc + 1, y, 8, st("barrel", "facing", "up"));
                b.set(xc - 1, y + 1, 8, blk("lantern")); b.set(xc + 1, y + 1, 8, blk("lantern"));
                table(b, xc + 1, y, 10, "spruce_fence", "spruce_pressure_plate");
                bed(b, xc, y, 15, "blue", Direction.SOUTH);
                b.set(xc - 1, y, 16, st("barrel", "facing", "up")); b.set(xc + 1, y, 16, st("barrel", "facing", "up"));
                b.set(xc - 1, y + 1, 16, blk("lantern")); b.set(xc + 1, y + 1, 16, blk("lantern"));
                table(b, xc + 1, y, 14, "spruce_fence", "spruce_pressure_plate");
            }
            for (int x = 6; x <= 16; x++) b.set(x, y, 12, blk("red_carpet"));
            for (int x : new int[]{8, 12, 16}) b.set(x, y0 + 4, 12, blk("sea_lantern"));
        }
        stairCore(b, 17, 11, 8, 9, 4, "quartz_stairs");
        flight(b, 22, 13, 1, 8, Direction.WEST, "quartz_stairs");                // z lobby do pierwszego pietra
        // lobby: recepcja, dzwonek, rosliny, kanapy, lampy, drzwi wejsciowe
        for (int x = 9; x <= 19; x++) { b.set(x, 2, 10, blk("quartz_block")); b.set(x, 3, 10, blk("quartz_slab")); }
        facing(b, 14, 4, 10, "lectern", Direction.SOUTH);
        b.set(11, 4, 10, st("bell", "attachment", "floor", "facing", "north"));
        for (int x = 8; x <= 20; x += 4) { b.set(x, 2, 15, stair("quartz_stairs", Direction.NORTH, false)); b.set(x + 1, 2, 15, stair("quartz_stairs", Direction.NORTH, false)); }
        for (int x : new int[]{4, 24}) for (int z : new int[]{6, 18}) b.set(x, 2, z, blk("potted_fern"));
        for (int x = 8; x <= 20; x += 4) for (int z = 8; z <= 16; z += 4) hangingLantern(b, x, 6, z);
        for (int x = 12; x <= 16; x++) for (int z = 5; z <= 9; z++) b.set(x, 2, z, blk("red_carpet"));
        for (int y = 2; y <= 5; y++) for (int x : new int[]{12, 15, 16}) b.set(x, y, 4, blk("glass"));
        door(b, 13, 2, 4, "spruce_door", Direction.SOUTH, true, false);
        door(b, 14, 2, 4, "spruce_door", Direction.SOUTH, false, false);
        b.set(13, 4, 4, blk("glass")); b.set(14, 4, 4, blk("glass")); b.set(13, 5, 4, blk("glass")); b.set(14, 5, 4, blk("glass"));
        // mechanizm: lampa na dzwignie przy wejsciu
        b.set(10, 5, 4, blk("redstone_lamp")); lever(b, 10, 4, 5, "wall", Direction.SOUTH, true);
    }

    private static void furnishBlok(B b) {
        int[] ents = {4, 16, 28};
        for (int x0 : ents) {
            door(b, x0, 2, 2, "spruce_door", Direction.SOUTH, true, false);
            door(b, x0 + 1, 2, 2, "spruce_door", Direction.SOUTH, false, false);
            for (int y = 4; y <= 4; y++) { b.set(x0, y, 2, blk("glass_pane")); b.set(x0 + 1, y, 2, blk("glass_pane")); }
            stairCore(b, x0 + 2, 3, 1, 5, 4, "stone_brick_stairs");
            b.set(x0 - 1, 2, 3, blk("dropper"));                                      // skrzynki pocztowe
            b.set(x0 - 1, 3, 3, blk("dropper"));
        }
        int[] xs = {10, 22, 34};
        for (int f = 0; f < 5; f++) {
            int y = 4 * f + 2;
            for (int xs0 : xs) {
                bed(b, xs0, y, 11, f % 2 == 0 ? "red" : "blue", Direction.NORTH);
                chest(b, xs0 + 1, y, 11, Direction.NORTH);
                furnace(b, xs0 + 3, y, 11, "furnace", Direction.NORTH);
                b.set(xs0 + 4, y, 11, blk("cauldron"));
                table(b, xs0 + 2, y, 8, "oak_fence", "oak_pressure_plate");
                chair(b, xs0 + 1, y, 8, Direction.WEST); chair(b, xs0 + 3, y, 8, Direction.EAST);
                b.set(xs0 + 1, y, 6, blk("brown_carpet")); b.set(xs0 + 2, y, 6, blk("brown_carpet"));
                hangingLantern(b, xs0 + 2, y + 2, 8);
            }
        }
    }

    private static void furnishBiurowiec(B b) {
        int[] lv = {1, 5, 9, 13, 17, 21, 25, 27};
        stairChain(b, 5, 6, lv, 4, "quartz_stairs");
        for (int k = 0; k + 1 < lv.length; k++) officeEx(b, 3, 3, 33, 21, lv[k] + 1, 5, 6, 12, 8);
        for (int x = 8; x <= 28; x += 4) { b.set(x, 3, 4, blk("potted_fern")); }
        door(b, 17, 2, 3, "iron_door", Direction.SOUTH, true, false);
        door(b, 18, 2, 3, "iron_door", Direction.SOUTH, false, false);
        b.set(16, 3, 2, blk("stone_pressure_plate")); b.set(19, 3, 2, blk("stone_pressure_plate"));
    }

    private static void furnishSzklany(B b) {
        int[] lv = {7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 45, 48, 52, 56, 60, 64, 65, 68, 72, 75};
        stairChain(b, 9, 10, lv, 4, "quartz_stairs");
        for (int k = 0; k + 1 < lv.length; k++) if (lv[k + 1] - lv[k] >= 3) {
            int a = lv[k] < 45 ? 3 : lv[k] < 65 ? 6 : 9, c = lv[k] < 45 ? 21 : lv[k] < 65 ? 18 : 15;
            officeEx(b, a, a, c, c, lv[k] + 1, 9, 10, 16, 14);
        }
        flight(b, 17, 8, 1, 7, Direction.WEST, "quartz_stairs");
        for (int x = 6; x <= 18; x += 4) { b.set(x, 2, 20, stair("quartz_stairs", Direction.NORTH, false)); b.set(x, 2, 6, blk("potted_fern")); }
        door(b, 11, 2, 2, "iron_door", Direction.SOUTH, true, false);
        door(b, 12, 2, 2, "iron_door", Direction.SOUTH, false, false);
    }

    private static void furnishStalinka(B b) {
        stairChain(b, 14, 14, new int[]{1, 6, 11, 16, 21, 27, 33, 39, 45, 50, 55, 61}, 6, "stone_brick_stairs");
        stairChain(b, 16, 17, new int[]{61, 66, 71, 75}, 5, "stone_brick_stairs");
        for (int[] lv : new int[][]{{1, 6}, {6, 11}, {11, 16}, {16, 21}}) officeEx(b, 2, 2, 36, 36, lv[0] + 1, 14, 14, 21, 18);
        for (int[] lv : new int[][]{{21, 27}, {27, 33}, {33, 39}, {39, 45}}) officeEx(b, 8, 8, 30, 30, lv[0] + 1, 14, 14, 21, 18);
        door(b, 18, 2, 2, "iron_door", Direction.SOUTH, true, false);
        door(b, 19, 2, 2, "iron_door", Direction.SOUTH, false, false);
        flight(b, 25, 6, 1, 6, Direction.WEST, "stone_brick_stairs");
    }

    private static void furnishArtDeco(B b) {
        int[] lv = {1, 5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 43, 47, 51, 55, 59, 63, 67, 71, 75, 79, 83, 85, 89, 93, 97};
        stairChain(b, 11, 12, lv, 4, "quartz_stairs");
        for (int k = 0; k + 1 < lv.length; k++) if (lv[k + 1] - lv[k] >= 3) {
            int a = lv[k] < 43 ? 1 : lv[k] < 67 ? 4 : lv[k] < 85 ? 7 : 10;
            int c = 30 - a;
            officeEx(b, a, a, c, c, lv[k] + 1, 11, 12, 16, 16);
        }
        door(b, 14, 2, 1, "iron_door", Direction.SOUTH, true, false);
        door(b, 15, 2, 1, "iron_door", Direction.SOUTH, false, false);
        b.set(13, 3, 0, blk("stone_pressure_plate")); b.set(16, 3, 0, blk("stone_pressure_plate"));
    }

    private static void furnishBank(B b) {
        // lada kasjerow, okienka, krzesla
        for (int x = 5; x <= 15; x++) { b.set(x, 3, 12, blk("quartz_block")); b.set(x, 4, 12, blk("quartz_slab")); }
        for (int x = 6; x <= 14; x += 2) { b.set(x, 5, 12, blk("glass_pane")); b.set(x, 6, 12, blk("glass_pane")); chair(b, x, 3, 14, Direction.NORTH); }
        b.set(20, 3, 9, st("dispenser", "facing", "south")); button(b, 20, 4, 9, "stone_button", "floor", Direction.SOUTH);
        for (int z = 8; z <= 11; z++) b.set(15, 3, z, blk("red_carpet"));
        for (int x = 12; x <= 18; x++) b.set(x, 3, 8, blk("red_carpet"));
        for (int x = 8; x <= 24; x += 4) hangingLantern(b, x, 12, 14);
        // drzwi wejsciowe
        door(b, 14, 3, 7, "spruce_door", Direction.SOUTH, true, false);
        door(b, 15, 3, 7, "spruce_door", Direction.SOUTH, false, false);
        b.set(16, 3, 7, blk("glass_pane")); b.set(16, 4, 7, blk("glass_pane")); b.set(16, 5, 7, blk("glass_pane"));
        // skarbiec: zelazne drzwi otwierane dzwignia, skrzynki depozytowe
        for (int y = 3; y <= 6; y++) b.set(21, y, 18, blk("iron_block"));
        door(b, 22, 3, 18, "iron_door", Direction.SOUTH, true, false);
        for (int y = 3; y <= 6; y++) b.set(23, y, 18, blk("iron_block"));
        lever(b, 21, 4, 17, "wall", Direction.NORTH, false);
        for (int x = 19; x <= 25; x += 2) { chest(b, x, 3, 25, Direction.NORTH); chest(b, x, 4, 25, Direction.NORTH); }
        for (int z = 20; z <= 24; z += 2) { b.set(19, 3, z, st("barrel", "facing", "up")); b.set(25, 3, z, st("barrel", "facing", "up")); }
        // tajne wyjscie na tlok (mechanizm): lepki tlok + dzwignia
        b.set(26, 4, 22, st("sticky_piston", "facing", "west"));
        lever(b, 26, 5, 22, "ceiling", Direction.NORTH, false);
    }

    private static void furnishKosciol(B b) {
        // portal glowny
        door(b, 9, 1, 0, "dark_oak_door", Direction.SOUTH, true, false);
        door(b, 10, 1, 0, "dark_oak_door", Direction.SOUTH, false, false);
        for (int y = 1; y <= 5; y++) b.set(11, y, 0, cm(11, y, 0));
        for (int y = 3; y <= 5; y++) { b.set(9, y, 0, blk("glass_pane")); b.set(10, y, 0, blk("glass_pane")); }
        // lawki, dywan, oltarz
        for (int z = 14; z <= 34; z += 2) for (int x : new int[]{6, 7, 8, 12, 13, 14}) b.set(x, 1, z, stair("spruce_stairs", Direction.NORTH, false));
        for (int z = 11; z <= 37; z++) b.set(10, 1, z, blk("red_carpet"));
        candle(b, 9, 2, 38, "white", 4); candle(b, 11, 2, 38, "white", 4); candle(b, 10, 2, 40, "red", 3);
        facing(b, 10, 1, 35, "lectern", Direction.NORTH);
        for (int z = 14; z <= 34; z += 4) { b.set(5, 6, z, blk("lantern")); b.set(15, 6, z, blk("lantern")); }
        for (int z = 12; z <= 36; z += 6) { b.set(5, 5, z, st("wall_torch", "facing", "east")); b.set(15, 5, z, st("wall_torch", "facing", "west")); }
        // wieza: drabina z podestami i dzwon
        for (int y = 1; y <= 28; y++) b.set(8, y, 4, blk("oak_planks"));
        ladder(b, 9, 1, 28, 4, Direction.EAST);
        b.carve(9, 9, 4, 9, 9, 4); b.carve(9, 18, 4, 9, 18, 4);
        b.set(10, 28, 4, st("bell", "attachment", "ceiling", "facing", "north"));
        // organy i mechanizm (lampy + dzwignia przy oltarzu)
        for (int x = 5; x <= 7; x++) for (int y = 2; y <= 6; y++) b.set(x, y, 12, blk("iron_bars"));
        b.set(6, 1, 13, blk("note_block"));
        b.set(7, 1, 38, blk("redstone_lamp")); lever(b, 8, 1, 38, "floor", Direction.EAST, true);
    }

    private static void furnishWiatrak(B b) {
        int c = 11;
        door(b, c, 1, c - 5, "spruce_door", Direction.SOUTH, false, false);
        b.set(c, 3, c - 5, blk("glass_pane"));
        for (int y = 1; y <= 18; y++) b.set(c - 3, y, c, blk("oak_planks"));
        ladder(b, c - 2, 1, 18, c, Direction.EAST);
        b.carve(c - 2, 6, c, c - 2, 6, c); b.carve(c - 2, 12, c, c - 2, 12, c);
        b.set(c + 2, 1, c, st("grindstone", "face", "floor", "facing", "north"));
        for (int z : new int[]{c - 3, c - 2}) { b.set(c + 2, 1, z, blk("hay_block")); b.set(c + 3, 1, z, st("barrel", "facing", "up")); }
        b.set(c + 1, 1, c + 3, blk("composter"));
        b.set(c + 2, 7, c, blk("hopper")); b.set(c + 2, 8, c, st("barrel", "facing", "up"));
        hangingLantern(b, c, 5, c); hangingLantern(b, c, 11, c);
        chest(b, c - 1, 7, c + 3, Direction.NORTH);
    }

    private static void furnishLatarnia(B b) {
        int c = 8;
        door(b, c, 2, c - 4, "spruce_door", Direction.SOUTH, false, false);
        for (int y = 2; y <= 29; y++) b.set(c - 2, y, c, blk("stone_bricks"));
        ladder(b, c - 1, 2, 29, c, Direction.EAST);
        for (int y : new int[]{8, 15, 22, 28}) b.carve(c - 1, y, c, c - 1, y, c);
        hangingLantern(b, c, 7, c); hangingLantern(b, c, 14, c);
        // domek latarnika
        door(b, 11, 2, 6, "spruce_door", Direction.WEST, false, false);
        bed(b, 14, 2, 5, "blue", Direction.NORTH);
        chest(b, 13, 2, 7, Direction.NORTH);
        furnace(b, 12, 2, 7, "furnace", Direction.NORTH);
        table(b, 13, 2, 5, "spruce_fence", "spruce_pressure_plate");
        chair(b, 12, 2, 5, Direction.WEST);
        hangingLantern(b, 13, 5, 6);
        // dzwignia i lampa sygnalowa
        b.set(c + 3, 29, c, blk("redstone_lamp")); lever(b, c + 4, 29, c, "wall", Direction.EAST, true);
    }

    private static void furnishKamienica(B b) {
        int[] lv = {1, 6, 11, 16, 21};
        stairChain(b, 3, 10, lv, 5, "stone_brick_stairs");
        door(b, 8, 1, 2, "dark_oak_door", Direction.SOUTH, true, false);
        door(b, 9, 1, 2, "dark_oak_door", Direction.SOUTH, false, false);
        for (int y = 1; y <= 4; y++) b.set(10, y, 2, blk("glass_pane"));
        for (int k = 0; k < 4; k++) {
            int y = lv[k] + 1;
            bed(b, 14, y, 12, k % 2 == 0 ? "red" : "blue", Direction.NORTH);
            chest(b, 15, y, 12, Direction.NORTH);
            furnace(b, 14, y, 4, "furnace", Direction.SOUTH);
            b.set(15, y, 4, blk("cauldron"));
            table(b, 11, y, 7, "oak_fence", "oak_pressure_plate");
            chair(b, 10, y, 7, Direction.WEST); chair(b, 12, y, 7, Direction.EAST);
            b.set(11, y, 9, blk("brown_carpet")); b.set(11, y, 10, blk("brown_carpet"));
            hangingLantern(b, 11, y + 3, 7);
        }
        // sklep na parterze: lada i polki
        for (int x = 4; x <= 7; x++) b.set(x, 2, 5, st("barrel", "facing", "up"));
        b.set(5, 2, 8, blk("smooth_stone"));
    }

    private static void furnishStodola(B b) {
        for (int z = 6; z <= 22; z += 4) {
            for (int x = 14; x <= 17; x++) b.set(x, 1, z, blk("oak_fence"));
            gate(b, 13, 1, z + 2, "oak_fence_gate", Direction.EAST, false);
            b.set(15, 1, z + 1, blk("hay_block")); b.set(16, 1, z + 2, st("barrel", "facing", "up"));
        }
        b.fill(3, 8, 14, 17, 8, 25, blk("oak_planks"));
        for (int y = 1; y <= 8; y++) b.set(3, y, 20, blk("oak_planks"));
        ladder(b, 4, 1, 8, 20, Direction.EAST);
        b.carve(4, 8, 20, 4, 8, 20);
        for (int x = 5; x <= 9; x++) for (int z = 16; z <= 18; z++) b.set(x, 9, z, blk("hay_block"));
        chest(b, 5, 1, 4, Direction.SOUTH); chest(b, 6, 1, 4, Direction.SOUTH);
        b.set(8, 1, 4, blk("cauldron")); b.set(9, 1, 4, blk("composter"));
        for (int x = 5; x <= 11; x += 3) b.set(x, 9, 6, blk("lantern"));
    }

    private static void furnishWieza(B b) {
        door(b, 6, 1, 2, "dark_oak_door", Direction.SOUTH, true, false);
        door(b, 7, 1, 2, "dark_oak_door", Direction.SOUTH, false, false);
        for (int y = 1; y <= 4; y++) b.set(8, y, 2, blk("bricks"));
        for (int y = 1; y <= 33; y++) b.set(3, y, 7, blk("stone_bricks"));
        ladder(b, 4, 1, 33, 7, Direction.EAST);
        for (int y : new int[]{8, 16}) { b.fill(4, y, 3, 11, y, 11, blk("oak_planks")); b.carve(4, y, 7, 4, y, 7); }
        b.carve(4, 23, 7, 4, 23, 7); b.carve(4, 34, 7, 4, 34, 7);
        for (int y = 24; y <= 30; y++) b.set(7, y, 7, st("chain", "axis", "y"));
        b.set(7, 23, 7, blk("gold_block"));
        b.set(7, 33, 7, st("bell", "attachment", "ceiling", "facing", "north"));
        hangingLantern(b, 7, 7, 7); hangingLantern(b, 7, 15, 7);
        b.set(9, 17, 9, st("barrel", "facing", "up")); chest(b, 10, 17, 9, Direction.NORTH);
    }

    private static void furnishMur(B b) {
        door(b, 6, 1, 5, "iron_door", Direction.EAST, false, false);
        door(b, 34, 1, 5, "iron_door", Direction.WEST, false, false);
        lever(b, 17, 4, 4, "wall", Direction.EAST, false);
        for (int x = 18; x <= 22; x++) b.set(x, 8, 4, blk("iron_bars"));
        for (int x = 10; x <= 30; x += 4) b.set(x, 11, 5, blk("lantern"));
    }

    private static void furnishPiramida(B b) {
        // pulapka: plyta naciskowa + pyl + dozownik w scianie korytarza
        b.set(15, 1, 6, blk("stone_pressure_plate"));
        b.set(14, 1, 6, blk("redstone_wire"));
        b.set(13, 1, 6, st("dispenser", "facing", "east"));
        b.set(15, 1, 10, blk("stone_pressure_plate"));
        b.set(16, 1, 10, blk("redstone_wire"));
        b.set(17, 1, 10, st("dispenser", "facing", "west"));
        // komnata: skrzynie, drzwi do skarbca, lampy
        chest(b, 11, 2, 12, Direction.EAST); chest(b, 11, 2, 14, Direction.EAST); chest(b, 19, 2, 20, Direction.WEST);
        b.set(12, 2, 20, st("barrel", "facing", "up"));
        for (int[] p : new int[][]{{10, 12}, {20, 12}}) b.set(p[0], 5, p[1], st("wall_torch", "facing", p[0] == 10 ? "east" : "west"));
        b.set(15, 3, 21, st("lever", "face", "wall", "facing", "north", "powered", "false"));
    }
}
