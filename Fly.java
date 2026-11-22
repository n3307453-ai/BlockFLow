package pl.blockflow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lot bez kolizji: kazda pozycja na trasie jest sprawdzana jako wolna od blokow, wiec serwer
 * widzi zwykly, poprawny ruch i niczego nie cofa (prawdziwy noclip nie przejdzie na serwerze vanilla).
 */
final class Fly {
    private Fly() {}

    private static final double[][] OFFS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
    private static final double[] DYS = {3.2, 3.8, 2.6, 4.2};

    /** Czy hitbox gracza (0.6 x 1.8) o nogach w (x,y,z) nie koliduje z zadnym blokiem. */
    static boolean freeAt(ClientLevel lv, double x, double y, double z) {
        int x0 = Mth.floor(x - 0.31), x1 = Mth.floor(x + 0.31);
        int z0 = Mth.floor(z - 0.31), z1 = Mth.floor(z + 0.31);
        int y0 = Mth.floor(y + 0.01), y1 = Mth.floor(y + 1.8);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int yy = y0; yy <= y1; yy++)
            for (int xx = x0; xx <= x1; xx++)
                for (int zz = z0; zz <= z1; zz++) {
                    mp.set(xx, yy, zz);
                    if (!lv.getBlockState(mp).getCollisionShape(lv, mp).isEmpty()) return false;
                }
        return true;
    }

    /** Czy odcinek a-b (po pozycjach nog) jest wolny. */
    static boolean segFree(ClientLevel lv, Vec3 a, Vec3 b) {
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) Math.ceil(len / 0.25));
        for (int k = 1; k <= n; k++) {
            double t = (double) k / n;
            double x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t, z = a.z + (b.z - a.z) * t;
            if (!freeAt(lv, x, y, z)) return false;
        }
        return true;
    }

    /**
     * Wolne miejsce (pozycja nog) blisko celu, z ktorego da sie postawic blok.
     * @param c   srodek bloku docelowego
     * @param eyeH wysokosc oczu nad nogami
     * @param maxDist max odleglosc oka od srodka bloku
     */
    static Vec3 pickHover(ClientLevel lv, Vec3 c, double eyeH, double maxDist) {
        for (double dy : DYS) {
            for (double[] o : OFFS) {
                double dist = Math.sqrt(o[0] * o[0] + dy * dy + o[1] * o[1]);
                if (dist > maxDist) continue;
                double fx = c.x + o[0], fy = c.y + dy - eyeH, fz = c.z + o[1];
                if (freeAt(lv, fx, fy, fz)) return new Vec3(fx, fy, fz);
            }
        }
        return null;
    }

    /**
     * Trasa z punktu 'from' do 'to' (pozycje nog): prosto, a jesli zablokowane - w gore, nad przeszkody,
     * poziomo i w dol. null = nie znaleziono wolnej trasy.
     */
    static List<Vec3> plan(ClientLevel lv, Vec3 from, Vec3 to, double topBuilt) {
        if (segFree(lv, from, to)) return List.of(to);
        double base = Math.max(Math.max(from.y, to.y), topBuilt + 1);
        for (int extra = 2; extra <= 66; extra += 4) {
            double cy = base + extra;
            Vec3 up = new Vec3(from.x, cy, from.z);
            Vec3 over = new Vec3(to.x, cy, to.z);
            if (segFree(lv, from, up) && segFree(lv, up, over) && segFree(lv, over, to)) {
                return List.of(up, over, to);
            }
        }
        return null;
    }
}
