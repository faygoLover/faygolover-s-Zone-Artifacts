package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client -&gt; server: "the client's own (non-authoritative) raytrace says I left-clicked anomaly
 * {@code typeId} at {@code pos}; please remove it." Left-click needs this client-detect-then-send
 * round trip for the same reason cycling used to: the anomaly's zone doesn't necessarily coincide
 * with any real block, so Forge's LeftClickBlock/LeftClickEmpty never reaches the server reliably
 * on their own (LeftClickEmpty in particular is client-only) — see
 * {@code ClientAnomalyInputHandler} / {@code AnomalyClientTargeting}.
 * <p>
 * The server re-resolves the target itself by exact position + type rather than trusting the
 * client's aim, and also checks the sender is still holding a matching placer item. Worst case a
 * modified client can remove an anomaly it can already see synced to it, which is an acceptable
 * ceiling for a small trusted-GM server — it can't affect anything this item couldn't already do
 * through normal use.
 */
public class RemoveAnomalyPacket {

    private final ResourceLocation typeId;
    private final BlockPos pos;

    public RemoveAnomalyPacket(ResourceLocation typeId, BlockPos pos) {
        this.typeId = typeId;
        this.pos = pos;
    }

    public static void encode(RemoveAnomalyPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.typeId);
        buf.writeBlockPos(packet.pos);
    }

    public static RemoveAnomalyPacket decode(FriendlyByteBuf buf) {
        return new RemoveAnomalyPacket(buf.readResourceLocation(), buf.readBlockPos());
    }

    public static void handle(RemoveAnomalyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!packet.typeId.equals(AnomalyPlacerItem.heldTypeId(player))) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            AnomalySavedData data = AnomalySavedData.get(serverLevel);
            findAt(data, packet.pos, packet.typeId).ifPresent(instance -> {
                data.remove(instance);
                player.displayClientMessage(Component.translatable("message.fl_zone_arts.anomaly.removed",
                        Component.translatable("anomaly.fl_zone_arts.electra"), instance.pos().toShortString()), true);
                AnomalySyncHandler.broadcast(serverLevel);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static Optional<AnomalyInstance> findAt(AnomalySavedData data, BlockPos pos, ResourceLocation typeId) {
        for (AnomalyInstance instance : List.copyOf(data.instances())) {
            if (instance.pos().equals(pos) && instance.typeId().equals(typeId)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }
}
