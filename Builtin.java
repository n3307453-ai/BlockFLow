package pl.blockflow;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Kilka gotowych schematow generowanych w kodzie (do wczytania i postawienia). */
final class Builtin {
    private Builtin() {}

    static final String[] NAMES = {
            "Domek 7x5x7", "Wieza 5x12x5", "Piramida 15x8x15", "Mur 20x4x1", "Platforma 9x1x9"};

    private static void put(List<Flow.Cell> l, int x, int y, int z, BlockState s) {
        l.add(new Flow.Cell(x, y, z, s));
    }

    static String load(int idx) {
        List<Flow.Cell> l = new ArrayList<>();
        int w, h, d;
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState sand = Blocks.SANDSTONE.defaultBlockState();

        switch (idx) {
            case 0 -> {            // domek
                w = 7; h = 5; d = 7;
                for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) put(l, x, 0, z, planks);
                for (int y = 1; y <= 3; y++)
                    for (int x = 0; x < w; x++)
                        for (int z = 0; z < d; z++) {
                            boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                            if (!edge) continue;
                            boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                            if (x == 3 && z == 0 && y <= 2) continue;          // drzwi
                            boolean window = y == 2 && ((x == 3 && z == d - 1)
                                    || (z == 3 && (x == 0 || x == w - 1)));
                            put(l, x, y, z, corner ? log : window ? glass : cobble);
                        }
                for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) put(l, x, 4, z, planks);
            }
            case 1 -> {            // wieza
                w = 5; h = 12; d = 5;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        for (int z = 0; z < d; z++) {
                            boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                            if (y == 0 || y == h - 1) { put(l, x, y, z, bricks); continue; }
                            if (!edge) continue;
                            if (x == 2 && z == 0 && y <= 2) continue;           // drzwi
                            boolean window = y % 4 == 2 && (x == 2 || z == 2);
                            put(l, x, y, z, window ? glass : bricks);
                        }
            }
            case 2 -> {            // piramida
                w = 15; h = 8; d = 15;
                for (int y = 0; y < h; y++)
                    for (int x = y; x < w - y; x++)
                        for (int z = y; z < d - y; z++) put(l, x, y, z, sand);
            }
            case 3 -> {            // mur z zebatkami
                w = 20; h = 4; d = 1;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        if (y == 3 && x % 2 == 1) continue;
                        put(l, x, y, 0, bricks);
                    }
            }
            default -> {           // platforma
                w = 9; h = 1; d = 9;
                for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) put(l, x, 0, z, bricks);
            }
        }
        Flow.setClipboard(l.toArray(new Flow.Cell[0]), w, h, d);
        return "Wczytano: " + NAMES[idx] + " (" + l.size() + " blokow). Teraz: Postaw (menu) albo klawisz \\.";
    }
}
