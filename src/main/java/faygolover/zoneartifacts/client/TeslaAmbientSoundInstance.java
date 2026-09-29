package faygolover.zoneartifacts.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Tesla's idle hum as a real, individually-stoppable, position-tracking sound — unlike Electra's
 * ambient loop (an anomaly zone never moves, so {@code AnomalyAmbientSoundHandler} only ever needs
 * a static {@code SimpleSoundInstance}), a Tesla is constantly flying somewhere, so this instance
 * re-reads {@link #entity}'s position every client tick in {@link #tick()} rather than baking a
 * position in once at construction time.
 */
public class TeslaAmbientSoundInstance extends AbstractTickableSoundInstance {

    private final Entity entity;

    public TeslaAmbientSoundInstance(SoundEvent sound, Entity entity, float volume, float pitch) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.entity = entity;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    /**
     * {@code stopped} on the base class is private, not protected — there's no field to flip from
     * here — so instead of trying to set it, this replaces the check entirely: the sound counts as
     * stopped the moment {@link #entity} is dead or removed, no separate flag needed.
     */
    @Override
    public boolean isStopped() {
        return super.isStopped() || !entity.isAlive() || entity.isRemoved();
    }
}
