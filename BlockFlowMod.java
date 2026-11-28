package pl.blockflow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * BlockFLow - czysto kliencki mod (dziala na kazdym serwerze, serwer nie musi go miec).
 * Zaznacz obszar, skopiuj, obroc, ustaw hologram. Creative: auto-budowa. Survival: podglad + lista.
 *
 * Kazdy element jest odizolowany try/catch: jesli zmieni sie jakies API, mod wylaczy
 * tylko dana funkcje i zapisze blad w logu, zamiast wywalac cala gre.
 */
public final class BlockFlowMod implements ClientModInitializer {
    public static final String MOD_ID = "blockflow";

    static KeyMapping kPos1, kPos2, kCopy, kRotate, kPlace, kBuild, kClear, kMenu,
            kNFwd, kNBack, kNLeft, kNRight, kNUp, kNDown, kQueue;
    private static int ticks;
    private static boolean tickFailed, renderFailed;

    @Override
    public void onInitializeClient() {
        step("klawisze", () -> {
            KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
            // kody GLFW: [ ] ; ' \ . =
            kPos1 = key("pos1", 91, cat);
            kPos2 = key("pos2", 93, cat);
            kCopy = key("copy", 59, cat);
            kRotate = key("rotate", 39, cat);
            kPlace = key("place", 92, cat);
            kBuild = key("build", 46, cat);
            kClear = key("clear", 61, cat);
            kMenu = key("menu", 344, cat);   // prawy Shift
            kQueue = key("queue", 75, cat);  // K = dodaj do kolejki
            kNFwd = key("nudge_fwd", 265, cat);     // strzalka w gore  = przod
            kNBack = key("nudge_back", 264, cat);   // strzalka w dol   = tyl
            kNLeft = key("nudge_left", 263, cat);   // strzalka w lewo
            kNRight = key("nudge_right", 262, cat); // strzalka w prawo
            kNUp = key("nudge_up", 266, cat);       // Page Up   = w gore
            kNDown = key("nudge_down", 267, cat);   // Page Down = w dol
        });
        step("komendy", () ->
                ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Cmds.register(dispatcher)));
        step("tick", () -> ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (tickFailed) return;
            try {
                tick(mc);
            } catch (Throwable t) {
                tickFailed = true;
                Msg.log("blad w tick - funkcja wylaczona", t);
            }
        }));
        step("render", () -> LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
            if (renderFailed) return;
            try {
                Overlay.render();
            } catch (Throwable t) {
                renderFailed = true;
                Msg.log("blad w renderowaniu podgladu - funkcja wylaczona", t);
            }
        }));
    }

    private static void step(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Msg.log("blad przy inicjalizacji: " + name, t);
        }
    }

    private static KeyMapping key(String id, int glfw, KeyMapping.Category cat) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + MOD_ID + "." + id, glfw, cat));
    }

    private static boolean pressed(KeyMapping k) {
        return k != null && k.consumeClick();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            AutoBuilder.abort();
            return;
        }
        while (pressed(kPos1)) Actions.pos1(null);
        while (pressed(kPos2)) Actions.pos2(null);
        while (pressed(kCopy)) Actions.copy();
        while (pressed(kRotate)) Actions.rotate(90);
        while (pressed(kPlace)) Actions.place(null);
        while (pressed(kBuild)) Actions.toggleBuild();
        while (pressed(kClear)) Actions.clear();
        while (pressed(kMenu)) Ui.openMenuNextTick();
        while (pressed(kQueue)) Actions.queueAdd();
        while (pressed(kNFwd)) Actions.nudge("przod", Actions.step);
        while (pressed(kNBack)) Actions.nudge("tyl", Actions.step);
        while (pressed(kNLeft)) Actions.nudge("lewo", Actions.step);
        while (pressed(kNRight)) Actions.nudge("prawo", Actions.step);
        while (pressed(kNUp)) Actions.nudge("gora", Actions.step);
        while (pressed(kNDown)) Actions.nudge("dol", Actions.step);
        Ui.tick();

        Flow.scan(mc.level);
        AutoBuilder.tick(mc);

        ticks++;
        if (ticks % 5 == 0) Flow.refreshVisible(mc.player.position());
        if (ticks % 10 == 0 && Flow.n > 0) {
            Msg.bar(String.format("BlockFLow  %d/%d (%.1f%%)  %s  %d\u00b0%s",
                    Flow.doneCount, Flow.n, Flow.percent(),
                    AutoBuilder.running ? "\u25b6 budowa" : "\u23f8",
                    Flow.rotationDegrees(), Flow.mirror.ordinal() != 0 ? " lustro" : ""));
        }
    }
}
