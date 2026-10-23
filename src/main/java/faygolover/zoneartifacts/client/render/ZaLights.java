package faygolover.zoneartifacts.client.render;

import faygolover.flrender.render.WorldLight;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.client.Breath;
import faygolover.zoneartifacts.client.fantom.FantomClient;
import faygolover.zoneartifacts.client.firefly.FireflyClient;
import faygolover.zoneartifacts.client.kisel.KiselClient;
import faygolover.zoneartifacts.client.thermal.ThermalClientHandler;
import faygolover.zoneartifacts.tesla.ChemCometEntity;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.GraviEntity;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * The light burning and glowing anomalies cast on the world round them — the Comets, the Tesla, the
 * Kisel's puddles, the Zharka at work, the fireflies and the phantoms — handed to FL Render's
 * {@link WorldLight}, which draws it (for the picture only) with the other mods' lights.
 */
final class ZaLights {

    private ZaLights() {
    }

    static void collect(float partial, Vec3 cam, WorldLight.Sink out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long time = mc.level.getGameTime();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof TeslaEntity t) || e instanceof GraviEntity || !t.getState().isVisible()) continue;
            Vec3 c = e.getPosition(partial).add(0.0, e.getBbHeight() / 2.0, 0.0);
            float size = t.getSize();
            // Steady light that breathes slowly, like the ball itself — no flicker.
            float flicker = 1.0f + 0.12f * Breath.of(time % 72000L + partial, e.getId() * 0.37f);
            if (e instanceof ChemCometEntity) {
                out.point(c, 3.5f * size + 1.0f, 0.55f, 0.85f, 0.2f, 0.35f);
            } else if (e instanceof CometEntity comet) {
                // Deep, saturated fire colours (paler ones washed the walls almost white).
                if (comet.isCold()) out.point(c, 6.5f * size + 1.5f, 0.2f, 0.6f, 1.0f, 1.35f * flicker);
                else out.point(c, 6.5f * size + 1.5f, 1.0f, 0.42f, 0.07f, 1.5f * flicker);
            } else {
                // The Tesla: a steady blue-white light (it strobed).
                out.point(c, 5.0f * size + 1.5f, 0.6f, 0.75f, 1.0f, 0.9f * flicker);
            }
        }
        KiselClient.lights((x, y, z, size, act) -> out.point(new Vec3(x, y, z), (float) (2.0 + size),
                0.45f, 1.0f, 0.25f, 0.3f + 0.35f * act), partial);
        for (Map.Entry<ThermalClientHandler.Key, ThermalClientHandler.State> e : ThermalClientHandler.states()) {
            ThermalClientHandler.State st = e.getValue();
            if (st.entry() == null || !st.isZharka()) continue;
            float a = st.activity(partial);
            if (a < 0.02f) continue;
            AABB zone = AnomalyGeometry.box(st.entry());
            Vec3 c = new Vec3((zone.minX + zone.maxX) * 0.5, zone.minY + 0.6, (zone.minZ + zone.maxZ) * 0.5);
            out.point(c, (float) (2.5 + st.entry().size()), 1.0f, 0.5f, 0.15f, 0.9f * a);
        }
        FireflyClient.lights((pos, size) -> out.point(pos, (float) (size * 0.6 + 2.5), 0.55f, 1.0f, 0.25f, 0.35f));
        FantomClient.lights(cam, (pos, size, fade) -> out.point(pos, (float) (size + 3.0), 0.35f, 0.55f, 1.0f, 0.45f * fade));
    }
}
