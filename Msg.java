package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/** Wszystkie wiadomosci w jednym miejscu (latwo poprawic przy zmianie API). */
final class Msg {
    private Msg() {}

    // Pasek akcji: API zmienialo sie miedzy wersjami, wiec szukamy metody w czasie dzialania.
    private static int kind = -1;          // -1 nie szukano, -2 brak, 0/1/2 = wariant
    private static Method method;

    static void chat(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a7b[BlockFLow] \u00a7f" + s));
    }

    static void bar(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component c = Component.literal(s);
        try {
            if (kind == -1) resolve(mc);
            switch (kind) {
                case 0 -> method.invoke(mc.player, c, true);    // displayClientMessage(Component, boolean)
                case 1 -> method.invoke(mc.player, c);          // sendOverlayMessage(Component)
                case 2 -> method.invoke(mc.gui, c, false);      // Gui.setOverlayMessage(Component, boolean)
                default -> { }
            }
        } catch (Throwable t) {
            kind = -2;
        }
    }

    private static void resolve(Minecraft mc) {
        kind = -2;
        Class<?> pc = mc.player.getClass();
        try { method = pc.getMethod("sendOverlayMessage", Component.class); kind = 1; return; } catch (Throwable ignored) { }
        try { method = pc.getMethod("displayClientMessage", Component.class, boolean.class); kind = 0; return; } catch (Throwable ignored) { }
        try { method = mc.gui.getClass().getMethod("setOverlayMessage", Component.class, boolean.class); kind = 2; } catch (Throwable ignored) { }
    }
}
