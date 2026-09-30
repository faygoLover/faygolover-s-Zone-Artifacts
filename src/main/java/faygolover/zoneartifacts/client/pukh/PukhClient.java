package faygolover.zoneartifacts.client.pukh;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.anomaly.PukhSpores;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.client.gravity.GoreClient;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Burning Fluff's puffs as the client sees them: a small grey-green cloud of spores flying the
 * same path as on the server ({@link PukhSpores#step}), swelling, and where it hits a surface it
 * leaves dark burns that fade after a while. Also the crumbling of its strands when it's torn off.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PukhClient {

    private static final int ASH = 0x4F5646;
    private static final int ASH_LIGHT = 0x7F866C;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Puff {
        Vec3 pos;
        Vec3 vel;
        int age;
        boolean landed;

        Puff(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
        }
    }

    private static final List<Puff> PUFFS = new ArrayList<>();
    private static ClientLevel lastLevel;

    private PukhClient() {
    }

    public static void onPuff(Vec3 from, Vec3 at, double range) {
        Vec3 dir = at.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) return;
        PUFFS.add(new Puff(from, dir.normalize().scale(range * (1.0 - Pukh.SPORE_DRAG))));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // The strands give a shudder of spores where it came from.
            for (int i = 0; i < 6; i++) {
                faygolover.zoneartifacts.client.ClientAnomalyCache.particle(mc.level, ModParticles.PUKH_SPORE.get(), from.x + RANDOM.nextGaussian() * 0.2, from.y + RANDOM.nextGaussian() * 0.2,
                        from.z + RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.02, RANDOM.nextGaussian() * 0.02, RANDOM.nextGaussian() * 0.02);
            }
        }
    }

    public static void onCrumble(BlockPos pos, Direction facing, double length) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        AABB box = Pukh.hangingBox(pos, facing, length);
        int n = (int) Mth.clamp(20 * length, 15, 90);
        for (int i = 0; i < n; i++) {
            faygolover.zoneartifacts.client.ClientAnomalyCache.particle(mc.level, ModParticles.PUKH_FLAKE.get(), Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX),
                    Mth.lerp(RANDOM.nextDouble(), box.minY, box.maxY), Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ),
                    RANDOM.nextGaussian() * 0.01, -0.03, RANDOM.nextGaussian() * 0.01);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            PUFFS.clear();
            lastLevel = level;
        }
        if (level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        for (Iterator<Puff> it = PUFFS.iterator(); it.hasNext(); ) {
            Puff puff = it.next();
            if (++puff.age > Pukh.SPORE_LIFE || puff.landed) {
                it.remove();
                continue;
            }
            Vec3[] s = PukhSpores.step(level, puff.pos, puff.vel);
            Vec3 prev = puff.pos;
            puff.pos = s[0];
            puff.vel = s[1];
            double grow = 0.45 + Math.min(1.0, puff.age / 20.0) * 0.55;
            // A trail of grey-green haze and a few spores.
            Gas.add(new Gas.Puff(prev.lerp(puff.pos, 0.5), puff.vel.scale(0.3), 0.25 * grow, 0.55 * grow, now,
                    18 + RANDOM.nextInt(8), 0.3f, ASH, ASH_LIGHT, RANDOM.nextFloat() * 10f).drag(0.85));
            for (int i = 0; i < 2; i++) {
                faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.PUKH_SPORE.get(), puff.pos.x + RANDOM.nextGaussian() * 0.25 * grow,
                        puff.pos.y + RANDOM.nextGaussian() * 0.25 * grow, puff.pos.z + RANDOM.nextGaussian() * 0.25 * grow,
                        puff.vel.x * 0.4, puff.vel.y * 0.4, puff.vel.z * 0.4);
            }
            if (s[2] != null) {
                // It hit a surface: burns, a small fading scorch.
                puff.landed = true;
                Vec3 n = s[2];
                for (int i = 0; i < 4; i++) {
                    Vec3 jitter = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).scale(0.3);
                    GoreClient.addStain(level, puff.pos.add(n.scale(0.3)).add(jitter), n.scale(-1.0), 0.9,
                            0.18 + RANDOM.nextDouble() * 0.25, 0x302E22, 0x15140E, 150, 600 + RANDOM.nextInt(300));
                }
                Gas.add(new Gas.Puff(puff.pos, n.scale(0.02), 0.4, 0.9, now, 25, 0.35f, ASH, ASH_LIGHT, RANDOM.nextFloat() * 10f));
            }
        }
    }
}
