package faygolover.zoneartifacts.gear;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /fl_zone_arts bolts rescan}: sort every block into a bolt sound group again, now ({@link BoltSounds}). */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class BoltCommands {

    private BoltCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fl_zone_arts")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("bolts")
                        .then(Commands.literal("rescan").executes(ctx -> {
                            int n = BoltSounds.load(true);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.fl_zone_arts.bolts.rescanned", n), true);
                            return n;
                        }))));
    }
}
