package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A fully-parsed anomaly definition loaded from data/&lt;ns&gt;/anomaly_types/*.json.
 * Immutable; {@link AnomalyTypeManager} swaps in a new map of these on every datapack reload.
 */
public record AnomalyType(
        ResourceLocation id,
        AnomalyShape shape,
        AnomalyTrigger trigger,
        AnomalyDetect detect,
        AnomalyEffect effect,
        @Nullable AnomalyVisualSound ambient,
        @Nullable AnomalyVisualSound triggerEffect
) {
    public int maxLevel() {
        return shape.maxLevel();
    }
}
