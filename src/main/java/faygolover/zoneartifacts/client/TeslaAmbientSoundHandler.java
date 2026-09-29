package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.entity.TeslaEntity;
import faygolover.zoneartifacts.network.SyncTeslaTypesPacket.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
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
 * Runs every loaded Tesla's idle hum as a real, individually-stoppable, position-tracking
 * {@link TeslaAmbientSoundInstance} — the moving-entity counterpart to {@code
 * AnomalyAmbientSoundHandler}'s static per-zone loop. Every client tick this reconciles "which
 * Teslas are currently loaded and have an idle sound configured" (from {@link
 * ClientTeslaTypeCache}) against what's actually playing, starting and stopping instances as
 * needed — a Tesla popping (hit landed) or simply leaving render distance both fall out of the
 * same "no longer desired" check.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeslaAmbientSoundHandler {

    private static final Map<Integer, TeslaAmbientSoundInstance> ACTIVE = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopAll(mc);
            return;
        }

        Set<Integer> desired = new HashSet<>();
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TeslaEntity tesla)) continue;

            Info info = ClientTeslaTypeCache.get(tesla.typeId());
            if (info == null || info.idleSound() == null) continue;

            desired.add(tesla.getId());
            if (!ACTIVE.containsKey(tesla.getId())) {
                start(mc, tesla, info);
            }
        }

        Iterator<Map.Entry<Integer, TeslaAmbientSoundInstance>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TeslaAmbientSoundInstance> active = it.next();
            if (!desired.contains(active.getKey())) {
                mc.getSoundManager().stop(active.getValue());
                it.remove();
            }
        }
    }

    private static void start(Minecraft mc, TeslaEntity tesla, Info info) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(info.idleSound());
        if (sound == null) return;

        TeslaAmbientSoundInstance instance = new TeslaAmbientSoundInstance(sound, tesla, info.idleVolume(), info.idlePitch());
        mc.getSoundManager().play(instance);
        ACTIVE.put(tesla.getId(), instance);
    }

    private static void stopAll(Minecraft mc) {
        if (ACTIVE.isEmpty()) return;
        for (TeslaAmbientSoundInstance instance : ACTIVE.values()) {
            mc.getSoundManager().stop(instance);
        }
        ACTIVE.clear();
    }

    private TeslaAmbientSoundHandler() {
    }
}
