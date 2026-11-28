package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Wszystkie akcje uzytkownika (wspolne dla klawiszy i komend). */
final class Actions {
    private Actions() {}

    /** Blok, na ktory patrzysz; jesli brak - blok pod stopami. */
    static BlockPos looked(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK) return h.getBlockPos();
        return mc.player.blockPosition();
    }

    static void pos1(BlockPos p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Flow.pos1 = (p != null ? p : looked(mc)).immutable();
        Msg.chat("Punkt 1: " + fmt(Flow.pos1) + sizeInfo());
    }

    static void pos2(BlockPos p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Flow.pos2 = (p != null ? p : looked(mc)).immutable();
        Msg.chat("Punkt 2: " + fmt(Flow.pos2) + sizeInfo());
    }

    static void copy() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Msg.chat(Flow.copy(mc.level));
    }

    static void rotate(int deg) {
        if (Flow.clip.length == 0) { Msg.chat("Najpierw skopiuj budynek (copy)."); return; }
        int steps = Math.floorMod(deg / 90, 4);
        for (int i = 0; i < steps; i++) Flow.rotation = Flow.rotation.getRotated(Rotation.CLOCKWISE_90);
        Flow.rebuild();
        Msg.chat("Obrot: " + Flow.rotationDegrees() + "\u00b0" + (Flow.mirror.ordinal() != 0 ? " + lustro" : ""));
    }

    static void mirror() {
        if (Flow.clip.length == 0) { Msg.chat("Najpierw skopiuj budynek (copy)."); return; }
        Flow.toggleMirror();
        Msg.chat("Lustro: " + (Flow.mirror.ordinal() != 0 ? "wlaczone" : "wylaczone"));
    }

    static void place(BlockPos at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (Flow.clip.length == 0) { Msg.chat("Najpierw skopiuj budynek (copy)."); return; }
        BlockPos p = at;
        if (p == null) {
            boolean hit = mc.hitResult instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK;
            p = hit ? looked(mc).above() : mc.player.blockPosition();
        }
        Flow.setAnchor(p);
        Msg.chat("Budynek ustawiony w " + fmt(p) + " (naroznik). Obrot/lustro/move do poprawek, potem start.");
    }

    static void move(int dx, int dy, int dz) {
        if (Flow.anchor == null) { Msg.chat("Najpierw ustaw miejsce (place)."); return; }
        Flow.setAnchor(Flow.anchor.offset(dx, dy, dz));
        Msg.chat("Przesunieto: " + fmt(Flow.anchor));
    }

    /** Krok przesuwania (klawisze strzalek / menu). */
    static int step = 1;

    static void cycleStep() {
        step = step == 1 ? 5 : step == 5 ? 10 : 1;
    }

    /** Przesuniecie budowli wzgledem kierunku, w ktory patrzysz: przod/tyl/lewo/prawo/gora/dol. */
    static void nudge(String rel, int n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (Flow.anchor == null) { Msg.chat("Najpierw ustaw miejsce (place)."); return; }
        Direction f = mc.player.getDirection();
        Direction d = switch (rel) {
            case "przod" -> f;
            case "tyl" -> f.getOpposite();
            case "prawo" -> f.getClockWise();
            case "lewo" -> f.getCounterClockWise();
            case "gora" -> Direction.UP;
            default -> Direction.DOWN;
        };
        Flow.setAnchor(Flow.anchor.relative(d, n));
        Msg.chat("Przesunieto (" + rel + " x" + n + "): " + fmt(Flow.anchor));
    }

    // ------------------------------------------------------------------ kolejka, tempo
    static void queueAdd() { Msg.chat(Flow.queueAdd()); }

    static void queueClear() {
        Flow.queueClear();
        Msg.chat("Kolejka wyczyszczona.");
    }

    static void queueMode() {
        Flow.setParallel(!Flow.parallel);
        Msg.chat("Tryb kolejki: " + (Flow.parallel ? "WSZYSTKIE NARAZ (warstwami)" : "po kolei, budynek po budynku"));
    }

    static int buildings() { return Flow.bounds.size(); }

    /** Orientacyjny czas budowy w sekundach (bez czasu lotu). */
    static double etaSeconds() {
        int remaining = Math.max(0, Flow.n - Flow.doneCount);
        return remaining / (AutoBuilder.perTick * 20.0);
    }

    static void tempo(int blocksPerSecond) {
        AutoBuilder.perTick = Math.max(1, Math.min(64, (int) Math.round(blocksPerSecond / 20.0)));
        Msg.chat(String.format("Tempo: %d blokow na tick (%d/s). Pozostale %d blokow = ok. %.0f s (bez lotu).",
                AutoBuilder.perTick, AutoBuilder.perTick * 20, Math.max(0, Flow.n - Flow.doneCount), etaSeconds())
                + (AutoBuilder.perTick > 25 ? " Uwaga: wysokie tempo moze wyzwolic limity pakietow serwera." : ""));
    }

    /** Ustawia tempo tak, zeby pozostale bloki zbudowac w mniej wiecej tylu sekundach. */
    static void czas(int seconds) {
        int remaining = Math.max(1, Flow.n - Flow.doneCount);
        AutoBuilder.perTick = Math.max(1, Math.min(64, (int) Math.ceil(remaining / (Math.max(1, seconds) * 20.0))));
        Msg.chat(String.format("Cel: %d blokow w ok. %d s -> %d blokow na tick (%d/s). Rzeczywisty czas zalezy tez od lotu i serwera.",
                remaining, seconds, AutoBuilder.perTick, AutoBuilder.perTick * 20));
    }

    static void lot(double v) {
        AutoBuilder.speed = Math.max(0.3, Math.min(6.0, v));
        Msg.chat("Predkosc lotu: " + AutoBuilder.speed + " bloku/tick (serwer vanilla toleruje do ok. 10).");
    }

    static void togglePodpory() {
        AutoBuilder.useTemps = !AutoBuilder.useTemps;
        Msg.chat("Podpory z rusztowania dla blokow w powietrzu: " + (AutoBuilder.useTemps ? "TAK" : "nie"));
    }

    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        AutoBuilder.pause(mc);
        Flow.pos1 = null;
        Flow.pos2 = null;
        Flow.clearPlan();
        Msg.chat("Wyczyszczono.");
    }

    static void start() { AutoBuilder.start(Minecraft.getInstance()); }
    static void pause() { AutoBuilder.pause(Minecraft.getInstance()); }
    static void toggleBuild() { AutoBuilder.toggle(Minecraft.getInstance()); }

    static void status() {
        if (Flow.n == 0) { Msg.chat("Brak planu."); return; }
        Msg.chat(String.format("%d/%d (%.1f%%), budynkow: %d (%s), tempo %d/s (ok. %.0f s), auto-budowa: %s",
                Flow.doneCount, Flow.n, Flow.percent(), buildings(),
                Flow.parallel ? "naraz" : "po kolei", AutoBuilder.perTick * 20, etaSeconds(),
                AutoBuilder.running ? "dziala" : "stop"));
    }

    static void list() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (Flow.n == 0) { Msg.chat("Brak planu."); return; }
        Map<Block, Integer> rem = Flow.remaining();
        if (rem.isEmpty()) { Msg.chat("Wszystko postawione!"); return; }
        List<Map.Entry<Block, Integer>> es = new ArrayList<>(rem.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder("Brakuje " + (Flow.n - Flow.doneCount) + " blokow:");
        int shown = 0;
        for (var e : es) {
            if (shown++ >= 25) { sb.append("\n... i ").append(es.size() - 25).append(" innych"); break; }
            int need = e.getValue();
            int have = count(p, e.getKey().asItem());
            sb.append("\n").append(e.getKey().getName().getString())
              .append(": ").append(need)
              .append(" (").append(need / 64).append("st+").append(need % 64).append(")")
              .append(have >= need ? "  \u00a7a[masz]" : "  \u00a7c[masz " + have + "]").append("\u00a7f");
        }
        Msg.chat(sb.toString());
    }

    static void layer(Integer count) {
        if (count == null) Flow.layerMode = !Flow.layerMode;
        else { Flow.layerMode = true; Flow.layerCount = Math.max(1, count); }
        Msg.chat("Tryb warstw: " + (Flow.layerMode ? "wlaczony (" + Flow.layerCount + ")" : "wylaczony"));
    }

    static void range(int r) {
        Flow.range = Math.max(4, Math.min(128, r));
        Msg.chat("Zasieg podgladu: " + Flow.range);
    }

    static void speed(int perTick) {
        AutoBuilder.perTick = Math.max(1, Math.min(64, perTick));
        Msg.chat("Blokow na tick: " + AutoBuilder.perTick);
    }

    /** Wyciaga gracza z blokow: przesuwa w gore do pierwszego wolnego miejsca (2 bloki wysokosci). */
    static void unstuck(boolean announce) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        var lv = mc.level;
        if (p == null || lv == null) return;
        BlockPos bp = p.blockPosition();
        for (int dy = 0; dy <= 64; dy++) {
            BlockPos feet = bp.above(dy);
            BlockPos head = feet.above();
            boolean free = lv.getBlockState(feet).getCollisionShape(lv, feet).isEmpty()
                    && lv.getBlockState(head).getCollisionShape(lv, head).isEmpty();
            if (free) {
                if (dy > 0) {
                    p.setPos(p.getX(), feet.getY(), p.getZ());
                    Msg.chat("Wyciagnieto z blokow (o " + dy + " w gore).");
                } else if (announce) {
                    Msg.chat("Nie jestes w blokach.");
                }
                return;
            }
        }
        if (announce) Msg.chat("Nie znaleziono wolnego miejsca w gore - uzyj /tp.");
    }

    static void replace() {
        AutoBuilder.replace = !AutoBuilder.replace;
        Msg.chat("Niszczenie przeszkadzajacych blokow: " + (AutoBuilder.replace ? "TAK" : "nie"));
    }

    // ------------------------------------------------------------------
    private static int count(LocalPlayer p, Item it) {
        int c = 0;
        Inventory inv = p.getInventory();
        for (int s = 0; s < inv.getContainerSize(); s++) {
            ItemStack st = inv.getItem(s);
            if (st.is(it)) c += st.getCount();
        }
        return c;
    }

    private static String fmt(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static String sizeInfo() {
        if (Flow.pos1 == null || Flow.pos2 == null) return "";
        int w = Math.abs(Flow.pos1.getX() - Flow.pos2.getX()) + 1;
        int h = Math.abs(Flow.pos1.getY() - Flow.pos2.getY()) + 1;
        int d = Math.abs(Flow.pos1.getZ() - Flow.pos2.getZ()) + 1;
        return "  (obszar " + w + "x" + h + "x" + d + ")";
    }
}
