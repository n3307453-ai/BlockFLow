package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
        Msg.chat(String.format("%d/%d (%.1f%%), obrot %d\u00b0, %s, auto-budowa: %s",
                Flow.doneCount, Flow.n, Flow.percent(), Flow.rotationDegrees(),
                Flow.mirror.ordinal() != 0 ? "lustro" : "bez lustra",
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
        AutoBuilder.perTick = Math.max(1, Math.min(8, perTick));
        Msg.chat("Blokow na tick: " + AutoBuilder.perTick);
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
