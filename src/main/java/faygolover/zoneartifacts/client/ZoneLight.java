package faygolover.zoneartifacts.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How lit a non-glowing anomaly is where it stands (gas, jelly, glass, film, mist): 0.03..1. The night
 * sky gives almost nothing (a little moonlight), torches and lamps do; a curve, so dusk darkens it
 * gradually. Before, every such effect kept a floor of 15–25 % and a fifth of the daylight at night —
 * in the dark (above all with a shader pack's black nights) they looked as if they glowed.
 */
public final class ZoneLight {

    private ZoneLight() {
    }

    public static float at(ClientLevel level, Vec3 p, float partial) {
        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(p));
        float block = LightTexture.block(packed) / 15.0f;
        float sky = LightTexture.sky(packed) / 15.0f;
        // getSkyDarken runs from about 0.2 (night) to 1 (day): what of the daylight is left.
        float day = Mth.clamp((level.getSkyDarken(partial) - 0.2f) / 0.8f, 0.0f, 1.0f);
        float l = Math.max(block * 0.9f, sky * (0.06f + 0.94f * day));
        return 0.03f + 0.97f * (float) Math.pow(l, 1.4);
    }
}
