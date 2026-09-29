package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The Tesla's fixed characteristics — what used to live in {@code tesla/tesla.json}. The adjustable
 * parts (size, speed, respawn delay, damage, intensity) are stored per route in {@link TeslaRoute}
 * and changed with the tuners; their defaults and the base speed come from the common config.
 */
public final class Tesla {

    /** Size of a new route's Tesla in blocks: the visible ball is {@code size} blocks across. */
    public static final double DEFAULT_SIZE = 1.0;

    /** Hitbox edge at size 1 — a bit under the visible ball, so a route hugging a floor or wall
     *  doesn't register false collisions. Scales with the size. */
    public static final float BASE_HITBOX = 0.8f;

    /** How long the "growing out of a point" spawn takes, standing still. */
    public static final int SPAWN_GROW_TICKS = 20;

    public static final ResourceLocation DAMAGE_TYPE = id("anomaly_shock");

    public static final ResourceLocation IDLE_SOUND = id("tesla_idle");
    public static final float IDLE_VOLUME = 0.6f;
    public static final float IDLE_PITCH = 1.0f;

    public static final ResourceLocation CONTACT_SOUND = id("electra_blast_living");
    public static final ResourceLocation BLOCK_SOUND = id("electra_blast_nut");
    public static final List<ResourceLocation> HIT_SOUNDS = List.of(id("electra_hit"), id("electra_hit1"));
    public static final float SOUND_VOLUME = 0.8f;

    private Tesla() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
