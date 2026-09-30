package faygolover.zoneartifacts.pda;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One setting shown in the KPK: a slider from {@code min} to {@code max} in steps of {@code step},
 * its current {@code value} and the {@code standard} one. {@code label} is a translation key.
 */
public record PdaParam(String id, String label, double min, double max, double step, double value, double standard) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(label);
        buf.writeDouble(min);
        buf.writeDouble(max);
        buf.writeDouble(step);
        buf.writeDouble(value);
        buf.writeDouble(standard);
    }

    public static PdaParam decode(FriendlyByteBuf buf) {
        return new PdaParam(buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble());
    }

    /** {@code v} kept within the range and on a step. */
    public double snap(double v) {
        double k = Math.round((Math.max(min, Math.min(max, v)) - min) / step);
        return Math.max(min, Math.min(max, min + k * step));
    }
}
