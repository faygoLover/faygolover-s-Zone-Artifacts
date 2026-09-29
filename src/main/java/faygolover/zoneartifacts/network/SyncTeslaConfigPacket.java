package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.tesla.TeslaClientCache;
import faygolover.zoneartifacts.tesla.TeslaConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: the few Tesla config values the client needs for itself — how long the spawn
 * "grow" takes, and which idle loop to play at what volume/pitch. Everything else in the Tesla
 * config (speed, damage, radius...) stays server-only. Sent on login and after every /reload.
 */
public class SyncTeslaConfigPacket {

    private final int spawnGrowTicks;
    private final ResourceLocation idleSound;
    private final float idleVolume;
    private final float idlePitch;

    public SyncTeslaConfigPacket(int spawnGrowTicks, ResourceLocation idleSound, float idleVolume, float idlePitch) {
        this.spawnGrowTicks = spawnGrowTicks;
        this.idleSound = idleSound;
        this.idleVolume = idleVolume;
        this.idlePitch = idlePitch;
    }

    public static SyncTeslaConfigPacket of(TeslaConfig config) {
        return new SyncTeslaConfigPacket(config.spawnGrowTicks(), config.idleSound(), config.idleVolume(), config.idlePitch());
    }

    public static void encode(SyncTeslaConfigPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.spawnGrowTicks);
        buf.writeResourceLocation(packet.idleSound);
        buf.writeFloat(packet.idleVolume);
        buf.writeFloat(packet.idlePitch);
    }

    public static SyncTeslaConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncTeslaConfigPacket(buf.readVarInt(), buf.readResourceLocation(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(SyncTeslaConfigPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeslaClientCache.setConfig(packet.spawnGrowTicks, packet.idleSound, packet.idleVolume, packet.idlePitch))
        );
        ctx.get().setPacketHandled(true);
    }
}
