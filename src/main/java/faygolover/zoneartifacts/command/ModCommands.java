package faygolover.zoneartifacts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.entity.ArtifactFlag;
import faygolover.zoneartifacts.entity.TeslaRouteInteractionHandler;
import faygolover.zoneartifacts.entity.TeslaSavedData;
import faygolover.zoneartifacts.network.TeslaRouteSyncHandler;
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
 * {@code /fl_zone_arts tesla_reset_routes} — the "something went sideways" escape hatch: wipes every
 * persisted Tesla route (and its live Tesla, if any) in every loaded dimension, and clears every
 * online player's in-progress route chain, without spawning anything new. For when a route or two
 * ends up in some inconsistent state and it's simpler to just start over than track down why.
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
                .then(Commands.literal("tesla_reset_routes")
                        .executes(ctx -> {
                            int totalRemoved = 0;
                            for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                                totalRemoved += TeslaSavedData.get(level).clearAll(level);
                                TeslaRouteSyncHandler.broadcast(level);
                            }
                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                TeslaRouteInteractionHandler.clearAllChains(player);
                            }
                            int finalTotal = totalRemoved;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "fl_zone_arts: сброшено маршрутов Теслы: " + finalTotal
                                            + "; строящиеся цепочки у всех игроков онлайн очищены"), true);
                            return finalTotal;
                        })
                )
        );
    }

    private ModCommands() {
    }
}
