package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A real, looping, entity-following sound instance per live Tesla (not on cooldown/invisible)
 * playing its idle sound — the moving-target counterpart of {@code AnomalyAmbientSoundHandler}'s
 * fixed-position loop for Electra. Diffed every client tick against which Tesla entities actually
 * exist right now, same stop/start discipline as the Electra version.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class TeslaIdleSoundHandler {

    private static final Map<Integer, TeslaIdleSound> PLAYING = new HashMap<>();

    private TeslaIdleSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ResourceLocation idleSoundId = ClientTeslaTypeCache.idleSound();
        SoundEvent sound = idleSoundId == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(idleSoundId);

        Set<Integer> live = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TeslaEntity tesla)) continue;
            if (tesla.getTeslaState() == TeslaEntity.STATE_RESPAWNING) continue;

            live.add(tesla.getId());
            if (sound != null && !PLAYING.containsKey(tesla.getId())) {
                TeslaIdleSound instance = new TeslaIdleSound(tesla, sound,
                        ClientTeslaTypeCache.idleVolume(), ClientTeslaTypeCache.idlePitch());
                PLAYING.put(tesla.getId(), instance);
                mc.getSoundManager().play(instance);
            }
        }

        PLAYING.keySet().removeIf(id -> {
            if (live.contains(id)) return false;
            TeslaIdleSound instance = PLAYING.get(id);
            if (instance != null) {
                mc.getSoundManager().stop(instance);
            }
            return true;
        });
    }

    private static final class TeslaIdleSound extends AbstractTickableSoundInstance {
        private final TeslaEntity entity;
        private boolean stopped = false;

        TeslaIdleSound(TeslaEntity entity, SoundEvent sound, float volume, float pitch) {
            super(sound, SoundSource.AMBIENT, RandomSource.create());
            this.entity = entity;
            this.volume = volume;
            this.pitch = pitch;
            this.looping = true;
            this.delay = 0;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        }

        @Override
        public void tick() {
            if (!entity.isAlive() || entity.getTeslaState() == TeslaEntity.STATE_RESPAWNING) {
                stopped = true;
                return;
            }
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }
    }
}
