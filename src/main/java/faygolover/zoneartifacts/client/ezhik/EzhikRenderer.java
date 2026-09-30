package faygolover.zoneartifacts.client.ezhik;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.block.EzhikBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hedgehog: an urchin buried in the host block — every spike runs out of one centre under the
 * face, all alike, and only the part that pierces the face is seen: long in the middle, the rim ones
 * mere stubs. They wear the host block's texture (mapped as if it were still the flat face) and
 * tint, lit by the world. Slowly: all lean together as the centre drifts aside, now and then the
 * centre sinks and they all draw in, rings of shortening run out from the middle, and each changes
 * its length a little on its own. No trembling.
 */
public class EzhikRenderer implements BlockEntityRenderer<EzhikBlockEntity> {

    private static final int SEGMENTS = 8;
    private static final float[] RING_T = {0.0f, 0.35f, 0.65f, 0.84f, 0.95f, 1.0f};
    private static final float[] RING_R = {1.0f, 0.96f, 0.8f, 0.55f, 0.28f, 0.0f};

    public EzhikRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EzhikBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;
        Direction facing = be.facing();
        BlockPos hostPos = be.getBlockPos().relative(facing.getOpposite());
        BlockState host = level.getBlockState(hostPos);
        if (host.isAir()) return;

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(host);
        List<BakedQuad> quads = model.getQuads(host, facing, RandomSource.create(42L), ModelData.EMPTY, null);
        TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
        int tint = 0xFFFFFF;
        if (!quads.isEmpty() && quads.get(0).isTinted()) {
            tint = mc.getBlockColors().getColor(host, level, hostPos, quads.get(0).getTintIndex());
        }
        int cr = (tint >> 16) & 0xFF;
        int cg = (tint >> 8) & 0xFF;
        int cb = tint & 0xFF;

        // The face's frame: n out of the host, t and b along it; c its middle.
        Vec3 n = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 t = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 b = n.cross(t);
        Vec3 c = new Vec3(0.5, 0.5, 0.5).subtract(n.scale(0.5));

        float time = (level.getGameTime() % 72000L) + partialTick;
        double speed = be.speed();
        double r = be.radius();
        int count = Mth.clamp(8 + be.intensity() * 6, 6, 90);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();

        // One urchin buried in the block: every spike runs out of the same centre, all alike; only
        // where one pierces the face (its hole) is it seen — long in the middle, a stub at the rim.
        long seed = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        RandomSource rand = RandomSource.create(seed);
        double ts = time * Math.max(0.0, speed);
        float s0 = (float) ((seed >>> 16) & 0xFFFF) / 6553.6f;
        double depth = be.depth() * r;
        // The centre wanders a little to the side (all spikes lean together)…
        double shiftA = 0.22 * r * Math.sin(ts * 0.0061 + s0);
        double shiftB = 0.22 * r * Math.sin(ts * 0.0047 + s0 * 1.7);
        // …and now and then sinks deeper (all of them draw in).
        double sink = depth * 0.9 * Math.pow(Math.max(0.0, Math.sin(ts * 0.0023 + s0 * 2.3)), 8.0);
        Vec3 centre = c.subtract(n.scale(depth + sink)).add(t.scale(shiftA)).add(b.scale(shiftB));
        // Every spike this long from the centre: the rim ones just break the surface.
        double length = Math.sqrt(depth * depth + r * r) + 0.12 * r;
        // Rings of shortening running out from the middle, now and then (deep enough to hide them).
        double waveMood = Math.pow(Math.max(0.0, Math.sin(ts * 0.0035 + s0 * 0.7)), 3.0);
        double thick = r * 0.22 + 0.03;
        List<double[]> holes = scatter(rand, count, r * 0.95);
        for (int i = 0; i < holes.size(); i++) {
            double rho = holes.get(i)[0];
            double ang = holes.get(i)[1];
            double phase = rand.nextDouble() * Math.PI * 2.0;
            Vec3 hole = c.add(t.scale(Math.cos(ang) * rho)).add(b.scale(Math.sin(ang) * rho));
            Vec3 toHole = hole.subtract(centre);
            double inside = toHole.length();
            Vec3 dir = toHole.scale(1.0 / Math.max(1.0E-4, inside));
            double own = length * (1.0 + 0.06 * Math.sin(ts * 0.011 + phase));
            double wave = waveMood * length * 0.75 * Math.pow(Math.max(0.0, Math.sin(ts * 0.03 - rho / Math.max(0.05, r) * 4.0)), 4.0);
            double shown = own - wave - inside;
            if (shown <= 0.01) continue;
            // As thick where it comes out as the spike is there (thinner the farther from the centre).
            double baseR = thick * Mth.clamp(1.0 - inside / (own * 1.1), 0.35, 1.0);
            spike(vc, m, nm, sprite, hole.subtract(dir.scale(0.06 * r + 0.01)), dir, baseR, shown + 0.06 * r + 0.01, c, t, b, cr, cg, cb, packedLight);
        }
    }

    /** Where the holes are: {distance from the middle, angle}, scattered but not on top of each other. */
    private static List<double[]> scatter(RandomSource rand, int count, double radius) {
        List<double[]> out = new ArrayList<>();
        double minGap = radius * 1.6 / Math.sqrt(Math.max(1, count));
        for (int tries = 0; tries < count * 12 && out.size() < count; tries++) {
            double rho = radius * Math.sqrt(rand.nextDouble());
            double ang = rand.nextDouble() * Math.PI * 2.0;
            double x = Math.cos(ang) * rho;
            double y = Math.sin(ang) * rho;
            boolean free = true;
            for (double[] o : out) {
                double dx = Math.cos(o[1]) * o[0] - x;
                double dy = Math.sin(o[1]) * o[0] - y;
                if (dx * dx + dy * dy < minGap * minGap) {
                    free = false;
                    break;
                }
            }
            if (free) out.add(new double[]{rho, ang});
        }
        return out;
    }

    /** A thick, round-tipped spike from {@code foot} along {@code axis}, {@code h} long. */
    private static void spike(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sprite, Vec3 foot, Vec3 axis,
                              double baseR, double h, Vec3 c, Vec3 t, Vec3 b, int cr, int cg, int cb, int light) {
        Vec3 u = axis.cross(Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = axis.cross(u).normalize();
        int rings = RING_T.length;
        Vec3[][] p = new Vec3[rings][SEGMENTS + 1];
        Vec3[][] nrm = new Vec3[rings][SEGMENTS + 1];
        for (int k = 0; k < rings; k++) {
            Vec3 mid = foot.add(axis.scale(RING_T[k] * h));
            double rr = RING_R[k] * Math.min(baseR, h * 0.45);
            for (int s = 0; s <= SEGMENTS; s++) {
                double a = Math.PI * 2.0 * s / SEGMENTS;
                Vec3 dir = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                p[k][s] = mid.add(dir.scale(rr));
                nrm[k][s] = dir.scale(0.8).add(axis.scale(0.2 + 0.6 * RING_T[k])).normalize();
            }
        }
        for (int k = 0; k < rings - 1; k++) {
            for (int s = 0; s < SEGMENTS; s++) {
                put(vc, m, nm, sprite, p[k][s], nrm[k][s], c, t, b, cr, cg, cb, light);
                put(vc, m, nm, sprite, p[k][s + 1], nrm[k][s + 1], c, t, b, cr, cg, cb, light);
                put(vc, m, nm, sprite, p[k + 1][s + 1], nrm[k + 1][s + 1], c, t, b, cr, cg, cb, light);
                put(vc, m, nm, sprite, p[k + 1][s], nrm[k + 1][s], c, t, b, cr, cg, cb, light);
            }
        }
    }

    /** A vertex, textured as if it lay flat on the face (the texture repeating block by block). */
    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sprite, Vec3 p, Vec3 normal,
                            Vec3 c, Vec3 t, Vec3 b, int cr, int cg, int cb, int light) {
        Vec3 d = p.subtract(c);
        double s = d.dot(t) + 0.5;
        double q = d.dot(b) + 0.5;
        // Repeat per block, mirrored so neighbouring blocks' seams match.
        s = tri(s);
        q = tri(q);
        float u = sprite.getU(s * 16.0);
        float v = sprite.getV(q * 16.0);
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(cr, cg, cb, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }

    private static double tri(double w) {
        double f = w - Math.floor(w / 2.0) * 2.0;
        return f <= 1.0 ? f : 2.0 - f;
    }

    @Override
    public boolean shouldRenderOffScreen(EzhikBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
