package pl.blockflow;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/** Podglad w swiecie: zaznaczenie, obrys budowli i kolorowe "duchy" brakujacych blokow. */
final class Overlay {
    private Overlay() {}

    private static final Map<Block, GizmoStyle> STYLES = new HashMap<>();
    private static GizmoStyle selStyle, boundsStyle, targetStyle;

    /** Wywolywane z LevelRenderEvents.BEFORE_GIZMOS. */
    static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (selStyle == null) {
            selStyle = GizmoStyle.strokeAndFill(0xFFFFFFFF, 2.0f, 0x18FFFFFF);
            boundsStyle = GizmoStyle.strokeAndFill(0xFF00E5FF, 2.0f, 0x0800E5FF);
            targetStyle = GizmoStyle.strokeAndFill(0xFF40FF40, 3.0f, 0x6040FF40);
        }

        // zaznaczenie
        if (Flow.pos1 != null) {
            var a = Flow.pos1;
            var b = Flow.pos2 != null ? Flow.pos2 : Flow.pos1;
            Gizmos.cuboid(new AABB(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1),
                    selStyle);
        }

        if (Flow.n == 0) return;

        // obrysy wszystkich budynkow (kolejka + aktualny)
        for (int[] b : Flow.bounds) {
            Gizmos.cuboid(new AABB(b[0], b[1], b[2], b[0] + b[3], b[1] + b[4], b[2] + b[5]), boundsStyle);
        }

        // brakujace bloki
        for (int k = 0; k < Flow.visN; k++) {
            int i = Flow.vis[k];
            if (i >= Flow.n || Flow.done[i]) continue;
            double x = Flow.wx[i], y = Flow.wy[i], z = Flow.wz[i];
            Gizmos.cuboid(new AABB(x + 0.08, y + 0.08, z + 0.08, x + 0.92, y + 0.92, z + 0.92),
                    styleFor(Flow.ws[i].getBlock()));
        }

        // aktualny cel auto-budowy
        int t = AutoBuilder.targetIdx;
        if (AutoBuilder.running && t >= 0 && t < Flow.n) {
            double x = Flow.wx[t], y = Flow.wy[t], z = Flow.wz[t];
            Gizmos.cuboid(new AABB(x - 0.02, y - 0.02, z - 0.02, x + 1.02, y + 1.02, z + 1.02), targetStyle);
        }
    }

    private static GizmoStyle styleFor(Block b) {
        return STYLES.computeIfAbsent(b, bb -> {
            int rgb = rgb(bb);
            return GizmoStyle.strokeAndFill(0xFF000000 | rgb, 1.5f, 0x55000000 | rgb);
        });
    }

    private static int rgb(Block b) {
        int h = BuiltInRegistries.BLOCK.getKey(b).toString().hashCode();
        float hue = ((h & 0xFFFF) % 360) / 360f;
        return hsv(hue, 0.65f, 1f);
    }

    private static int hsv(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
