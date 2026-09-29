package faygolover.zoneartifacts.client.poppy;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/** A poppy petal: drifts, flutters and turns over as it sinks, and fades before it lands. */
public class PoppyPetalParticle extends TextureSheetParticle {

    private final float spin;
    private final float phase;

    protected PoppyPetalParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.02f;
        this.friction = 0.96f;
        this.hasPhysics = true;
        this.lifetime = 50 + random.nextInt(50);
        this.quadSize = 0.05f + random.nextFloat() * 0.04f;
        float shade = 0.8f + random.nextFloat() * 0.2f;
        setColor(0.95f * shade, (0.12f + random.nextFloat() * 0.1f) * shade, 0.08f * shade);
        this.alpha = 0.95f;
        this.spin = (random.nextFloat() - 0.5f) * 0.3f;
        this.phase = random.nextFloat() * 10.0f;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        this.oRoll = this.roll;
        this.roll += spin;
        // Fluttering side to side.
        this.xd += Mth.sin(age * 0.3f + phase) * 0.002;
        this.zd += Mth.cos(age * 0.27f + phase) * 0.002;
        float t = age / (float) lifetime;
        if (t > 0.75f) alpha = 0.95f * (1.0f - t) / 0.25f;
        if (onGround) remove();
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
            return new PoppyPetalParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
