package faygolover.zoneartifacts.client.pda;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.block.EzhikBlock;
import faygolover.zoneartifacts.block.PukhBlock;
import faygolover.zoneartifacts.block.PukhBlockEntity;
import faygolover.zoneartifacts.client.AnomalyClientTargeting;
import faygolover.zoneartifacts.client.tesla.TeslaClientCache;
import faygolover.zoneartifacts.client.web.WebClient;
import faygolover.zoneartifacts.item.PdaItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.PdaOpenPacket;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.pda.PdaTarget;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The KPK in hand: a right click on an anomaly asks the server for its settings (the window opens
 * when they arrive, {@link PdaScreen}). The target is whatever the player aims at first — a zone, a
 * point of a completed route, Burning Fluff or a Hedgehog, a thread of a Web — as long as no block
 * stands in front of it. The click never reaches vanilla handling.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PdaClientHandler {

    private static long lastOpen = -1_000_000L;

    private PdaClientHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().level().isClientSide() || !(event.getItemStack().getItem() instanceof PdaItem)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        use(event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().level().isClientSide() || !(event.getItemStack().getItem() instanceof PdaItem)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        use(event.getEntity());
    }

    private static void use(Player player) {
        long now = player.level().getGameTime();
        // Another world's clock may be far ahead of this one's (a new world starts at 0): only a
        // click a moment after the last one in this same world counts as a double click.
        if (now >= lastOpen && now - lastOpen < 5) return;
        lastOpen = now;
        PdaTarget target = pick(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.pda.nothing"), true);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new PdaOpenPacket(target));
    }

    /** Nearest anomaly along the view ray, unless a block is closer. */
    @Nullable
    public static PdaTarget pick(Player player) {
        Double blockDistSq = blockHitDistanceSq(player);
        PdaTarget best = null;
        double bestDistSq = Double.MAX_VALUE;

        Optional<SyncAnomaliesPacket.Entry> zone = AnomalyClientTargeting.pick(player, null);
        if (zone.isPresent()) {
            Optional<Double> d = AnomalyClientTargeting.hitDistanceSq(player, zone.get());
            if (d.isPresent()) {
                best = PdaTarget.zone(zone.get().typeId(), zone.get().pos());
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
            best = PdaTarget.route(point.get().ref().routeId(), point.get().ref().index(), point.get().ref().pos());
            bestDistSq = point.get().distanceSq();
        }

        if (player instanceof LocalPlayer local) {
            WebClient.Hit web = WebClient.pick(local, 12.0);
            if (web != null && web.distanceSq() < bestDistSq) {
                best = PdaTarget.web(web.webId());
                bestDistSq = web.distanceSq();
            }
        }

        // Block anomalies: the block itself, or Burning Fluff's hanging strands (no block: the ray
        // would pass straight through them).
        BlockPos block = null;
        double blockAnomalyDistSq = Double.MAX_VALUE;
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK && blockDistSq != null
                && (player.level().getBlockState(blockHit.getBlockPos()).getBlock() instanceof PukhBlock
                || player.level().getBlockState(blockHit.getBlockPos()).getBlock() instanceof EzhikBlock)) {
            block = blockHit.getBlockPos();
            blockAnomalyDistSq = blockDistSq;
        }
        for (PukhBlockEntity pukh : List.copyOf(PukhBlockEntity.CLIENT_LOADED)) {
            if (pukh.isRemoved() || pukh.getLevel() != player.level()) continue;
            BlockPos pos = pukh.getBlockPos();
            if (pos.distToCenterSqr(eye) > 24 * 24) continue;
            AABB box = Pukh.hangingBox(pos, pukh.facing(), pukh.effectiveLength()).minmax(new AABB(pos));
            Optional<Vec3> at = box.clip(eye, end);
            if (at.isEmpty()) continue;
            double d = at.get().distanceToSqr(eye);
            if (d < blockAnomalyDistSq) {
                block = pos;
                blockAnomalyDistSq = d;
            }
        }
        if (block != null && blockAnomalyDistSq <= bestDistSq && (blockDistSq == null || blockAnomalyDistSq <= blockDistSq + 1.0E-3)) {
            return PdaTarget.block(block);
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
