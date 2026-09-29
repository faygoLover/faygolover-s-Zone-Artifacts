package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.WebSavedData;
import faygolover.zoneartifacts.item.WebPlacerItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: with the Web tool, left-click on a thread — remove it, or (sneaking) its whole web. */
public class WebEditPacket {

    private final int webId;
    private final int index;
    private final boolean whole;

    public WebEditPacket(int webId, int index, boolean whole) {
        this.webId = webId;
        this.index = index;
        this.whole = whole;
    }

    public static void encode(WebEditPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.webId);
        buf.writeVarInt(packet.index);
        buf.writeBoolean(packet.whole);
    }

    public static WebEditPacket decode(FriendlyByteBuf buf) {
        return new WebEditPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(WebEditPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !WebPlacerItem.holds(player)) return;
            WebSavedData data = WebSavedData.get(player.serverLevel());
            WebSavedData.Web web = data.get(packet.webId);
            if (web == null || web.distanceSqTo(player.getEyePosition()) > 20.0 * 20.0) return;
            if (packet.whole || web.strands.size() <= 1) {
                data.remove(web.id);
                player.displayClientMessage(Component.translatable("message.fl_zone_arts.web.removed_web"), true);
            } else if (packet.index >= 0 && packet.index < web.strands.size()) {
                web.strands.remove(packet.index);
                data.setDirty();
                player.displayClientMessage(Component.translatable("message.fl_zone_arts.web.removed_strand", web.strands.size()), true);
            }
            SyncWebsPacket.broadcast(player.serverLevel());
        });
        ctx.get().setPacketHandled(true);
    }
}
