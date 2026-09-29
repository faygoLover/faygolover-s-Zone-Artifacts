package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: "here is the full list of placed anomalies in this dimension right now."
 * Sent on join/dimension change and whenever an anomaly is placed, removed or tuned. Carries
 * only what clients draw: position, size, visual intensity, cooldown flag (damage and cooldown
 * length stay server-side).
 * Purely a rendering aid ({@link ClientAnomalyCache}) — no gameplay decision ever trusts it;
 * placement/removal/level changes stay server-authoritative (see AnomalyTargeting).
 */
public class SyncAnomaliesPacket {

    private final ResourceKey<Level> dimension;
    private final List<Entry> entries;

    public SyncAnomaliesPacket(ResourceKey<Level> dimension, List<Entry> entries) {
        this.dimension = dimension;
        this.entries = entries;
    }

    public static SyncAnomaliesPacket of(ResourceKey<Level> dimension, List<AnomalyInstance> instances) {
        List<Entry> entries = new ArrayList<>(instances.size());
        for (AnomalyInstance instance : instances) {
            entries.add(new Entry(instance.typeId(), instance.pos(), (float) instance.size(), instance.intensity(),
                    instance.cooldownTicks() > 0, instance.active(), (float) instance.speed()));
        }
        return new SyncAnomaliesPacket(dimension, entries);
    }

    public static void encode(SyncAnomaliesPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension.location());
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeResourceLocation(entry.typeId());
            buf.writeBlockPos(entry.pos());
            buf.writeFloat(entry.size());
            buf.writeVarInt(entry.intensity());
            buf.writeBoolean(entry.onCooldown());
            buf.writeBoolean(entry.active());
            buf.writeFloat(entry.speed());
        }
    }

    public static SyncAnomaliesPacket decode(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            float size = buf.readFloat();
            int intensity = buf.readVarInt();
            boolean onCooldown = buf.readBoolean();
            boolean active = buf.readBoolean();
            float speed = buf.readFloat();
            entries.add(new Entry(typeId, pos, size, intensity, onCooldown, active, speed));
        }
        return new SyncAnomaliesPacket(dimension, entries);
    }

    public static void handle(SyncAnomaliesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAnomalyCache.set(packet.dimension, packet.entries))
        );
        ctx.get().setPacketHandled(true);
    }

    /** {@code onCooldown} drives both the client-side idle-loop sound handler ({@code
     *  AnomalyAmbientSoundHandler}) and the ambient lightning arcs ({@code AnomalyArcRenderer}):
     *  both stop the instant this flips to true and resume the instant it flips back. In practice
     *  this flag is usually kept current by the much lighter {@link SyncAnomalyCooldownPacket}
     *  rather than a full resend of this packet — see that class's javadoc.
     *  {@code active} is used by the thermal anomalies (Zharka, Iney): someone is inside right now,
     *  so the visuals and sound ramp up. Always false for Electra. {@code speed}: the gravitational
     *  anomalies' force multiplier (the client computes the forces on its own player). */
    public record Entry(ResourceLocation typeId, BlockPos pos, float size, int intensity, boolean onCooldown, boolean active,
                        float speed) {
    }
}
