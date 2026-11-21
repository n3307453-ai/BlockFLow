package pl.blockflow;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Porownanie 1:1 - ten sam blok i te same wlasciwosci "ustawialne przy stawianiu"
 * (kierunek, os, polowa, plyta). Wlasciwosci wyliczane przez swiat (polaczenia plotow,
 * ksztalt schodow, zasilenie itp.) sa pomijane, bo nie da sie ich ustawic recznie.
 */
final class Match {
    private Match() {}

    static boolean same(BlockState cur, BlockState want) {
        if (cur.getBlock() != want.getBlock()) return false;
        return eq(cur, want, BlockStateProperties.FACING)
                && eq(cur, want, BlockStateProperties.HORIZONTAL_FACING)
                && eq(cur, want, BlockStateProperties.AXIS)
                && eq(cur, want, BlockStateProperties.HALF)
                && eq(cur, want, BlockStateProperties.SLAB_TYPE)
                && eq(cur, want, BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    private static <T extends Comparable<T>> boolean eq(BlockState a, BlockState b, Property<T> p) {
        if (!a.hasProperty(p) || !b.hasProperty(p)) return true;
        return a.getValue(p).equals(b.getValue(p));
    }
}
