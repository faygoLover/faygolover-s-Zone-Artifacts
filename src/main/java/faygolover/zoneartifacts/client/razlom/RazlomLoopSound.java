package faygolover.zoneartifacts.client.razlom;

import faygolover.zoneartifacts.anomaly.Razlom;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The Razlom flame's quiet hum (Zharka's loop, softer), a little louder while the jet burns. */
public class RazlomLoopSound extends AbstractTickableSoundInstance {

    private final BlockPos pos;

    public RazlomLoopSound(BlockPos pos, SoundEvent sound, Vec3 at) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.pos = pos;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
        this.volume = Razlom.IDLE_VOLUME;
    }

    @Override
    public void tick() {
        if (!RazlomClientHandler.isTracked(pos)) {
            stop();
            return;
        }
        // Silent while the flame is out (resting), louder while the jet burns.
        float target = RazlomClientHandler.isResting(pos) ? 0.0f
                : RazlomClientHandler.isJetting(pos) ? Razlom.JET_IDLE_VOLUME : Razlom.IDLE_VOLUME;
        this.volume += (target - this.volume) * 0.2f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
