package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side tick logic that has nowhere else to live once a Tesla has actually died: advancing
 * a route's respawn countdown and its delayed second-hit timer. Everything about a <em>live</em>
 * Tesla (movement, pursuit, the hit itself) is ticked by the entity itself in {@link
 * TeslaEntity#tick()} — this only covers the gap where there's no entity object to tick at all.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaEngine {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        TeslaSavedData.get(serverLevel).tick(serverLevel);
    }
}
