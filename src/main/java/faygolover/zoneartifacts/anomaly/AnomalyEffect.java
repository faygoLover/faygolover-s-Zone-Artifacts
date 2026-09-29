package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The damage dealt to a living, damageable entity that trips the zone.
 * {@code damageType} names a data-driven damage type (see data/&lt;ns&gt;/damage_type/*.json);
 * {@code damageByLevel} must have exactly one entry per level declared in the anomaly's shape.
 */
public record AnomalyEffect(ResourceLocation damageType, List<Float> damageByLevel) {

    public float damageForLevel(int level) {
        int index = Math.max(1, Math.min(level, damageByLevel.size())) - 1;
        return damageByLevel.get(index);
    }
}
