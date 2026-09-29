package faygolover.zoneartifacts.client.fx;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.dymka.DymkaClient;
import faygolover.zoneartifacts.client.poppy.PoppyClient;
import faygolover.zoneartifacts.client.sumrak.SumrakClient;
import faygolover.zoneartifacts.client.swamp.SwampClient;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Muffling of the world's sounds by the anomalies that dull the senses — without touching the sound
 * engine: every sound that starts (a plain one-shot, not a looping managed one) is wrapped so that
 * its volume and pitch follow, tick by tick, what the anomalies say right now:
 * <ul>
 *     <li>the Haze ({@link DymkaClient}): quieter and a little lower while one is in it;</li>
 *     <li>the Dusk ({@link SumrakClient}): from inside nearly nothing is heard; a sound from inside it
 *     barely reaches anyone outside;</li>
 *     <li>the swamp ({@link SwampClient}): under the mud, almost nothing;</li>
 *     <li>the poppy field ({@link PoppyClient}): with the eyes closed, only the hum.</li>
 * </ul>
 * Sounds already playing when one walks in aren't caught (only new ones), and neither are music,
 * records and the interface's clicks. The anomalies' own hums aren't touched either.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoundFx {

    private SoundFx() {
    }

    /** Our own ambience that must stay audible (the hums one hears instead of the world). */
    private static boolean own(ResourceLocation id) {
        if (!ZoneArtifacts.MODID.equals(id.getNamespace())) return false;
        String p = id.getPath();
        return p.equals("sumrak_drone") || p.equals("psi_hum") || p.startsWith("psi_whisper") || p.equals("poppy_hum")
                || p.startsWith("dymka_distant") || p.equals("khlopushka_ring");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof TickableSoundInstance || sound instanceof Muffled) return;
        SoundSource source = sound.getSource();
        if (source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.RECORDS) return;
        if (own(sound.getLocation())) return;
        if (!anyNearby()) return;
        event.setSound(new Muffled(sound));
    }

    private static boolean anyNearby() {
        return DymkaClient.present() || SumrakClient.present() || SwampClient.darkness(1.0f) > 0.0f || PoppyClient.hearing() < 1.0f
                || faygolover.zoneartifacts.client.khlopushka.KhlopushkaClient.hearing() < 1.0f;
    }

    static float volume(SoundInstance s) {
        float v = DymkaClient.hearing() * SwampClient.hearing() * PoppyClient.hearing()
                * faygolover.zoneartifacts.client.khlopushka.KhlopushkaClient.hearing();
        v *= SumrakClient.hearing(s.isRelative(), s.getX(), s.getY(), s.getZ());
        return v;
    }

    static float pitch() {
        return DymkaClient.pitch();
    }

    /** A sound whose loudness and pitch follow the muffling while it plays. */
    public static final class Muffled implements TickableSoundInstance {
        private final SoundInstance d;

        Muffled(SoundInstance delegate) {
            this.d = delegate;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public void tick() {
        }

        @Override
        public ResourceLocation getLocation() {
            return d.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return d.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return d.getSound();
        }

        @Override
        public SoundSource getSource() {
            return d.getSource();
        }

        @Override
        public boolean isLooping() {
            return d.isLooping();
        }

        @Override
        public boolean isRelative() {
            return d.isRelative();
        }

        @Override
        public int getDelay() {
            return d.getDelay();
        }

        @Override
        public float getVolume() {
            return d.getVolume() * volume(d);
        }

        @Override
        public float getPitch() {
            return d.getPitch() * pitch();
        }

        @Override
        public double getX() {
            return d.getX();
        }

        @Override
        public double getY() {
            return d.getY();
        }

        @Override
        public double getZ() {
            return d.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return d.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public boolean canPlaySound() {
            return d.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return d.getStream(buffers, sound, looping);
        }
    }
}
