package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Tesla's hum: a looping sound that follows the Tesla every tick (unlike Electra's idle loop,
 * which stands still), and stops itself the moment the Tesla pops or disappears.
 */
public class TeslaIdleSound extends AbstractTickableSoundInstance {

    private final TeslaEntity tesla;

    public TeslaIdleSound(TeslaEntity tesla, SoundEvent sound, float volume, float pitch) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.tesla = tesla;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        moveToTesla();
    }

    @Override
    public void tick() {
        if (tesla.isRemoved() || !tesla.getState().isVisible()) {
            stop();
            return;
        }
        moveToTesla();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    private void moveToTesla() {
        Vec3 c = tesla.center();
        this.x = c.x;
        this.y = c.y;
        this.z = c.z;
    }
}
