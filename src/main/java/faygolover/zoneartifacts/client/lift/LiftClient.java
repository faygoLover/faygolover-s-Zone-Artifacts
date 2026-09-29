package faygolover.zoneartifacts.client.lift;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Lift;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side of the Lift: your own player floating in it ({@link Lift}; jump to rise, sneak to
 * sink, let go to drift back to the hover height), and its barely-there look — a few specks of
 * dust drifting up from the ground and the faintest shimmer ({@link Distortion}).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LiftClient {

    private static final double VISIBLE_RADIUS = 48.0;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Your own player in one Lift: time inside and how far the hover point was moved. */
    private static final class Mine {
        int ticks;
        double offset;
    }

    private static final Map<BlockPos, Mine> MINE = new HashMap<>();

    private LiftClient() {
    }

    private static double hoverHeight() {
        try {
            return ModCommonConfig.LIFT_HOVER_HEIGHT.get();
        } catch (IllegalStateException notLoaded) {
            return 1.5;
        }
    }

    private static int pushOutTicks() {
        try {
            return (int) Math.round(ModCommonConfig.LIFT_PUSH_OUT_SECONDS.get() * 20.0);
        } catch (IllegalStateException notLoaded) {
            return 200;
        }
    }

    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;
        boolean free = !player.isCreative() && !player.isSpectator() && !player.isPassenger() && !player.getAbilities().flying;
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.LIFT.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            if (!free || !player.getBoundingBox().intersects(zone)) {
                MINE.remove(entry.pos());
                continue;
            }
            Mine mine = MINE.computeIfAbsent(entry.pos(), p -> new Mine());
            mine.ticks++;
            double height = hoverHeight() * entry.speed();
            if (player.input.jumping) {
                mine.offset += Lift.CONTROL_SPEED;
            } else if (player.input.shiftKeyDown) {
                mine.offset -= Lift.CONTROL_SPEED;
            } else {
                mine.offset *= 0.95;
                if (Math.abs(mine.offset) < 0.01) mine.offset = 0.0;
            }
            mine.offset = Mth.clamp(mine.offset, -height, Math.max(0.0, zone.maxY - zone.minY));
            player.resetFallDistance();
            double target = Lift.targetFeetY(level, player, zone, height, mine.offset);
            player.setDeltaMovement(Lift.apply(player, zone, target, mine.ticks, pushOutTicks()));
        }
    }

    @SubscribeEvent
    public static void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            if (level == null) MINE.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.LIFT.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(cam) > VISIBLE_RADIUS * VISIBLE_RADIUS) continue;
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            // Now and then a speck of dust drifting up from the ground.
            double rate = 0.015 * ModClientConfig.effective(entry.intensity()) * Math.max(1.0, entry.size() * entry.size());
            if (RANDOM.nextDouble() < rate) {
                double x = Mth.lerp(RANDOM.nextDouble(), zone.minX, zone.maxX);
                double z = Mth.lerp(RANDOM.nextDouble(), zone.minZ, zone.maxZ);
                level.addParticle(ModParticles.GRAV_DUST.get(), x, zone.minY + 0.05, z,
                        (RANDOM.nextDouble() - 0.5) * 0.004, 0.012 + RANDOM.nextDouble() * 0.01, (RANDOM.nextDouble() - 0.5) * 0.004);
            }
        }
    }

    /** The faintest shimmer through the zone. */
    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = (now % 72000L) + partial;
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(mc.level.dimension())) {
            if (!AnomalyTypeIds.LIFT.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            Vec3 base = new Vec3((zone.minX + zone.maxX) * 0.5, zone.minY, (zone.minZ + zone.maxZ) * 0.5);
            out.add(new Distortion.Haze(base, Math.max(zone.getXsize(), zone.getZsize()), zone.getYsize(), 0.012,
                    time * 0.01, 1.0, false, 1.0f, entry.pos().hashCode() * 0.01));
        }
    }
}
