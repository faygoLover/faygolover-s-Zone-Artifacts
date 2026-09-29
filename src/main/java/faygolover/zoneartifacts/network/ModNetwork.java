package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    /** Bump whenever packets are added or changed, so a client and server on different mod
     *  versions get a clear "incompatible" message at login instead of odd behaviour. 2 = Tesla. */
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZoneArtifacts.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncAnomaliesPacket.class,
                SyncAnomaliesPacket::encode, SyncAnomaliesPacket::decode, SyncAnomaliesPacket::handle);
        CHANNEL.registerMessage(id++, SyncAnomalyTypeShapesPacket.class,
                SyncAnomalyTypeShapesPacket::encode, SyncAnomalyTypeShapesPacket::decode, SyncAnomalyTypeShapesPacket::handle);
        CHANNEL.registerMessage(id++, RemoveAnomalyPacket.class,
                RemoveAnomalyPacket::encode, RemoveAnomalyPacket::decode, RemoveAnomalyPacket::handle);
        CHANNEL.registerMessage(id++, AnomalyStrikePacket.class,
                AnomalyStrikePacket::encode, AnomalyStrikePacket::decode, AnomalyStrikePacket::handle);
        CHANNEL.registerMessage(id++, SyncAnomalyCooldownPacket.class,
                SyncAnomalyCooldownPacket::encode, SyncAnomalyCooldownPacket::decode, SyncAnomalyCooldownPacket::handle);
        // Tesla (0.2.0). New packets always go at the end so existing ids never shift.
        CHANNEL.registerMessage(id++, SyncTeslaRoutesPacket.class,
                SyncTeslaRoutesPacket::encode, SyncTeslaRoutesPacket::decode, SyncTeslaRoutesPacket::handle);
        CHANNEL.registerMessage(id++, TeslaWaypointClickPacket.class,
                TeslaWaypointClickPacket::encode, TeslaWaypointClickPacket::decode, TeslaWaypointClickPacket::handle);
        CHANNEL.registerMessage(id++, TeslaBurstPacket.class,
                TeslaBurstPacket::encode, TeslaBurstPacket::decode, TeslaBurstPacket::handle);
        CHANNEL.registerMessage(id++, TeslaElectrifyPacket.class,
                TeslaElectrifyPacket::encode, TeslaElectrifyPacket::decode, TeslaElectrifyPacket::handle);
        CHANNEL.registerMessage(id++, SyncTeslaConfigPacket.class,
                SyncTeslaConfigPacket::encode, SyncTeslaConfigPacket::decode, SyncTeslaConfigPacket::handle);
    }

    private ModNetwork() {
    }
}
