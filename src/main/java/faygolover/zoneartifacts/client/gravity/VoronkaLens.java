package faygolover.zoneartifacts.client.gravity;

import faygolover.zoneartifacts.anomaly.Gravity;
import faygolover.zoneartifacts.client.distortion.Distortion;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The gravitational anomalies' air distortion (drawn by {@link Distortion}):
 * <ul>
 *     <li><b>Voronka</b> — a gravitational lens: at rest a barely visible, slowly breathing pinch
 *     with a thin band of stronger refraction at the zone's edge, fading in after the cooldown
 *     (nothing during it); while pulling the pinch deepens, ripples run in to the center, the
 *     picture twists and goes blurry; the tear sends a ring of distortion rushing out;</li>
 *     <li><b>Plesh</b> — while pulling, a lighter pinch with ripples; a small ring at the throw;</li>
 *     <li><b>Karusel</b> — a twisting column of shimmering air over its axis, faint at rest.</li>
 * </ul>
 */
public final class VoronkaLens {

    /** Where the Voronka's edge shows: a thin band of stronger refraction at this fraction of the lens. */
    static final double RIM_AT = 0.86;
    static final float RELEASE_TICKS = 20.0f;

    private VoronkaLens() {
    }

    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        float time = (now % 72000L) + partial;
        for (GravityClientHandler.State state : GravityClientHandler.states()) {
            switch (state.kind()) {
                case VORONKA -> voronka(out, state, now, partial, time);
                case PLESH -> plesh(out, state, now, partial, time);
                case KARUSEL -> karusel(out, state, now, partial, time);
                default -> {
                }
            }
        }
    }

    private static void voronka(List<Distortion.Patch> out, GravityClientHandler.State state, long now, float partial, float time) {
        Vec3 c = state.center();
        double half = state.entry().size() * 0.5;
        double reach = Gravity.reach(state.entry().size());
        float release = (now - state.releaseTick() + partial) / RELEASE_TICKS;
        double radius;
        double pinch = 0.0;
        double twist = 0.0;
        double ripple = 0.0;
        double ripplePhase = 0.0;
        double bump;
        double bumpAt = RIM_AT;
        double bumpWidth = 0.045;
        double blur = 0.0;
        float alpha;
        if (state.active()) {
            float t = state.progress(now, partial);
            // Opens out from the resting size over the first moments, then closes in.
            double open = Math.min(1.0, t / 0.15);
            radius = Mth.lerp(open, half * 1.1, Math.max(half, reach * (1.0 - 0.45 * t)));
            pinch = Mth.lerp(open, 0.2, 0.3 + 1.5 * t);
            twist = (0.35 + 0.1 * Math.sin(time * 0.15)) * t;
            ripple = 0.03 + 0.05 * t;
            ripplePhase = time * (0.05 + 0.07 * t);
            bump = -0.045 - 0.03 * t;
            blur = 0.0015 + 0.0055 * t;
            alpha = 1.0f;
        } else if (release >= 0.0f && release < 1.0f) {
            float e = 1.0f - (1.0f - release) * (1.0f - release);
            radius = reach * (0.5 + 1.3 * e);
            bump = -0.12 * (1.0f - release);
            bumpAt = 0.78;
            bumpWidth = 0.12;
            blur = 0.004 * (1.0f - release);
            alpha = 1.0f - release * release;
        } else {
            float ready = state.readiness(now, partial);
            if (ready <= 0.0f) return;
            radius = half * 1.1;
            pinch = 0.2 * ready * (1.0 + 0.25 * Math.sin(time * 0.04));
            ripple = 0.01 * ready;
            ripplePhase = time * 0.02;
            bump = -0.035;
            alpha = ready;
        }
        out.add(new Distortion.Lens(c, radius, pinch, twist, ripple, ripplePhase, bump, bumpAt, bumpWidth, blur, alpha, 28, 40));
    }

    private static void plesh(List<Distortion.Patch> out, GravityClientHandler.State state, long now, float partial, float time) {
        Vec3 c = state.center();
        double reach = Gravity.reach(state.entry().size());
        float release = (now - state.releaseTick() + partial) / 14.0f;
        if (state.active()) {
            float t = state.progress(now, partial);
            out.add(new Distortion.Lens(c, reach * (1.0 - 0.3 * t), 0.25 + 0.7 * t, 0.0, 0.02 + 0.03 * t,
                    time * (0.04 + 0.05 * t), 0.0, 0.8, 0.1, 0.001 + 0.002 * t, Math.min(1.0f, t * 4.0f), 16, 32));
        } else if (release >= 0.0f && release < 1.0f) {
            float e = 1.0f - (1.0f - release) * (1.0f - release);
            out.add(new Distortion.Lens(c, reach * (0.4 + 1.2 * e), 0.0, 0.0, 0.0, 0.0, -0.08 * (1.0f - release), 0.78, 0.12,
                    0.0, 1.0f - release * release, 16, 32));
        }
    }

    private static void karusel(List<Distortion.Patch> out, GravityClientHandler.State state, long now, float partial, float time) {
        Vec3 c = state.center();
        double reach = Gravity.reach(state.entry().size());
        double ground = state.groundY() != null ? state.groundY() : state.zone().minY;
        float strength;
        double speed;
        if (state.active()) {
            strength = Math.min(1.0f, 0.3f + state.progress(now, partial) * 2.0f);
            speed = 0.12;
        } else {
            strength = 0.2f * state.readiness(now, partial);
            speed = 0.03;
        }
        if (strength <= 0.01f) return;
        out.add(new Distortion.Haze(new Vec3(c.x, ground, c.z), reach * 1.1, reach * 1.1, 0.07 * strength,
                time * speed, 1.5, true, 1.0f, state.entry().pos().hashCode() * 0.01));
    }
}
