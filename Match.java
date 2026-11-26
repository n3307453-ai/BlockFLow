package pl.blockflow;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Porownanie 1:1: ten sam blok i te same wlasciwosci, ktore da sie ustawic przy stawianiu lub kliknieciem
 * (kierunek, os, polowa, plyta, zawias, otwarcie, dzwignia, opoznienie przekaznika, stos swiec itd.).
 * Wlasciwosci wyliczane przez swiat (polaczenia plotow, ksztalt schodow, zasilenie, ciekle) sa pomijane.
 */
final class Match {
    private Match() {}

    private static final String[] ALWAYS = {"facing", "axis", "half", "part", "hinge", "face", "rotation", "attachment",
            "hanging", "orientation", "vertical_direction"};
    private static final String[] STACK = {"candles", "pickles", "layers", "eggs", "flower_amount", "segment_amount"};
    private static final String[] CLICKABLE = {"open", "powered", "delay", "mode", "note", "inverted"};

    static boolean same(BlockState cur, BlockState want) {
        return core(cur, want) && clickable(cur, want);
    }

    /** Takie same poza stanami, ktore mozna poprawic kliknieciem (otwarcie, dzwignia, przekaznik). */
    static boolean sameExceptClickable(BlockState cur, BlockState want) {
        return cur.getBlock() == want.getBlock() && core(cur, want);
    }

    private static boolean core(BlockState cur, BlockState want) {
        if (cur.getBlock() != want.getBlock()) return false;
        for (String n : ALWAYS) if (!eq(cur, want, n)) return false;
        String t = Props.get(want, "type");
        if (t != null && (t.equals("top") || t.equals("bottom") || t.equals("double")) && !eq(cur, want, "type")) return false;
        for (String n : STACK) if (!eq(cur, want, n)) return false;
        return true;
    }

    /** Czy blok ma jedna z klikanych wlasciwosci, ktora mozna ustawic recznie. */
    static boolean isClickable(BlockState want, String prop) {
        String id = Props.id(want);
        switch (prop) {
            case "open": return (id.contains("door") || id.contains("fence_gate")) && !id.startsWith("iron");
            case "powered": return id.equals("lever");
            case "delay": return id.equals("repeater");
            case "mode": return id.equals("comparator");
            case "note": return id.equals("note_block");
            case "inverted": return id.contains("daylight_detector");
            default: return false;
        }
    }

    private static boolean clickable(BlockState cur, BlockState want) {
        for (String n : CLICKABLE) if (isClickable(want, n) && !eq(cur, want, n)) return false;
        return true;
    }

    private static boolean eq(BlockState a, BlockState b, String name) {
        String x = Props.get(a, name), y = Props.get(b, name);
        return x == null || y == null || x.equals(y);
    }
}
