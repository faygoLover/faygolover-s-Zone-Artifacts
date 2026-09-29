package faygolover.zoneartifacts.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** An anomaly's standing loop whose volume follows {@code volume} (smoothly) and which stops once
 *  {@code alive} says the anomaly is gone or out of range. */
public class ZoneLoopSound extends AbstractTickableSoundInstance {

    private final BooleanSupplier alive;
    private final DoubleSupplier target;

    public ZoneLoopSound(SoundEvent sound, Vec3 pos, BooleanSupplier alive, DoubleSupplier volume) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.alive = alive;
        this.target = volume;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.volume = 0.0f;
        this.pitch = 1.0f;
    }

    /** Plays it at another pitch (so several of the same anomaly don't sound in unison). */
    public ZoneLoopSound pitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    @Override
    public void tick() {
        if (!alive.getAsBoolean()) {
            stop();
            return;
        }
        float want = (float) target.getAsDouble();
        this.volume += (want - this.volume) * 0.1f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
