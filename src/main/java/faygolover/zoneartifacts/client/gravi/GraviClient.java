package faygolover.zoneartifacts.client.gravi;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.tesla.Gravi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gravi's pops, as each client sees them: a tiny Voronka. For {@link Gravi#WINDUP_TICKS} ticks the
 * air is sucked in and wound round (a small lens, {@link Distortion}), a little dust swirling in with
 * it; then a snap — a small ring of bent air, a few specks thrown out a short way.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GraviClient {

    private static final int AFTER_TICKS = 10;
    private static final RandomSource RANDOM = RandomSource.create();

    private record Pop(Vec3 pos, @Nullable Vec3 normal, @Nullable BlockState surface, long start) {
        long due() {
            return start + Gravi.WINDUP_TICKS;
        }
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static ClientLevel lastLevel;

    private GraviClient() {
    }

    public static void onPop(Vec3 pos, @Nullable Vec3 normal, int stateId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (pos.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 64.0 * 64.0) return;
        BlockState state = stateId == 0 ? null : Block.stateById(stateId);
        if (state != null && state.isAir()) state = null;
        if (POPS.size() > 200) POPS.remove(0);
        POPS.add(new Pop(pos, normal, state, mc.level.getGameTime()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            POPS.clear();
            lastLevel = level;
        }
        if (level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        for (Iterator<Pop> it = POPS.iterator(); it.hasNext(); ) {
            Pop pop = it.next();
            if (now > pop.due() + AFTER_TICKS) {
                it.remove();
                continue;
            }
            Vec3 p = pop.pos();
            if (now < pop.due()) {
                // Drawn in: a little dust swirling in from all round, faster the nearer the snap.
                float t = (now - pop.start()) / (float) Gravi.WINDUP_TICKS;
                if (RANDOM.nextFloat() < 0.6f) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                    if (pop.normal() != null && dir.dot(pop.normal()) < 0) dir = dir.scale(-1);
                    Vec3 from = p.add(dir.scale(0.8 + RANDOM.nextDouble() * 0.5));
                    Vec3 in = p.subtract(from);
                    Vec3 swirl = in.cross(pop.normal() != null ? pop.normal() : new Vec3(0, 1, 0));
                    if (swirl.lengthSqr() > 1.0E-6) swirl = swirl.normalize().scale(in.length() * 0.08);
                    // Reaches the middle at the snap and is gone there (the dust has hardly any drag:
                    // left to live, it flew on out the other side and far away).
                    long left = Math.max(2, pop.due() - now);
                    Vec3 v = in.scale(1.0 / left).add(swirl.scale(0.6));
                    if (!faygolover.zoneartifacts.client.ClientAnomalyCache.hiddenAt(from)) {
                        Particle dust = mc.particleEngine.createParticle(ModParticles.GRAV_DUST.get(), from.x, from.y, from.z, v.x, v.y, v.z);
                        if (dust != null) dust.setLifetime((int) left + 1);
                    }
                }
            } else if (now == pop.due()) {
                // The snap: a few specks thrown out a short way, settling.
                for (int i = 0; i < 4; i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize()
                            .scale(0.03 + RANDOM.nextDouble() * 0.04);
                    if (pop.normal() != null && v.dot(pop.normal()) < 0) v = v.subtract(pop.normal().scale(2.0 * v.dot(pop.normal())));
                    faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.GRAV_DUST.get(), p.x, p.y, p.z, v.x, v.y - 0.01, v.z);
                }
            }
        }
    }

    /** The bent air, for {@code Distortion}: a small pinch while sucking in, a ring rushing out after. */
    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        float time = (now % 72000L) + partial;
        for (Pop pop : POPS) {
            float t = (now - pop.start() + partial) / Gravi.WINDUP_TICKS;
            if (t < 1.0f) {
                // A plain gravitational pull: the air sucked in and wound round, tighter and tighter.
                out.add(new Distortion.Lens(pop.pos(), 1.1, 0.4 + 1.8 * t, 0.9 * t * t, 0.025, time * 0.12, -0.04 * t, 0.8, 0.1,
                        0.0, Math.min(1.0f, t * 3.0f), 12, 24).churn(0.05 + 0.08 * t, 0.0, time * 0.1));
            } else {
                float after = (now - pop.due() + partial) / AFTER_TICKS;
                if (after >= 1.0f) continue;
                float e = 1.0f - (1.0f - after) * (1.0f - after);
                out.add(new Distortion.Lens(pop.pos(), 0.4 + 1.1 * e, 0.0, 0.0, 0.0, 0.0, -0.1 * (1.0f - after), 0.75, 0.12,
                        0.0, 1.0f - after * after, 10, 20));
            }
        }
    }
}
