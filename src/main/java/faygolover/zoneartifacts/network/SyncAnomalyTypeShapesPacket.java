package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.AnomalyArcEffect;
import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import faygolover.zoneartifacts.anomaly.AnomalyVisualSound;
import faygolover.zoneartifacts.client.ClientAnomalyTypeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -> client, sent once on login: every loaded anomaly type's per-level sizes, plus its
 * ambient sound and lightning-arc config (if any). This is the piece that lets the client size a
 * preview/highlight box and run the ambient idle sound and arc visual at all — datapack content
 * ({@code AnomalyTypeManager}) only ever loads server-side, so on a real dedicated server the
 * client would otherwise have no idea how big "level 2" is or what Electra is supposed to
 * look/sound like.
 */
public class SyncAnomalyTypeShapesPacket {

    private final Map<ResourceLocation, List<Integer>> sizesByLevelByType;
    private final Map<ResourceLocation, AmbientSoundInfo> ambientSoundByType;
    private final Map<ResourceLocation, ArcInfo> arcByType;

    public SyncAnomalyTypeShapesPacket(Map<ResourceLocation, List<Integer>> sizesByLevelByType,
                                        Map<ResourceLocation, AmbientSoundInfo> ambientSoundByType,
                                        Map<ResourceLocation, ArcInfo> arcByType) {
        this.sizesByLevelByType = sizesByLevelByType;
        this.ambientSoundByType = ambientSoundByType;
        this.arcByType = arcByType;
    }

    public static SyncAnomalyTypeShapesPacket ofAllLoadedTypes() {
        Map<ResourceLocation, List<Integer>> sizes = new HashMap<>();
        Map<ResourceLocation, AmbientSoundInfo> sounds = new HashMap<>();
        Map<ResourceLocation, ArcInfo> arcs = new HashMap<>();
        for (Map.Entry<ResourceLocation, AnomalyType> entry : AnomalyTypeManager.all().entrySet()) {
            AnomalyType type = entry.getValue();
            sizes.put(entry.getKey(), type.shape().sizesByLevel());

            AnomalyVisualSound ambient = type.ambient();
            if (ambient != null && ambient.sound() != null) {
                sounds.put(entry.getKey(), new AmbientSoundInfo(ambient.sound(), ambient.soundVolume(), ambient.soundPitch()));
            }

            AnomalyArcEffect arc = type.arc();
            if (arc != null) {
                arcs.put(entry.getKey(), new ArcInfo(arc.bundleCount(), arc.pointsPerBundle(),
                        arc.minLifetimeTicks(), arc.maxLifetimeTicks(), arc.color()));
            }
        }
        return new SyncAnomalyTypeShapesPacket(sizes, sounds, arcs);
    }

    public static void encode(SyncAnomalyTypeShapesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.sizesByLevelByType.size());
        for (Map.Entry<ResourceLocation, List<Integer>> entry : packet.sizesByLevelByType.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (int size : entry.getValue()) {
                buf.writeVarInt(size);
            }
        }

        buf.writeVarInt(packet.ambientSoundByType.size());
        for (Map.Entry<ResourceLocation, AmbientSoundInfo> entry : packet.ambientSoundByType.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeResourceLocation(entry.getValue().soundId());
            buf.writeFloat(entry.getValue().volume());
            buf.writeFloat(entry.getValue().pitch());
        }

        buf.writeVarInt(packet.arcByType.size());
        for (Map.Entry<ResourceLocation, ArcInfo> entry : packet.arcByType.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            ArcInfo arc = entry.getValue();
            buf.writeVarInt(arc.bundleCount());
            buf.writeVarInt(arc.pointsPerBundle());
            buf.writeVarInt(arc.minLifetimeTicks());
            buf.writeVarInt(arc.maxLifetimeTicks());
            buf.writeInt(arc.color());
        }
    }

    public static SyncAnomalyTypeShapesPacket decode(FriendlyByteBuf buf) {
        int typeCount = buf.readVarInt();
        Map<ResourceLocation, List<Integer>> sizes = new HashMap<>();
        for (int i = 0; i < typeCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int levelCount = buf.readVarInt();
            List<Integer> levelSizes = new ArrayList<>(levelCount);
            for (int j = 0; j < levelCount; j++) {
                levelSizes.add(buf.readVarInt());
            }
            sizes.put(id, levelSizes);
        }

        int soundCount = buf.readVarInt();
        Map<ResourceLocation, AmbientSoundInfo> sounds = new HashMap<>();
        for (int i = 0; i < soundCount; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            ResourceLocation soundId = buf.readResourceLocation();
            float volume = buf.readFloat();
            float pitch = buf.readFloat();
            sounds.put(typeId, new AmbientSoundInfo(soundId, volume, pitch));
        }

        int arcCount = buf.readVarInt();
        Map<ResourceLocation, ArcInfo> arcs = new HashMap<>();
        for (int i = 0; i < arcCount; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            int bundleCount = buf.readVarInt();
            int pointsPerBundle = buf.readVarInt();
            int minLifetimeTicks = buf.readVarInt();
            int maxLifetimeTicks = buf.readVarInt();
            int color = buf.readInt();
            arcs.put(typeId, new ArcInfo(bundleCount, pointsPerBundle, minLifetimeTicks, maxLifetimeTicks, color));
        }

        return new SyncAnomalyTypeShapesPacket(sizes, sounds, arcs);
    }

    public static void handle(SyncAnomalyTypeShapesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientAnomalyTypeCache.set(packet.sizesByLevelByType, packet.ambientSoundByType, packet.arcByType))
        );
        ctx.get().setPacketHandled(true);
    }

    /** Just enough of {@code AnomalyVisualSound} for the client to run the looping idle sound
     *  itself (see {@code AnomalyAmbientSoundHandler}) without needing the rest of the — otherwise
     *  server-only — {@code AnomalyType}. */
    public record AmbientSoundInfo(ResourceLocation soundId, float volume, float pitch) {
    }

    /** Just enough of {@code AnomalyArcEffect} for the client to run the lightning-arc visual
     *  itself (see {@code AnomalyArcRenderer}). */
    public record ArcInfo(int bundleCount, int pointsPerBundle, int minLifetimeTicks, int maxLifetimeTicks, int color) {
    }
}
