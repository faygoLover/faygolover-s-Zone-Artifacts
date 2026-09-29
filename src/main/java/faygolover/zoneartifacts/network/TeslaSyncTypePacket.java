package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientTeslaTypeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Syncs the handful of Tesla type fields the client needs: core/arc color and the idle sound. */
public class TeslaSyncTypePacket {

    private final int color;
    @Nullable
    private final ResourceLocation idleSound;
    private final float idleVolume;
    private final float idlePitch;

    public TeslaSyncTypePacket(int color, @Nullable ResourceLocation idleSound, float idleVolume, float idlePitch) {
        this.color = color;
        this.idleSound = idleSound;
        this.idleVolume = idleVolume;
        this.idlePitch = idlePitch;
    }

    public TeslaSyncTypePacket(FriendlyByteBuf buf) {
        this.color = buf.readVarInt();
        this.idleSound = buf.readBoolean() ? buf.readResourceLocation() : null;
        this.idleVolume = buf.readFloat();
        this.idlePitch = buf.readFloat();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(color);
        buf.writeBoolean(idleSound != null);
        if (idleSound != null) {
            buf.writeResourceLocation(idleSound);
        }
        buf.writeFloat(idleVolume);
        buf.writeFloat(idlePitch);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeslaTypeCache.set(color, idleSound, idleVolume, idlePitch));
        ctx.get().setPacketHandled(true);
    }
}
