package faygolover.zoneartifacts.client.thermal;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * The thermal anomalies' slow, long-lived particles. The spawn velocity is used as given (no vanilla
 * randomisation) and kept almost constant, so a spark lifted off a surface keeps drifting the way it
 * was sent: barely moving while the anomaly is idle, rising fast while it's active.
 */
public class ThermalParticle extends TextureSheetParticle {

    public enum Kind {
        /** Tiny glowing spark: full-bright, flickers, fades in and out. */
        EMBER,
        /** Small dark wisp: grows a little and thins out as it rises. */
        HEAT_SMOKE,
        /** Pale cold haze: large, faint, creeps sideways and slightly down. */
        FROST_MIST,
        /** Gravitational anomalies: short-lived dusty speck, flies exactly where it's sent. */
        DUST
    }

    private final Kind kind;
    private final SpriteSet sprites;
    private final float baseAlpha;
    private final float baseSize;
    private final float phase;

    protected ThermalParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              Kind kind, SpriteSet sprites) {
        super(level, x, y, z);
        this.kind = kind;
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.0f;
        this.friction = 1.0f;
        this.hasPhysics = false;
        this.phase = random.nextFloat() * (float) (Math.PI * 2.0);

        switch (kind) {
            case EMBER -> {
                this.lifetime = 70 + random.nextInt(90);
                this.baseSize = 0.025f + random.nextFloat() * 0.03f;
                this.baseAlpha = 0.95f;
                float heat = random.nextFloat();
                setColor(1.0f, 0.45f + heat * 0.35f, 0.08f + heat * 0.18f);
                pickSprite(sprites);
            }
            case HEAT_SMOKE -> {
                this.lifetime = 90 + random.nextInt(90);
                this.baseSize = 0.07f + random.nextFloat() * 0.07f;
                this.baseAlpha = 0.45f;
                float shade = 0.18f + random.nextFloat() * 0.14f;
                setColor(shade, shade * 0.95f, shade * 0.9f);
                setSpriteFromAge(sprites);
            }
            case DUST -> {
                this.lifetime = 22 + random.nextInt(18);
                this.baseSize = 0.04f + random.nextFloat() * 0.06f;
                this.baseAlpha = 0.55f;
                float shade = 0.8f + random.nextFloat() * 0.25f;
                setColor(0.6f * shade, 0.54f * shade, 0.44f * shade);
                setSpriteFromAge(sprites);
            }
            default -> {
                this.lifetime = 60 + random.nextInt(70);
                this.baseSize = 0.16f + random.nextFloat() * 0.16f;
                this.baseAlpha = 0.28f;
                float tint = random.nextFloat() * 0.08f;
                setColor(0.82f + tint, 0.92f + tint * 0.5f, 1.0f);
                pickSprite(sprites);
            }
        }
        this.quadSize = baseSize;
        this.alpha = 0.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;

        // A little lazy wobble so they don't move in dead-straight lines.
        float wobble = kind == Kind.FROST_MIST ? 0.0006f : 0.0004f;
        xd += (random.nextFloat() - 0.5f) * wobble;
        zd += (random.nextFloat() - 0.5f) * wobble;
        xd *= 0.99;
        zd *= 0.99;

        float t = age / (float) lifetime;
        float fadeIn = kind == Kind.DUST ? 4.0f : 12.0f;
        float fade = Math.min(1.0f, age / fadeIn) * Math.min(1.0f, (1.0f - t) * 4.0f);
        switch (kind) {
            case EMBER -> {
                float flicker = 0.75f + 0.25f * Mth.sin(age * 0.45f + phase);
                alpha = baseAlpha * fade * flicker;
            }
            case HEAT_SMOKE, DUST -> {
                setSpriteFromAge(sprites);
                alpha = baseAlpha * fade * (1.0f - t * 0.5f);
            }
            default -> alpha = baseAlpha * fade;
        }
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float t = (age + partialTicks) / lifetime;
        return switch (kind) {
            case EMBER -> baseSize * (1.0f - t * 0.4f);
            case HEAT_SMOKE -> baseSize * (1.0f + t * 1.5f);
            case DUST -> baseSize * (1.0f + t * 0.3f);
            default -> baseSize * (1.0f + t * 0.8f);
        };
    }

    @Override
    protected int getLightColor(float partialTicks) {
        if (kind == Kind.EMBER) return 0xF000F0; // full-bright
        if (kind == Kind.FROST_MIST) {
            // Slightly self-lit, so the haze still reads as pale at night.
            int base = super.getLightColor(partialTicks);
            int block = Math.max(base & 0xFF, 0x90);
            return (base & 0xFFFF0000) | block;
        }
        return super.getLightColor(partialTicks);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ThermalParticle(level, x, y, z, vx, vy, vz, kind, sprites);
        }
    }
}
