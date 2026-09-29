package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ElectrifyRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server -> client: draw arcs crawling over entity {@code entityId}'s surface for
 * {@code durationTicks}, starting after {@code delayTicks} (Electra waits for its strike bolts to
 * connect; the Tesla and the command start at once). Purely visual — any damage is dealt
 * separately, once, by whoever sent this.
 */
public class ElectrifyPacket {

    private final int entityId;
    private final int durationTicks;
    private final int delayTicks;
    private final int intensity;

    public ElectrifyPacket(int entityId, int durationTicks, int delayTicks, int intensity) {
        this.entityId = entityId;
        this.durationTicks = durationTicks;
        this.delayTicks = delayTicks;
        this.intensity = intensity;
    }

    /** Sends to everyone tracking {@code target}, and to {@code target} itself if it's a player,
     *  so the victim sees their own electrification too. */
    public static void send(Entity target, int durationTicks, int delayTicks, int intensity) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new ElectrifyPacket(target.getId(), durationTicks, delayTicks, intensity));
    }

    public static void encode(ElectrifyPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.durationTicks);
        buf.writeVarInt(packet.delayTicks);
        buf.writeVarInt(packet.intensity);
    }

    public static ElectrifyPacket decode(FriendlyByteBuf buf) {
        return new ElectrifyPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ElectrifyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ElectrifyRenderer.onElectrify(packet.entityId, packet.durationTicks, packet.delayTicks, packet.intensity))
        );
        ctx.get().setPacketHandled(true);
    }
}
