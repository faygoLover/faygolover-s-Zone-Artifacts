package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(ZoneArtifacts.MODID, "main");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, SyncAnomaliesPacket.class,
                SyncAnomaliesPacket::write, SyncAnomaliesPacket::new, SyncAnomaliesPacket::handle);
        CHANNEL.registerMessage(nextId++, SyncAnomalyTypeShapesPacket.class,
                SyncAnomalyTypeShapesPacket::write, SyncAnomalyTypeShapesPacket::new, SyncAnomalyTypeShapesPacket::handle);
        CHANNEL.registerMessage(nextId++, SyncAnomalyCooldownPacket.class,
                SyncAnomalyCooldownPacket::write, SyncAnomalyCooldownPacket::new, SyncAnomalyCooldownPacket::handle);
        CHANNEL.registerMessage(nextId++, AnomalyStrikePacket.class,
                AnomalyStrikePacket::write, AnomalyStrikePacket::new, AnomalyStrikePacket::handle);
        CHANNEL.registerMessage(nextId++, RemoveAnomalyPacket.class,
                RemoveAnomalyPacket::write, RemoveAnomalyPacket::new, RemoveAnomalyPacket::handle);

        CHANNEL.registerMessage(nextId++, TeslaSyncRoutesPacket.class,
                TeslaSyncRoutesPacket::write, TeslaSyncRoutesPacket::new, TeslaSyncRoutesPacket::handle);
        CHANNEL.registerMessage(nextId++, TeslaRemoveRoutePacket.class,
                TeslaRemoveRoutePacket::write, TeslaRemoveRoutePacket::new, TeslaRemoveRoutePacket::handle);
        CHANNEL.registerMessage(nextId++, TeslaSyncBuildSessionPacket.class,
                TeslaSyncBuildSessionPacket::write, TeslaSyncBuildSessionPacket::new, TeslaSyncBuildSessionPacket::handle);
        CHANNEL.registerMessage(nextId++, TeslaSyncTypePacket.class,
                TeslaSyncTypePacket::write, TeslaSyncTypePacket::new, TeslaSyncTypePacket::handle);
        CHANNEL.registerMessage(nextId++, TeslaElectrifyPacket.class,
                TeslaElectrifyPacket::write, TeslaElectrifyPacket::new, TeslaElectrifyPacket::handle);
        CHANNEL.registerMessage(nextId++, TeslaBlockBurstPacket.class,
                TeslaBlockBurstPacket::write, TeslaBlockBurstPacket::new, TeslaBlockBurstPacket::handle);
    }
}
