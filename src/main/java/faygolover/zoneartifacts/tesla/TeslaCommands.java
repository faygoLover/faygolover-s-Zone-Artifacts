package faygolover.zoneartifacts.tesla;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.TeslaSyncHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /fl_zone_arts tesla cleanup} — the GM/admin safety net for hung Tesla state. It does two
 * things, and never anything else:
 * <ol>
 *     <li>Discards every in-progress waypoint build session in memory (a player-side build that
 *     got stuck for whatever reason — a missed logout event, a crash, etc.) and unconditionally
 *     tells every online player to drop their yellow preview, exactly the same "just always send
 *     the clear" trick {@code TeslaSyncHandler} already uses on dimension change.</li>
 *     <li>Scans every dimension's persisted, already-finalized routes and removes only the ones
 *     that are true orphans — a route whose Tesla entity UUID no longer resolves to a real entity
 *     at all. A route whose Tesla is alive and resolvable is never touched, no matter what:
 *     "Полноценные готовые маршруты с активной Теслой не убираются."</li>
 * </ol>
 * Requires permission level 2, same bar as most other GM-only commands.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaCommands {

    private TeslaCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fl_zone_arts")
                .then(Commands.literal("tesla")
                        .then(Commands.literal("cleanup")
                                .requires(source -> source.hasPermission(2))
                                .executes(TeslaCommands::runCleanup))));
    }

    private static int runCleanup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        // 1) Clear every hung/incomplete build session in memory.
        boolean hadSessions = TeslaBuildManager.hasAnySession();
        TeslaBuildManager.discardAll();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeslaSyncHandler.sendBuildSession(player, List.of());
        }

        // 2) Remove only true orphans among already-finalized, persisted routes: a route whose
        // Tesla entity no longer resolves at all. Anything with a live Tesla is left completely
        // alone, per the spec's explicit "never touch a completed route with an active Tesla".
        int removedRoutes = 0;
        for (ServerLevel level : server.getAllLevels()) {
            TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
            List<UUID> orphanIds = new ArrayList<>();
            for (TeslaRouteSavedData.TeslaRoute route : data.routes().values()) {
                boolean alive = route.teslaEntityUuid() != null
                        && level.getEntity(route.teslaEntityUuid()) != null;
                if (!alive) {
                    orphanIds.add(route.routeId());
                }
            }
            for (UUID routeId : orphanIds) {
                data.remove(routeId);
                TeslaSyncHandler.broadcastRouteRemoved(level, routeId);
                removedRoutes++;
            }
        }

        int finalRemovedRoutes = removedRoutes;
        boolean finalHadSessions = hadSessions;
        source.sendSuccess(() -> Component.literal(String.format(
                "[Тесла] Очистка завершена: %s; осиротевших маршрутов удалено: %d.",
                finalHadSessions ? "прерванные построения сброшены" : "активных построений не найдено",
                finalRemovedRoutes)), true);

        return removedRoutes + (hadSessions ? 1 : 0);
    }
}
