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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-budowa (TYLKO creative), czysto po stronie klienta.
 *
 * Budowa WARSTWAMI: od podlogi w gore, cala warstwa przed nastepna. Bot leci nad biezaca warstwa
 * (trasa po wolnych miejscach - Fly), wiec nie wchodzi w postawione bloki i serwer niczego nie cofa.
 *
 * Bezpieczniki: wlasny ruch gracza (WASD/spacja/shift) wstrzymuje bota; bot, ktory nic nie robi,
 * pomija blok; po pauzie/koncu gracz jest wyciagany z blokow.
 */
final class AutoBuilder {
    private AutoBuilder() {}

    static final int HOT = 8;            // slot paska, ktory "pozyczamy" (potem oddajemy)
    static final double REACH = 4.2;     // max odleglosc oka od srodka bloku przy stawianiu

    static boolean running;
    static int cursor;
    static boolean progressed;
    static boolean[] deferred = new boolean[0];
    static int[] attempts = new int[0];
    static int[] destroys = new int[0];
    static int placedCount, skippedCount, approxCount, destroyCount;
    static int targetIdx = -1;
    static int maxPlacedY = Integer.MIN_VALUE;

    static double speed = 0.45;          // blokow/tick podczas lotu
    static int perTick = 1;              // ile blokow na tick
    static boolean replace = false;      // czy niszczyc przeszkadzajace (obce) bloki
    static boolean useTemps = true;      // tymczasowe podpory dla blokow wiszacych w powietrzu

    private static int flyingTo = -1;
    private static List<Vec3> path;
    private static int pathIdx;
    private static int replans;
    private static Vec3 expected;
    private static int rubber, idle, idleSkips, manualTicks;
    private static boolean moved;
    private static boolean approachFailed;

    // tymczasowe podpory (stawiane, a po postawieniu docelowego bloku zdejmowane)
    private static final int AUX_KEY = Integer.MIN_VALUE + 7;
    private static final ArrayDeque<BlockPos> tempQueue = new ArrayDeque<>();
    private static final ArrayList<BlockPos> tempPlaced = new ArrayList<>();
    private static final ArrayDeque<BlockPos> removeQueue = new ArrayDeque<>();
    private static int tempFor = -1;
    private static int[] tempRounds = new int[0];

    // budowa warstwami od podlogi w gore
    private static int layerY = Integer.MIN_VALUE;
    private static int layerStart;
    private static boolean layerProgress;

    private static ItemStack saved = ItemStack.EMPTY;
    private static int savedSel = -1;
    private static boolean hasSaved;

    /** Plan jednego stawiania: wymagany obrot gracza (null = bez zmiany) i klikniecie. */
    private record Plan(Float yaw, Float pitch, BlockHitResult hit) {}

    static void reset() {
        cursor = 0;
        progressed = false;
        targetIdx = -1;
        placedCount = 0;
        skippedCount = 0;
        approxCount = 0;
        destroyCount = 0;
        maxPlacedY = Integer.MIN_VALUE;
        resetFlight();
        rubber = 0;
        idle = 0;
        idleSkips = 0;
        manualTicks = 0;
        layerY = Integer.MIN_VALUE;
        layerStart = 0;
        layerProgress = false;
        deferred = new boolean[Flow.n];
        attempts = new int[Flow.n];
        destroys = new int[Flow.n];
        tempRounds = new int[Flow.n];
        tempQueue.clear();
        tempPlaced.clear();
        removeQueue.clear();
        tempFor = -1;
        approachFailed = false;
    }

    private static void resetFlight() {
        flyingTo = -1;
        path = null;
        pathIdx = 0;
        replans = 0;
        expected = null;
    }

    static double topBuilt() {
        int t = Flow.scanWrapped ? Math.max(Flow.maxDoneY, maxPlacedY) : Flow.anchor.getY() + Flow.sy;
        return t;
    }

    // ------------------------------------------------------------------ sterowanie
    static void start(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return;
        if (Flow.n == 0) { Msg.chat("Brak planu. Zrob: pos1, pos2, copy, place (albo menu: prawy Shift)."); return; }
        if (!p.isCreative()) {
            Msg.chat("Auto-budowa dziala tylko w creative. W survivalu masz podglad i /blockflow list.");
            return;
        }
        if (!running) {
            Inventory inv = p.getInventory();
            saved = inv.getItem(HOT).copy();
            savedSel = inv.getSelectedSlot();
            hasSaved = true;
            idle = 0;
            idleSkips = 0;
            manualTicks = 0;
            rubber = 0;
            resetFlight();
        }
        running = true;
        Msg.chat("Budowa warstwami od podlogi w gore wystartowala. "
                + "Pauza: ten sam klawisz, /bf pause albo po prostu sam sie poruszaj.");
    }

    static void pause(Minecraft mc) {
        if (!running) return;
        running = false;
        restore(mc);
        afterStop();
        Msg.chat("Pauza (" + Flow.doneCount + "/" + Flow.n + ").");
    }

    static void toggle(Minecraft mc) {
        if (running) pause(mc); else start(mc);
    }

    private static void afterStop() {
        resetFlight();
        Actions.unstuck(false);
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
        resetFlight();
        tempQueue.clear();
        removeQueue.clear();
    }

    // ------------------------------------------------------------------ petla
    static void tick(Minecraft mc) {
        if (!running) return;
        LocalPlayer p = mc.player;
        ClientLevel lv = mc.level;
        MultiPlayerGameMode gm = mc.gameMode;
        if (p == null || lv == null || gm == null) { abort(); return; }
        if (!p.isCreative()) { Msg.chat("Wyszedles z creative - wstrzymano."); pause(mc); return; }

        // gracz sam sie rusza -> oddajemy sterowanie
        if (Manual.pressed(mc)) {
            if (++manualTicks >= 3) {
                manualTicks = 0;
                Msg.chat("Przejales sterowanie - pauza (wznow klawiszem budowy).");
                pause(mc);
                return;
            }
        } else {
            manualTicks = 0;
        }

        int before = placedCount + destroyCount + skippedCount + approxCount + Flow.doneCount;
        moved = false;

        // zadania pomocnicze: najpierw zdejmowanie starych podpor, potem stawianie nowych
        if (!removeQueue.isEmpty() || !tempQueue.isEmpty()) {
            boolean place = removeQueue.isEmpty();
            BlockPos pos = place ? tempQueue.peek() : removeQueue.peek();
            if (auxStep(p, lv, gm, pos, place)) {
                if (place) tempPlaced.add(tempQueue.poll()); else removeQueue.poll();
            }
            idle = 0;
            return;
        }

        for (int k = 0; k < perTick; k++) {
            int i = next(lv);
            if (i < 0) { finish(mc); return; }
            targetIdx = i;
            if (!stepOne(p, lv, gm, i)) break;
        }
        int after = placedCount + destroyCount + skippedCount + approxCount + Flow.doneCount;

        // bezpiecznik: nic sie nie dzieje -> pomin blok, a przy powtorkach zatrzymaj bota
        if (moved || after != before) {
            idle = 0;
        } else if (++idle > 100) {
            idle = 0;
            if (targetIdx >= 0 && targetIdx < Flow.n) skip(targetIdx);
            resetFlight();
            if (++idleSkips > 5) {
                idleSkips = 0;
                Msg.chat("Bot sie zacial - pauza. Sprawdz /bf status albo /bf unstuck.");
                pause(mc);
            }
        }
    }

    private static void finish(Minecraft mc) {
        running = false;
        restore(mc);
        targetIdx = -1;
        afterStop();
        String extra = "";
        if (skippedCount > 0) extra += " (pominieto " + skippedCount + " - przeszkoda/brak podparcia)";
        if (approxCount > 0) extra += " (" + approxCount + " z przyblizonym stanem)";
        Msg.chat("Gotowe! " + Flow.doneCount + "/" + Flow.n + extra);
    }

    private static void markDone(int i) {
        if (tempFor == i && !tempPlaced.isEmpty()) {          // blok stoi - podpory juz niepotrzebne
            removeQueue.addAll(tempPlaced);
            tempPlaced.clear();
            tempFor = -1;
        }
        if (!Flow.done[i]) { Flow.done[i] = true; Flow.doneCount++; }
        if (Flow.wy[i] > Flow.maxDoneY) Flow.maxDoneY = Flow.wy[i];
    }

    /** Czy w biezacej warstwie zostaly bloki odlozone (bez podparcia). */
    private static boolean layerHasDeferred(int end) {
        for (int k = layerStart; k < end; k++) {
            int j = Flow.order[k];
            if (deferred[j] && !Flow.skip[j] && !Flow.done[j]) return true;
        }
        return false;
    }

    /**
     * Nastepny indeks planu do postawienia albo -1.
     * Budowa WARSTWAMI: cala warstwa (jeden poziom Y) jest konczona, zanim bot wzniesie sie wyzej.
     * Bloki bez podparcia w warstwie sa ponawiane po reszcie tej samej warstwy.
     */
    private static int next(ClientLevel lv) {
        int n = Flow.n;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (true) {
            while (cursor < n) {
                int i = Flow.order[cursor];
                if (Flow.wy[i] != layerY) {
                    // koniec warstwy: jesli cos czeka na podparcie, a w warstwie cos postawilismy - ponow
                    if (layerProgress && layerHasDeferred(cursor)) {
                        for (int k = layerStart; k < cursor; k++) deferred[Flow.order[k]] = false;
                        cursor = layerStart;
                        layerProgress = false;
                        continue;
                    }
                    layerY = Flow.wy[i];
                    layerStart = cursor;
                    layerProgress = false;
                }
                if (Flow.skip[i] || deferred[i]) { cursor++; continue; }
                mp.set(Flow.wx[i], Flow.wy[i], Flow.wz[i]);
                if (Match.same(lv.getBlockState(mp), Flow.ws[i])) {
                    markDone(i);
                    cursor++;
                    continue;
                }
                return i;
            }
            // koniec planu: ostatnia warstwa
            if (layerProgress && layerHasDeferred(n)) {
                for (int k = layerStart; k < n; k++) deferred[Flow.order[k]] = false;
                cursor = layerStart;
                layerProgress = false;
                continue;
            }
            boolean anyDeferred = false;
            for (boolean b : deferred) if (b) { anyDeferred = true; break; }
            if (anyDeferred && progressed) {          // przebieg koncowy dla reszty
                Arrays.fill(deferred, false);
                cursor = 0;
                layerY = Integer.MIN_VALUE;
                progressed = false;
                continue;
            }
            return -1;
        }
    }

    /** true = mozna od razu brac kolejny blok w tym ticku. */
    private static boolean stepOne(LocalPlayer p, ClientLevel lv, MultiPlayerGameMode gm, int i) {
        BlockPos pos = new BlockPos(Flow.wx[i], Flow.wy[i], Flow.wz[i]);
        if (!approach(p, lv, pos, i)) {
            if (approachFailed) { approachFailed = false; skip(i); }
            return false;
        }

        BlockState want = Flow.ws[i];
        BlockState cur = lv.getBlockState(pos);

        if (Match.same(cur, want)) { markDone(i); return true; }

        Item item = want.getBlock().asItem();
        if (item == Items.AIR) { skip(i); return false; }

        // --- podwojna plyta: klikamy plyte, ktora juz stoi
        if (want.hasProperty(BlockStateProperties.SLAB_TYPE)
                && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE
                && cur.getBlock() == want.getBlock()
                && cur.hasProperty(BlockStateProperties.SLAB_TYPE)
                && cur.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
            boolean bottom = cur.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM;
            Vec3 hv = new Vec3(pos.getX() + 0.5, pos.getY() + (bottom ? 1.0 : 0.0), pos.getZ() + 0.5);
            BlockHitResult hit = new BlockHitResult(hv, bottom ? Direction.UP : Direction.DOWN, pos, false);
            use(p, gm, item, hit, i, pos);
            return true;
        }

        // --- ten sam blok, ale zly stan: burzymy i probujemy innego sposobu
        if (cur.getBlock() == want.getBlock()) {
            if (attempts[i] >= 3) {         // trzy sposoby wyprobowane - akceptujemy przyblizenie
                Flow.approx[i] = true;
                Flow.skip[i] = true;
                approxCount++;
                markDone(i);
                return false;
            }
            return destroy(gm, pos, i);
        }

        // --- obcy blok na miejscu
        if (!cur.canBeReplaced()) {
            if (!replace) { skip(i); return false; }
            return destroy(gm, pos, i);
        }

        // --- wybor sposobu stawiania (3 strategie)
        int strat = attempts[i];
        Plan pl = null;
        for (int k = 0; k < 3 && pl == null; k++) pl = plan(lv, pos, want, strat + k);
        if (pl == null) {               // nie ma o co oprzec
            if (prepareTemps(lv, pos, i)) return false;     // postawimy tymczasowa podpore
            deferred[i] = true;                             // inaczej - sprobujemy pozniej
            cursor++;
            return false;
        }

        // --- obrot gracza: czekamy tick, zeby serwer dostal nowy obrot
        boolean needYaw = pl.yaw() != null && Math.abs(Mth.wrapDegrees(p.getYRot() - pl.yaw())) > 2f;
        boolean needPitch = pl.pitch() != null && Math.abs(p.getXRot() - pl.pitch()) > 2f;
        if (needYaw || needPitch) {
            if (pl.yaw() != null) p.setYRot(pl.yaw());
            if (pl.pitch() != null) p.setXRot(pl.pitch());
            return false;
        }

        use(p, gm, item, pl.hit(), i, pos);
        return true;
    }

    /** Burzenie bloku z limitem prob (serwer moze odmowic - np. chroniony teren). */
    private static boolean destroy(MultiPlayerGameMode gm, BlockPos pos, int i) {
        if (++destroys[i] > 4) { skip(i); return false; }
        gm.destroyBlock(pos);
        destroyCount++;
        return false;
    }

    private static void use(LocalPlayer p, MultiPlayerGameMode gm, Item item, BlockHitResult hit, int i, BlockPos pos) {
        give(p, gm, item);
        gm.useItemOn(p, InteractionHand.MAIN_HAND, hit);
        progressed = true;
        layerProgress = true;
        placedCount++;
        if (pos.getY() > maxPlacedY) maxPlacedY = pos.getY();
        if (++attempts[i] > 6) skip(i);
    }

    private static void skip(int i) {
        Flow.skip[i] = true;
        skippedCount++;
        resetFlight();
        if (tempFor == i) {
            removeQueue.addAll(tempPlaced);
            tempPlaced.clear();
            tempQueue.clear();
            tempFor = -1;
        }
    }

    // ------------------------------------------------------------------ lot
    /** Dolot w zasieg bloku (trasa bezkolizyjna). true = jestesmy na miejscu i mozna dzialac. */
    private static boolean approach(LocalPlayer p, ClientLevel lv, BlockPos pos, int key) {
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 eye = p.getEyePosition();
        double dist = eye.distanceTo(c);
        double eyeH = eye.y - p.getY();

        var ab = p.getAbilities();
        if (!ab.flying) {
            ab.flying = true;
            p.onUpdateAbilities();
        }

        boolean inReach = dist <= REACH - 0.2 && Fly.freeAt(lv, p.getX(), p.getY(), p.getZ());
        if (flyingTo != key && !inReach) {
            Vec3 hover = Fly.pickHover(lv, c, eyeH, REACH - 0.3);
            if (hover == null) { approachFailed = true; return false; }     // zewszad zablokowane
            List<Vec3> pth = Fly.plan(lv, p.position(), hover, topBuilt());
            if (pth == null) {
                if (!Fly.freeAt(lv, p.getX(), p.getY(), p.getZ())) {   // utknal w bloku
                    Actions.unstuck(false);
                    return false;
                }
                if (++replans > 3) { replans = 0; approachFailed = true; }
                return false;
            }
            path = pth;
            pathIdx = 0;
            flyingTo = key;
            replans = 0;
        }
        if (flyingTo == key) {
            if (!follow(p, lv, c)) return false;     // jeszcze lecimy
            flyingTo = -1;
        }
        expected = null;
        p.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** Krok zadania pomocniczego (podpora/zdjecie). true = zadanie zakonczone (lub porzucone). */
    private static boolean auxStep(LocalPlayer p, ClientLevel lv, MultiPlayerGameMode gm, BlockPos pos, boolean place) {
        if (!approach(p, lv, pos, AUX_KEY)) {
            if (approachFailed) { approachFailed = false; return true; }   // nie da sie dolecic - porzucamy
            return false;
        }
        BlockState cur = lv.getBlockState(pos);
        if (place) {
            if (!cur.canBeReplaced()) return true;
            BlockHitResult hit = support(lv, pos, false, null, null, false);
            if (hit == null) return true;
            give(p, gm, Blocks.COBBLESTONE.asItem());
            gm.useItemOn(p, InteractionHand.MAIN_HAND, hit);
            return true;
        }
        if (!cur.isAir()) { gm.destroyBlock(pos); destroyCount++; }
        return true;
    }

    private static boolean hasSolidNeighbor(ClientLevel lv, BlockPos c) {
        for (Direction d : Direction.values()) {
            BlockPos n = c.relative(d);
            BlockState ns = lv.getBlockState(n);
            if (!ns.isAir() && !ns.canBeReplaced() && ns.isCollisionShapeFullBlock(lv, n)) return true;
        }
        return false;
    }

    /**
     * Blok wisi w powietrzu: szukamy najkrotszej sciezki pustych komorek (do 4) od miejsca bloku do
     * najblizszego litego bloku i stawiamy tam tymczasowe podpory (cobblestone). Po postawieniu
     * docelowego bloku bot je zdejmuje. true = zaplanowano podpory.
     */
    private static boolean prepareTemps(ClientLevel lv, BlockPos pos, int i) {
        if (!useTemps || tempRounds[i] >= 3) return false;
        if (Flow.ws[i].getBlock() instanceof FallingBlock) return false;   // piasek/zwir spadlby po zdjeciu podpory
        if (hasSolidNeighbor(lv, pos)) return false;                         // podparcie jest, problemem jest inne ograniczenie

        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Map<BlockPos, Integer> depth = new HashMap<>();
        q.add(pos);
        parent.put(pos, null);
        depth.put(pos, 0);
        BlockPos found = null;
        search:
        while (!q.isEmpty()) {
            BlockPos cur = q.poll();
            int d = depth.get(cur);
            if (d >= 4) continue;
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (parent.containsKey(nb)) continue;
                BlockState ns = lv.getBlockState(nb);
                if (!(ns.isAir() || ns.canBeReplaced())) continue;
                if (Flow.inPlan(nb.getX(), nb.getY(), nb.getZ())) continue;     // nie zajmuj miejsc budowli
                parent.put(nb, cur);
                depth.put(nb, d + 1);
                if (hasSolidNeighbor(lv, nb)) { found = nb; break search; }
                q.add(nb);
            }
        }
        if (found == null) return false;

        tempRounds[i]++;
        tempFor = i;
        tempPlaced.clear();
        BlockPos cur = found;
        while (cur != null && !cur.equals(pos)) {       // od litego bloku w strone celu
            tempQueue.add(cur);
            cur = parent.get(cur);
        }
        return true;
    }

    /**
     * Jeden krok lotu po trasie. Zwraca true dopiero w tyku PO dotarciu (serwer zdazy poznac pozycje).
     */
    private static boolean follow(LocalPlayer p, ClientLevel lv, Vec3 target) {
        if (path != null && pathIdx >= path.size()) {   // dotarlismy w poprzednim ticku
            expected = null;
            return true;
        }
        if (path == null) { flyingTo = -1; return false; }

        // serwer cofnal gracza? (rubber-banding)
        if (expected != null && p.position().distanceTo(expected) > 0.7) {
            rubber++;
            path = null;
            flyingTo = -1;
            expected = null;
            if (rubber >= 8) {
                rubber = 0;
                Msg.chat("Serwer ciagle cofa gracza - zatrzymuje bota. Sprawdz uprawnienia / zasady serwera.");
                running = false;
            }
            return false;
        }

        Vec3 pos = p.position();
        Vec3 tgt = path.get(pathIdx);
        Vec3 d = tgt.subtract(pos);
        double len = d.length();
        Vec3 np;
        if (len <= speed) {
            np = tgt;
            pathIdx++;
        } else {
            np = pos.add(d.scale(speed / len));
        }
        if (!Fly.freeAt(lv, np.x, np.y, np.z)) {   // swiat sie zmienil - przeliczymy trase
            path = null;
            flyingTo = -1;
            return false;
        }
        p.setPos(np.x, np.y, np.z);
        p.setDeltaMovement(Vec3.ZERO);
        expected = np;
        moved = true;
        lookAt(p, target);
        return false;
    }

    // ------------------------------------------------------------------ stawianie
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

    private static boolean isTop(BlockState s) {
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) return s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP;
        if (s.hasProperty(BlockStateProperties.HALF)) return s.getValue(BlockStateProperties.HALF) == Half.TOP;
        return false;
    }

    /**
     * Sposob stawiania nr {@code strat} dla danego stanu:
     *  - kierunek poziomy: 0/1 = obrot gracza (w te lub druga strone), 2 = klikniecie sciany o tym kierunku
     *  - kierunek 6-stronny: 0/1 = patrzenie (przeciwnie/zgodnie), 2 = klikniecie sciany
     *  - os (belki, kolumny): parzysty = podparcie wzdluz osi, nieparzysty = dowolne
     */
    private static Plan plan(ClientLevel lv, BlockPos pos, BlockState want, int strat) {
        // Rusztowanie: klikniecie BOKU rusztowania pod spodem = rosnie w gore (gorna sciana = w bok!)
        if (want.getBlock() instanceof ScaffoldingBlock) {
            BlockPos below = pos.below();
            if (lv.getBlockState(below).getBlock() instanceof ScaffoldingBlock) {
                Vec3 hv = new Vec3(below.getX() + 0.5, below.getY() + 0.5, below.getZ());   // sciana polnocna
                return new Plan(null, null, new BlockHitResult(hv, Direction.NORTH, below, false));
            }
            BlockHitResult h = support(lv, pos, false, null, null, true);
            return h == null ? null : new Plan(null, null, h);
        }

        boolean top = isTop(want);
        Direction hf = want.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? want.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
        Direction f6 = want.hasProperty(BlockStateProperties.FACING)
                ? want.getValue(BlockStateProperties.FACING) : null;
        Direction.Axis axis = want.hasProperty(BlockStateProperties.AXIS)
                ? want.getValue(BlockStateProperties.AXIS) : null;

        Direction forced = null;
        Direction.Axis ax = null;
        Float yaw = null, pitch = null;

        if (hf != null) {
            boolean stair = want.getBlock() instanceof StairBlock;     // schody patrza "zgodnie", reszta "przeciwnie"
            switch (Math.floorMod(strat, 3)) {
                case 0 -> yaw = (stair ? hf : hf.getOpposite()).toYRot();
                case 1 -> yaw = (stair ? hf.getOpposite() : hf).toYRot();
                default -> forced = hf;
            }
        } else if (f6 != null) {
            int m = Math.floorMod(strat, 3);
            if (m == 2) {
                forced = f6;
            } else {
                Direction look = (m == 0) ? f6.getOpposite() : f6;
                if (look == Direction.UP) pitch = -90f;
                else if (look == Direction.DOWN) pitch = 90f;
                else { yaw = look.toYRot(); pitch = 0f; }
            }
        } else if (axis != null) {
            if (Math.floorMod(strat, 2) == 0) ax = axis;
        }

        BlockHitResult hit = support(lv, pos, top, forced, ax, false);
        return hit == null ? null : new Plan(yaw, pitch, hit);
    }

    /**
     * Szuka sasiedniego bloku, o ktory mozna oprzec stawiany blok.
     * @param forcedFace jesli != null: klikana sciana musi byc ta
     * @param axis       jesli != null: sasiad musi lezec na tej osi
     * @param avoidScaffold pomija rusztowania jako podparcie
     */
    private static BlockHitResult support(ClientLevel lv, BlockPos pos, boolean top,
                                          Direction forcedFace, Direction.Axis axis, boolean avoidScaffold) {
        Direction[] order = top
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}
                : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        BlockHitResult weak = null;
        for (Direction d : order) {
            Direction face = d.getOpposite();                  // sciana sasiada, ktora dotyka naszego bloku
            if (forcedFace != null && face != forcedFace) continue;
            if (axis != null && d.getAxis() != axis) continue;
            BlockPos nb = pos.relative(d);
            BlockState ns = lv.getBlockState(nb);
            if (ns.isAir() || ns.canBeReplaced()) continue;
            if (avoidScaffold && ns.getBlock() instanceof ScaffoldingBlock) continue;
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
