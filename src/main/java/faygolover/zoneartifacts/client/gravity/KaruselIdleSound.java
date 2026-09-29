package faygolover.zoneartifacts.client.gravity;

import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Karusel's idle: a very quiet, slowly swelling rustle of wind. Silent on cooldown, fades back in
 * with the anomaly once it's ready, and gives way to the blowout sound while it spins. Stops
 * itself once the anomaly is gone or out of range.
 */
public class KaruselIdleSound extends AbstractTickableSoundInstance {

    private static final float BASE_VOLUME = 0.14f;

    private final GravityClientHandler.State state;
    private int age;

    public KaruselIdleSound(GravityClientHandler.State state, Vec3 center) {
        super(ModSounds.KARUSEL_IDLE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.state = state;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        this.volume = 0.0f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !GravityClientHandler.isTracked(state)) {
            stop();
            return;
        }
        age++;
        long now = mc.level.getGameTime();
        float target = state.active() ? 0.0f : BASE_VOLUME * state.readiness(now, 0.0f);
        // Slow swells, as if the air were going round.
        target *= 0.75f + 0.25f * Mth.sin(age * 0.045f);
        this.volume += (target - this.volume) * 0.08f;
        this.pitch = 0.95f + 0.06f * Mth.sin(age * 0.021f + 1.3f);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
