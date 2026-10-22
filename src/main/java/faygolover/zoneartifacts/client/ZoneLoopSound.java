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
    /** Was heard at least once (the engine ticked it). */
    private boolean heard;
    /** When it was made: one the engine never took up (master volume at 0 plays nothing) counts as stopped after a second. */
    private final long born = net.minecraft.Util.getMillis();

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
        heard = true;
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

    /**
     * Also "stopped" once the sound engine dropped it without us: it stops every channel whose
     * volume goes to 0 (a volume slider at 0, the game minimised — flperf mutes it), and then the
     * loop was gone for good, its owner thinking it still played. Now the owner starts a new one. The
     * same if the engine never took it up at all (with the master volume at 0 nothing new plays).
     */
    @Override
    public boolean isStopped() {
        if (super.isStopped()) return true;
        if (net.minecraft.client.Minecraft.getInstance().getSoundManager().isActive(this)) return false;
        return heard || net.minecraft.Util.getMillis() - born > 1000L;
    }
}
