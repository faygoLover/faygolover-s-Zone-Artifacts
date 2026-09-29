package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientAnomalyTypeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Syncs the handful of fields the client actually needs from each datapack-defined
 * {@code AnomalyType}: zone sizes-by-level (for geometry and highlighting), the ambient arc
 * config, and the ambient idle-sound config. Sent once per player on join and whenever the
 * datapack reloads. Everything else about a type (damage amounts, detect flags, trigger cooldown,
 * trigger-effect sounds — those are played by the server broadcasting a normal sound packet, so
 * the client never needs to know their ids) stays server-only.
 */
public class SyncAnomalyTypeShapesPacket {

    private final Map<ResourceLocation, TypeShape> types;

    public SyncAnomalyTypeShapesPacket(Map<ResourceLocation, TypeShape> types) {
        this.types = types;
    }

    public SyncAnomalyTypeShapesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, TypeShape> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();

            int levelCount = buf.readVarInt();
            List<Integer> sizes = new ArrayList<>(levelCount);
            for (int lvl = 0; lvl < levelCount; lvl++) {
                sizes.add(buf.readVarInt());
            }

            ArcShape arc = null;
            if (buf.readBoolean()) {
                int bundleCount = buf.readVarInt();
                int pointsPerBundle = buf.readVarInt();
                int minLifetime = buf.readVarInt();
                int maxLifetime = buf.readVarInt();
                int color = buf.readVarInt();
                arc = new ArcShape(bundleCount, pointsPerBundle, minLifetime, maxLifetime, color);
            }

            ResourceLocation ambientSound = null;
            float ambientVolume = 1.0f;
            float ambientPitch = 1.0f;
            if (buf.readBoolean()) {
                ambientSound = buf.readResourceLocation();
                ambientVolume = buf.readFloat();
                ambientPitch = buf.readFloat();
            }

            map.put(id, new TypeShape(sizes, arc, ambientSound, ambientVolume, ambientPitch));
        }
        this.types = map;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(types.size());
        for (Map.Entry<ResourceLocation, TypeShape> entry : types.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            TypeShape shape = entry.getValue();

            buf.writeVarInt(shape.sizesByLevel().size());
            for (int size : shape.sizesByLevel()) {
                buf.writeVarInt(size);
            }

            buf.writeBoolean(shape.arc() != null);
            if (shape.arc() != null) {
                ArcShape arc = shape.arc();
                buf.writeVarInt(arc.bundleCount());
                buf.writeVarInt(arc.pointsPerBundle());
                buf.writeVarInt(arc.minLifetimeTicks());
                buf.writeVarInt(arc.maxLifetimeTicks());
                buf.writeVarInt(arc.color());
            }

            buf.writeBoolean(shape.ambientSound() != null);
            if (shape.ambientSound() != null) {
                buf.writeResourceLocation(shape.ambientSound());
                buf.writeFloat(shape.ambientVolume());
                buf.writeFloat(shape.ambientPitch());
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAnomalyTypeCache.replaceAll(types));
        ctx.get().setPacketHandled(true);
    }

    public record TypeShape(List<Integer> sizesByLevel, @Nullable ArcShape arc,
                             @Nullable ResourceLocation ambientSound, float ambientVolume, float ambientPitch) {
    }

    public record ArcShape(int bundleCount, int pointsPerBundle, int minLifetimeTicks, int maxLifetimeTicks, int color) {
    }
}
