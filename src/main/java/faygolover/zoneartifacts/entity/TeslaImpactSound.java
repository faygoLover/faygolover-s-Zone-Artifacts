package faygolover.zoneartifacts.entity;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sound cues for Tesla actually hitting a living target. Unlike Electra's {@code
 * AnomalyTriggerEffect}, there's no living-vs-projectile split to make — Tesla never reacts to
 * projectiles at all — so this is just a blast sound plus a close-up "zap" from the target's own
 * position, picked at random per hit for variety, exactly like Electra's {@code hitSounds}. Tesla
 * reuses Electra's own sound assets by default (see tesla.json) rather than needing new ones.
 */
public record TeslaImpactSound(
        @Nullable ResourceLocation blastSound,
        float blastVolume,
        float blastPitch,
        List<ResourceLocation> hitSounds,
        float hitVolume,
        float hitPitch
) {
}
