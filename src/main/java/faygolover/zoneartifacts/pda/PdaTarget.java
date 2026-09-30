package faygolover.zoneartifacts.pda;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * What the KPK is pointed at: a zone anomaly ({@code typeId} + anchor {@code pos}), a completed
 * route (its {@code id} and the clicked point: {@code index} + {@code pos}), a block anomaly
 * (Burning Fluff, Hedgehog: {@code pos}) or a Web ({@code id}).
 */
public record PdaTarget(int kind, @Nullable ResourceLocation typeId, BlockPos pos, int id, int index) {

    public static final int ZONE = 0;
    public static final int ROUTE = 1;
    public static final int BLOCK = 2;
    public static final int WEB = 3;

    public static PdaTarget zone(ResourceLocation typeId, BlockPos pos) {
        return new PdaTarget(ZONE, typeId, pos, 0, 0);
    }

    public static PdaTarget route(int routeId, int index, BlockPos pos) {
        return new PdaTarget(ROUTE, null, pos, routeId, index);
    }

    public static PdaTarget block(BlockPos pos) {
        return new PdaTarget(BLOCK, null, pos, 0, 0);
    }

    public static PdaTarget web(int webId) {
        return new PdaTarget(WEB, null, BlockPos.ZERO, webId, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeBoolean(typeId != null);
        if (typeId != null) buf.writeResourceLocation(typeId);
        buf.writeBlockPos(pos);
        buf.writeVarInt(id);
        buf.writeVarInt(index);
    }

    public static PdaTarget decode(FriendlyByteBuf buf) {
        int kind = buf.readByte();
        ResourceLocation typeId = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new PdaTarget(kind, typeId, buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }
}
