package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A configurable particle + sound cue, entirely datapack-driven so new anomalies of the same
 * family never need new Java code just to look/sound different.
 * <p>
 * Used for two different purposes on the same {@link AnomalyType}:
 * <ul>
 *     <li>{@code ambient}: repeats every {@code intervalTicks} / {@code soundIntervalTicks}
 *     while the anomaly exists (e.g. Electra's crackling sparks + hum).</li>
 *     <li>{@code trigger_effect}: fires once whenever the anomaly's effect triggers
 *     (interval fields are ignored for this use).</li>
 * </ul>
 */
public record AnomalyVisualSound(
        @Nullable ResourceLocation particle,
        int particleCount,
        int intervalTicks,
        @Nullable ResourceLocation sound,
        int soundIntervalTicks,
        float soundVolume,
        float soundPitch
) {
}
