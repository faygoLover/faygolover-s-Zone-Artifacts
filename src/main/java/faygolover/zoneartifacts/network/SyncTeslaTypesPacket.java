package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.AnomalyVisualSound;
import faygolover.zoneartifacts.client.ClientTeslaTypeCache;
import faygolover.zoneartifacts.entity.TeslaArcVisual;
import faygolover.zoneartifacts.entity.TeslaType;
import faygolover.zoneartifacts.entity.TeslaTypeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -&gt; client, sent once on login (unlike Electra's per-dimension {@code
 * SyncAnomaliesPacket}, a Tesla type's visual/sound config doesn't vary by dimension, so there's
 * nothing to re-send on a dimension change): every loaded Tesla type's ambient-ball, bump and idle
 * sound settings — everything {@code TeslaVisualRenderer} / {@code TeslaAmbientSoundHandler} need
 * that only ever lived in the (server-only) datapack-loaded {@link TeslaType} otherwise. A live
 * {@code TeslaEntity}'s position, rotation etc. need no such packet at all — those are ordinary
 * vanilla entity-tracker traffic, since she's a real registered entity.
 */
public class SyncTeslaTypesPacket {

    private final Map<ResourceLocation, Info> infoByType;

    public SyncTeslaTypesPacket(Map<ResourceLocation, Info> infoByType) {
        this.infoByType = infoByType;
    }

    public static SyncTeslaTypesPacket ofAllLoadedTypes() {
        Map<ResourceLocation, Info> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, TeslaType> entry : TeslaTypeManager.all().entrySet()) {
            TeslaType type = entry.getValue();
            TeslaArcVisual arc = type.arc();
            AnomalyVisualSound idle = type.idle();
            map.put(entry.getKey(), new Info(type.growTicks(),
                    arc.bundleCount(), arc.pointsPerBundle(), arc.minLifetimeTicks(), arc.maxLifetimeTicks(), arc.color(), arc.radius(),
                    idle.sound(), idle.soundVolume(), idle.soundPitch()));
        }
        return new SyncTeslaTypesPacket(map);
    }

    public static void encode(SyncTeslaTypesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.infoByType.size());
        for (Map.Entry<ResourceLocation, Info> entry : packet.infoByType.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            Info info = entry.getValue();
            buf.writeVarInt(info.growTicks());
            buf.writeVarInt(info.bundleCount());
            buf.writeVarInt(info.pointsPerBundle());
            buf.writeVarInt(info.minLifetimeTicks());
            buf.writeVarInt(info.maxLifetimeTicks());
            buf.writeInt(info.color());
            buf.writeDouble(info.radius());
            buf.writeBoolean(info.idleSound() != null);
            if (info.idleSound() != null) {
                buf.writeResourceLocation(info.idleSound());
            }
            buf.writeFloat(info.idleVolume());
            buf.writeFloat(info.idlePitch());
        }
    }

    public static SyncTeslaTypesPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, Info> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            int growTicks = buf.readVarInt();
            int bundleCount = buf.readVarInt();
            int pointsPerBundle = buf.readVarInt();
            int minLifetimeTicks = buf.readVarInt();
            int maxLifetimeTicks = buf.readVarInt();
            int color = buf.readInt();
            double radius = buf.readDouble();
            ResourceLocation idleSound = buf.readBoolean() ? buf.readResourceLocation() : null;
            float idleVolume = buf.readFloat();
            float idlePitch = buf.readFloat();
            map.put(typeId, new Info(growTicks, bundleCount, pointsPerBundle, minLifetimeTicks, maxLifetimeTicks,
                    color, radius, idleSound, idleVolume, idlePitch));
        }
        return new SyncTeslaTypesPacket(map);
    }

    public static void handle(SyncTeslaTypesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTeslaTypeCache.set(packet.infoByType))
        );
        ctx.get().setPacketHandled(true);
    }

    /** Just enough of a {@link TeslaType} for the client to run the ambient ball, bump visual and
     *  looping idle sound itself, without needing the rest (damage, speed, aggro radius, ...),
     *  which stay server-only. */
    public record Info(int growTicks, int bundleCount, int pointsPerBundle, int minLifetimeTicks, int maxLifetimeTicks,
                        int color, double radius, @Nullable ResourceLocation idleSound, float idleVolume, float idlePitch) {
    }
}
