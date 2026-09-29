package faygolover.zoneartifacts.client.gravity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A gravitational anomaly's idle loop (Voronka's hum). Silent on cooldown, fades back in with the
 * anomaly once it's ready, and gives way to the blowout sound while it's active. Stops itself
 * once the anomaly is gone or out of range.
 */
public class GravityLoopSound extends AbstractTickableSoundInstance {

    private final GravityClientHandler.State state;
    private final float baseVolume;

    public GravityLoopSound(GravityClientHandler.State state, SoundEvent sound, Vec3 center, float baseVolume) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.state = state;
        this.baseVolume = baseVolume;
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
        long now = mc.level.getGameTime();
        float target = state.active() ? 0.0f : baseVolume * state.readiness(now, 0.0f);
        this.volume += (target - this.volume) * (target < this.volume ? 0.15f : 0.05f);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
