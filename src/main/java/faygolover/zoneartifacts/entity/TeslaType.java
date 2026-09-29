package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.anomaly.AnomalyVisualSound;
import net.minecraft.resources.ResourceLocation;

/**
 * Datapack-defined tuning for one Tesla "flavor" — everything about how fast she flies, how hard
 * she hits, how long she takes to respawn, and what she looks/sounds like. Unlike Electra's {@code
 * AnomalyType} (which parameterizes a shared volumetric-zone engine), Tesla's actual behavior
 * (route-following, pursuit, block bumps, the hit-then-electrify-then-die sequence) lives in
 * {@code TeslaEntity} itself — this record only ever supplies numbers, never new behavior, exactly
 * as the project's own design notes describe for "genuinely unique" anomalies.
 * <p>
 * {@code idle} reuses {@link AnomalyVisualSound} (sound + volume + pitch) since the shape is
 * identical to Electra's ambient sound config.
 */
public record TeslaType(
        ResourceLocation id,
        double speed,
        double aggroRadius,
        ResourceLocation damageType,
        float damage,
        int respawnTicks,
        int growTicks,
        int electrifyTicks,
        TeslaArcVisual arc,
        TeslaBumpVisual bump,
        AnomalyVisualSound idle,
        TeslaImpactSound impact
) {
}
