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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Auto-budowa (TYLKO creative), czysto po stronie klienta.
 *
 * - Budowa WARSTWAMI od podlogi w gore; lot bezkolizyjny nad biezaca warstwa (Fly).
 * - Stawianie 1:1: dla kazdego bloku do 3 sposobow (obrot gracza, klikana sciana, os, miejsce klikniecia),
 *   weryfikacja stanu, burzenie i ponowna proba; zawias drzwi, lozka, przyciski/dzwignie, drabiny, tablice,
 *   stosy (swiece), podwojne plyty; otwarcie drzwi/dzwignia/przekaznik poprawiane kliknieciem.
 * - Blok wiszacy w powietrzu: tymczasowa podpora z RUSZTOWANIA (kolumna od ziemi), potem zdejmowana.
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
    static int[] clicks = new int[0];
    static boolean[] placedHere = new boolean[0];
    static final int MAX_TRIES = 8;
    private static final java.util.Map<String, Integer> skippedTypes = new java.util.TreeMap<>();
    static int placedCount, skippedCount, approxCount, destroyCount;
    static int targetIdx = -1;
    static int maxPlacedY = Integer.MIN_VALUE;

    static double speed = 0.45;          // blokow/tick podczas lotu
    static int perTick = 1;              // ile blokow na tick
    static boolean replace = false;      // czy niszczyc przeszkadzajace (obce) bloki
    static boolean useTemps = true;      // podpory z rusztowania dla blokow wiszacych w powietrzu

    private static int flyingTo = -1;
    private static List<Vec3> path;
    private static int pathIdx;
    private static int replans;
    private static Vec3 expected;
    private static int rubber, idle, idleSkips, manualTicks;
    private static boolean moved;
    private static boolean approachFailed;

    // budowa warstwami od podlogi w gore
    private static int layerY = Integer.MIN_VALUE;
    private static int layerStart;
    private static boolean layerProgress;

    // tymczasowe podpory (rusztowanie): stawiane od dolu, zdejmowane od gory
    private static final int AUX_KEY = Integer.MIN_VALUE + 7;
    private static final ArrayDeque<BlockPos> tempQueue = new ArrayDeque<>();
    private static final ArrayList<BlockPos> tempPlaced = new ArrayList<>();
    private static final ArrayDeque<BlockPos> removeQueue = new ArrayDeque<>();
    private static int tempFor = -1;
    private static int[] tempRounds = new int[0];

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
        clicks = new int[Flow.n];
        placedHere = new boolean[Flow.n];
        skippedTypes.clear();
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
        Ui.clearSigns();
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
        if (skippedCount > 0) extra += " (pominieto " + skippedCount + " - przeszkoda/brak podparcia/brak przedmiotu)";
        if (approxCount > 0) extra += " (" + approxCount + " z przyblizonym stanem)";
        if (!skippedTypes.isEmpty()) {
            StringBuilder sb = new StringBuilder(" Bez przedmiotu (nie da sie postawic): ");
            int n = 0;
            for (var e : skippedTypes.entrySet()) { if (n++ > 0) sb.append(", "); if (n > 6) { sb.append("..."); break; } sb.append(e.getKey()).append(" x").append(e.getValue()); }
            extra += sb;
        }
        Msg.chat("Gotowe! " + Flow.doneCount + "/" + Flow.n + extra);
    }

    private static void releaseTemps() {
        for (int k = tempPlaced.size() - 1; k >= 0; k--) removeQueue.add(tempPlaced.get(k));   // od gory
        tempPlaced.clear();
        tempQueue.clear();
        tempFor = -1;
    }

    private static void markDone(int i) {
        if (tempFor == i) releaseTemps();                      // blok stoi - podpory juz niepotrzebne
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

    /** Nastepny indeks planu do postawienia albo -1 (budowa warstwami). */
    private static int next(ClientLevel lv) {
        int n = Flow.n;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (true) {
            while (cursor < n) {
                int i = Flow.order[cursor];
                if (Flow.wy[i] != layerY) {
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
            if (layerProgress && layerHasDeferred(n)) {
                for (int k = layerStart; k < n; k++) deferred[Flow.order[k]] = false;
                cursor = layerStart;
                layerProgress = false;
                continue;
            }
            boolean anyDeferred = false;
            for (boolean b : deferred) if (b) { anyDeferred = true; break; }
            if (anyDeferred && progressed) {
                Arrays.fill(deferred, false);
                cursor = 0;
                layerY = Integer.MIN_VALUE;
                progressed = false;
                continue;
            }
            return -1;
        }
    }

    private static String stackProp(BlockState s) {
        for (String n : new String[]{"candles", "pickles", "layers", "eggs", "flower_amount", "segment_amount"})
            if (Props.has(s, n)) return n;
        return null;
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
        if (item == Items.AIR) {                                // np. woda, lawa, ogien - brak przedmiotu
            skippedTypes.merge(Props.id(want), 1, Integer::sum);
            skip(i);
            return false;
        }

        // druga polowka drzwi / lozka / wysokiej rosliny powstaje razem z pierwsza
        if ("upper".equals(Props.get(want, "half")) || "head".equals(Props.get(want, "part"))) {
            deferred[i] = true;
            cursor++;
            return false;
        }

        // podwojna plyta: klikamy plyte, ktora juz stoi
        if ("double".equals(Props.get(want, "type")) && cur.getBlock() == want.getBlock()
                && Props.has(cur, "type") && !"double".equals(Props.get(cur, "type"))) {
            boolean bottom = !"top".equals(Props.get(cur, "type"));
            Vec3 hv = new Vec3(pos.getX() + 0.5, pos.getY() + (bottom ? 1.0 : 0.0), pos.getZ() + 0.5);
            use(p, gm, item, new BlockHitResult(hv, bottom ? Direction.UP : Direction.DOWN, pos, false), i, pos);
            return true;
        }

        // stosy: swiece, sniegi, jaja, ogorki morskie - klikamy blok tym samym przedmiotem
        String sp = stackProp(want);
        if (sp != null && cur.getBlock() == want.getBlock()) {
            int have = Props.num(Props.get(cur, sp)), need = Props.num(Props.get(want, sp));
            if (have > 0 && have < need) {
                Vec3 hv = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                use(p, gm, item, new BlockHitResult(hv, Direction.UP, pos, false), i, pos);
                return true;
            }
        }

        // ten sam blok, zly stan
        if (cur.getBlock() == want.getBlock()) {
            if (Match.sameExceptClickable(cur, want)) return clickFix(p, gm, pos, i);   // otwarcie/dzwignia/przekaznik
            if (attempts[i] >= MAX_TRIES) {  // wszystkie sposoby wyprobowane - akceptujemy przyblizenie
                Flow.approx[i] = true;
                Flow.skip[i] = true;
                approxCount++;
                markDone(i);
                return false;
            }
            return destroy(gm, pos, i);
        }

        // inny blok na miejscu
        if (!cur.canBeReplaced()) {
            if (placedHere[i]) {            // sami postawilismy zly wariant (np. tabliczka scienna zamiast stojacej) - burzymy i probujemy inaczej
                if (attempts[i] >= MAX_TRIES) { skip(i); return false; }
                return destroy(gm, pos, i);
            }
            if (!replace) { skip(i); return false; }
            return destroy(gm, pos, i);
        }

        // wybor sposobu stawiania: najpierw symulacja vanilli (dokladnie ten stan), awaryjnie heurystyka
        int strat = attempts[i];
        Plan pl = solve(lv, p, pos, want, item, strat);
        for (int k = 0; pl == null && k < 3; k++) pl = plan(lv, pos, want, strat + k);
        if (pl == null) {               // nie ma o co oprzec
            if (prepareTemps(lv, pos, i)) return false;     // postawimy podpore z rusztowania
            deferred[i] = true;                             // inaczej - sprobujemy pozniej
            cursor++;
            return false;
        }

        // obrot gracza: czekamy tick, zeby serwer dostal nowy obrot
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

    /** Poprawia otwarcie drzwi/furtek, dzwignie, przekaznik, komparator pustą ręką (klikniecie). */
    private static boolean clickFix(LocalPlayer p, MultiPlayerGameMode gm, BlockPos pos, int i) {
        if (++clicks[i] > 6) {
            Flow.approx[i] = true;
            Flow.skip[i] = true;
            approxCount++;
            markDone(i);
            return false;
        }
        Inventory inv = p.getInventory();
        inv.setItem(HOT, ItemStack.EMPTY);
        gm.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + HOT);
        if (inv.getSelectedSlot() != HOT) inv.setSelectedSlot(HOT);
        gm.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        progressed = true;
        return true;
    }

    /** Burzenie bloku z limitem prob (serwer moze odmowic - np. chroniony teren). */
    private static boolean destroy(MultiPlayerGameMode gm, BlockPos pos, int i) {
        if (++destroys[i] > MAX_TRIES + 2) { skip(i); return false; }
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
        placedHere[i] = true;
        if (Props.id(Flow.ws[i]).contains("sign")) Ui.queueSign(Flow.wtext.length > i ? Flow.wtext[i] : null);   // tekst tabliczki
        if (++attempts[i] > 6) skip(i);
    }

    private static void skip(int i) {
        Flow.skip[i] = true;
        skippedCount++;
        resetFlight();
        if (tempFor == i) releaseTemps();
    }

    // ------------------------------------------------------------------ lot
    private static Item scaffoldItem() {
        Block b = Schem.blockById("minecraft:scaffolding");
        return b == null ? Items.AIR : b.asItem();
    }

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

    /** Krok zadania pomocniczego (rusztowanie/zdjecie). true = zadanie zakonczone (lub porzucone). */
    private static boolean auxStep(LocalPlayer p, ClientLevel lv, MultiPlayerGameMode gm, BlockPos pos, boolean place) {
        if (!approach(p, lv, pos, AUX_KEY)) {
            if (approachFailed) { approachFailed = false; return true; }   // nie da sie dolecic - porzucamy
            return false;
        }
        BlockState cur = lv.getBlockState(pos);
        if (place) {
            if (!cur.canBeReplaced()) return true;
            Item sc = scaffoldItem();
            if (sc == Items.AIR) return true;
            BlockPos below = pos.below();
            BlockHitResult hit;
            if (lv.getBlockState(below).getBlock() instanceof ScaffoldingBlock) {
                // klik w BOK rusztowania pod spodem = rosnie w gore (gorna sciana = w bok!)
                hit = new BlockHitResult(new Vec3(below.getX() + 0.5, below.getY() + 0.5, below.getZ()), Direction.NORTH, below, false);
            } else {
                hit = support(lv, pos, false, null, null, true, 0.5, 0.5);
            }
            if (hit == null) return true;
            give(p, gm, sc);
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

    /** Kolumna pustych komorek od 'end' w dol do litego gruntu (nie zajmuje pol budowli) albo null. */
    private static List<BlockPos> column(ClientLevel lv, BlockPos end) {
        List<BlockPos> col = new ArrayList<>();
        BlockPos c = end;
        for (int k = 0; k < 48; k++) {
            BlockState s = lv.getBlockState(c);
            if (!(s.isAir() || s.canBeReplaced())) return null;
            if (Flow.inPlan(c.getX(), c.getY(), c.getZ())) return null;
            col.add(c);
            BlockPos below = c.below();
            BlockState bs = lv.getBlockState(below);
            if (!bs.isAir() && !bs.canBeReplaced()) return bs.isCollisionShapeFullBlock(lv, below) ? col : null;
            c = below;
        }
        return null;
    }

    /**
     * Blok wisi w powietrzu: budujemy kolumne RUSZTOWANIA od ziemi pod nim lub obok niego (wybieramy
     * najkrotsza), stawiamy blok, a potem rusztowanie zdejmujemy (od gory). true = zaplanowano.
     */
    private static boolean prepareTemps(ClientLevel lv, BlockPos pos, int i) {
        if (!useTemps || tempRounds[i] >= 3) return false;
        if (Flow.ws[i].getBlock() instanceof FallingBlock) return false;   // piasek/zwir spadlby po zdjeciu podpory
        if (hasSolidNeighbor(lv, pos)) return false;                         // podparcie jest, problemem jest co innego
        if (scaffoldItem() == Items.AIR) return false;

        List<BlockPos> ends = new ArrayList<>();
        ends.add(pos.below());
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) ends.add(pos.relative(d));
        ends.add(pos.above());
        List<BlockPos> best = null;
        for (BlockPos e : ends) {
            List<BlockPos> col = column(lv, e);
            if (col != null && (best == null || col.size() < best.size())) best = col;
        }
        if (best == null) return false;

        tempRounds[i]++;
        tempFor = i;
        tempPlaced.clear();
        tempQueue.clear();
        for (int k = best.size() - 1; k >= 0; k--) tempQueue.add(best.get(k));   // od dolu do gory
        return true;
    }

    /**
     * Jeden krok lotu po trasie. Zwraca true dopiero w ticku PO dotarciu (serwer zdazy poznac pozycje).
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

    private static Direction dirOf(String s) {
        if (s == null) return null;
        return switch (s) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    private static boolean isTop(BlockState s) {
        return "top".equals(Props.get(s, "type")) || "top".equals(Props.get(s, "half"));
    }

    /** Bloki, ktorych kierunek = kierunek patrzenia gracza (schody, furtki, drzwi, lozka), a nie przeciwny. */
    private static boolean lookSame(String id) {
        return id.endsWith("_stairs") || id.endsWith("fence_gate") || id.endsWith("_door") || id.endsWith("_bed");
    }

    /** Bloki przyklejane do sciany (kierunek = klikana sciana): pochodnie scienne, drabina, tabliczki scienne... */
    private static boolean wallAttached(String id) {
        return id.contains("wall_") || id.equals("ladder") || id.equals("cocoa");
    }

    private static double[][] variants(Direction face) {
        if (face.getAxis() == Direction.Axis.Y)
            return new double[][]{{0.5, 0.5}, {0.25, 0.25}, {0.75, 0.75}, {0.25, 0.75}, {0.75, 0.25}};
        return new double[][]{{0.5, 0.75}, {0.5, 0.25}, {0.25, 0.75}, {0.75, 0.75}, {0.25, 0.25}, {0.75, 0.25}, {0.5, 0.5}};
    }

    private static BlockHitResult hitAt(BlockPos nb, Direction face, double a, double b) {
        double hx, hy, hz;
        if (face.getAxis() == Direction.Axis.Y) {
            hx = nb.getX() + a; hz = nb.getZ() + b; hy = nb.getY() + 0.5 + face.getStepY() * 0.5;
        } else {
            hy = nb.getY() + b;
            if (face.getAxis() == Direction.Axis.X) { hx = nb.getX() + 0.5 + face.getStepX() * 0.5; hz = nb.getZ() + a; }
            else { hz = nb.getZ() + 0.5 + face.getStepZ() * 0.5; hx = nb.getX() + a; }
        }
        return new BlockHitResult(new Vec3(hx, hy, hz), face, nb, false);
    }

    /**
     * Przeszukuje ustawienia (podpierany blok, miejsce klikniecia, obrot i pochylenie gracza) i pyta symulacje
     * vanilli, jaki stan by powstal. Zwraca skipN-te ustawienie dajace DOKLADNIE zadany stan (kolejne proby
     * po nieudanym stawianiu biora kolejne ustawienia) albo null.
     */
    private static Plan solve(ClientLevel lv, LocalPlayer p, BlockPos pos, BlockState want, Item item, int skipN) {
        if (Sim.unavailable()) return null;
        float cy = p.getYRot(), cp = p.getXRot();
        String rot = Props.get(want, "rotation");
        float[] yaws = rot != null ? new float[]{Mth.wrapDegrees(Props.num(rot) * 22.5f - 180f)}
                                   : new float[]{0f, 90f, 180f, -90f};
        float[] pitches = {0f, 90f, -90f};
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        int found = 0;
        for (Direction d : order) {
            BlockPos nb = pos.relative(d);
            BlockState ns = lv.getBlockState(nb);
            if (ns.isAir() || ns.canBeReplaced()) continue;
            Direction face = d.getOpposite();
            for (double[] v : variants(face)) {
                BlockHitResult hit = hitAt(nb, face, v[0], v[1]);
                BlockState pr0 = Sim.predict(p, item, hit, cy, cp);              // bez zmiany obrotu
                if (pr0 != null && Match.sameExceptClickable(pr0, want) && found++ == skipN) return new Plan(null, null, hit);
                for (float pitch : pitches)
                    for (float yaw : yaws) {
                        BlockState pr = Sim.predict(p, item, hit, yaw, pitch);
                        if (pr != null && Match.sameExceptClickable(pr, want) && found++ == skipN) return new Plan(yaw, pitch, hit);
                    }
            }
        }
        return null;
    }

    /** Czy przy kliknieciu w pozycji (dx,dz) w bloku drzwi dostana zawias LEWY (wg zasad vanilli). */
    private static boolean hingeLeft(Direction f, double dx, double dz) {
        int j = f.getStepX(), k = f.getStepZ();
        return (j >= 0 || !(dz < 0.5)) && (j <= 0 || !(dz > 0.5)) && (k >= 0 || !(dx > 0.5)) && (k <= 0 || !(dx < 0.5));
    }

    /**
     * Sposob stawiania nr {@code strat}:
     *  - drzwi: obrot gracza + miejsce klikniecia decyduje o zawiasie (parzysty = zadany, nieparzysty = odwrotny)
     *  - przyciski/dzwignie ("face"): podloga/sufit/sciana przez pochylenie i kierunek patrzenia
     *  - kierunek: 0/1 = obrot gracza (w te lub druga strone), 2 = klikniecie sciany (dla "przyklejanych" kolejnosc 2,0,1)
     *  - os belek/kolumn: parzysty = podparcie wzdluz osi
     *  - tabliczki/sztandary ("rotation"): obrot gracza = obrot bloku
     */
    private static Plan plan(ClientLevel lv, BlockPos pos, BlockState want, int strat) {
        // Rusztowanie: klikniecie BOKU rusztowania pod spodem = rosnie w gore (gorna sciana = w bok!)
        if (want.getBlock() instanceof ScaffoldingBlock) {
            BlockPos below = pos.below();
            if (lv.getBlockState(below).getBlock() instanceof ScaffoldingBlock) {
                Vec3 hv = new Vec3(below.getX() + 0.5, below.getY() + 0.5, below.getZ());   // sciana polnocna
                return new Plan(null, null, new BlockHitResult(hv, Direction.NORTH, below, false));
            }
            BlockHitResult h = support(lv, pos, false, null, null, true, 0.5, 0.5);
            return h == null ? null : new Plan(null, null, h);
        }

        String id = Props.id(want);
        boolean top = isTop(want);
        Direction fd = dirOf(Props.get(want, "facing"));
        String axisS = Props.get(want, "axis");
        String face = Props.get(want, "face");          // floor / wall / ceiling (przyciski, dzwignie, szlifierka)
        String rot = Props.get(want, "rotation");       // 0..15 (tabliczki stojace, sztandary, glowy)

        Direction forced = null;
        Direction.Axis ax = null;
        Float yaw = null, pitch = null;
        double fx = 0.5, fz = 0.5;

        if (face != null && fd != null && fd.getAxis().isHorizontal()) {
            switch (face) {
                case "floor" -> { pitch = 90f; yaw = fd.toYRot(); forced = Direction.UP; }
                case "ceiling" -> { pitch = -90f; yaw = fd.toYRot(); forced = Direction.DOWN; }
                default -> { pitch = 0f; yaw = fd.getOpposite().toYRot(); forced = fd; }
            }
        } else if (Props.has(want, "hinge") && fd != null && fd.getAxis().isHorizontal()) {          // drzwi
            yaw = fd.toYRot();
            boolean wantLeft = "left".equals(Props.get(want, "hinge"));
            if (Math.floorMod(strat, 2) == 1) wantLeft = !wantLeft;
            outer:
            for (double a : new double[]{0.25, 0.75})
                for (double b : new double[]{0.25, 0.75})
                    if (hingeLeft(fd, a, b) == wantLeft) { fx = a; fz = b; break outer; }
        } else if (fd != null) {
            boolean same = lookSame(id);
            int m = Math.floorMod(strat + (wallAttached(id) ? 2 : 0), 3);
            if (m == 2) {
                forced = fd;
            } else {
                Direction look = (m == 0) == same ? fd : fd.getOpposite();
                if (look == Direction.UP) pitch = -90f;
                else if (look == Direction.DOWN) pitch = 90f;
                else { yaw = look.toYRot(); pitch = 0f; }
            }
        } else if (rot != null) {
            yaw = Mth.wrapDegrees(Props.num(rot) * 22.5f - 180f);
        } else if (axisS != null) {
            if (Math.floorMod(strat, 2) == 0) {
                ax = switch (axisS) { case "x" -> Direction.Axis.X; case "y" -> Direction.Axis.Y; default -> Direction.Axis.Z; };
            }
        }

        BlockHitResult hit = support(lv, pos, top, forced, ax, false, fx, fz);
        return hit == null ? null : new Plan(yaw, pitch, hit);
    }

    /**
     * Szuka sasiedniego bloku, o ktory mozna oprzec stawiany blok.
     * @param forcedFace jesli != null: klikana sciana musi byc ta
     * @param axis       jesli != null: sasiad musi lezec na tej osi
     * @param avoidScaffold pomija rusztowania jako podparcie
     * @param fx,fz      miejsce klikniecia w bloku (0..1) - wazne np. dla zawiasu drzwi
     */
    private static BlockHitResult support(ClientLevel lv, BlockPos pos, boolean top, Direction forcedFace,
                                          Direction.Axis axis, boolean avoidScaffold, double fx, double fz) {
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
            double hx = nb.getX() + (face.getStepX() != 0 ? 0.5 + face.getStepX() * 0.5 : fx);
            double hz = nb.getZ() + (face.getStepZ() != 0 ? 0.5 + face.getStepZ() * 0.5 : fz);
            double hy = face.getStepY() != 0 ? nb.getY() + 0.5 + face.getStepY() * 0.5 : nb.getY() + (top ? 0.75 : 0.25);
            BlockHitResult h = new BlockHitResult(new Vec3(hx, hy, hz), face, nb, false);
            if (ns.isCollisionShapeFullBlock(lv, nb)) return h;   // preferuj pelne bloki
            if (weak == null) weak = h;
        }
        return weak;
    }
}
