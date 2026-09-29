package faygolover.zoneartifacts.client.chem;

import faygolover.zoneartifacts.client.gravity.GoreClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** A heavy drop of the Chemical Comet's gas: falls, and where it lands leaves a small pale stain. */
public class ChemDropParticle extends TextureSheetParticle {

    protected ChemDropParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.6f;
        this.friction = 0.98f;
        this.hasPhysics = true;
        this.lifetime = 80;
        this.quadSize = 0.05f + random.nextFloat() * 0.04f;
        float shade = 0.85f + random.nextFloat() * 0.15f;
        setColor(0.78f * shade, 0.82f * shade, 0.25f * shade);
        this.alpha = 0.85f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (onGround) {
            GoreClient.addStain(level, new Vec3(x, y + 0.2, z), new Vec3(0, -1, 0), 0.6,
                    0.08 + random.nextDouble() * 0.1, 0xB8C24A, 0x6E7A22, 110, 500);
            remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ChemDropParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
