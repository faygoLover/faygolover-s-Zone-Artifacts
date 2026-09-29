package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket.ArcInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Electra's lightning-arc visuals, entirely client-side (purely cosmetic — the server doesn't
 * need to agree on exactly where every arc bends or strikes). Two things happen here:
 * <ul>
 *     <li><b>Ambient bundles</b>: several jagged arcs, each chaining through a handful of anchor
 *     points sitting exactly on the collision faces of blocks inside the zone (a non-solid cell
 *     touching a solid neighbor — see {@link #findSurfacePoints}), so the arcs read as connecting
 *     real surfaces rather than floating in mid-air. Each "bundle" keeps the same anchors for
 *     roughly a second, then a fresh set is rolled — and every bundle's clock runs independently,
 *     so they never all flip at once. A link between two anchors occasionally gets a bulge point
 *     lifted slightly above the straight line between them, just enough to keep the arcs from all
 *     reading as perfectly flat. Gated on {@link SyncAnomaliesPacket.Entry#onCooldown()}, exactly
 *     like the idle sound in {@code AnomalyAmbientSoundHandler}.</li>
 *     <li><b>Strikes</b>: bolts from the zone's own surface points converging onto whatever the
 *     anomaly just hit — a player, a mob, or the thrown item that tripped it — fired by {@link
 *     #onStrike} in response to a server-sent {@code AnomalyStrikePacket}; this is what plays
 *     instead of the old particle burst. They don't snap onto the target instantly: there's a
 *     short windup where the bolts visibly reach out and close the distance before connecting,
 *     so a hit reads as a beat rather than a flash right at the target's edge.</li>
 * </ul>
 * Both are rendered with vanilla's own {@link RenderType#lightning()} — the same public,
 * additive-blended, unlit RenderType the real lightning bolt entity uses — so unlike the
 * semi-transparent highlight box (which needed manual Tesselator/RenderSystem state because no
 * matching public RenderType exists for it) this needs no low-level state hacking at all.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnomalyArcRenderer {

    /** How far around the player arcs get simulated/drawn at all. */
    private static final double VISIBLE_RADIUS = 32.0;

    /** Shrinks the point-sampling volume in from the zone's true bounds, used only as a fallback
     *  when a zone touches no solid surface at all (so anchors don't sit exactly on/through a
     *  wall the zone happens to touch). */
    private static final double VOLUME_INSET = 0.15;

    /** Segments per anchor-to-anchor link (or per half-link when a bulge splits it); higher =
     *  more jagged. */
    private static final int SEGMENTS_PER_LINK = 5;

    /** How far a segment's midpoints wander off the straight line, as a fraction of the link's
     *  own length. */
    private static final double JITTER_FRACTION = 0.14;

    /** How often (in ticks) the jagged path between two anchors re-rolls. The anchors themselves
     *  (which points are connected, and whether a link has a bulge) only change when a bundle's
     *  lifetime expires — this is just the faster "crackle" of the line connecting them. */
    private static final int JITTER_REFRESH_TICKS = 2;

    private static final float HALF_WIDTH = 0.02f;
    private static final float STRIKE_HALF_WIDTH = 0.03f;

    /** Chance a given link gets an extra point bowed slightly upward instead of running dead
     *  straight — "not always", just enough of the time to read as having some volume. */
    private static final double BULGE_CHANCE = 0.4;
    private static final double BULGE_MIN_LIFT = 0.12;
    private static final double BULGE_LIFT_FRACTION = 0.18;

    /** A short charge-up before a strike actually connects: the bolts visibly reach out from the
     *  zone's points toward the target instead of just snapping onto it the instant the anomaly
     *  fires, so a hit reads as a beat more dramatic than a flash right at the target's edge. */
    private static final int STRIKE_WINDUP_TICKS = 6;

    /** How far the charging bolts reach toward the target during the windup, as a fraction of the
     *  full distance — they close the rest of the gap the moment the windup ends. */
    private static final double STRIKE_WINDUP_MAX_REACH = 0.55;

    /** How long the fully-connected strike itself stays on screen after the windup ends. */
    private static final int STRIKE_STRIKE_TICKS = 8;

    private static final Map<Key, List<Bundle>> BUNDLES = new HashMap<>();
    private static final Map<Key, List<Strike>> STRIKES = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            BUNDLES.clear();
            STRIKES.clear();
            return;
        }

        long now = mc.level.getGameTime();
        Vec3 playerPos = mc.player.position();
        double radiusSq = VISIBLE_RADIUS * VISIBLE_RADIUS;

        Set<Key> desired = new HashSet<>();
        List<Bundle> toRender = new ArrayList<>();
        List<Integer> colorsToRender = new ArrayList<>();

        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(mc.level.dimension())) {
            if (entry.onCooldown()) continue;

            ArcInfo arc = ClientAnomalyTypeCache.arcFor(entry.typeId());
            if (arc == null) continue;

            double dx = entry.pos().getX() + 0.5 - playerPos.x;
            double dy = entry.pos().getY() + 0.5 - playerPos.y;
            double dz = entry.pos().getZ() + 0.5 - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            Key key = new Key(entry.typeId(), entry.pos());
            desired.add(key);

            int size = ClientAnomalyTypeCache.sizeForLevel(entry.typeId(), entry.level());
            AABB trueAabb = AnomalyGeometry.centeredAabb(entry.pos(), size);
            AABB sampleAabb = trueAabb.deflate(VOLUME_INSET);

            List<Bundle> bundles = BUNDLES.computeIfAbsent(key, k -> new ArrayList<>());
            // Bring the bundle count up to spec; stagger a freshly-created bundle's first expiry
            // instead of starting it at "now" so a batch of brand-new anomalies doesn't have every
            // bundle change in lockstep the first time either.
            RandomSource seedSource = RandomSource.create();
            while (bundles.size() < arc.bundleCount()) {
                Bundle b = new Bundle();
                b.reroll(mc.level, trueAabb, sampleAabb, arc, seedSource);
                b.expiresAtTick = now - seedSource.nextInt(Math.max(1, arc.maxLifetimeTicks()));
                bundles.add(b);
            }
            while (bundles.size() > arc.bundleCount()) {
                bundles.remove(bundles.size() - 1);
            }

            for (Bundle b : bundles) {
                if (now >= b.expiresAtTick) {
                    b.reroll(mc.level, trueAabb, sampleAabb, arc, seedSource);
                    int lifetime = arc.minLifetimeTicks()
                            + (arc.maxLifetimeTicks() > arc.minLifetimeTicks()
                                    ? seedSource.nextInt(arc.maxLifetimeTicks() - arc.minLifetimeTicks() + 1) : 0);
                    b.expiresAtTick = now + lifetime;
                }
                toRender.add(b);
                colorsToRender.add(arc.color());
            }
        }

        BUNDLES.keySet().removeIf(k -> !desired.contains(k));
        if (toRender.isEmpty() && STRIKES.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (int i = 0; i < toRender.size(); i++) {
            renderBundle(matrix, buffer, toRender.get(i), colorsToRender.get(i), now, camPos);
        }
        renderStrikes(matrix, buffer, now, camPos, mc.level);

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    /**
     * Called from {@code AnomalyStrikePacket.handle} the instant a burst anomaly fires: rolls a
     * fresh {@link Strike} from the zone's own surface points onto {@code targetEntityId} — the
     * player, mob, or thrown projectile that actually tripped it. Falls back to the zone's own
     * center if no valid id was sent, or if the entity is already gone by the time this runs (a
     * thrown item hitting a wall in the same tick it trips the anomaly, say) — see {@link
     * #resolveStrikeTarget}.
     */
    public static void onStrike(ResourceLocation typeId, BlockPos pos, int level, int targetEntityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ArcInfo arc = ClientAnomalyTypeCache.arcFor(typeId);
        if (arc == null) return;

        int size = ClientAnomalyTypeCache.sizeForLevel(typeId, level);
        AABB trueAabb = AnomalyGeometry.centeredAabb(pos, size);
        List<Vec3> surface = findSurfacePoints(mc.level, trueAabb);

        RandomSource rand = RandomSource.create();
        int originCount = Math.max(3, arc.pointsPerBundle());
        List<Vec3> origins = surface.isEmpty()
                ? List.of(trueAabb.getCenter())
                : pickDistinct(surface, Math.min(originCount, surface.size()), rand);

        long now = mc.level.getGameTime();
        Strike strike = new Strike();
        strike.origins = origins;
        strike.targetEntityId = targetEntityId;
        strike.fallbackTarget = trueAabb.getCenter();
        strike.color = arc.color();
        strike.seed = rand.nextLong();
        strike.windupStartTick = now;
        strike.windupUntilTick = now + STRIKE_WINDUP_TICKS;
        strike.expiresAtTick = strike.windupUntilTick + STRIKE_STRIKE_TICKS;

        STRIKES.computeIfAbsent(new Key(typeId, pos), k -> new ArrayList<>()).add(strike);
    }

    private static void renderStrikes(Matrix4f matrix, VertexConsumer buffer, long now, Vec3 camPos, Level level) {
        for (Iterator<Map.Entry<Key, List<Strike>>> it = STRIKES.entrySet().iterator(); it.hasNext(); ) {
            List<Strike> strikes = it.next().getValue();
            strikes.removeIf(s -> now >= s.expiresAtTick);
            if (strikes.isEmpty()) {
                it.remove();
                continue;
            }
            for (Strike s : strikes) {
                Vec3 target = resolveStrikeTarget(s, level);

                long timeBucket = now / JITTER_REFRESH_TICKS;
                int r = (s.color >> 16) & 0xFF;
                int g = (s.color >> 8) & 0xFF;
                int b = s.color & 0xFF;

                // During the windup the bolts only reach partway toward the target (growing closer
                // every tick) and crackle harder; once the windup ends they snap fully onto it for
                // the rest of the strike's life.
                boolean windingUp = now < s.windupUntilTick;
                double reachFraction = 1.0;
                double jitterFraction = JITTER_FRACTION;
                if (windingUp) {
                    double windupSpan = Math.max(1, s.windupUntilTick - s.windupStartTick);
                    double t = Mth.clamp((now - s.windupStartTick) / windupSpan, 0.0, 1.0);
                    reachFraction = t * STRIKE_WINDUP_MAX_REACH;
                    jitterFraction = JITTER_FRACTION * 1.6;
                }

                for (int i = 0; i < s.origins.size(); i++) {
                    Vec3 origin = s.origins.get(i);
                    Vec3 endPoint = windingUp ? origin.add(target.subtract(origin).scale(reachFraction)) : target;

                    long segSeed = s.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (timeBucket * 0xBF58476D1CE4E5B9L);
                    RandomSource rand = RandomSource.create(segSeed);
                    Vec3[] points = buildJitteredPoints(origin, endPoint, rand, SEGMENTS_PER_LINK, jitterFraction);
                    renderPolylineQuads(matrix, buffer, points, STRIKE_HALF_WIDTH, r, g, b, camPos);
                }
            }
        }
    }

    /** The point a strike's bolts are currently aimed at: the target entity's live position when
     *  it can still be found, otherwise the zone's own center (also used outright for the
     *  projectile-trip fallback case where no entity id was ever given). */
    private static Vec3 resolveStrikeTarget(Strike s, Level level) {
        if (s.targetEntityId >= 0) {
            Entity targetEntity = level.getEntity(s.targetEntityId);
            if (targetEntity != null) {
                return targetEntity.getBoundingBox().getCenter();
            }
        }
        return s.fallbackTarget;
    }

    private static void renderBundle(Matrix4f matrix, VertexConsumer buffer, Bundle bundle, int color, long now, Vec3 camPos) {
        List<Vec3> anchors = bundle.anchors;
        if (anchors.size() < 2) return;

        long timeBucket = now / JITTER_REFRESH_TICKS;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int i = 0; i < anchors.size() - 1; i++) {
            long segSeed = bundle.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (timeBucket * 0xBF58476D1CE4E5B9L);
            Vec3 bulge = i < bundle.bulges.size() ? bundle.bulges.get(i) : null;

            if (bulge == null) {
                RandomSource rand = RandomSource.create(segSeed);
                Vec3[] points = buildJitteredPoints(anchors.get(i), anchors.get(i + 1), rand, SEGMENTS_PER_LINK, JITTER_FRACTION);
                renderPolylineQuads(matrix, buffer, points, HALF_WIDTH, r, g, b, camPos);
            } else {
                // Split the link into two tapered halves through the bulge point instead of one
                // straight run, so the arc bows slightly rather than just cutting a corner.
                int halfSegments = Math.max(2, SEGMENTS_PER_LINK / 2);
                RandomSource rand1 = RandomSource.create(segSeed);
                Vec3[] first = buildJitteredPoints(anchors.get(i), bulge, rand1, halfSegments, JITTER_FRACTION);
                RandomSource rand2 = RandomSource.create(segSeed ^ 0xA24BAED4963EE407L);
                Vec3[] second = buildJitteredPoints(bulge, anchors.get(i + 1), rand2, halfSegments, JITTER_FRACTION);
                renderPolylineQuads(matrix, buffer, first, HALF_WIDTH, r, g, b, camPos);
                renderPolylineQuads(matrix, buffer, second, HALF_WIDTH, r, g, b, camPos);
            }
        }
    }

    /** A polyline between two anchors, jittered perpendicular to the straight line between them.
     *  The jitter amplitude tapers to zero at both ends (via a sine envelope) so the path always
     *  lands exactly on its anchors no matter how jagged the middle gets. */
    private static Vec3[] buildJitteredPoints(Vec3 start, Vec3 end, RandomSource rand, int segments, double jitterFraction) {
        Vec3 dir = end.subtract(start);
        double len = dir.length();
        Vec3[] points = new Vec3[segments + 1];
        if (len < 1.0E-4) {
            for (int i = 0; i <= segments; i++) points[i] = start;
            return points;
        }

        Vec3 dirNorm = dir.scale(1.0 / len);
        Vec3 arbitrary = Math.abs(dirNorm.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = dirNorm.cross(arbitrary).normalize();
        Vec3 perp2 = dirNorm.cross(perp1).normalize();

        for (int j = 0; j <= segments; j++) {
            double t = j / (double) segments;
            Vec3 base = start.add(dir.scale(t));
            if (j == 0 || j == segments) {
                points[j] = base;
            } else {
                double jitterScale = Math.sin(Math.PI * t) * len * jitterFraction;
                double j2 = (rand.nextDouble() - 0.5) * 2.0 * jitterScale;
                double j3 = (rand.nextDouble() - 0.5) * 2.0 * jitterScale;
                points[j] = base.add(perp1.scale(j2)).add(perp2.scale(j3));
            }
        }
        return points;
    }

    /** Draws each segment as a quad billboarded to face the camera around the segment's own axis
     *  (not a flat camera-plane billboard), so the ribbon reads correctly from any angle. */
    private static void renderPolylineQuads(Matrix4f matrix, VertexConsumer buffer, Vec3[] points, float halfWidth,
                                             int r, int g, int b, Vec3 camPos) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 pa = points[i];
            Vec3 pb = points[i + 1];
            Vec3 mid = pa.add(pb).scale(0.5);

            Vec3 toCam = camPos.subtract(mid);
            double toCamLen = toCam.length();
            if (toCamLen < 1.0E-4) continue;
            toCam = toCam.scale(1.0 / toCamLen);

            Vec3 segDir = pb.subtract(pa);
            double segLen = segDir.length();
            if (segLen < 1.0E-4) continue;
            segDir = segDir.scale(1.0 / segLen);

            Vec3 side = segDir.cross(toCam);
            double sideLen = side.length();
            if (sideLen < 1.0E-4) continue;
            side = side.scale(halfWidth / sideLen);

            vertex(buffer, matrix, pa.subtract(side), r, g, b);
            vertex(buffer, matrix, pa.add(side), r, g, b);
            vertex(buffer, matrix, pb.add(side), r, g, b);
            vertex(buffer, matrix, pb.subtract(side), r, g, b);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int r, int g, int b) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, 255).endVertex();
    }

    // ---- surface-point detection -------------------------------------------

    /**
     * Every point where a non-solid cell inside {@code aabb} touches a solid neighbor — the
     * midpoint of that shared face — so arc anchors can sit exactly on a block's collision
     * surface instead of floating at a random point in the zone's volume.
     */
    private static List<Vec3> findSurfacePoints(Level level, AABB aabb) {
        List<Vec3> points = new ArrayList<>();
        BlockPos min = new BlockPos(Mth.floor(aabb.minX), Mth.floor(aabb.minY), Mth.floor(aabb.minZ));
        BlockPos max = new BlockPos(Mth.ceil(aabb.maxX) - 1, Mth.ceil(aabb.maxY) - 1, Mth.ceil(aabb.maxZ) - 1);
        AABB inflated = aabb.inflate(0.51);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isSolid(level, pos)) continue;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!isSolid(level, neighbor)) continue;
                Vec3 face = faceMidpoint(pos, dir);
                if (inflated.contains(face.x, face.y, face.z)) {
                    points.add(face);
                }
            }
        }
        return points;
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3 faceMidpoint(BlockPos emptyPos, Direction towardSolid) {
        double cx = emptyPos.getX() + 0.5 + towardSolid.getStepX() * 0.5;
        double cy = emptyPos.getY() + 0.5 + towardSolid.getStepY() * 0.5;
        double cz = emptyPos.getZ() + 0.5 + towardSolid.getStepZ() * 0.5;
        return new Vec3(cx, cy, cz);
    }

    /** Picks {@code count} points without repeats while the pool lasts, then falls back to
     *  picking with replacement — used so a bundle's anchors don't collapse onto the same surface
     *  point (a zero-length, invisible link) whenever there are at least as many distinct surface
     *  points as anchors wanted. */
    private static List<Vec3> pickDistinct(List<Vec3> source, int count, RandomSource rand) {
        List<Vec3> pool = new ArrayList<>(source);
        List<Vec3> picked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (pool.isEmpty()) {
                picked.add(source.get(rand.nextInt(source.size())));
            } else {
                picked.add(pool.remove(rand.nextInt(pool.size())));
            }
        }
        return picked;
    }

    private static Vec3 randomPointIn(AABB aabb, RandomSource rand) {
        double x = lerp(rand.nextDouble(), aabb.minX, aabb.maxX);
        double y = lerp(rand.nextDouble(), aabb.minY, aabb.maxY);
        double z = lerp(rand.nextDouble(), aabb.minZ, aabb.maxZ);
        return new Vec3(x, y, z);
    }

    /** A point lifted a little above the midpoint of a link, giving it some volume instead of
     *  running dead straight — longer links get a slightly bigger (but still modest) lift. */
    private static Vec3 computeBulge(Vec3 a, Vec3 b, RandomSource rand) {
        Vec3 mid = a.add(b).scale(0.5);
        double linkLen = a.distanceTo(b);
        double lift = (BULGE_MIN_LIFT + linkLen * BULGE_LIFT_FRACTION) * (0.6 + rand.nextDouble() * 0.6);
        return mid.add(0.0, lift, 0.0);
    }

    private static double lerp(double t, double min, double max) {
        return min + (max - min) * t;
    }

    private static final class Bundle {
        List<Vec3> anchors = List.of();
        /** One entry per link (size {@code anchors.size() - 1}); {@code null} means that link
         *  runs straight, a non-null point means it bows through that point instead. */
        List<Vec3> bulges = List.of();
        long seed;
        long expiresAtTick;

        void reroll(Level level, AABB trueAabb, AABB sampleAabb, ArcInfo arc, RandomSource rand) {
            List<Vec3> surface = findSurfacePoints(level, trueAabb);
            List<Vec3> pts;
            if (surface.isEmpty()) {
                // No solid surface anywhere in this zone (e.g. floating in open air) — fall back
                // to the old random-in-volume sampling rather than having no anchors at all.
                pts = new ArrayList<>(arc.pointsPerBundle());
                for (int i = 0; i < arc.pointsPerBundle(); i++) {
                    pts.add(randomPointIn(sampleAabb, rand));
                }
            } else {
                pts = pickDistinct(surface, arc.pointsPerBundle(), rand);
            }
            anchors = pts;
            seed = rand.nextLong();

            List<Vec3> newBulges = new ArrayList<>(Math.max(0, anchors.size() - 1));
            for (int i = 0; i < anchors.size() - 1; i++) {
                newBulges.add(rand.nextDouble() < BULGE_CHANCE
                        ? computeBulge(anchors.get(i), anchors.get(i + 1), rand)
                        : null);
            }
            bulges = newBulges;
        }
    }

    private static final class Strike {
        List<Vec3> origins = List.of();
        int targetEntityId;
        Vec3 fallbackTarget;
        int color;
        long seed;
        long windupStartTick;
        long windupUntilTick;
        long expiresAtTick;
    }

    private record Key(ResourceLocation typeId, BlockPos pos) {
    }
}
