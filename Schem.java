package pl.blockflow;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 10 slotow na wlasne budowle: pliki config/blockflow/slotN.bfs (gzip, wlasny format). */
final class Schem {
    private Schem() {}

    static final int SLOTS = 10;
    private static final int MAGIC = 0x42465331; // "BFS1"

    record Meta(String name, int sx, int sy, int sz, int count) {}

    private static Map<String, Block> blockById;

    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("blockflow");
    }

    static Path file(int slot) {
        return dir().resolve("slot" + slot + ".bfs");
    }

    static boolean exists(int slot) {
        return Files.exists(file(slot));
    }

    static void delete(int slot) {
        try {
            Files.deleteIfExists(file(slot));
        } catch (IOException e) {
            Msg.log("nie udalo sie usunac slotu " + slot, e);
        }
    }

    /** Naglowek slotu (bez wczytywania blokow) albo null. */
    static Meta meta(int slot) {
        if (!exists(slot)) return null;
        try (DataInputStream in = open(slot)) {
            if (in.readInt() != MAGIC) return null;
            String name = in.readUTF();
            int sx = in.readShort(), sy = in.readShort(), sz = in.readShort();
            int count = in.readInt();
            return new Meta(name, sx, sy, sz, count);
        } catch (Throwable t) {
            return null;
        }
    }

    static String save(int slot) {
        if (Flow.clip.length == 0) return "Schowek jest pusty - najpierw zaznacz obszar i skopiuj (copy).";
        try {
            Files.createDirectories(dir());
            Map<BlockState, Integer> pal = new LinkedHashMap<>();
            for (Flow.Cell c : Flow.clip) pal.putIfAbsent(c.state(), pal.size());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(file(slot)))))) {
                out.writeInt(MAGIC);
                out.writeUTF("Budowla " + slot);
                out.writeShort(Flow.sx);
                out.writeShort(Flow.sy);
                out.writeShort(Flow.sz);
                out.writeInt(Flow.clip.length);
                out.writeInt(pal.size());
                for (BlockState st : pal.keySet()) writeState(out, st);
                for (Flow.Cell c : Flow.clip) {
                    out.writeShort(c.x());
                    out.writeShort(c.y());
                    out.writeShort(c.z());
                    out.writeInt(pal.get(c.state()));
                }
                int tn = 0;                                          // tekst tabliczek
                for (Flow.Cell c : Flow.clip) if (c.text() != null) tn++;
                out.writeInt(tn);
                for (int i = 0; i < Flow.clip.length; i++) {
                    String[] t = Flow.clip[i].text();
                    if (t == null) continue;
                    out.writeInt(i);
                    for (int k = 0; k < 4; k++) out.writeUTF(k < t.length && t[k] != null ? t[k] : "");
                }
            }
            return "Zapisano w slocie " + slot + " (" + Flow.clip.length + " blokow, "
                    + Flow.sx + "x" + Flow.sy + "x" + Flow.sz + ").";
        } catch (Throwable t) {
            Msg.log("blad zapisu slotu " + slot, t);
            return "Blad zapisu: " + t.getMessage();
        }
    }

    static String load(int slot) {
        if (!exists(slot)) return "Slot " + slot + " jest pusty.";
        try (DataInputStream in = open(slot)) {
            if (in.readInt() != MAGIC) return "Plik slotu jest uszkodzony.";
            in.readUTF();
            int sx = in.readShort(), sy = in.readShort(), sz = in.readShort();
            int count = in.readInt();
            int pn = in.readInt();
            List<BlockState> pal = new ArrayList<>(pn);
            for (int i = 0; i < pn; i++) pal.add(readState(in));
            int[] cx = new int[count], cy = new int[count], cz = new int[count];
            BlockState[] cs = new BlockState[count];
            for (int i = 0; i < count; i++) {
                cx[i] = in.readShort(); cy[i] = in.readShort(); cz[i] = in.readShort();
                int idx = in.readInt();
                cs[i] = (idx >= 0 && idx < pal.size()) ? pal.get(idx) : null;
            }
            String[][] texts = new String[count][];
            try {                                                    // starsze zapisy nie maja tej sekcji
                int tn = in.readInt();
                for (int k = 0; k < tn; k++) {
                    int ci = in.readInt();
                    String[] t = new String[4];
                    for (int q = 0; q < 4; q++) t[q] = in.readUTF();
                    if (ci >= 0 && ci < count) texts[ci] = t;
                }
            } catch (java.io.EOFException ignored) { }
            List<Flow.Cell> cells = new ArrayList<>(count);
            int missing = 0;
            for (int i = 0; i < count; i++) {
                if (cs[i] == null) { missing++; continue; }
                cells.add(new Flow.Cell(cx[i], cy[i], cz[i], cs[i], texts[i]));
            }
            Flow.setClipboard(cells.toArray(new Flow.Cell[0]), sx, sy, sz);
            return "Wczytano slot " + slot + " (" + cells.size() + " blokow, " + sx + "x" + sy + "x" + sz + ")"
                    + (missing > 0 ? " - pominieto " + missing + " nieznanych blokow" : "")
                    + ". Teraz: Postaw (menu) albo klawisz \\.";
        } catch (Throwable t) {
            Msg.log("blad odczytu slotu " + slot, t);
            return "Blad odczytu: " + t.getMessage();
        }
    }

    // ------------------------------------------------------------------ pomocnicze
    private static DataInputStream open(int slot) throws IOException {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file(slot)))));
    }

    private static void writeState(DataOutputStream out, BlockState st) throws IOException {
        out.writeUTF(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
        var props = st.getProperties();
        out.writeShort(props.size());
        for (Property<?> p : props) {
            out.writeUTF(p.getName());
            out.writeUTF(valueName(st, p));
        }
    }

    private static <T extends Comparable<T>> String valueName(BlockState st, Property<T> p) {
        return p.getName(st.getValue(p));
    }

    private static BlockState readState(DataInputStream in) throws IOException {
        String id = in.readUTF();
        int np = in.readShort();
        Block b = blocks().get(id);
        BlockState s = b != null ? b.defaultBlockState() : null;
        for (int k = 0; k < np; k++) {
            String pn = in.readUTF();
            String pv = in.readUTF();
            if (s != null) {
                Property<?> pr = b.getStateDefinition().getProperty(pn);
                if (pr != null) s = setProp(s, pr, pv);
            }
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState setProp(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(x -> s.setValue(p, x)).orElse(s);
    }

    /** Blok po id (np. "minecraft:white_concrete") albo null - odporne na zmiany nazw stalych w Blocks. */
    static Block blockById(String id) {
        return blocks().get(id);
    }

    private static Map<String, Block> blocks() {
        if (blockById == null) {
            Map<String, Block> m = new HashMap<>();
            for (Block b : BuiltInRegistries.BLOCK) m.put(BuiltInRegistries.BLOCK.getKey(b).toString(), b);
            blockById = m;
        }
        return blockById;
    }
}
