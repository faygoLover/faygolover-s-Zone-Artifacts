package faygolover.zoneartifacts.client.gravity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

import javax.annotation.Nullable;

/** A drop of blood: flies where it's sent, falls, and stays put a moment where it lands. */
public class BloodParticle extends TextureSheetParticle {

    protected BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.9f;
        this.friction = 0.98f;
        this.hasPhysics = true;
        this.lifetime = 40 + random.nextInt(40);
        this.quadSize = 0.035f + random.nextFloat() * 0.055f;
        float shade = 0.75f + random.nextFloat() * 0.25f;
        setColor(0.55f * shade, 0.03f * shade, 0.03f * shade);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (onGround) {
            xd = 0.0;
            zd = 0.0;
        }
        float t = age / (float) lifetime;
        alpha = t < 0.8f ? 1.0f : (1.0f - t) / 0.2f;
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
            return new BloodParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
