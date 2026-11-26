package pl.blockflow;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Symulacja stawiania: pyta gre (BlockItem#getPlacementState), jaki stan postawilaby vanilla dla danego
 * kliknietego bloku/sciany, miejsca klikniecia oraz obrotu gracza - bez stawiania czegokolwiek.
 * Dzieki temu bot wybiera ustawienie, ktore daje DOKLADNIE zadany blok (np. stojaca a nie scienna tabliczka,
 * wiszaca latarnia, zawias drzwi, polowka schodow). Wszystko przez refleksje: jesli API sie zmieni,
 * symulacja sie wylacza, a bot wraca do heurystyk.
 */
final class Sim {
    private Sim() {}

    private static boolean inited, broken;
    private static Class<?> ctxClass;
    private static Constructor<?> ctor;
    private static final Map<Class<?>, Method> METHODS = new HashMap<>();
    private static int fails;

    private static void init() {
        inited = true;
        try {
            ctxClass = Class.forName("net.minecraft.world.item.context.BlockPlaceContext");
            ctor = ctxClass.getConstructor(Player.class, InteractionHand.class, ItemStack.class, BlockHitResult.class);
        } catch (Throwable t) {
            broken = true;
            Msg.log("symulacja stawiania niedostepna (uzywam heurystyk)", t);
        }
    }

    static boolean unavailable() {
        if (!inited) init();
        return broken;
    }

    private static Method find(Class<?> itemClass) {
        for (Class<?> k = itemClass; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod("getPlacementState", ctxClass);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    /** Stan, ktory vanilla postawilaby przy takim kliknieciu i obrocie gracza; null = nie da sie postawic. */
    static BlockState predict(LocalPlayer p, Item item, BlockHitResult hit, float yaw, float pitch) {
        if (unavailable()) return null;
        float oy = p.getYRot(), op = p.getXRot();
        try {
            Method m = METHODS.computeIfAbsent(item.getClass(), Sim::find);
            if (m == null) return null;
            p.setYRot(yaw);
            p.setXRot(pitch);
            Object ctx = ctor.newInstance(p, InteractionHand.MAIN_HAND, new ItemStack(item), hit);
            return (BlockState) m.invoke(item, ctx);
        } catch (Throwable t) {
            if (++fails > 30) broken = true;
            return null;
        } finally {
            p.setYRot(oy);
            p.setXRot(op);
        }
    }
}
