package pl.blockflow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stan moda: zaznaczenie, schowek, transformacja (obrot/lustro), plan budowy i postep. */
public final class Flow {
    public record Cell(int x, int y, int z, BlockState state, String[] text) {
        public Cell(int x, int y, int z, BlockState state) { this(x, y, z, state, null); }
    }

    static final int MAX_VOLUME = 1_000_000;
    static final int MAX_DIM = 2000;

    // zaznaczenie
    static BlockPos pos1, pos2;

    // schowek (wspolrzedne wzgledne, 0..s-1)
    static int sx, sy, sz;
    static Cell[] clip = new Cell[0];

    // transformacja i miejsce docelowe
    static Rotation rotation = Rotation.NONE;
    static Mirror mirror = Mirror.NONE;
    static BlockPos anchor;           // minimalny narozik postawionej budowli

    // plan po transformacji
    static int n;
    static int[] wx = new int[0], wy = new int[0], wz = new int[0];
    static BlockState[] ws = new BlockState[0];
    static String[][] wtext = new String[0][];          // tekst tabliczek (null = brak)
    static boolean[] done = new boolean[0];
    static boolean[] skip = new boolean[0];
    static boolean[] approx = new boolean[0];   // zaakceptowane z przyblizonym stanem
    static int maxDoneY = Integer.MIN_VALUE;     // najwyzszy potwierdzony blok planu
    static boolean scanWrapped;                  // czy skan przeszedl caly plan
    static int[] order = new int[0];  // kolejnosc budowy: warstwami od dolu
    static long[] posKeys = new long[0];   // posortowane pozycje planu (dla inPlan)
    static int doneCount, scanCursor;

    // ustawienia podgladu
    static int range = 24;
    static int cap = 2500;
    static boolean layerMode = false;
    static int layerCount = 1;
    static int[] vis = new int[0];
    static int visN = 0;

    private Flow() {}

    // ------------------------------------------------------------------ kopiowanie
    static String copy(ClientLevel lv) {
        if (pos1 == null || pos2 == null) return "Najpierw ustaw oba punkty (pos1 i pos2).";
        int x0 = Math.min(pos1.getX(), pos2.getX()), x1 = Math.max(pos1.getX(), pos2.getX());
        int y0 = Math.min(pos1.getY(), pos2.getY()), y1 = Math.max(pos1.getY(), pos2.getY());
        int z0 = Math.min(pos1.getZ(), pos2.getZ()), z1 = Math.max(pos1.getZ(), pos2.getZ());
        int w = x1 - x0 + 1, h = y1 - y0 + 1, d = z1 - z0 + 1;
        if (w > MAX_DIM || h > MAX_DIM || d > MAX_DIM) return "Za duzy obszar (max " + MAX_DIM + " na wymiar).";
        long vol = (long) w * h * d;
        if (vol > MAX_VOLUME) return "Za duzy obszar (" + vol + " blokow, limit " + MAX_VOLUME + ").";

        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                if (!lv.hasChunkAt(new BlockPos(cx << 4, y0, cz << 4)))
                    return "Czesc obszaru nie jest zaladowana - podejdz blizej i skopiuj ponownie.";

        List<Cell> cells = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) {
                    mp.set(x, y, z);
                    BlockState st = lv.getBlockState(mp);
                    if (!st.isAir()) cells.add(new Cell(x - x0, y - y0, z - z0, st, st.hasBlockEntity() ? Signs.read(lv, mp) : null));
                }
        clip = cells.toArray(new Cell[0]);
        sx = w; sy = h; sz = d;
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        rebuild();
        return "Skopiowano " + clip.length + " blokow (" + w + "x" + h + "x" + d + "). Teraz ustaw miejsce: /blockflow place";
    }

    /** Podmienia schowek (np. wczytany z slotu albo gotowy schemat). */
    static void setClipboard(Cell[] cells, int w, int h, int d) {
        clip = cells;
        sx = w; sy = h; sz = d;
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        rebuild();
    }

    // ------------------------------------------------------------------ transformacja
    static void rebuild() {
        rebuildInner();
        AutoBuilderReset.reset();   // po ustawieniu n, zeby tablice mialy dobry rozmiar
    }

    private static void rebuildInner() {
        maxDoneY = Integer.MIN_VALUE;
        scanWrapped = false;
        doneCount = 0;
        scanCursor = 0;
        visN = 0;
        if (clip.length == 0 || anchor == null) {
            n = 0;
            wx = wy = wz = new int[0];
            ws = new BlockState[0];
            wtext = new String[0][];
            done = new boolean[0];
            skip = new boolean[0];
            approx = new boolean[0];
            order = new int[0];
            posKeys = new long[0];
            return;
        }
        n = clip.length;
        wx = new int[n]; wy = new int[n]; wz = new int[n];
        ws = new BlockState[n];
        wtext = new String[n][];
        done = new boolean[n];
        skip = new boolean[n];
        approx = new boolean[n];
        long[] keys = new long[n];

        for (int i = 0; i < n; i++) {
            Cell c = clip[i];
            int x = c.x(), z = c.z();
            if (mirror == Mirror.LEFT_RIGHT) z = sz - 1 - z;
            int nx, nz;
            if (rotation == Rotation.CLOCKWISE_90) { nx = sz - 1 - z; nz = x; }
            else if (rotation == Rotation.CLOCKWISE_180) { nx = sx - 1 - x; nz = sz - 1 - z; }
            else if (rotation == Rotation.COUNTERCLOCKWISE_90) { nx = z; nz = sx - 1 - x; }
            else { nx = x; nz = z; }

            wx[i] = anchor.getX() + nx;
            wy[i] = anchor.getY() + c.y();
            wz[i] = anchor.getZ() + nz;
            ws[i] = c.state().mirror(mirror).rotate(rotation);
            wtext[i] = c.text();

            int snake = (nz & 1) == 0 ? nx : 4095 - Math.min(nx, 4095);
            long key = (((long) c.y() & 0xFFF) << 24) | (((long) nz & 0xFFF) << 12) | (snake & 0xFFF);
            keys[i] = (key << 20) | i;
        }
        Arrays.sort(keys);
        order = new int[n];
        for (int i = 0; i < n; i++) order[i] = (int) (keys[i] & 0xFFFFF);
        posKeys = new long[n];
        for (int i = 0; i < n; i++) posKeys[i] = pack(wx[i], wy[i], wz[i]);
        Arrays.sort(posKeys);
    }

    private static long pack(int x, int y, int z) {
        return (((long) x & 0x1FFFFF) << 42) | (((long) y & 0x1FFFFF) << 21) | ((long) z & 0x1FFFFF);
    }

    /** Czy dana pozycja nalezy do planu budowy. */
    static boolean inPlan(int x, int y, int z) {
        return n > 0 && Arrays.binarySearch(posKeys, pack(x, y, z)) >= 0;
    }

    static void rotate90() {
        rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        rebuild();
    }

    static void toggleMirror() {
        mirror = (mirror == Mirror.NONE) ? Mirror.LEFT_RIGHT : Mirror.NONE;
        rebuild();
    }

    static void setAnchor(BlockPos p) {
        anchor = p.immutable();
        rebuild();
    }

    static void clearPlan() {
        clip = new Cell[0];
        anchor = null;
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        rebuild();
    }

    static int rotationDegrees() {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
            default -> 0;
        };
    }

    /** Wymiary X/Z po obrocie. */
    static int dimX() { return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? sz : sx; }
    static int dimZ() { return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? sx : sz; }

    // ------------------------------------------------------------------ postep
    /** Co tick sprawdza kolejna paczke blokow, czy stoja w swiecie. */
    static void scan(ClientLevel lv) {
        if (n == 0) return;
        int k = Math.min(n, 4000);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int j = 0; j < k; j++) {
            int i = scanCursor;
            scanCursor = (scanCursor + 1) % n;
            if (scanCursor == 0) scanWrapped = true;
            mp.set(wx[i], wy[i], wz[i]);
            BlockState st = lv.getBlockState(mp);
            boolean ok = Match.same(st, ws[i]) || (approx[i] && st.getBlock() == ws[i].getBlock());
            if (ok != done[i]) {
                done[i] = ok;
                doneCount += ok ? 1 : -1;
            }
            if (ok && wy[i] > maxDoneY) maxDoneY = wy[i];
        }
    }

    static void refreshVisible(Vec3 p) {
        if (n == 0) { visN = 0; return; }
        if (vis.length != cap) vis = new int[cap];
        int lowest = Integer.MAX_VALUE;
        if (layerMode) {
            for (int i = 0; i < n; i++) if (!done[i] && wy[i] < lowest) lowest = wy[i];
        }
        double r2 = (double) range * range;
        int k = 0;
        for (int i = 0; i < n && k < cap; i++) {
            if (done[i]) continue;
            if (layerMode && wy[i] >= lowest + layerCount) continue;
            double dx = wx[i] + 0.5 - p.x, dy = wy[i] + 0.5 - p.y, dz = wz[i] + 0.5 - p.z;
            if (dx * dx + dy * dy + dz * dz <= r2) vis[k++] = i;
        }
        visN = k;
    }

    static Map<Block, Integer> remaining() {
        Map<Block, Integer> m = new HashMap<>();
        for (int i = 0; i < n; i++) if (!done[i]) m.merge(ws[i].getBlock(), 1, Integer::sum);
        return m;
    }

    static double percent() {
        return n == 0 ? 0 : 100.0 * doneCount / n;
    }
}
