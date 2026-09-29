package faygolover.zoneartifacts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.entity.ArtifactFlag;
import faygolover.zoneartifacts.entity.TeslaRouteInteractionHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /fl_zone_arts artifact_flag <player> <true|false>} — sets or clears whether a player
 * counts as "wearing the artifact" for {@code TeslaEntity}'s pursuit check (see {@link
 * ArtifactFlag#isEquipped}). A stand-in for whatever actual equipment/effect ends up granting this
 * in the finished mod; a GM can flip it by hand for testing in the meantime.
 * <p>
 * {@code /fl_zone_arts tesla_reset_chains} — clears every online player's in-progress (not yet
 * finished) route chain, nothing else. Deliberately never touches a finished, persisted {@link
 * faygolover.zoneartifacts.entity.TeslaRoute} - a route that already exists always already has its
 * Tesla ticking away on it somewhere (see {@code TeslaSavedData#createRoute}), so there's no such
 * thing as a "broken" finished route to clean up here; a route the GM genuinely doesn't want is
 * removed the normal way instead (left-click it with the tool in hand, no chain in progress). This
 * command is purely for the other half of "stuck": a chain someone was mid-build on that never got
 * closed or cancelled properly.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fl_zone_arts")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("artifact_flag")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("equipped", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            boolean equipped = BoolArgumentType.getBool(ctx, "equipped");
                                            ArtifactFlag.setEquipped(player, equipped);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "fl_zone_arts: флаг артефакта у " + player.getGameProfile().getName()
                                                            + " = " + equipped), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("tesla_reset_chains")
                        .executes(ctx -> {
                            int affected = 0;
                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                if (player.level() instanceof ServerLevel level
                                        && TeslaRouteInteractionHandler.clearAllChains(level, player)) {
                                    affected++;
                                }
                            }
                            int finalAffected = affected;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "fl_zone_arts: недостроенные маршруты сброшены у игроков: " + finalAffected
                                            + " (готовые маршруты не тронуты)"), true);
                            return finalAffected;
                        })
                )
        );
    }

    private ModCommands() {
    }
}
