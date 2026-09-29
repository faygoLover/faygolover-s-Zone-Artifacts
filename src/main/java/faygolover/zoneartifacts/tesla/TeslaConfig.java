package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Tesla gameplay parameters, loaded from {@code data/fl_zone_arts/tesla/tesla.json}
 * (see {@link TeslaConfigManager}). Every field has a default, so a missing or broken file never
 * leaves Teslas without a config — the loader just logs and falls back to {@link #DEFAULTS}.
 *
 * @param speed             movement speed in blocks per tick, same while patrolling and chasing
 * @param damage            damage of each of the two hits (on contact, and when electrification ends)
 * @param chaseRadius       radius in which a player flagged {@code artifact_equipped} is picked up
 * @param respawnDelayTicks how long a popped Tesla stays gone before reappearing on its route
 * @param spawnGrowTicks    how long the "growing out of a point" spawn takes, standing still
 * @param electrifyTicks    how long a struck entity stays electrified before the second hit
 */
public record TeslaConfig(
        double speed,
        float damage,
        ResourceLocation damageType,
        double chaseRadius,
        int respawnDelayTicks,
        int spawnGrowTicks,
        int electrifyTicks,
        ResourceLocation idleSound,
        float idleVolume,
        float idlePitch,
        ResourceLocation contactSound,
        ResourceLocation blockSound,
        List<ResourceLocation> hitSounds,
        float soundVolume
) {

    public static final TeslaConfig DEFAULTS = new TeslaConfig(
            0.12,
            3.0f,
            new ResourceLocation(ZoneArtifacts.MODID, "anomaly_shock"),
            10.0,
            80,
            20,
            20,
            new ResourceLocation(ZoneArtifacts.MODID, "tesla_idle"),
            0.6f,
            1.0f,
            new ResourceLocation(ZoneArtifacts.MODID, "electra_blast_living"),
            new ResourceLocation(ZoneArtifacts.MODID, "electra_blast_nut"),
            List.of(new ResourceLocation(ZoneArtifacts.MODID, "electra_hit"),
                    new ResourceLocation(ZoneArtifacts.MODID, "electra_hit1")),
            0.8f
    );
}
