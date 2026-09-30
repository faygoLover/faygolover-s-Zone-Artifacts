package faygolover.zoneartifacts.client.poppy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.PoppyEngine;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.fx.HumLoop;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The poppy field on the client:
 * <ul>
 *     <li>thick poppies over the ground of the field (drawn, not placed), swaying; swarms of petals
 *     drifting over it with trails of petals behind them;</li>
 *     <li>its own player's micro-sleeps (from the server, {@link PoppyEngine}): heavy blinking, eyes
 *     shut and only a hum, the legs walking off on their own, the view held; full sleep; waking;</li>
 *     <li>anyone asleep lies on their back (and gets up while waking).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PoppyClient {

    private static final ResourceLocation POPPY = new ResourceLocation("minecraft", "block/poppy");
    private static final RandomSource RANDOM = RandomSource.create();
    private static final double NEAR = 64.0;

    private record Sleep(PoppyEngine.Phase phase, long start, int ticks) {
    }

    private static final Map<Integer, Sleep> SLEEP = new HashMap<>();

    private static final class Field {
        SyncAnomaliesPacket.Entry entry;
        float[] plants = new float[0]; // x, y, z, scale, angle, seed per plant
        double groundY;
        int nextScan;
    }

    private static final Map<BlockPos, Field> FIELDS = new HashMap<>();

    // The own player.
    private static float lids;
    private static float prevLids;
    private static boolean walking;
    private static boolean frozen;
    private static float lockYaw;
    private static float lockPitch;
    private static float yawDrift;
    @Nullable
    private static HumLoop hum;

    private PoppyClient() {
    }

    public static void onState(int entityId, int phase, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoppyEngine.Phase[] all = PoppyEngine.Phase.values();
        PoppyEngine.Phase p = phase >= 0 && phase < all.length ? all[phase] : PoppyEngine.Phase.NONE;
        if (p == PoppyEngine.Phase.NONE) SLEEP.remove(entityId);
        else SLEEP.put(entityId, new Sleep(p, mc.level.getGameTime(), ticks));
    }

    /** 0..1: how far the own eyes are shut. */
    public static float eyelids(float partial) {
        return Mth.lerp(partial, prevLids, lids);
    }

    /** Fully shut: the screen is black. */
    public static float blackout(float partial) {
        return eyelids(partial) >= 0.999f ? 1.0f : 0.0f;
    }

    /** With the eyes shut only the hum is heard. */
    public static float hearing() {
        return 1.0f - 0.9f * Mth.clamp((lids - 0.3f) / 0.7f, 0.0f, 1.0f);
    }

    // ---- the own player ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (event.phase == TickEvent.Phase.START) {
            if (player != null && (walking || frozen)) {
                // The view stays where the eyes closed (drifting a little with the steps).
                if (walking) lockYaw += yawDrift;
                player.setYRot(lockYaw);
                player.setXRot(lockPitch);
                player.yRotO = lockYaw - (walking ? yawDrift : 0.0f);
                player.xRotO = lockPitch;
            }
            return;
        }
        prevLids = lids;
        if (level == null || player == null) {
            SLEEP.clear();
            FIELDS.clear();
            lids = 0.0f;
            walking = false;
            frozen = false;
            return;
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        Sleep own = SLEEP.get(player.getId());
        boolean wasHeld = walking || frozen;
        walking = false;
        frozen = false;
        float target = 0.0f;
        if (own != null) {
            int t = (int) (now - own.start());
            switch (own.phase()) {
                case EPISODE -> {
                    int closed = own.ticks() - PoppyEngine.OPEN_TICKS;
                    if (t < PoppyEngine.CLOSE_TICKS) target = blink(t);
                    else if (t < closed) {
                        target = 1.0f;
                        walking = true;
                    } else target = 1.0f - Mth.clamp((t - closed) / (float) PoppyEngine.OPEN_TICKS, 0.0f, 1.0f);
                    if (t > own.ticks() + 20) SLEEP.remove(player.getId());
                }
                case ASLEEP -> {
                    target = Math.min(1.0f, blink(Math.min(t, PoppyEngine.CLOSE_TICKS)) + t / 12.0f);
                    frozen = true;
                }
                case WAKING -> {
                    target = 1.0f - Mth.clamp(t / (float) own.ticks(), 0.0f, 1.0f);
                    frozen = true;
                    if (t > own.ticks() + 20) SLEEP.remove(player.getId());
                }
                default -> {
                }
            }
        }
        lids = target;
        if ((walking || frozen) && !wasHeld) {
            lockYaw = player.getYRot();
            lockPitch = Mth.clamp(player.getXRot(), -10.0f, 25.0f);
            yawDrift = (RANDOM.nextFloat() - 0.5f) * 3.0f;
        }
        if (walking && RANDOM.nextInt(15) == 0) yawDrift = Mth.clamp(yawDrift + (RANDOM.nextFloat() - 0.5f) * 2.0f, -2.0f, 2.0f);
        if (lids > 0.3f && (hum == null || hum.isStopped())) {
            hum = new HumLoop(ModSounds.POPPY_HUM.get(), () -> 0.6 * Mth.clamp((lids - 0.3f) / 0.7f, 0.0f, 1.0f));
            mc.getSoundManager().play(hum);
        }
        fields(mc, level, now);
    }

    /** Heavy blinking as the eyes close (ticks 0..{@link PoppyEngine#CLOSE_TICKS}). */
    private static float blink(int t) {
        if (t < 6) return t / 6.0f * 0.7f;
        if (t < 10) return 0.7f - (t - 6) / 4.0f * 0.5f;
        if (t < 18) return 0.2f + (t - 10) / 8.0f * 0.75f;
        return Math.min(1.0f, 0.95f + (t - 18) / 6.0f * 0.05f);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(walking || frozen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
    }

    /** Asleep, the legs don't obey; in a micro-sleep they walk off on their own. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInput(MovementInputUpdateEvent event) {
        if (!walking && !frozen) return;
        Input input = event.getInput();
        input.up = walking;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = walking ? 1.0f : 0.0f;
        input.leftImpulse = 0.0f;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (walking || frozen) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    // ---- lying down ---------------------------------------------------------------------------------

    private static final Set<LivingEntity> TILTED = new HashSet<>();

    /** Lower than the Dusk's hiding (which cancels): only bodies that are drawn get laid down. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (SLEEP.isEmpty()) return;
        LivingEntity e = event.getEntity();
        Sleep s = SLEEP.get(e.getId());
        if (s == null || (s.phase() != PoppyEngine.Phase.ASLEEP && s.phase() != PoppyEngine.Phase.WAKING)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = (mc.level.getGameTime() - s.start()) + event.getPartialTick();
        float tilt = s.phase() == PoppyEngine.Phase.ASLEEP ? Math.min(1.0f, t / 12.0f)
                : 1.0f - Mth.clamp(t / Math.max(1.0f, s.ticks()), 0.0f, 1.0f);
        tilt = tilt * tilt * (3.0f - 2.0f * tilt);
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot);
        float phi = 180.0f - yaw;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, 0.22 * tilt * e.getBbWidth() / 0.6, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(phi));
        pose.mulPose(Axis.XP.rotationDegrees(90.0f * tilt));
        pose.mulPose(Axis.YP.rotationDegrees(-phi));
        TILTED.add(e);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (TILTED.remove(event.getEntity())) event.getPoseStack().popPose();
    }

    // ---- the field ----------------------------------------------------------------------------------

    private static void fields(Minecraft mc, ClientLevel level, long now) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Set<BlockPos> seen = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.POPPY.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceTo(cam) > NEAR + entry.size()) continue;
            seen.add(entry.pos());
            Field f = FIELDS.computeIfAbsent(entry.pos(), p -> new Field());
            boolean resized = f.entry == null || f.entry.size() != entry.size();
            f.entry = entry;
            if (resized || --f.nextScan <= 0) {
                scan(level, f);
                f.nextScan = 100;
            }
            petals(level, f, now);
        }
        FIELDS.keySet().removeIf(p -> !seen.contains(p));
    }

    /** Where the poppies grow: on the top faces of the ground in the zone, open to the sky above. */
    private static void scan(ClientLevel level, Field f) {
        AABB zone = AnomalyGeometry.box(f.entry);
        int per = Math.max(1, ModCommonConfig.POPPY_DENSITY.get());
        int x0 = Mth.floor(zone.minX);
        int x1 = Mth.floor(zone.maxX - 1.0E-6);
        int z0 = Mth.floor(zone.minZ);
        int z1 = Mth.floor(zone.maxZ - 1.0E-6);
        int columns = (x1 - x0 + 1) * (z1 - z0 + 1);
        per = Math.max(1, Math.min(per, 3000 / Math.max(1, columns)));
        List<Float> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        double groundSum = 0.0;
        int groundCount = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int top = Integer.MIN_VALUE;
                for (int y = Mth.floor(zone.maxY); y >= Mth.floor(zone.minY) - 1; y--) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.isFaceSturdy(level, p, Direction.UP)) {
                        BlockState above = level.getBlockState(p.set(x, y + 1, z));
                        if (above.getCollisionShape(level, p).isEmpty()) top = y + 1;
                        break;
                    }
                }
                if (top == Integer.MIN_VALUE) continue;
                groundSum += top;
                groundCount++;
                RandomSource r = RandomSource.create(BlockPos.asLong(x, top, z) * 31L);
                for (int i = 0; i < per; i++) {
                    out.add((float) (x + 0.1 + r.nextDouble() * 0.8));
                    out.add((float) top);
                    out.add((float) (z + 0.1 + r.nextDouble() * 0.8));
                    out.add(0.6f + r.nextFloat() * 0.5f);
                    out.add(r.nextFloat() * (float) Math.PI);
                    out.add(r.nextFloat() * 100.0f);
                }
            }
        }
        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        f.plants = arr;
        f.groundY = groundCount > 0 ? groundSum / groundCount : zone.minY;
    }

    /** Swarms of petals wandering over the field, each trailing petals. */
    private static void petals(ClientLevel level, Field f, long now) {
        AABB zone = AnomalyGeometry.box(f.entry);
        int swarms = 2 + (int) (f.entry.size() / 3.0);
        double hx = zone.getXsize() * 0.4;
        double hz = zone.getZsize() * 0.4;
        Vec3 c = zone.getCenter();
        for (int i = 0; i < swarms; i++) {
            float seed = i * 12.7f + f.entry.pos().hashCode() % 100;
            Vec3 a = swarm(c, hx, hz, f.groundY, seed, now);
            Vec3 b = swarm(c, hx, hz, f.groundY, seed, now + 1);
            Vec3 v = b.subtract(a);
            int n = 1 + RANDOM.nextInt(2);
            for (int k = 0; k < n; k++) {
                faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.POPPY_PETAL.get(),
                        a.x + RANDOM.nextGaussian() * 0.25, a.y + RANDOM.nextGaussian() * 0.2, a.z + RANDOM.nextGaussian() * 0.25,
                        -v.x * 0.3 + RANDOM.nextGaussian() * 0.01, -0.005, -v.z * 0.3 + RANDOM.nextGaussian() * 0.01);
            }
        }
    }

    private static Vec3 swarm(Vec3 c, double hx, double hz, double ground, float seed, long t) {
        double x = c.x + hx * Math.sin(t * 0.011 + seed) * Math.cos(t * 0.0047 + seed * 0.3);
        double z = c.z + hz * Math.cos(t * 0.009 + seed * 1.7) * Math.sin(t * 0.0061 + seed);
        double y = ground + 1.0 + 0.7 * Math.sin(t * 0.017 + seed * 2.3);
        return new Vec3(x, y, z);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || FIELDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(POPPY);
        float time = (level.getGameTime() % 72000L) + event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        Matrix3f nm = poseStack.last().normal();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        BlockPos.MutableBlockPos lp = new BlockPos.MutableBlockPos();
        for (Field f : FIELDS.values()) {
            if (f.entry != null && !f.entry.visible()) continue;
            float[] a = f.plants;
            for (int i = 0; i + 5 < a.length; i += 6) {
                float x = a[i];
                float y = a[i + 1];
                float z = a[i + 2];
                double dx = x - cam.x;
                double dz = z - cam.z;
                if (dx * dx + dz * dz > NEAR * NEAR) continue;
                float scale = a[i + 3];
                float angle = a[i + 4];
                float seed = a[i + 5];
                int light = LevelRenderer.getLightColor(level, lp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
                float sway = 0.05f * scale * Mth.sin(time * 0.05f + seed) + 0.03f * scale * Mth.sin(time * 0.13f + seed * 2.0f);
                cross(vc, m, nm, sprite, x, y, z, 0.42f * scale, 0.8f * scale, angle, sway, light);
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        poseStack.popPose();
    }

    private static void cross(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sprite, float x, float y, float z,
                              float half, float height, float angle, float sway, int light) {
        for (int k = 0; k < 2; k++) {
            float a = angle + k * (float) Math.PI * 0.5f;
            float cx = Mth.cos(a) * half;
            float cz = Mth.sin(a) * half;
            float sx = Mth.cos(angle + 0.7f) * sway;
            float sz = Mth.sin(angle + 0.7f) * sway;
            vertex(vc, m, nm, x - cx, y, z - cz, sprite.getU0(), sprite.getV1(), light);
            vertex(vc, m, nm, x + cx, y, z + cz, sprite.getU1(), sprite.getV1(), light);
            vertex(vc, m, nm, x + cx + sx, y + height, z + cz + sz, sprite.getU1(), sprite.getV0(), light);
            vertex(vc, m, nm, x - cx + sx, y + height, z - cz + sz, sprite.getU0(), sprite.getV0(), light);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(nm, 0.0f, 1.0f, 0.0f).endVertex();
    }
}
