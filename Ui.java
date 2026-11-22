package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

/** Otwieranie ekranow (API setScreen zmienialo miejsce miedzy wersjami - szukamy w czasie dzialania). */
final class Ui {
    private Ui() {}

    private static boolean pendingMenu;

    static void openMenuNextTick() {
        pendingMenu = true;
    }

    static void tick() {
        if (pendingMenu) {
            pendingMenu = false;
            open(new MenuScreen());
        }
    }

    static void open(Screen s) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Method m = mc.gui.getClass().getMethod("setScreen", Screen.class);
            m.invoke(mc.gui, s);
            return;
        } catch (Throwable ignored) { }
        try {
            Method m = mc.getClass().getMethod("setScreen", Screen.class);
            m.invoke(mc, s);
        } catch (Throwable t) {
            Msg.log("nie udalo sie otworzyc menu", t);
        }
    }
}
