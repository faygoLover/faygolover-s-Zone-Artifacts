package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

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
        CHANNEL.registerMessage(id++, TeslaElectrifyPacket.class,
                TeslaElectrifyPacket::encode, TeslaElectrifyPacket::decode, TeslaElectrifyPacket::handle);
        CHANNEL.registerMessage(id++, TeslaBumpPacket.class,
                TeslaBumpPacket::encode, TeslaBumpPacket::decode, TeslaBumpPacket::handle);
        CHANNEL.registerMessage(id++, SyncTeslaTypesPacket.class,
                SyncTeslaTypesPacket::encode, SyncTeslaTypesPacket::decode, SyncTeslaTypesPacket::handle);
    }

    private ModNetwork() {
    }
}
