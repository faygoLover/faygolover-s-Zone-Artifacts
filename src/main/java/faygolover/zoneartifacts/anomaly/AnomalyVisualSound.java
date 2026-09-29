package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A configurable particle + sound cue, entirely datapack-driven so new anomalies of the same
 * family never need new Java code just to look/sound different.
 * <p>
 * Used for the {@code ambient} cue on an {@link AnomalyType}: repeats every {@code intervalTicks}
 * / {@code soundIntervalTicks} while the anomaly exists and isn't on cooldown (e.g. Electra's
 * crackling sparks + hum). The one-shot trigger effect has its own, differently-shaped record —
 * see {@link AnomalyTriggerEffect}.
 * <p>
 * {@code glowParticle}/{@code glowParticleCount} are optional and, when set, are spawned at the
 * same surface points as {@code particle} (see {@code AnomalyEngine.spawnParticlesOnSurfaces}) —
 * a faint persistent-looking glow riding along with the sparks, e.g. {@code minecraft:glow}.
 */
public record AnomalyVisualSound(
        @Nullable ResourceLocation particle,
        int particleCount,
        int intervalTicks,
        @Nullable ResourceLocation sound,
        int soundIntervalTicks,
        float soundVolume,
        float soundPitch,
        @Nullable ResourceLocation glowParticle,
        int glowParticleCount
) {
}
