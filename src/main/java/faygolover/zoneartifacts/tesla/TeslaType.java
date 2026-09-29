package faygolover.zoneartifacts.tesla;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Datapack-driven config for a Tesla-style anomaly entity: constant damage/speed, detection
 * radius, the respawn/grow timings, and the sounds/colors used by its client-side effects. Mirrors
 * {@code AnomalyType}'s conventions (same {@code util.JsonHelper}/{@code DatapackParseException}
 * plumbing) but is its own, differently-shaped record, since Tesla is a moving entity rather than
 * a static zone.
 */
public record TeslaType(
        ResourceLocation id,
        ResourceLocation damageType,
        float damage,
        float speed,
        double detectRadius,
        int respawnDelayTicks,
        int growTicks,
        int color,
        @Nullable ResourceLocation idleSound,
        float idleVolume,
        float idlePitch,
        @Nullable ResourceLocation livingHitSound,
        List<ResourceLocation> hitSounds,
        float hitSoundVolume,
        float hitSoundPitch,
        int blockBurstRayCount,
        double blockBurstDistance
) {
}
