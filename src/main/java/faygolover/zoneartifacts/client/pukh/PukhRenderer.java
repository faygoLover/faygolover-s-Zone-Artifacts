package faygolover.zoneartifacts.client.pukh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.block.PukhBlockEntity;
import faygolover.zoneartifacts.block.PukhLayout;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Burning Fluff's hanging strands: sheets of lacy, cobweb-like fibre (a cut-out texture, lit by
 * the world light at each height) hanging from the base, swaying more the lower they hang, pushed
 * aside where something goes through ({@link PukhBlockEntity#partX}) and slowly closing again.
 * The texture's bottom edge (ragged hems and loose threads) always ends the strands.
 */
public class PukhRenderer implements BlockEntityRenderer<PukhBlockEntity> {

    private static final ResourceLocation LACE = new ResourceLocation(ZoneArtifacts.MODID, "textures/block/pukh_lace.png");
    /** The lace texture covers this many blocks top to bottom. */
    private static final double TEXTURE_BLOCKS = 4.0;
    private static final int ROWS = 12;

    public PukhRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PukhBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float time = (level.getGameTime() % 72000L) + partialTick;
        Direction facing = be.facing();
        double length = be.effectiveLength();
        if (length <= 0.05) return;
        boolean wall = facing != Direction.DOWN && facing != Direction.UP;
        List<PukhLayout.Sheet> sheets = PukhLayout.sheets(facing, be.intensity());
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LACE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        BlockPos origin = be.getBlockPos();

        // World light at each row's height (strands hang out of the block into others).
        int[] light = new int[ROWS + 1];
        for (int r = 0; r <= ROWS; r++) {
            double d = length * r / ROWS;
            light[r] = LevelRenderer.getLightColor(level, BlockPos.containing(origin.getX() + 0.5,
                    origin.getY() + (wall ? Pukh.WALL_TOP : Pukh.CEILING_TOP) - d - 0.02, origin.getZ() + 0.5));
        }
        Vec3 away = wall ? new Vec3(facing.getStepX(), 0, facing.getStepZ()) : Vec3.ZERO;

        // Wall bases stacked over each other hang their sheets in the same planes: shift every other
        // one a hair, so where the upper's strands hang over the lower's they don't flicker.
        double parity = wall && (origin.getY() & 1) != 0 ? 0.018 : 0.0;
        int columns = PukhBlockEntity.PART_COLUMNS;
        for (int s = 0; s < sheets.size() && s < PukhBlockEntity.MAX_SHEETS; s++) {
            PukhLayout.Sheet sheet = sheets.get(s);
            Vec3 across = sheet.across();
            Vec3 normal = wall ? away : new Vec3(-across.z, 0, across.x);
            float[][] px = new float[ROWS + 1][columns + 1];
            float[][] py = new float[ROWS + 1][columns + 1];
            float[][] pz = new float[ROWS + 1][columns + 1];
            float[] vv = new float[ROWS + 1];
            int[] col = new int[ROWS + 1];
            for (int r = 0; r <= ROWS; r++) {
                double d = length * r / ROWS;
                double amp = 0.07 * Math.pow(d, 1.1);
                double depthWeight = Math.min(1.0, d / 0.5);
                for (int c = 0; c <= columns; c++) {
                    double u = c / (double) columns;
                    Vec3 p = sheet.origin().add(across.scale(u * sheet.width())).add(0.0, -d, 0.0);
                    double sway = Math.sin(time * 0.04 + d * 1.2 + sheet.seed() + u * 0.8) * amp;
                    double sway2 = Math.sin(time * 0.031 + d * 0.9 + sheet.seed() * 1.3) * amp * 0.5;
                    p = p.add(across.scale(sway)).add(normal.scale(sway2 * (wall ? 0.4 : 1.0)));
                    if (wall) {
                        // Growing out of the base's face, then falling away from it.
                        double k = Math.max(0.0, 1.0 - d / PukhLayout.LEAN_LENGTH);
                        p = p.add(away.scale(parity * (1.0 - k) - sheet.lean() * k * k));
                    }
                    // Parted where something went through.
                    float fc = Mth.clamp((float) (u * columns - 0.5), 0.0f, columns - 1.0f);
                    int c0 = (int) Math.floor(fc);
                    int c1 = Math.min(columns - 1, c0 + 1);
                    float t = fc - c0;
                    double ox = Mth.lerp(t, be.partX[s][c0], be.partX[s][c1]) * depthWeight;
                    double oz = Mth.lerp(t, be.partZ[s][c0], be.partZ[s][c1]) * depthWeight;
                    if (wall) {
                        // Never into the wall.
                        double into = -(ox * away.x + oz * away.z);
                        if (into > 0) {
                            ox += away.x * into;
                            oz += away.z * into;
                        }
                    }
                    px[r][c] = (float) (p.x + ox);
                    py[r][c] = (float) p.y;
                    pz[r][c] = (float) (p.z + oz);
                }
                vv[r] = (float) (1.0 - (length - d) / TEXTURE_BLOCKS);
                float k = (float) (d / length);
                col[r] = mix(0x8C8266, 0xE2DFD2, Mth.clamp(k * 1.3f, 0.0f, 1.0f));
            }
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < columns; c++) {
                    float u0 = c / (float) columns;
                    float u1 = (c + 1) / (float) columns;
                    vertex(vc, m, nm, px[r][c], py[r][c], pz[r][c], u0, vv[r], col[r], light[r], packedOverlay);
                    vertex(vc, m, nm, px[r + 1][c], py[r + 1][c], pz[r + 1][c], u0, vv[r + 1], col[r + 1], light[r + 1], packedOverlay);
                    vertex(vc, m, nm, px[r + 1][c + 1], py[r + 1][c + 1], pz[r + 1][c + 1], u1, vv[r + 1], col[r + 1], light[r + 1], packedOverlay);
                    vertex(vc, m, nm, px[r][c + 1], py[r][c + 1], pz[r][c + 1], u1, vv[r], col[r], light[r], packedOverlay);
                }
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, float u, float v,
                               int rgb, int light, int overlay) {
        vc.vertex(m, x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, 0.0f, 1.0f, 0.0f).endVertex();
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean shouldRenderOffScreen(PukhBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
