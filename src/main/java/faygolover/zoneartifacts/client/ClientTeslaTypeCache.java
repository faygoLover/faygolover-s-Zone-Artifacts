package faygolover.zoneartifacts.client;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Client-side mirror of the Tesla type fields synced via {@code TeslaSyncTypePacket}. */
public final class ClientTeslaTypeCache {

    private static int color = 0x8FE8FF;
    @Nullable
    private static ResourceLocation idleSound = null;
    private static float idleVolume = 1.0f;
    private static float idlePitch = 1.0f;

    private ClientTeslaTypeCache() {
    }

    public static void set(int newColor, @Nullable ResourceLocation newIdleSound, float newIdleVolume, float newIdlePitch) {
        color = newColor;
        idleSound = newIdleSound;
        idleVolume = newIdleVolume;
        idlePitch = newIdlePitch;
    }

    public static int color() {
        return color;
    }

    @Nullable
    public static ResourceLocation idleSound() {
        return idleSound;
    }

    public static float idleVolume() {
        return idleVolume;
    }

    public static float idlePitch() {
        return idlePitch;
    }
}
