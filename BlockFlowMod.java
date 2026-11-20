package pl.blockflow;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * BlockFLow - czysto kliencki mod (dziala na kazdym serwerze, serwer nie musi go miec).
 * Zaznacz obszar, skopiuj, obroc, ustaw hologram. Creative: auto-budowa. Survival: podglad + lista.
 */
public final class BlockFlowMod implements ClientModInitializer {
    public static final String MOD_ID = "blockflow";

    static KeyMapping kPos1, kPos2, kCopy, kRotate, kPlace, kBuild, kClear;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        kPos1 = key("pos1", GLFW.GLFW_KEY_LEFT_BRACKET, cat);
        kPos2 = key("pos2", GLFW.GLFW_KEY_RIGHT_BRACKET, cat);
        kCopy = key("copy", GLFW.GLFW_KEY_SEMICOLON, cat);
        kRotate = key("rotate", GLFW.GLFW_KEY_APOSTROPHE, cat);
        kPlace = key("place", GLFW.GLFW_KEY_BACKSLASH, cat);
        kBuild = key("build", GLFW.GLFW_KEY_PERIOD, cat);
        kClear = key("clear", GLFW.GLFW_KEY_EQUAL, cat);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Cmds.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(BlockFlowMod::tick);
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> Overlay.render());
    }

    private static KeyMapping key(String id, int glfw, KeyMapping.Category cat) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key." + MOD_ID + "." + id, InputConstants.Type.KEYSYM, glfw, cat));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            AutoBuilder.abort();
            return;
        }
        while (kPos1.consumeClick()) Actions.pos1(null);
        while (kPos2.consumeClick()) Actions.pos2(null);
        while (kCopy.consumeClick()) Actions.copy();
        while (kRotate.consumeClick()) Actions.rotate(90);
        while (kPlace.consumeClick()) Actions.place(null);
        while (kBuild.consumeClick()) Actions.toggleBuild();
        while (kClear.consumeClick()) Actions.clear();

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
