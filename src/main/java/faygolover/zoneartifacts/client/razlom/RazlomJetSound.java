package faygolover.zoneartifacts.client.razlom;

import faygolover.zoneartifacts.anomaly.Razlom;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The roar of the Razlom's fire jet (crack_blow). The clip is longer than a jet, so once the jet
 * is over (or the Razlom is gone) it fades out over half a second instead of playing to the end.
 */
public class RazlomJetSound extends AbstractTickableSoundInstance {

    private static final float FADE_PER_TICK = Razlom.JET_VOLUME / 10.0f;

    private final BlockPos pos;
    private boolean fading;

    public RazlomJetSound(BlockPos pos, SoundEvent sound, Vec3 at) {
        super(sound, SoundSource.HOSTILE, RandomSource.create());
        this.pos = pos;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
        this.volume = Razlom.JET_VOLUME;
    }

    @Override
    public void tick() {
        if (!fading && !RazlomClientHandler.isJetting(pos)) fading = true;
        if (fading) {
            this.volume -= FADE_PER_TICK;
            if (this.volume <= 0.0f) {
                this.volume = 0.0f;
                stop();
            }
        }
    }

    public boolean isFading() {
        return fading;
    }
}
