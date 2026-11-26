package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;

/** Otwieranie ekranow (API setScreen zmienialo miejsce miedzy wersjami - szukamy w czasie dzialania). */
final class Ui {
    private Ui() {}

    private static boolean pendingMenu;
    private static final ArrayDeque<String[]> signQueue = new ArrayDeque<>();

    /** Zapamietuje tekst dla kolejnej postawionej tabliczki (pusta tablica = bez tekstu). */
    static void queueSign(String[] text) {
        if (signQueue.size() < 64) signQueue.add(text == null ? new String[0] : text);
    }

    static void clearSigns() {
        signQueue.clear();
    }

    /** Zachowane dla zgodnosci - okno tabliczki obsluguje tick(). */
    static void closeScreenSoon() { }

    static void openMenuNextTick() {
        pendingMenu = true;
    }

    private static Object currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Method m = mc.gui.getClass().getMethod("screen");
            return m.invoke(mc.gui);
        } catch (Throwable ignored) { }
        try {
            Field f = Minecraft.class.getField("screen");
            return f.get(mc);
        } catch (Throwable ignored) { }
        return null;
    }

    static void tick() {
        if (pendingMenu) {
            pendingMenu = false;
            open(new MenuScreen());
        }
        Object sc = currentScreen();
        if (sc instanceof Screen s && s.getClass().getName().contains("SignEditScreen")) {
            String[] t = signQueue.poll();
            if (t != null && t.length > 0) fillSign(s, t);
            s.onClose();                // zamkniecie wysyla wpisany tekst do serwera
        }
    }

    private static void fillSign(Screen s, String[] t) {
        for (Class<?> k = s.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField("messages");
                f.setAccessible(true);
                Object o = f.get(s);
                if (o instanceof String[] arr) {
                    for (int i = 0; i < arr.length && i < t.length; i++) arr[i] = t[i];
                    return;
                }
            } catch (Throwable ignored) { }
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
