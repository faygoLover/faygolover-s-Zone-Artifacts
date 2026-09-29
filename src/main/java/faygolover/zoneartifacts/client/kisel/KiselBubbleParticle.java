package faygolover.zoneartifacts.client.kisel;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

import javax.annotation.Nullable;

/** A glowing green bubble in Kisel: swells on the surface, drifts a little and bursts. */
public class KiselBubbleParticle extends TextureSheetParticle {

    private final float maxSize;

    protected KiselBubbleParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.0f;
        this.friction = 0.9f;
        this.hasPhysics = false;
        this.lifetime = 10 + random.nextInt(18);
        this.maxSize = 0.03f + random.nextFloat() * 0.05f;
        this.quadSize = maxSize * 0.3f;
        float s = 0.85f + random.nextFloat() * 0.15f;
        setColor(0.45f * s, 0.85f * s, 0.25f * s);
        this.alpha = 0.75f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = age / (float) lifetime;
        quadSize = maxSize * (0.3f + 0.7f * Math.min(1.0f, t * 1.6f));
        if (t > 0.85f) alpha = (1.0f - t) / 0.15f * 0.75f;
    }

    /** It glows. */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
            return new KiselBubbleParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
