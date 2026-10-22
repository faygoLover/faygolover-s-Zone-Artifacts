package faygolover.zoneartifacts.client.fx;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.DoubleSupplier;

/** A loop heard "inside the head" (not from anywhere): its volume follows {@code volume}, and it
 *  stops itself once that has stayed at nothing for two seconds. */
public class HumLoop extends AbstractTickableSoundInstance {

    private final DoubleSupplier target;
    /** Was heard at least once (the engine ticked it). */
    private boolean heard;
    /** When it was made: one the engine never took up (master volume at 0 plays nothing) counts as stopped after a second. */
    private final long born = net.minecraft.Util.getMillis();
    private int silent;

    public HumLoop(SoundEvent sound, DoubleSupplier volume) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.target = volume;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0f;
    }

    @Override
    public void tick() {
        heard = true;
        float want = (float) target.getAsDouble();
        this.volume += (want - this.volume) * 0.08f;
        if (want <= 0.001f && this.volume < 0.01f) {
            if (++silent > 40) stop();
        } else {
            silent = 0;
        }
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

    /** 0..1: how deep {@code p} is inside {@code zone} ({@code ramp} blocks from any side to 1). */
    public static float depthInside(AABB zone, Vec3 p, double ramp) {
        if (!zone.contains(p)) return 0.0f;
        double d = Math.min(Math.min(Math.min(p.x - zone.minX, zone.maxX - p.x), Math.min(p.y - zone.minY, zone.maxY - p.y)),
                Math.min(p.z - zone.minZ, zone.maxZ - p.z));
        return (float) Math.min(1.0, d / ramp);
    }
}
