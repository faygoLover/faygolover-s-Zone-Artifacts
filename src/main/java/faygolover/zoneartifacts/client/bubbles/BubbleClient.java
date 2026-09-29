package faygolover.zoneartifacts.client.bubbles;

import faygolover.zoneartifacts.anomaly.BubbleEntity;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Soap bubbles' extras: the shimmer of the air round each bubble, and a burst's flash, rippling
 *  shock and spray of film. */
public final class BubbleClient {

    private static final RandomSource RANDOM = RandomSource.create();

    private record Pop(Vec3 at, float radius, long born) {
    }

    private static final List<Pop> POPS = new ArrayList<>();

    private BubbleClient() {
    }

    public static void onPop(Vec3 at, float radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        POPS.add(new Pop(at, radius, level.getGameTime()));
        if (POPS.size() > 32) POPS.remove(0);
        level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        for (int i = 0; i < 24; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(0.15 + RANDOM.nextDouble() * 0.2);
            level.addParticle(ModParticles.GRAV_DUST.get(), at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.BUBBLE_POP, at.x + RANDOM.nextGaussian() * 0.3, at.y + RANDOM.nextGaussian() * 0.3,
                    at.z + RANDOM.nextGaussian() * 0.3, 0.0, 0.0, 0.0);
        }
    }

    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = (now % 72000L) + partial;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof BubbleEntity bubble)) continue;
            Vec3 c = bubble.getPosition(partial).add(0.0, bubble.getBbHeight() / 2.0, 0.0);
            if (c.distanceToSqr(cam) > 32.0 * 32.0) continue;
            float charge = bubble.charging() ? 1.0f : 0.0f;
            out.add(Distortion.Lens.shimmer(c, 0.42 + 0.1 * charge, 0.03 + 0.05 * charge, time * 0.1 + bubble.getId(), 0.9f));
        }
        POPS.removeIf(p -> now - p.born() > 12);
        for (Pop p : POPS) {
            float t = (now - p.born() + partial) / 12.0f;
            out.add(Distortion.Lens.shimmer(p.at(), p.radius() * (0.3 + 0.9 * t), 0.08 * (1.0f - t), time * 0.3, 1.0f - t));
        }
    }
}
