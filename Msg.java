package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Wszystkie wiadomosci w jednym miejscu (latwo poprawic przy zmianie API). */
final class Msg {
    private Msg() {}

    static void chat(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a7b[BlockFLow] \u00a7f" + s));
    }

    static void bar(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(s), false);
    }
}
