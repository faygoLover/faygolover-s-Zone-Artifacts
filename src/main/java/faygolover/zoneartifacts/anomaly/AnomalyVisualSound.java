package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A configurable particle + sound cue, entirely datapack-driven so new anomalies of the same
 * family never need new Java code just to look/sound different.
 * <p>
 * Used for the {@code ambient} cue on an {@link AnomalyType}. {@code particle} repeats server-side
 * every {@code intervalTicks} while the anomaly exists and isn't on cooldown (e.g. Electra's
 * crackling sparks). {@code sound}/{@code soundVolume}/{@code soundPitch} describe a continuous
 * idle loop instead: they aren't played from here at all — they're synced to the client (see
 * {@code SyncAnomalyTypeShapesPacket}) and run as a real, individually stoppable client-side
 * {@code SoundInstance} by {@code AnomalyAmbientSoundHandler}, so it can actually stop the instant
 * the anomaly fires or is removed instead of playing out a long clip to the end.
 * <p>
 * The one-shot trigger effect has its own, differently-shaped record — see
 * {@link AnomalyTriggerEffect}.
 */
public record AnomalyVisualSound(
        @Nullable ResourceLocation particle,
        int particleCount,
        int intervalTicks,
        @Nullable ResourceLocation sound,
        float soundVolume,
        float soundPitch
) {
}
