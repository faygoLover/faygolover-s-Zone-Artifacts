package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * The {@code ambient} idle-loop sound cue on an {@link AnomalyType}, entirely datapack-driven so
 * new anomalies of the same family never need new Java code just to sound different.
 * <p>
 * Never played from server code at all — it's synced to the client (see
 * {@code SyncAnomalyTypeShapesPacket}) and run as a real, individually stoppable client-side
 * {@code SoundInstance} by {@code AnomalyAmbientSoundHandler}, so it can actually stop the instant
 * the anomaly fires or is removed instead of playing out a long clip to the end.
 * <p>
 * The always-on ambient <em>visual</em> is a separate concern now — see {@link AnomalyArcEffect} —
 * and the one-shot trigger effect has its own, differently-shaped record — see
 * {@link AnomalyTriggerEffect}.
 */
public record AnomalyVisualSound(
        @Nullable ResourceLocation sound,
        float soundVolume,
        float soundPitch
) {
}
