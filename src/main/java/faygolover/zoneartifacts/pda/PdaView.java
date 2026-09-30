package faygolover.zoneartifacts.pda;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * What the KPK window shows for one target: its name, its settings, the four switches (packed as
 * bits: 0 enabled, 1 harmful, 2 audible, 3 visible, 4 acts on creative / spectators; -1 = it has none), which of them
 * it has at all ({@code switchMask}) and whether it can be moved.
 */
public record PdaView(PdaTarget target, Component title, List<PdaParam> params, int switches, int switchMask, boolean movable) {

    public void encode(FriendlyByteBuf buf) {
        target.encode(buf);
        buf.writeComponent(title);
        buf.writeVarInt(params.size());
        for (PdaParam p : params) p.encode(buf);
        buf.writeInt(switches);
        buf.writeInt(switchMask);
        buf.writeBoolean(movable);
    }

    public static PdaView decode(FriendlyByteBuf buf) {
        PdaTarget target = PdaTarget.decode(buf);
        Component title = buf.readComponent();
        int n = buf.readVarInt();
        List<PdaParam> params = new ArrayList<>(n);
        for (int i = 0; i < n; i++) params.add(PdaParam.decode(buf));
        return new PdaView(target, title, params, buf.readInt(), buf.readInt(), buf.readBoolean());
    }
}
