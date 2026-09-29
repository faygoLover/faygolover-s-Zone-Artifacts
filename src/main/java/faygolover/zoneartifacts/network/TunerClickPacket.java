package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.tuner.TunerKind;
import faygolover.zoneartifacts.tuner.TunerService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Client -> server: a tuner click on an anomaly. The target is either a zone anomaly
 * ({@code typeId} + anchor {@code pos}) or a completed Tesla route ({@code routeId} + the clicked
 * waypoint's {@code index} and {@code pos}). Zones and route points float off the block grid, so
 * the aim has to be resolved client-side; the server re-checks the held tuner, the distance and
 * that the target really is there before changing anything.
 */
public class TunerClickPacket {

    private final TunerKind kind;
    private final boolean increase;
    private final boolean sneaking;
    private final boolean route;
    @Nullable
    private final ResourceLocation typeId;
    private final int routeId;
    private final int index;
    private final BlockPos pos;

    private TunerClickPacket(TunerKind kind, boolean increase, boolean sneaking, boolean route,
                             @Nullable ResourceLocation typeId, int routeId, int index, BlockPos pos) {
        this.kind = kind;
        this.increase = increase;
        this.sneaking = sneaking;
        this.route = route;
        this.typeId = typeId;
        this.routeId = routeId;
        this.index = index;
        this.pos = pos;
    }

    public static TunerClickPacket anomaly(TunerKind kind, boolean increase, boolean sneaking, ResourceLocation typeId, BlockPos pos) {
        return new TunerClickPacket(kind, increase, sneaking, false, typeId, 0, 0, pos);
    }

    public static TunerClickPacket route(TunerKind kind, boolean increase, boolean sneaking, int routeId, int index, BlockPos pos) {
        return new TunerClickPacket(kind, increase, sneaking, true, null, routeId, index, pos);
    }

    public static void encode(TunerClickPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.kind.ordinal());
        buf.writeBoolean(packet.increase);
        buf.writeBoolean(packet.sneaking);
        buf.writeBoolean(packet.route);
        if (packet.route) {
            buf.writeInt(packet.routeId);
            buf.writeVarInt(packet.index);
        } else {
            buf.writeResourceLocation(packet.typeId);
        }
        buf.writeBlockPos(packet.pos);
    }

    public static TunerClickPacket decode(FriendlyByteBuf buf) {
        TunerKind kind = TunerKind.byOrdinal(buf.readByte());
        boolean increase = buf.readBoolean();
        boolean sneaking = buf.readBoolean();
        boolean route = buf.readBoolean();
        if (route) {
            int routeId = buf.readInt();
            int index = buf.readVarInt();
            return new TunerClickPacket(kind, increase, sneaking, true, null, routeId, index, buf.readBlockPos());
        }
        ResourceLocation typeId = buf.readResourceLocation();
        return new TunerClickPacket(kind, increase, sneaking, false, typeId, 0, 0, buf.readBlockPos());
    }

    public static void handle(TunerClickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.route) {
                TunerService.tuneRoute(player, packet.kind, packet.increase, packet.sneaking, packet.routeId, packet.index, packet.pos);
            } else if (packet.typeId != null) {
                TunerService.tuneAnomaly(player, packet.kind, packet.increase, packet.sneaking, packet.typeId, packet.pos);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
