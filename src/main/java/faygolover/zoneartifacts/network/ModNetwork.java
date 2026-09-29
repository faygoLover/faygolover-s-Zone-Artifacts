package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    /** Bump whenever packets are added, removed or changed, so a client and server on different
     *  mod versions get a clear "incompatible" message at login instead of odd behaviour.
     *  2 = Tesla (0.1.2.0), 3 = tuners, no datapacks (0.1.3.0). */
    private static final String PROTOCOL_VERSION = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZoneArtifacts.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        // Electra
        CHANNEL.registerMessage(id++, SyncAnomaliesPacket.class,
                SyncAnomaliesPacket::encode, SyncAnomaliesPacket::decode, SyncAnomaliesPacket::handle);
        CHANNEL.registerMessage(id++, RemoveAnomalyPacket.class,
                RemoveAnomalyPacket::encode, RemoveAnomalyPacket::decode, RemoveAnomalyPacket::handle);
        CHANNEL.registerMessage(id++, AnomalyStrikePacket.class,
                AnomalyStrikePacket::encode, AnomalyStrikePacket::decode, AnomalyStrikePacket::handle);
        CHANNEL.registerMessage(id++, SyncAnomalyCooldownPacket.class,
                SyncAnomalyCooldownPacket::encode, SyncAnomalyCooldownPacket::decode, SyncAnomalyCooldownPacket::handle);
        // Tesla
        CHANNEL.registerMessage(id++, SyncTeslaRoutesPacket.class,
                SyncTeslaRoutesPacket::encode, SyncTeslaRoutesPacket::decode, SyncTeslaRoutesPacket::handle);
        CHANNEL.registerMessage(id++, TeslaWaypointClickPacket.class,
                TeslaWaypointClickPacket::encode, TeslaWaypointClickPacket::decode, TeslaWaypointClickPacket::handle);
        CHANNEL.registerMessage(id++, TeslaBurstPacket.class,
                TeslaBurstPacket::encode, TeslaBurstPacket::decode, TeslaBurstPacket::handle);
        // Shared
        CHANNEL.registerMessage(id++, ElectrifyPacket.class,
                ElectrifyPacket::encode, ElectrifyPacket::decode, ElectrifyPacket::handle);
        CHANNEL.registerMessage(id++, TunerClickPacket.class,
                TunerClickPacket::encode, TunerClickPacket::decode, TunerClickPacket::handle);
        // Comet, Razlom (0.1.8.0)
        CHANNEL.registerMessage(id++, CometBurstPacket.class,
                CometBurstPacket::encode, CometBurstPacket::decode, CometBurstPacket::handle);
        CHANNEL.registerMessage(id++, RazlomJetPacket.class,
                RazlomJetPacket::encode, RazlomJetPacket::decode, RazlomJetPacket::handle);
    }

    private ModNetwork() {
    }
}
