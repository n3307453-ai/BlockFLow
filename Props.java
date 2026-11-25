package pl.blockflow;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Odczyt/zapis wlasciwosci blokow po nazwie (np. "facing", "hinge") - bez stalych z BlockStateProperties. */
final class Props {
    private Props() {}

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    private static <T extends Comparable<T>> BlockState setProp(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(x -> s.setValue(p, x)).orElse(s);
    }

    /** Wartosc wlasciwosci jako tekst ("north", "true", "lower"...) albo null, gdy blok jej nie ma. */
    static String get(BlockState s, String name) {
        if (s == null) return null;
        for (Property<?> p : s.getProperties()) if (p.getName().equals(name)) return valueName(s, p);
        return null;
    }

    static boolean has(BlockState s, String name) {
        return get(s, name) != null;
    }

    static BlockState set(BlockState s, String name, String value) {
        for (Property<?> p : s.getProperties()) if (p.getName().equals(name)) return setProp(s, p, value);
        return s;
    }

    /** Id bloku bez przestrzeni nazw, np. "oak_door". */
    static String id(BlockState s) {
        String t = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        return t.substring(t.indexOf(':') + 1);
    }

    static int num(String v) {
        try { return v == null ? 0 : Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }
}
