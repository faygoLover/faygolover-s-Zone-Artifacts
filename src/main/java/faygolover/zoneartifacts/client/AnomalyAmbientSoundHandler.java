package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket.AmbientSoundInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Runs each anomaly's idle hum entirely client-side as a real, individually-stoppable
 * {@link SimpleSoundInstance} — replacing the earlier approach of the server periodically calling
 * {@code ServerLevel.playSound}, which turned out to be un-fixable for a loop: vanilla's "play
 * this sound" packet has no matching "stop" counterpart, so a long ambient clip just kept playing
 * to the end even after the anomaly triggered or was removed.
 * <p>
 * Every client tick this reconciles "what should be looping right now" — every synced anomaly
 * that has an ambient sound configured (via {@link ClientAnomalyTypeCache#ambientSoundFor}) and
 * isn't currently on cooldown ({@link SyncAnomaliesPacket.Entry#onCooldown()}) — against what's
 * actually playing, starting and stopping instances as needed. Both the anomaly's existence and
 * its cooldown state come from {@link ClientAnomalyCache}, kept current by
 * {@code AnomalySyncHandler} broadcasting on every placement, removal, level change, and now also
 * on every cooldown start/end (see {@code AnomalyEngine.tickBurst}).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnomalyAmbientSoundHandler {

    private static final Map<Key, SimpleSoundInstance> ACTIVE = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopAll(mc);
            return;
        }

        Set<Key> desired = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(mc.level.dimension())) {
            if (entry.onCooldown()) continue;

            AmbientSoundInfo ambient = ClientAnomalyTypeCache.ambientSoundFor(entry.typeId());
            if (ambient == null) continue;

            Key key = new Key(entry.typeId(), entry.pos());
            desired.add(key);
            if (!ACTIVE.containsKey(key)) {
                start(mc, key, entry, ambient);
            }
        }

        Iterator<Map.Entry<Key, SimpleSoundInstance>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, SimpleSoundInstance> active = it.next();
            if (!desired.contains(active.getKey())) {
                mc.getSoundManager().stop(active.getValue());
                it.remove();
            }
        }
    }

    private static void start(Minecraft mc, Key key, SyncAnomaliesPacket.Entry entry, AmbientSoundInfo ambient) {
        // Only a validity check (skip playing a sound id nothing registered) — the instance itself
        // is built from the ResourceLocation, not the SoundEvent: in 1.20.1
        // SimpleSoundInstance's looping constructor takes a ResourceLocation, not a SoundEvent
        // (the SoundEvent-typed overloads with this many arguments don't exist on this version).
        if (ForgeRegistries.SOUND_EVENTS.getValue(ambient.soundId()) == null) return;

        int size = ClientAnomalyTypeCache.sizeForLevel(entry.typeId(), entry.level());
        AABB aabb = AnomalyGeometry.centeredAabb(entry.pos(), size);
        Vec3 center = aabb.getCenter();

        SimpleSoundInstance instance = new SimpleSoundInstance(ambient.soundId(), SoundSource.AMBIENT,
                ambient.volume(), ambient.pitch(), RandomSource.create(), true, 0,
                SoundInstance.Attenuation.LINEAR, center.x, center.y, center.z, false);
        mc.getSoundManager().play(instance);
        ACTIVE.put(key, instance);
    }

    private static void stopAll(Minecraft mc) {
        if (ACTIVE.isEmpty()) return;
        for (SimpleSoundInstance instance : ACTIVE.values()) {
            mc.getSoundManager().stop(instance);
        }
        ACTIVE.clear();
    }

    private record Key(ResourceLocation typeId, BlockPos pos) {
    }
}
