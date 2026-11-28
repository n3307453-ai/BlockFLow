package pl.blockflow;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/** /blockflow oraz skrot /bf - komendy tylko po stronie klienta. */
final class Cmds {
    private Cmds() {}

    static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(root("blockflow"));
        d.register(root("bf"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root(String name) {
        return ClientCommands.literal(name)
                .then(xyz("pos1", Actions::pos1))
                .then(xyz("pos2", Actions::pos2))
                .then(xyz("place", Actions::place))
                .then(lit("copy", Actions::copy))
                .then(ClientCommands.literal("rotate")
                        .executes(c -> { Actions.rotate(90); return 1; })
                        .then(ClientCommands.argument("deg", IntegerArgumentType.integer())
                                .executes(c -> { Actions.rotate(IntegerArgumentType.getInteger(c, "deg")); return 1; })))
                .then(lit("mirror", Actions::mirror))
                .then(ClientCommands.literal("move")
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> {
                                                    Actions.move(IntegerArgumentType.getInteger(c, "x"),
                                                            IntegerArgumentType.getInteger(c, "y"),
                                                            IntegerArgumentType.getInteger(c, "z"));
                                                    return 1;
                                                })))))
                .then(lit("start", Actions::start))
                .then(lit("pause", Actions::pause))
                .then(lit("stop", Actions::pause))
                .then(lit("clear", Actions::clear))
                .then(lit("status", Actions::status))
                .then(lit("list", Actions::list))
                .then(ClientCommands.literal("layer")
                        .executes(c -> { Actions.layer(null); return 1; })
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 64))
                                .executes(c -> { Actions.layer(IntegerArgumentType.getInteger(c, "n")); return 1; })))
                .then(ClientCommands.literal("range")
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(4, 128))
                                .executes(c -> { Actions.range(IntegerArgumentType.getInteger(c, "n")); return 1; })))
                .then(ClientCommands.literal("speed")
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 64))
                                .executes(c -> { Actions.speed(IntegerArgumentType.getInteger(c, "n")); return 1; })))
                .then(lit("replace", Actions::replace))
                .then(lit("menu", Ui::openMenuNextTick))
                .then(lit("podpory", Actions::togglePodpory))
                .then(ClientCommands.literal("kolejka")
                        .then(lit("dodaj", Actions::queueAdd))
                        .then(lit("wyczysc", Actions::queueClear))
                        .then(lit("tryb", Actions::queueMode))
                        .then(lit("status", Actions::status)))
                .then(ClientCommands.literal("tempo")
                        .then(ClientCommands.argument("blokow_na_sekunde", IntegerArgumentType.integer(20, 1280))
                                .executes(c -> { Actions.tempo(IntegerArgumentType.getInteger(c, "blokow_na_sekunde")); return 1; })))
                .then(ClientCommands.literal("czas")
                        .then(ClientCommands.argument("sekundy", IntegerArgumentType.integer(1, 3600))
                                .executes(c -> { Actions.czas(IntegerArgumentType.getInteger(c, "sekundy")); return 1; })))
                .then(ClientCommands.literal("lot")
                        .then(ClientCommands.argument("v", DoubleArgumentType.doubleArg(0.3, 6.0))
                                .executes(c -> { Actions.lot(DoubleArgumentType.getDouble(c, "v")); return 1; })))
                .then(ClientCommands.literal("krok")
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 200))
                                .executes(c -> { Actions.step = IntegerArgumentType.getInteger(c, "n"); Msg.chat("Krok: " + Actions.step); return 1; })))
                .then(nudge("przod")).then(nudge("tyl")).then(nudge("lewo"))
                .then(nudge("prawo")).then(nudge("gora")).then(nudge("dol"))
                .then(lit("unstuck", () -> Actions.unstuck(true)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> nudge(String rel) {
        return ClientCommands.literal(rel)
                .executes(c -> { Actions.nudge(rel, Actions.step); return 1; })
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 200))
                        .executes(c -> { Actions.nudge(rel, IntegerArgumentType.getInteger(c, "n")); return 1; }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name, Runnable r) {
        return ClientCommands.literal(name).executes(c -> { r.run(); return 1; });
    }

    /** Komenda bez argumentow (patrzony blok) albo z trzema wspolrzednymi. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> xyz(String name, Consumer<BlockPos> action) {
        return ClientCommands.literal(name)
                .executes(c -> { action.accept(null); return 1; })
                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> {
                                            action.accept(new BlockPos(
                                                    IntegerArgumentType.getInteger(c, "x"),
                                                    IntegerArgumentType.getInteger(c, "y"),
                                                    IntegerArgumentType.getInteger(c, "z")));
                                            return 1;
                                        }))));
    }
}
