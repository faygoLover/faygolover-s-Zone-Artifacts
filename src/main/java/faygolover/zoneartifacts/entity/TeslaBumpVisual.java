package faygolover.zoneartifacts.entity;

/**
 * The discharge Tesla lets out when she bumps into solid terrain (patrolling or mid-chase): a
 * handful of bolts radiating outward from the point of impact for {@code reach} blocks, lasting
 * {@code durationTicks}. Purely cosmetic — the bump itself (losing a pursuit target, redirecting)
 * is decided server-side in {@code TeslaEntity} regardless of whether this ever renders.
 */
public record TeslaBumpVisual(int boltCount, double reach, int durationTicks) {
}
