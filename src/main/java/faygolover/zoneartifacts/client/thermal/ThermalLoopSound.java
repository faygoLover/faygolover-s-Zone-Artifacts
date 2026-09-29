package faygolover.zoneartifacts.client.thermal;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A thermal anomaly's standing loop (Zharka's fire hum). Volume and pitch follow the anomaly's
 * activity every tick, so the sound swells within half a second when someone steps in and dies
 * down over three seconds after. Stops itself once the anomaly is gone or out of range.
 */
public class ThermalLoopSound extends AbstractTickableSoundInstance {

    private final ThermalClientHandler.Key key;
    private final float idleVolume;
    private final float activeVolume;

    public ThermalLoopSound(ThermalClientHandler.Key key, SoundEvent sound, Vec3 center, float idleVolume, float activeVolume) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.key = key;
        this.idleVolume = idleVolume;
        this.activeVolume = activeVolume;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = center.x;
        this.y = center.y;
        this.z = center.z;
        this.volume = idleVolume;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (!ThermalClientHandler.isTracked(key)) {
            stop();
            return;
        }
        float a = ThermalClientHandler.activity(key);
        this.volume = Mth.lerp(a, idleVolume, activeVolume);
        this.pitch = 1.0f + 0.12f * a;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
