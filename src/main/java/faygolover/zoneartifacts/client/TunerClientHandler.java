package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.tesla.TeslaClientCache;
import faygolover.zoneartifacts.item.AnomalyTunerItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.network.TunerClickPacket;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import faygolover.zoneartifacts.tuner.TunerKind;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.block.PukhBlock;
import faygolover.zoneartifacts.block.PukhBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns tuner clicks into {@link TunerClickPacket}s: left-click lowers, right-click raises,
 * sneaking takes the larger step. The target is whatever the player aims at first — an anomaly
 * zone or a point of a completed Tesla route — as long as no block stands in front of it.
 * Tuner clicks never reach vanilla handling: nothing is mined, placed or used.
 * <p>
 * Holding a button repeats the step every {@link #REPEAT_TICKS} ticks, so a GM can hold the button
 * to sweep a value instead of clicking dozens of times.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TunerClientHandler {

    private static final int REPEAT_TICKS = 4;

    /** Far in the past, but not Long.MIN_VALUE: {@code now - Long.MIN_VALUE} overflows to a
     *  negative number, which made every click look like "too soon" and silently dropped it. */
    private static long lastActionTick = -1_000_000L;

    private TunerClientHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntity().level().isClientSide()) return;
        TunerKind kind = AnomalyTunerItem.kindOf(event.getEntity().getMainHandItem());
        if (kind == null) return;
        event.setCanceled(true);
        act(event.getEntity(), kind, false);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!event.getEntity().level().isClientSide()) return;
        TunerKind kind = AnomalyTunerItem.kindOf(event.getEntity().getMainHandItem());
        if (kind != null) act(event.getEntity(), kind, false);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().level().isClientSide()) return;
        TunerKind kind = AnomalyTunerItem.kindOf(event.getItemStack());
        if (kind == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        act(event.getEntity(), kind, true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().level().isClientSide()) return;
        TunerKind kind = AnomalyTunerItem.kindOf(event.getItemStack());
        if (kind == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        act(event.getEntity(), kind, true);
    }

    private static void act(Player player, TunerKind kind, boolean increase) {
        long now = player.level().getGameTime();
        if (now - lastActionTick < REPEAT_TICKS) return;

        Target target = pick(player);
        if (target == null) return;
        lastActionTick = now;

        boolean sneaking = player.isShiftKeyDown();
        TunerClickPacket packet = target.block() != null
                ? TunerClickPacket.block(kind, increase, sneaking, target.block())
                : target.anomaly() != null
                ? TunerClickPacket.anomaly(kind, increase, sneaking, target.anomaly().typeId(), target.anomaly().pos())
                : TunerClickPacket.route(kind, increase, sneaking, target.waypoint().routeId(), target.waypoint().index(), target.waypoint().pos());
        ModNetwork.CHANNEL.sendToServer(packet);
    }

    /** What a tuner click would hit: exactly one of the fields is set (a zone, a route point, or
     *  a block anomaly such as Burning Fluff). */
    public record Target(@Nullable SyncAnomaliesPacket.Entry anomaly, @Nullable TeslaGeometry.WaypointRef waypoint,
                         @Nullable BlockPos block) {
        public Target(@Nullable SyncAnomaliesPacket.Entry anomaly, @Nullable TeslaGeometry.WaypointRef waypoint) {
            this(anomaly, waypoint, null);
        }
    }

    /** Nearest tunable thing along the view ray, unless a block is closer. */
    @Nullable
    public static Target pick(Player player) {
        Double blockDistSq = blockHitDistanceSq(player);
        Target best = null;
        double bestDistSq = Double.MAX_VALUE;

        Optional<SyncAnomaliesPacket.Entry> zone = AnomalyClientTargeting.pick(player, null);
        if (zone.isPresent()) {
            Optional<Double> d = AnomalyClientTargeting.hitDistanceSq(player, zone.get());
            if (d.isPresent()) {
                best = new Target(zone.get(), null);
                bestDistSq = d.get();
            }
        }

        List<TeslaGeometry.WaypointRef> completed = new ArrayList<>();
        for (TeslaGeometry.WaypointRef ref : TeslaClientCache.waypointsFor(player.level().dimension())) {
            if (ref.routeId() > 0) completed.add(ref);
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(TeslaGeometry.CLICK_REACH));
        Optional<TeslaGeometry.WaypointHit> point = TeslaGeometry.pick(completed, eye, end);
        if (point.isPresent() && point.get().distanceSq() < bestDistSq) {
            best = new Target(null, point.get().ref());
            bestDistSq = point.get().distanceSq();
        }

        // A thread of a Web (sent as a zone click of the Web type, the web's id in x).
        if (player instanceof net.minecraft.client.player.LocalPlayer local) {
            faygolover.zoneartifacts.client.web.WebClient.Hit web = faygolover.zoneartifacts.client.web.WebClient.pick(local, 12.0);
            if (web != null && web.distanceSq() < bestDistSq) {
                best = new Target(new SyncAnomaliesPacket.Entry(faygolover.zoneartifacts.anomaly.AnomalyTypeIds.WEB,
                        new BlockPos(web.webId(), 0, 0), 1.0f, 1, false, false, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f), null);
                bestDistSq = web.distanceSq();
            }
        }

        // Burning Fluff: its base or its hanging strands (which are no block, so the ray would go
        // straight through them to whatever is behind).
        BlockPos fluff = null;
        double fluffDistSq = Double.MAX_VALUE;
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && (player.level().getBlockState(blockHit.getBlockPos()).getBlock() instanceof PukhBlock
                || player.level().getBlockState(blockHit.getBlockPos()).getBlock() instanceof faygolover.zoneartifacts.block.EzhikBlock)
                && blockDistSq != null) {
            fluff = blockHit.getBlockPos();
            fluffDistSq = blockDistSq;
        }
        for (PukhBlockEntity pukh : List.copyOf(PukhBlockEntity.CLIENT_LOADED)) {
            if (pukh.isRemoved() || pukh.getLevel() != player.level()) continue;
            BlockPos pos = pukh.getBlockPos();
            if (pos.distToCenterSqr(eye) > 24 * 24) continue;
            AABB box = Pukh.hangingBox(pos, pukh.facing(), pukh.effectiveLength()).minmax(new AABB(pos));
            Optional<Vec3> at = box.clip(eye, end);
            if (at.isEmpty()) continue;
            double d = at.get().distanceToSqr(eye);
            if (d < fluffDistSq) {
                fluff = pos;
                fluffDistSq = d;
            }
        }
        if (fluff != null && fluffDistSq <= bestDistSq && (blockDistSq == null || fluffDistSq <= blockDistSq + 1.0E-3)) {
            return new Target(null, null, fluff);
        }
        if (best == null || (blockDistSq != null && bestDistSq > blockDistSq)) return null;
        return best;
    }

    @Nullable
    private static Double blockHitDistanceSq(Player player) {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            return player.getEyePosition().distanceToSqr(blockHit.getLocation());
        }
        return null;
    }
}
