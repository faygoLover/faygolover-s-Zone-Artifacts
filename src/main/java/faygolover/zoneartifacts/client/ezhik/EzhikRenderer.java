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

import java.util.List;

/**
 * The Hedgehog: a patch of the host block's own surface risen into rounded lumps and spikes — a
 * phyllotaxis spiral, the big ones in the middle leaning out, the small ones round the rim — all
 * wearing the host block's texture (mapped as if it were still the flat face, so it stretches up the
 * spikes) and tint, lit by the world. They move in slow moods that blend into each other: growing
 * out and sinking back, trembling, a pulse running out from the middle.
 */
public class EzhikRenderer implements BlockEntityRenderer<EzhikBlockEntity> {

    private static final int SEGMENTS = 8;
    private static final float[] RING_T = {-0.04f, 0.3f, 0.62f, 0.86f, 1.0f};
    private static final float[] RING_R = {1.08f, 0.92f, 0.66f, 0.34f, 0.0f};

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

        // Moods, cross-fading: out/in, trembling, a pulse running outwards.
        double moodTime = time * 0.004 * Math.max(0.05, speed);
        double[] mood = new double[3];
        for (int k = 0; k < 3; k++) {
            double ph = moodTime - k / 3.0;
            mood[k] = Math.pow(Math.max(0.0, Math.cos((ph - Math.floor(ph)) * Math.PI * 2.0)), 2.0);
        }
        double sum = mood[0] + mood[1] + mood[2] + 1.0E-6;
        double wOut = mood[0] / sum;
        double wShake = mood[1] / sum;
        double wPulse = mood[2] / sum;

        // A low mound under them all (so no gap shows at their feet).
        mound(vc, m, nm, sprite, c, n, t, b, r, cr, cg, cb, packedLight, hostPos, be.getBlockPos());

        long seed = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        RandomSource rand = RandomSource.create(seed);
        double ts = time * Math.max(0.05, speed);
        for (int i = 0; i < count; i++) {
            double f = (i + 0.5) / count;
            double rho = r * Math.sqrt(f) * 0.92;
            double ang = i * 2.39996 + rand.nextDouble() * 0.3;
            double jitter = rand.nextDouble();
            double k = rho / Math.max(0.01, r);
            double baseR = r * (0.2 - 0.1 * k) * (0.8 + 0.4 * jitter) + 0.02;
            double height = r * (0.62 - 0.4 * Math.pow(k, 1.3)) * (0.75 + 0.5 * jitter);

            double out = 0.45 + 0.55 * (0.5 + 0.5 * Math.sin(ts * 0.025 + jitter * 6.0));
            double shake = 0.06 * Math.sin(ts * 1.9 + i * 3.1) + 0.04 * Math.sin(ts * 2.7 + i);
            double pulse = 0.35 * Math.max(0.0, Math.sin(ts * 0.09 - k * 5.0));
            double grow = wOut * out + wShake * (0.8 + shake) + wPulse * (0.6 + pulse);
            double h = height * Mth.clamp(grow, 0.15, 1.5);

            Vec3 radial = t.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
            Vec3 axis = n.add(radial.scale(0.9 * k + 0.15 * (jitter - 0.5))).normalize();
            Vec3 foot = c.add(radial.scale(rho)).add(n.scale(0.002 + 0.05 * (1.0 - k * k) * Math.min(1.0, r)));
            if (wShake > 0.05) {
                Vec3 side = radial.cross(n);
                foot = foot.add(side.scale(wShake * 0.01 * Math.sin(ts * 2.3 + i * 1.7)));
            }
            spike(vc, m, nm, sprite, foot, axis, baseR, h, c, t, b, cr, cg, cb, packedLight);
        }
    }

    private static void spike(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sprite, Vec3 foot, Vec3 axis,
                              double baseR, double h, Vec3 c, Vec3 t, Vec3 b, int cr, int cg, int cb, int light) {
        Vec3 u = axis.cross(Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = axis.cross(u).normalize();
        int rings = RING_T.length;
        Vec3[][] p = new Vec3[rings][SEGMENTS + 1];
        Vec3[][] nrm = new Vec3[rings][SEGMENTS + 1];
        for (int k = 0; k < rings; k++) {
            double along = RING_T[k] * h;
            double rr = RING_R[k] * baseR;
            for (int s = 0; s <= SEGMENTS; s++) {
                double a = Math.PI * 2.0 * s / SEGMENTS;
                Vec3 dir = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                p[k][s] = foot.add(axis.scale(along)).add(dir.scale(rr));
                nrm[k][s] = dir.scale(0.8).add(axis.scale(0.2 + 0.6 * RING_T[Math.max(0, k)])).normalize();
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

    /** A low round swelling of the face under the spikes, its rim flush with the face. */
    private static void mound(VertexConsumer vc, Matrix4f m, Matrix3f nm, TextureAtlasSprite sprite, Vec3 c, Vec3 n, Vec3 t, Vec3 b,
                              double r, int cr, int cg, int cb, int light, BlockPos host, BlockPos self) {
        int rings = 5;
        int seg = 16;
        double top = 0.05 * Math.min(1.0, r);
        for (int k = 0; k < rings; k++) {
            double r0 = r * k / rings;
            double r1 = r * (k + 1) / rings;
            double h0 = 0.002 + top * (1.0 - (r0 / r) * (r0 / r));
            double h1 = 0.002 + top * (1.0 - (r1 / r) * (r1 / r));
            for (int s = 0; s < seg; s++) {
                double a0 = Math.PI * 2.0 * s / seg;
                double a1 = Math.PI * 2.0 * (s + 1) / seg;
                Vec3 d0 = t.scale(Math.cos(a0)).add(b.scale(Math.sin(a0)));
                Vec3 d1 = t.scale(Math.cos(a1)).add(b.scale(Math.sin(a1)));
                put(vc, m, nm, sprite, c.add(d0.scale(r0)).add(n.scale(h0)), n, c, t, b, cr, cg, cb, light);
                put(vc, m, nm, sprite, c.add(d1.scale(r0)).add(n.scale(h0)), n, c, t, b, cr, cg, cb, light);
                put(vc, m, nm, sprite, c.add(d1.scale(r1)).add(n.scale(h1)), n, c, t, b, cr, cg, cb, light);
                put(vc, m, nm, sprite, c.add(d0.scale(r1)).add(n.scale(h1)), n, c, t, b, cr, cg, cb, light);
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
