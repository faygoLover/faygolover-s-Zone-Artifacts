package faygolover.zoneartifacts.client.pukh;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/** Burning Fluff's bits: a dark flake drifting down, swaying — or a pale, sickly spore in a puff. */
public class PukhParticle extends TextureSheetParticle {

    private final boolean spore;
    private final float swayPhase;

    protected PukhParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean spore) {
        super(level, x, y, z);
        this.spore = spore;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = true;
        this.swayPhase = random.nextFloat() * 6.28f;
        if (spore) {
            this.gravity = 0.02f;
            this.friction = 0.9f;
            this.lifetime = 25 + random.nextInt(20);
            this.quadSize = 0.03f + random.nextFloat() * 0.03f;
            float s = 0.85f + random.nextFloat() * 0.15f;
            setColor(0.62f * s, 0.66f * s, 0.46f * s);
        } else {
            this.gravity = 0.015f;
            this.friction = 0.96f;
            this.lifetime = 80 + random.nextInt(60);
            this.quadSize = 0.035f + random.nextFloat() * 0.035f;
            float s = 0.8f + random.nextFloat() * 0.2f;
            setColor(0.17f * s, 0.16f * s, 0.12f * s);
        }
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (!spore && !onGround) {
            // Flutters down.
            xd += Mth.sin(age * 0.2f + swayPhase) * 0.002;
            zd += Mth.cos(age * 0.17f + swayPhase) * 0.002;
        }
        float t = age / (float) lifetime;
        alpha = t < 0.75f ? 1.0f : (1.0f - t) / 0.25f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final boolean spore;

        public Provider(SpriteSet sprites, boolean spore) {
            this.sprites = sprites;
            this.spore = spore;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new PukhParticle(level, x, y, z, vx, vy, vz, sprites, spore);
        }
    }
}
