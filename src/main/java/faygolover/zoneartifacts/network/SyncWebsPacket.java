package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.WebSavedData;
import faygolover.zoneartifacts.client.web.WebClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> clients: every Web of a dimension (threads, which are snapped, glint strength). */
public class SyncWebsPacket {

    public record StrandView(Vec3 a, Vec3 b, boolean broken) {
    }

    public record WebView(int id, int intensity, List<StrandView> strands) {
    }

    private final ResourceKey<Level> dimension;
    private final List<WebView> webs;

    public SyncWebsPacket(ResourceKey<Level> dimension, List<WebView> webs) {
        this.dimension = dimension;
        this.webs = webs;
    }

    public static SyncWebsPacket of(ServerLevel level) {
        List<WebView> out = new ArrayList<>();
        for (WebSavedData.Web web : WebSavedData.get(level).webs()) {
            List<StrandView> strands = new ArrayList<>();
            for (WebSavedData.Strand s : web.strands) strands.add(new StrandView(s.a, s.b, s.brokenUntil > 0));
            out.add(new WebView(web.id, web.intensity, strands));
        }
        return new SyncWebsPacket(level.dimension(), out);
    }

    public static void broadcast(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), of(level));
    }

    public static void sendTo(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), of(player.serverLevel()));
    }

    private static void vec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 vec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void encode(SyncWebsPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension.location());
        buf.writeVarInt(packet.webs.size());
        for (WebView w : packet.webs) {
            buf.writeVarInt(w.id());
            buf.writeVarInt(w.intensity());
            buf.writeVarInt(w.strands().size());
            for (StrandView s : w.strands()) {
                vec(buf, s.a());
                vec(buf, s.b());
                buf.writeBoolean(s.broken());
            }
        }
    }

    public static SyncWebsPacket decode(FriendlyByteBuf buf) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        int n = buf.readVarInt();
        List<WebView> webs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = buf.readVarInt();
            int intensity = buf.readVarInt();
            int k = buf.readVarInt();
            List<StrandView> strands = new ArrayList<>(k);
            for (int j = 0; j < k; j++) strands.add(new StrandView(vec(buf), vec(buf), buf.readBoolean()));
            webs.add(new WebView(id, intensity, strands));
        }
        return new SyncWebsPacket(dim, webs);
    }

    public static void handle(SyncWebsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WebClient.set(packet.dimension, packet.webs))
        );
        ctx.get().setPacketHandled(true);
    }
}
