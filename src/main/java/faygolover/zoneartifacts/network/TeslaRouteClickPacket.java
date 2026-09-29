package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.entity.TeslaRouteInteractionHandler;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -&gt; server: "the client's own (non-authoritative) raytrace says I left-clicked Tesla
 * waypoint {@code pos}; please act on it." Left-click needs this round trip for the same reason
 * {@code RemoveAnomalyPacket} does: {@code LeftClickEmpty} — the event that fires once a waypoint's
 * marker block has been broken and it's just floating in open air (expected once its route is
 * finished; a Tesla bumps into her own route's blocks until the GM clears them) — is a client-only
 * Forge event that never reaches the server on its own. See {@code ClientTeslaRouteInputHandler} and
 * {@code TeslaRouteClientTargeting}. Right-click doesn't need this: {@code RightClickItem} reaches
 * the server directly, so it's resolved server-side by {@code TeslaRouteTargeting} instead (see
 * {@code TeslaRouteInteractionHandler#onRightClickItem}). While the waypoint is still a real block,
 * both click types reach the server through {@code RightClickBlock}/{@code LeftClickBlock} directly
 * and this packet is never sent.
 * <p>
 * The server re-runs the exact same {@link TeslaRouteInteractionHandler#handleLeftClick} logic a
 * direct block click would have, re-checking the sender is still holding a route tool rather than
 * trusting the client's aim at all.
 */
public class TeslaRouteClickPacket {

    private final BlockPos pos;

    public TeslaRouteClickPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(TeslaRouteClickPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static TeslaRouteClickPacket decode(FriendlyByteBuf buf) {
        return new TeslaRouteClickPacket(buf.readBlockPos());
    }

    public static void handle(TeslaRouteClickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (TeslaRouteToolItem.heldStack(player) == null) return;

            TeslaRouteInteractionHandler.handleLeftClick(level, player, packet.pos);
        });
        ctx.get().setPacketHandled(true);
    }
}
