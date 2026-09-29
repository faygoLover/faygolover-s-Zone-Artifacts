package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
 * Drives a real, individually-stoppable looping {@link SimpleSoundInstance} per anomaly whose
 * ambient idle sound should currently be audible (has an {@code ambient} sound configured and
 * isn't on cooldown), diffed against the desired state every client tick — rather than naively
 * re-triggering one-shot {@code playSound} calls, which can't be stopped mid-playback and would
 * leave old instances of a 40+ second idle loop audible long after the anomaly went on cooldown.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class AnomalyAmbientSoundHandler {

    private static final Map<ClientAnomalyCache.Key, SimpleSoundInstance> PLAYING = new HashMap<>();

    private AnomalyAmbientSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Set<ClientAnomalyCache.Key> desired = new HashSet<>();

        for (ClientAnomalyCache.Entry entry : ClientAnomalyCache.all()) {
            if (ClientAnomalyCache.isOnCooldown(entry.typeId(), entry.pos())) continue;

            SyncAnomalyTypeShapesPacket.TypeShape shape = ClientAnomalyTypeCache.get(entry.typeId());
            if (shape == null || shape.ambientSound() == null) continue;

            ClientAnomalyCache.Key key = entry.key();
            desired.add(key);
            if (!PLAYING.containsKey(key)) {
                start(key, entry.pos(), shape);
            }
        }

        PLAYING.keySet().removeIf(key -> {
            if (desired.contains(key)) return false;
            stop(key);
            return true;
        });
    }

    private static void start(ClientAnomalyCache.Key key, BlockPos pos, SyncAnomalyTypeShapesPacket.TypeShape shape) {
        ResourceLocation soundId = shape.ambientSound();
        if (ForgeRegistries.SOUND_EVENTS.getValue(soundId) == null) return; // not registered — skip silently

        SimpleSoundInstance instance = new SimpleSoundInstance(
                soundId, SoundSource.AMBIENT,
                shape.ambientVolume(), shape.ambientPitch(),
                RandomSource.create(),
                true, 0,
                SoundInstance.Attenuation.LINEAR,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                false);

        PLAYING.put(key, instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    private static void stop(ClientAnomalyCache.Key key) {
        SimpleSoundInstance instance = PLAYING.get(key);
        if (instance != null) {
            Minecraft.getInstance().getSoundManager().stop(instance);
        }
    }
}
