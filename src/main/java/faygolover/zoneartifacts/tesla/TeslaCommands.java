package faygolover.zoneartifacts.tesla;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.TeslaSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /fl_zone_arts tesla ...} (permission level 2):
 * <ul>
 *     <li>{@code list} — routes in the current dimension and whether their Tesla is alive;</li>
 *     <li>{@code cleanup} — removes every unfinished route and every completed route whose Tesla is
 *     verifiably gone. Routes with a living Tesla are never touched; a route whose Tesla was last
 *     seen in an unloaded chunk is skipped (it may be perfectly fine) and only reported;</li>
 *     <li>{@code flag <player> <true|false>} — the placeholder {@code artifact_equipped} flag that
 *     makes Teslas chase a player, until the artifact system exists.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaCommands {

    private enum Status { ALIVE, GONE, UNKNOWN }

    private TeslaCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fl_zone_arts")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("tesla")
                        .then(Commands.literal("list").executes(TeslaCommands::list))
                        .then(Commands.literal("cleanup").executes(TeslaCommands::cleanup))
                        .then(Commands.literal("flag")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(TeslaCommands::flag))))));
    }

    private static Status status(ServerLevel level, TeslaRoute route) {
        if (route.teslaUuid() == null) return Status.GONE;
        Entity tesla = level.getEntity(route.teslaUuid());
        if (tesla != null && tesla.isAlive()) return Status.ALIVE;
        BlockPos last = route.lastKnownTeslaPos() != null ? route.lastKnownTeslaPos() : route.waypoints().get(0);
        // Entities there are loaded and ticking, yet no Tesla → really gone. Otherwise we can't tell
        // (a chunk can be loaded while its entities still aren't), so leave the route be.
        return level.isPositionEntityTicking(last) ? Status.GONE : Status.UNKNOWN;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        List<String> lines = new ArrayList<>();

        for (TeslaRoute route : TeslaRouteSavedData.get(level).routes()) {
            String state = switch (status(level, route)) {
                case ALIVE -> "Тесла жива";
                case GONE -> "Теслы нет (маршрут завис)";
                case UNKNOWN -> "Тесла в незагруженном чанке";
            };
            lines.add("#" + route.id() + ": " + route.waypoints().size() + " точ., старт "
                    + TeslaRouteService.formatPos(route.waypoints().get(0)) + " — " + state
                    + String.format(java.util.Locale.ROOT, " [размер %.1f, скорость x%.1f, возрождение %d с, урон %.1f, насыщенность %d]",
                    route.size(), route.speedMultiplier(), route.respawnSeconds(), route.damage(), route.intensity()));
        }
        for (TeslaDrafts.Draft draft : TeslaDrafts.inDimension(level.dimension())) {
            lines.add("строится (" + draft.ownerName() + "): " + draft.points().size() + " точ., старт "
                    + TeslaRouteService.formatPos(draft.start()));
        }

        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Тесла] В этом измерении маршрутов нет."), false);
        } else {
            source.sendSuccess(() -> Component.literal("[Тесла] Маршруты в этом измерении (" + lines.size() + "):"), false);
            for (String line : lines) {
                source.sendSuccess(() -> Component.literal("  " + line), false);
            }
        }
        return lines.size();
    }

    private static int cleanup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int drafts = TeslaDrafts.all().size();
        TeslaDrafts.clear();

        int removed = 0;
        int skipped = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
            for (TeslaRoute route : List.copyOf(data.routes())) {
                switch (status(level, route)) {
                    case GONE -> {
                        TeslaRouteService.removeRoute(level, route);
                        removed++;
                    }
                    case UNKNOWN -> skipped++;
                    case ALIVE -> {
                    }
                }
            }
            TeslaSync.broadcast(level);
        }

        final int removedFinal = removed;
        final int skippedFinal = skipped;
        source.sendSuccess(() -> Component.literal("[Тесла] Очистка: незавершённых маршрутов удалено " + drafts
                + ", зависших готовых — " + removedFinal
                + (skippedFinal > 0 ? ", пропущено (Тесла в незагруженном чанке) — " + skippedFinal : "") + "."), true);
        return drafts + removed;
    }

    private static int flag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        TeslaEntity.setArtifactFlag(player, value);
        ctx.getSource().sendSuccess(() -> Component.literal("[Тесла] Флаг artifact_equipped для "
                + player.getGameProfile().getName() + ": " + value), true);
        return 1;
    }
}
