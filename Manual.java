package pl.blockflow;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Wykrywa, ze gracz sam chce sie ruszac (klawisze ruchu) - wtedy bot oddaje sterowanie. */
final class Manual {
    private Manual() {}

    private static KeyMapping[] keys;

    static boolean pressed(Minecraft mc) {
        if (keys == null) {
            List<KeyMapping> l = new ArrayList<>();
            for (String n : new String[]{"keyUp", "keyDown", "keyLeft", "keyRight", "keyJump", "keyShift"}) {
                try {
                    Object o = mc.options.getClass().getField(n).get(mc.options);
                    if (o instanceof KeyMapping k) l.add(k);
                } catch (Throwable ignored) { }
            }
            keys = l.toArray(new KeyMapping[0]);
        }
        for (KeyMapping k : keys) if (k.isDown()) return true;
        return false;
    }
}
