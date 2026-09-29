package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The one-shot effect an anomaly plays when it actually fires (as opposed to {@link
 * AnomalyVisualSound}, which is the always-on ambient hum/sparks). Unlike the ambient cue, a
 * trigger needs to tell two cases apart — the sound design Electra uses:
 * <ul>
 *     <li>{@code livingSound}: a player or mob got shocked (damage was actually applied).</li>
 *     <li>{@code projectileSound}: only a thrown projectile tripped the field, nothing was hurt.</li>
 *     <li>{@code hitSounds}: on a living hit, one of these is additionally played <em>from the
 *     hurt entity's own position</em> rather than the zone's center — a close-up "zap" layered on
 *     top of the center-based blast — with one chosen at random per hit for variety.</li>
 * </ul>
 * {@code particle} always plays regardless of which sound branch fires.
 */
public record AnomalyTriggerEffect(
        @Nullable ResourceLocation particle,
        int particleCount,
        @Nullable ResourceLocation livingSound,
        @Nullable ResourceLocation projectileSound,
        float soundVolume,
        float soundPitch,
        List<ResourceLocation> hitSounds,
        float hitSoundVolume,
        float hitSoundPitch
) {
}
