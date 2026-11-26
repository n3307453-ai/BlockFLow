package pl.blockflow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/** Odczyt tekstu tabliczek z kopiowanego obszaru (refleksja, best-effort). */
final class Signs {
    private Signs() {}

    static String[] read(ClientLevel lv, BlockPos pos) {
        try {
            Object be = lv.getBlockEntity(pos);
            if (be == null) return null;
            Method gft = be.getClass().getMethod("getFrontText");
            Object txt = gft.invoke(be);
            Method gm = txt.getClass().getMethod("getMessages", boolean.class);
            Object[] arr = (Object[]) gm.invoke(txt, false);
            String[] out = new String[4];
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                out[i] = i < arr.length ? ((Component) arr[i]).getString() : "";
                if (!out[i].isEmpty()) any = true;
            }
            return any ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
