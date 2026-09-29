package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Kisel: a puddle of bubbling, bright green, glowing liquid. Whatever falls or steps in — a
 * dropped item, a creature, an arrow — makes it seethe and hiss and glow brighter while its acid
 * eats it: items dissolve one by one, creatures are burnt (chemical damage) and their boots and
 * leggings corroded, projectiles melt away. Server logic in {@link KiselEngine}.
 */
public final class Kisel {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_chemical");
    public static final ResourceLocation IDLE_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "kisel_idle");
    public static final ResourceLocation HIT_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "kisel_hit");
    /** Seconds without contact before it calms down. */
    public static final int CALM_TICKS = 30;
    /** Something counts as "in it" up to this high over the surface. */
    public static final double CONTACT_HEIGHT = 0.6;

    private Kisel() {
    }

    /** Where touching it counts: the zone's footprint, around the surface. */
    public static AABB contactBox(AABB zone, double surfaceY) {
        return new AABB(zone.minX, surfaceY - 0.6, zone.minZ, zone.maxX, surfaceY + CONTACT_HEIGHT, zone.maxZ);
    }
}
