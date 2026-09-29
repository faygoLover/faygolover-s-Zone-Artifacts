package faygolover.zoneartifacts.client.distortion;

import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.gravity.VoronkaLens;
import faygolover.zoneartifacts.client.razlom.RazlomClientHandler;
import faygolover.zoneartifacts.client.thermal.ThermalClientHandler;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Where the air is bent ({@link Distortion}):
 * <ul>
 *     <li>gravitational anomalies — {@link VoronkaLens};</li>
 *     <li><b>Zharka</b> — heat shimmer rising through the zone, stronger when it flares;
 *     <b>Iney</b> — a faint, slow shimmer of cold air;</li>
 *     <li><b>Razlom</b> — heat shimmer over its flame (not while it's out), and along the jet;</li>
 *     <li><b>Comets</b> — a shimmer around the ball.</li>
 * </ul>
 */
final class DistortionSources {

    private DistortionSources() {
    }

    static void collect(Minecraft mc, List<Distortion.Patch> out, long now, float partial, Vec3 cam) {
        float time = (now % 72000L) + partial;
        VoronkaLens.collect(out, now, partial);
        faygolover.zoneartifacts.client.gravi.GraviClient.collect(out, now, partial);
        faygolover.zoneartifacts.client.lift.LiftClient.collect(out, now, partial);
        faygolover.zoneartifacts.client.rust.RustClient.collect(out, now, partial);
        faygolover.zoneartifacts.client.bubbles.BubbleClient.collect(out, now, partial);
        faygolover.zoneartifacts.client.khlopushka.KhlopushkaClient.collect(out, now, partial);
        faygolover.zoneartifacts.client.kamerton.KamertonClient.collect(out, now, partial);
        thermal(out, partial, time);
        razlom(mc, out, now, partial, time);
        comets(mc, out, partial, time, cam);
    }

    private static void thermal(List<Distortion.Patch> out, float partial, float time) {
        for (Map.Entry<ThermalClientHandler.Key, ThermalClientHandler.State> e : ThermalClientHandler.states()) {
            ThermalClientHandler.State state = e.getValue();
            if (state.entry() == null) continue;
            AABB zone = AnomalyGeometry.centeredAabb(state.entry().pos(), state.entry().size());
            float a = state.activity(partial);
            Vec3 base = new Vec3((zone.minX + zone.maxX) * 0.5, zone.minY, (zone.minZ + zone.maxZ) * 0.5);
            double width = Math.max(zone.getXsize(), zone.getZsize());
            double seed = state.entry().pos().hashCode() * 0.01;
            if (state.isZharka()) {
                out.add(new Distortion.Haze(base, width, zone.getYsize() * 1.2 + 0.5, 0.025 + 0.05 * a,
                        time * (0.035 + 0.03 * a), 2.5, false, 1.0f, seed));
            } else {
                out.add(new Distortion.Haze(base, width, zone.getYsize() * 0.8 + 0.3, 0.01 + 0.02 * a,
                        time * 0.015, 1.5, false, 1.0f, seed));
            }
        }
    }

    private static void razlom(Minecraft mc, List<Distortion.Patch> out, long now, float partial, float time) {
        for (RazlomClientHandler.State state : RazlomClientHandler.states()) {
            if (state.entry() == null) continue;
            boolean jetting = state.jetActive(now);
            float flame = state.flameLevel(partial);
            float heat = state.cold() ? 0.6f : 1.0f;
            double seed = state.entry().pos().hashCode() * 0.01;
            if (flame > 0.05f) {
                Vec3 f = state.flame();
                out.add(new Distortion.Haze(f.add(0.0, -0.15, 0.0), 0.8 * (jetting ? 1.3 : 1.0), 1.4,
                        0.035 * flame * heat * (jetting ? 1.6 : 1.0), time * 0.05, 2.0, false, 1.0f, seed));
            }
            RazlomClientHandler.Jet jet = state.jet();
            if (jetting && jet != null && mc.level != null) {
                Razlom.Arc arc = Razlom.arc(mc.level, state.flame(), jet.aim(partial), state.jetExtend(now, partial));
                List<Vec3> points = arc.points();
                for (int k = 3; k < points.size(); k += 3) {
                    double t = k / (double) Math.max(1, points.size() - 1);
                    out.add(Distortion.Lens.shimmer(points.get(k), 0.35 + 0.35 * t, 0.06 * heat, time * 0.15 + k * 0.3, 0.9f));
                }
            }
        }
    }

    private static void comets(Minecraft mc, List<Distortion.Patch> out, float partial, float time, Vec3 cam) {
        if (mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof CometEntity comet)) continue;
            if (!comet.getState().isVisible() || comet.getState() == TeslaEntity.State.SPAWNING) continue;
            Vec3 c = comet.getPosition(partial).add(0.0, comet.getBbHeight() / 2.0, 0.0);
            if (c.distanceToSqr(cam) > 48.0 * 48.0) continue;
            double size = comet.getSize();
            out.add(Distortion.Lens.shimmer(c, 0.95 * size, comet.isCold() ? 0.035 : 0.055,
                    time * 0.12 + comet.getId() * 0.37, 1.0f));
        }
    }
}
