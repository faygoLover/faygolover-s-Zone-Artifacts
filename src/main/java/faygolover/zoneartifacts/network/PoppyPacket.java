package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.PoppyEngine;
import faygolover.zoneartifacts.client.poppy.PoppyClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> clients: a creature's sleep on the poppy field changed (a micro-sleep of {@code ticks},
 *  full sleep, waking up over {@code ticks}, or awake). Its own client runs its eyes and legs; the
 *  others lay its body down. */
public class PoppyPacket {

    private final int entityId;
    private final int phase;
    private final int ticks;

    public PoppyPacket(int entityId, int phase, int ticks) {
        this.entityId = entityId;
        this.phase = phase;
        this.ticks = ticks;
    }

    public static void send(LivingEntity e, PoppyEngine.Phase phase, int ticks) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e),
                new PoppyPacket(e.getId(), phase.ordinal(), ticks));
    }

    public static void sendTo(ServerPlayer player, LivingEntity e, PoppyEngine.Phase phase, int ticks) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PoppyPacket(e.getId(), phase.ordinal(), ticks));
    }

    public static void encode(PoppyPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.phase);
        buf.writeVarInt(packet.ticks);
    }

    public static PoppyPacket decode(FriendlyByteBuf buf) {
        return new PoppyPacket(buf.readVarInt(), buf.readByte(), buf.readVarInt());
    }

    public static void handle(PoppyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PoppyClient.onState(packet.entityId, packet.phase, packet.ticks))
        );
        ctx.get().setPacketHandled(true);
    }
}
