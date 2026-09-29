package faygolover.zoneartifacts.client.chem;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.gravity.GoreClient;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.tesla.ChemCometEntity;
import faygolover.zoneartifacts.tesla.Tesla;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The Chemical Comet's look ({@link Gas}):
 * <ul>
 *     <li><b>the body</b> — a clot of yellow-green gas: a dense, darker middle with several puffs
 *     rolling over each other around it, turning slowly;</li>
 *     <li><b>the trail</b> — wisps left behind, sinking (the gas is heavy), now and then a drop that
 *     falls and leaves a small stain;</li>
 *     <li><b>the burst</b> — the gas spills out low over the ground, creeping outwards, rolling and
 *     thinning out over the cloud's lifetime; yellow-brown burns splash the surfaces around.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChemClient {

    static final int LEMON = 0xD9E250;
    static final int YELLOW_GREEN = 0xAFC43A;
    static final int OLIVE = 0x7A8A2C;
    static final int DARK_OLIVE = 0x4C561C;
    static final int ACID = 0xB6F03C;
    static final int ACID_DARK = 0x4E8A14;

    private static final RandomSource RANDOM = RandomSource.create();

    static {
        Gas.addFrameSource(ChemClient::bodies);
    }

    private ChemClient() {
    }

    /** Loads the class (so its body source is registered) — called from the renderer's constructor. */
    static void init() {
    }

    // ---- the body ----------------------------------------------------------------------------------

    private static void bodies(List<Gas.FramePuff> out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.getFrameTime();
        float time = (mc.level.getGameTime() % 72000L) + partial;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ChemCometEntity comet) || !comet.getState().isVisible()) continue;
            Vec3 c = comet.getPosition(partial).add(0.0, comet.getBbHeight() / 2.0, 0.0);
            if (c.distanceToSqr(cam) > 96.0 * 96.0) continue;
            float grow = 1.0f;
            if (comet.getState() == TeslaEntity.State.SPAWNING) {
                float t = Mth.clamp(comet.clientStateAge(partial) / Tesla.SPAWN_GROW_TICKS, 0.0f, 1.0f);
                grow = t * t * (3.0f - 2.0f * t);
            }
            if (grow < 0.02f) continue;
            double size = comet.getSize() * grow;
            int count = 5 + Math.min(10, ModClientConfig.effective(comet.getIntensity()));
            RandomSource shape = RandomSource.create(comet.getId() * 7919L);
            // A core of liquid acid, with the gas rolling round and over it.
            out.add(new Gas.FramePuff(c, 0.2 * size, 0.95f, ACID, ACID_DARK, shape.nextFloat() * 10f, true));
            // Dense middle.
            out.add(new Gas.FramePuff(c.add(cam.subtract(c).normalize().scale(-0.05 * size)), 0.42 * size, 0.6f, OLIVE, YELLOW_GREEN, shape.nextFloat() * 10f));
            for (int i = 0; i < count; i++) {
                Vec3 axis = new Vec3(shape.nextGaussian(), shape.nextGaussian(), shape.nextGaussian()).normalize();
                Vec3 start = new Vec3(shape.nextGaussian(), shape.nextGaussian(), shape.nextGaussian()).normalize();
                double speed = 0.01 + shape.nextDouble() * 0.025;
                double dist = (0.12 + shape.nextDouble() * 0.22) * size;
                double r = (0.22 + shape.nextDouble() * 0.2) * size * (1.0 + 0.12 * Math.sin(time * 0.07 + i));
                Vec3 dir = rotate(start, axis, time * speed + i);
                int inner = i % 3 == 0 ? YELLOW_GREEN : OLIVE;
                int outer = i % 2 == 0 ? LEMON : YELLOW_GREEN;
                out.add(new Gas.FramePuff(c.add(dir.scale(dist)), r, 0.5f + 0.2f * shape.nextFloat(), inner, outer, shape.nextFloat() * 10f));
            }
            satellites(out, comet, c, size, time);
        }
    }

    /**
     * Instead of the fiery Comets' prominences: little bubbles of the same liquid acid that bud off
     * the body, circle it for a while on a slanted orbit and sink back in (each on its own cycle, a
     * new orbit every time).
     */
    private static void satellites(List<Gas.FramePuff> out, ChemCometEntity comet, Vec3 c, double size, float time) {
        int count = Math.max(1, (ModClientConfig.effective(comet.getIntensity()) + 1) / 2);
        for (int i = 0; i < count; i++) {
            RandomSource fixed = RandomSource.create(comet.getId() * 131L + i * 977L);
            int period = 70 + fixed.nextInt(60);
            float offset = fixed.nextFloat() * period;
            long cycle = (long) Math.floor((time + offset) / period);
            float phase = ((time + offset) - cycle * period) / period;
            RandomSource orbit = RandomSource.create(comet.getId() * 7L + i * 1_000_003L + cycle * 31L);
            Vec3 axis = new Vec3(orbit.nextGaussian(), orbit.nextGaussian() * 0.6 + 1.0, orbit.nextGaussian()).normalize();
            Vec3 start = axis.cross(new Vec3(orbit.nextGaussian(), orbit.nextGaussian(), orbit.nextGaussian())).normalize();
            if (!Double.isFinite(start.x)) continue;
            double out01 = Math.sin(Math.PI * phase);
            double rise = Math.min(1.0, out01 * 1.6);
            double dist = (0.22 + (0.38 + 0.2 * orbit.nextDouble()) * rise) * size;
            double angle = phase * Math.PI * 2.0 * (1.0 + orbit.nextInt(2)) * (orbit.nextBoolean() ? 1 : -1);
            Vec3 dir = rotate(start, axis, angle);
            double r = (0.05 + 0.03 * orbit.nextDouble()) * size * (0.4 + 0.6 * rise);
            out.add(new Gas.FramePuff(c.add(dir.scale(dist)), r, (float) (0.9 * Math.min(1.0, rise * 2.0)), ACID, ACID_DARK,
                    orbit.nextFloat() * 10f, true));
        }
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1.0 - cos)));
    }

    // ---- the trail -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ChemCometEntity comet) || !comet.getState().isVisible()
                    || comet.getState() == TeslaEntity.State.SPAWNING) continue;
            Vec3 c = comet.position().add(0.0, comet.getBbHeight() / 2.0, 0.0);
            if (c.distanceToSqr(cam) > 64.0 * 64.0) continue;
            double size = comet.getSize();
            Vec3 motion = comet.position().subtract(comet.xo, comet.yo, comet.zo);
            if (motion.lengthSqr() > 4.0) motion = Vec3.ZERO;
            if ((now + comet.getId()) % 3 == 0) {
                Vec3 p = c.add(RANDOM.nextGaussian() * 0.12 * size, RANDOM.nextGaussian() * 0.12 * size, RANDOM.nextGaussian() * 0.12 * size);
                Gas.add(new Gas.Puff(p, motion.scale(-0.15).add(0.0, -0.006, 0.0), 0.22 * size, 0.5 * size, now,
                        30 + RANDOM.nextInt(15), 0.32f, OLIVE, YELLOW_GREEN, RANDOM.nextFloat() * 10f).drag(0.96));
            }
            if (RANDOM.nextInt(35) == 0) {
                level.addParticle(ModParticles.CHEM_DROP.get(), c.x + RANDOM.nextGaussian() * 0.1 * size,
                        c.y - 0.3 * size, c.z + RANDOM.nextGaussian() * 0.1 * size, motion.x * 0.5, -0.03, motion.z * 0.5);
            }
        }
    }

    // ---- the burst ----------------------------------------------------------------------------------

    public static void onBurst(Vec3 center, double groundY, float radius, float seconds, int intensity) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        int eff = ModClientConfig.effective(intensity);
        int life = Math.max(20, (int) (seconds * 20.0f));

        // A dense, bright gulp of gas right where it burst.
        for (int i = 0; i < 8; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian()).scale(0.05);
            Gas.add(new Gas.Puff(center, v, 0.4, 1.1 + RANDOM.nextDouble() * 0.6, now, 18 + RANDOM.nextInt(10), 0.6f,
                    YELLOW_GREEN, LEMON, RANDOM.nextFloat() * 10f).drag(0.9));
        }
        // The cloud: spilling out low and creeping over the ground.
        int count = Mth.clamp((int) ((16 + 6 * radius) * (0.6 + eff / 7.5)), 20, 140);
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            double dist = radius * Math.sqrt(RANDOM.nextDouble()) * 0.95;
            double v0 = dist / 16.0;
            Vec3 vel = new Vec3(Math.cos(a) * v0, -0.02, Math.sin(a) * v0);
            Vec3 start = center.add(RANDOM.nextGaussian() * 0.3, RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.3);
            double settle = groundY + 0.25 + RANDOM.nextDouble() * 1.1;
            int inner = RANDOM.nextInt(3) == 0 ? DARK_OLIVE : OLIVE;
            int outer = RANDOM.nextInt(2) == 0 ? YELLOW_GREEN : LEMON;
            Gas.add(new Gas.Puff(start, vel, 0.4, 0.9 + RANDOM.nextDouble() * 0.9, now,
                    (int) (life * (0.75 + RANDOM.nextDouble() * 0.45)), 0.38f + RANDOM.nextFloat() * 0.18f,
                    inner, outer, RANDOM.nextFloat() * 10f).settle(settle));
        }
        // A lighter part of it flies off sideways and up too: thinner, and gone sooner.
        int spray = Mth.clamp((int) ((8 + 2.5 * radius) * (0.6 + eff / 7.5)), 10, 50);
        for (int i = 0; i < spray; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) * 0.9 + 0.15, RANDOM.nextGaussian()).normalize();
            double v0 = 0.06 + RANDOM.nextDouble() * 0.08 + radius * 0.006;
            Vec3 start = center.add(dir.scale(0.2));
            int outer = RANDOM.nextInt(2) == 0 ? YELLOW_GREEN : LEMON;
            Gas.add(new Gas.Puff(start, dir.scale(v0), 0.3, 0.7 + RANDOM.nextDouble() * 0.6, now,
                    (int) (life * (0.2 + RANDOM.nextDouble() * 0.2)), 0.2f + RANDOM.nextFloat() * 0.1f,
                    OLIVE, outer, RANDOM.nextFloat() * 10f).drag(0.9));
        }
        // Burns on the surfaces around.
        int stains = 10 + (int) (radius * 2);
        for (int i = 0; i < stains; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), -Math.abs(RANDOM.nextGaussian()) - 0.3, RANDOM.nextGaussian()).normalize();
            GoreClient.addStain(level, center, dir, radius * 1.2, 0.25 + RANDOM.nextDouble() * 0.5,
                    0x8C8030, 0x3F3A12, 120, 900 + RANDOM.nextInt(300));
        }
    }
}
