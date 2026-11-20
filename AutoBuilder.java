package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Auto-budowa (TYLKO creative): leci do kolejnego bloku planu, bierze go do reki
 * (creative-slot) i stawia zwyklym "uzyciem przedmiotu" - czysto po stronie klienta,
 * wiec dziala na kazdym serwerze, ktory pozwala na creative.
 */
final class AutoBuilder {
    private AutoBuilder() {}

    static final int HOT = 8;           // slot paska, ktory "pozyczamy" (potem oddajemy)
    static final double REACH = 3.6;    // max odleglosc oka od bloku przy stawianiu

    static boolean running;
    static int cursor;
    static boolean progressed;
    static boolean[] deferred = new boolean[0];
    static int[] attempts = new int[0];
    static int stuck, lastTarget = -1;
    static double lastDist;
    static int placedCount, skippedCount;
    static int targetIdx = -1;

    static double speed = 0.45;         // blokow/tick podczas lotu
    static int perTick = 1;             // ile blokow na tick
    static boolean replace = false;     // czy niszczyc przeszkadzajace bloki

    private static ItemStack saved = ItemStack.EMPTY;
    private static int savedSel = -1;
    private static boolean hasSaved;

    static void reset() {
        cursor = 0;
        progressed = false;
        stuck = 0;
        lastTarget = -1;
        targetIdx = -1;
        placedCount = 0;
        skippedCount = 0;
        deferred = new boolean[Flow.n];
        attempts = new int[Flow.n];
    }

    // ------------------------------------------------------------------ sterowanie
    static void start(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return;
        if (Flow.n == 0) { Msg.chat("Brak planu. Zrob: pos1, pos2, copy, place."); return; }
        if (!p.isCreative()) {
            Msg.chat("Auto-budowa dziala tylko w creative. W survivalu masz podglad i /blockflow list.");
            return;
        }
        if (!running) {
            Inventory inv = p.getInventory();
            saved = inv.getItem(HOT).copy();
            savedSel = inv.getSelectedSlot();
            hasSaved = true;
        }
        running = true;
        Msg.chat("Budowa wystartowala. Pauza: ten sam klawisz lub /blockflow pause.");
    }

    static void pause(Minecraft mc) {
        if (!running) return;
        running = false;
        restore(mc);
        Msg.chat("Pauza (" + Flow.doneCount + "/" + Flow.n + ").");
    }

    static void toggle(Minecraft mc) {
        if (running) pause(mc); else start(mc);
    }

    private static void restore(Minecraft mc) {
        if (!hasSaved) return;
        hasSaved = false;
        LocalPlayer p = mc.player;
        MultiPlayerGameMode gm = mc.gameMode;
        if (p == null || gm == null || !p.isCreative()) return;
        Inventory inv = p.getInventory();
        inv.setItem(HOT, saved.copy());
        gm.handleCreativeModeItemAdd(saved.copy(), 36 + HOT);
        if (savedSel >= 0) inv.setSelectedSlot(savedSel);
    }

    /** Wywolywane przy wyjsciu ze swiata. */
    static void abort() {
        running = false;
        hasSaved = false;
    }

    // ------------------------------------------------------------------ petla
    static void tick(Minecraft mc) {
        if (!running) return;
        LocalPlayer p = mc.player;
        ClientLevel lv = mc.level;
        MultiPlayerGameMode gm = mc.gameMode;
        if (p == null || lv == null || gm == null) { abort(); return; }
        if (!p.isCreative()) { Msg.chat("Wyszedles z creative - wstrzymano."); pause(mc); return; }

        for (int k = 0; k < perTick; k++) {
            int i = next(lv);
            if (i < 0) { finish(mc); return; }
            targetIdx = i;
            if (!stepOne(mc, p, lv, gm, i)) break;
        }
    }

    private static void finish(Minecraft mc) {
        running = false;
        restore(mc);
        targetIdx = -1;
        Msg.chat("Gotowe! " + Flow.doneCount + "/" + Flow.n
                + (skippedCount > 0 ? " (pominieto " + skippedCount + " - przeszkoda/brak podparcia)" : ""));
    }

    /** Nastepny indeks planu do postawienia albo -1. */
    private static int next(ClientLevel lv) {
        int n = Flow.n;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (true) {
            while (cursor < n) {
                int i = Flow.order[cursor];
                if (Flow.skip[i] || deferred[i]) { cursor++; continue; }
                mp.set(Flow.wx[i], Flow.wy[i], Flow.wz[i]);
                if (lv.getBlockState(mp).getBlock() == Flow.ws[i].getBlock()) {
                    if (!Flow.done[i]) { Flow.done[i] = true; Flow.doneCount++; }
                    cursor++;
                    continue;
                }
                return i;
            }
            boolean anyDeferred = false;
            for (boolean b : deferred) if (b) { anyDeferred = true; break; }
            if (anyDeferred && progressed) {
                Arrays.fill(deferred, false);
                cursor = 0;
                progressed = false;
                continue;
            }
            return -1;
        }
    }

    /** true = postawiono blok (mozna kolejny w tym ticku). */
    private static boolean stepOne(Minecraft mc, LocalPlayer p, ClientLevel lv, MultiPlayerGameMode gm, int i) {
        BlockPos pos = new BlockPos(Flow.wx[i], Flow.wy[i], Flow.wz[i]);
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 eye = p.getEyePosition();
        double dist = eye.distanceTo(c);

        var ab = p.getAbilities();
        if (!ab.flying) {
            ab.flying = true;
            p.onUpdateAbilities();
        }

        // --- lot do celu
        if (dist > REACH) {
            Vec3 aim = c.add(0, 2.2, 0);
            Vec3 d = aim.subtract(eye);
            double len = d.length();
            if (len > 1.0E-6) p.setDeltaMovement(d.scale(Math.min(speed, len) / len));
            lookAt(p, c);
            if (i == lastTarget && Math.abs(dist - lastDist) < 0.01) stuck++; else stuck = 0;
            lastTarget = i;
            lastDist = dist;
            if (stuck > 60) {   // utknal (np. przeszkoda) - pomijamy
                Flow.skip[i] = true;
                skippedCount++;
                stuck = 0;
            }
            return false;
        }
        p.setDeltaMovement(Vec3.ZERO);
        stuck = 0;

        // --- co jest na miejscu
        BlockState want = Flow.ws[i];
        BlockState cur = lv.getBlockState(pos);
        if (!cur.canBeReplaced()) {
            if (!replace) { Flow.skip[i] = true; skippedCount++; return false; }
            gm.destroyBlock(pos);
            return false;
        }
        Item item = want.getBlock().asItem();
        if (item == Items.AIR) { Flow.skip[i] = true; skippedCount++; return false; }

        // --- obrot gracza (schody, piece itp.) - czekamy tick, zeby serwer dostal obrot
        Float yaw = desiredYaw(want);
        if (yaw != null && Math.abs(Mth.wrapDegrees(p.getYRot() - yaw)) > 2f) {
            p.setYRot(yaw);
            return false;
        }

        BlockHitResult hit = support(lv, pos, isTop(want));
        if (hit == null) {            // nie ma o co oprzec - sprobujemy pozniej
            deferred[i] = true;
            cursor++;
            return false;
        }

        give(p, gm, item);
        gm.useItemOn(p, InteractionHand.MAIN_HAND, hit);
        p.swing(InteractionHand.MAIN_HAND);
        progressed = true;
        placedCount++;
        if (++attempts[i] > 6) { Flow.skip[i] = true; skippedCount++; }
        return true;
    }

    // ------------------------------------------------------------------ pomocnicze
    private static void give(LocalPlayer p, MultiPlayerGameMode gm, Item item) {
        Inventory inv = p.getInventory();
        ItemStack cur = inv.getItem(HOT);
        if (!cur.is(item)) {
            ItemStack s = new ItemStack(item, 64);
            inv.setItem(HOT, s.copy());
            gm.handleCreativeModeItemAdd(s, 36 + HOT);
        }
        if (inv.getSelectedSlot() != HOT) inv.setSelectedSlot(HOT);
    }

    private static void lookAt(LocalPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        p.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        p.setXRot((float) (-Math.toDegrees(Math.atan2(d.y, h))));
    }

    private static Float desiredYaw(BlockState s) {
        if (s.getBlock() instanceof StairBlock && s.hasProperty(StairBlock.FACING))
            return s.getValue(StairBlock.FACING).toYRot();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            return s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot();
        return null;
    }

    private static boolean isTop(BlockState s) {
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) return s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP;
        if (s.hasProperty(BlockStateProperties.HALF)) return s.getValue(BlockStateProperties.HALF) == Half.TOP;
        return false;
    }

    /** Znajduje sasiedni blok, o ktory mozna oprzec stawiany blok. */
    private static BlockHitResult support(ClientLevel lv, BlockPos pos, boolean top) {
        Direction[] order = top
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}
                : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        BlockHitResult weak = null;
        for (Direction d : order) {
            BlockPos nb = pos.relative(d);
            BlockState ns = lv.getBlockState(nb);
            if (ns.isAir() || ns.canBeReplaced()) continue;
            Direction face = d.getOpposite();      // sciana sasiada, ktora dotyka naszego bloku
            double hx = nb.getX() + 0.5 + face.getStepX() * 0.5;
            double hy = nb.getY() + 0.5 + face.getStepY() * 0.5;
            double hz = nb.getZ() + 0.5 + face.getStepZ() * 0.5;
            if (face.getAxis().isHorizontal()) hy = nb.getY() + (top ? 0.75 : 0.25);
            BlockHitResult h = new BlockHitResult(new Vec3(hx, hy, hz), face, nb, false);
            if (ns.isCollisionShapeFullBlock(lv, nb)) return h;   // preferuj pelne bloki
            if (weak == null) weak = h;
        }
        return weak;
    }
}
