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
        CHANNEL.registerMessage(id++, CycleAnomalyPacket.class,
                CycleAnomalyPacket::encode, CycleAnomalyPacket::decode, CycleAnomalyPacket::handle);
    }

    private ModNetwork() {
    }
}
