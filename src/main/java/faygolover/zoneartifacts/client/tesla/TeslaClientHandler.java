package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.TeslaWaypointClickPacket;
import faygolover.zoneartifacts.tesla.Comet;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.Tesla;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side Tesla behaviour: starting each visible Tesla's idle loop, and turning left-clicks on
 * waypoints into {@link TeslaWaypointClickPacket}s.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeslaClientHandler {

    private static final Map<Integer, TeslaIdleSound> IDLE_SOUNDS = new HashMap<>();

    /** Game time of the last left-click event we saw. Holding the button re-fires the event every
     *  tick; only a click that follows a gap of 2+ ticks counts as a new press, so holding LMB can
     *  never chew through a route point after point. */
    private static long lastLeftClickTick = -1_000_000L; // not Long.MIN_VALUE: now - MIN_VALUE overflows

    private TeslaClientHandler() {
    }

    // ---- idle hum ----------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            IDLE_SOUNDS.clear();
            return;
        }

        // Drop finished loops (Tesla popped/removed) so a respawned Tesla gets a fresh one. The
        // sound normally stops itself; the explicit check covers a loop the engine never started.
        for (Iterator<Map.Entry<Integer, TeslaIdleSound>> it = IDLE_SOUNDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, TeslaIdleSound> entry = it.next();
            Entity owner = mc.level.getEntity(entry.getKey());
            boolean ownerGone = !(owner instanceof TeslaEntity tesla) || tesla.isRemoved() || !tesla.getState().isVisible();
            if (entry.getValue().isStopped() || ownerGone) {
                mc.getSoundManager().stop(entry.getValue());
                it.remove();
            }
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TeslaEntity tesla) || !tesla.getState().isVisible()) continue;
            if (IDLE_SOUNDS.containsKey(tesla.getId())) continue;
            boolean comet = tesla instanceof CometEntity;
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(comet ? Comet.IDLE_SOUND : Tesla.IDLE_SOUND);
            if (sound == null) continue;
            TeslaIdleSound idle = comet
                    ? new TeslaIdleSound(tesla, sound, Comet.IDLE_VOLUME, Comet.IDLE_PITCH)
                    : new TeslaIdleSound(tesla, sound, Tesla.IDLE_VOLUME, Tesla.IDLE_PITCH);
            IDLE_SOUNDS.put(tesla.getId(), idle);
            mc.getSoundManager().play(idle);
        }
    }

    // ---- left-click on waypoints ---------------------------------------------------

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().getMainHandItem().getItem() instanceof TeslaRoutePlacerItem)) return;
        handleLeftClick(event.getEntity());
        // The placer never mines: cancel even when no waypoint was hit.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().getMainHandItem().getItem() instanceof TeslaRoutePlacerItem)) return;
        handleLeftClick(event.getEntity());
    }

    private static void handleLeftClick(Player player) {
        long now = player.level().getGameTime();
        boolean held = now - lastLeftClickTick <= 1;
        lastLeftClickTick = now;
        if (held) return;

        Optional<TeslaGeometry.WaypointHit> hit = pick(player);
        if (hit.isEmpty() || !TeslaGeometry.beatsBlock(hit.get(), blockHitDistanceSq(player))) return;
        TeslaGeometry.WaypointRef ref = hit.get().ref();
        ModNetwork.CHANNEL.sendToServer(new TeslaWaypointClickPacket(ref.routeId(), ref.index(), ref.pos()));
    }

    /** Nearest waypoint along the player's view ray, from the synced route cache. */
    public static Optional<TeslaGeometry.WaypointHit> pick(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(TeslaGeometry.CLICK_REACH));
        return TeslaGeometry.pick(TeslaClientCache.waypointsFor(player.level().dimension(),
                TeslaRoutePlacerItem.heldKind(player)), eye, end);
    }

    @Nullable
    public static Double blockHitDistanceSq(Player player) {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            return player.getEyePosition().distanceToSqr(blockHit.getLocation());
        }
        return null;
    }
}
