package faygolover.zoneartifacts.client.psi;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import faygolover.zoneartifacts.client.distortion.Distortion;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The psi zone on its player: nothing to see from outside. Inside, after half a second the effect
 * builds up over another half second (and on leaving, half a second later it ebbs over half a
 * second): the legs grow heavy, voices fill the head (one in each ear; now and then something else
 * instead), and the sight
 * swims — wobbling, colours coming apart, in waves every {@code cooldown} seconds smearing
 * altogether ({@code ScreenFx}). Harmless.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PsiClient {

    private static final int DELAY = 10;
    private static final float RAMP = 0.1f;
    private static final RandomSource RANDOM = RandomSource.create();

    private static float level;
    private static float prevLevel;
    private static int insideTicks;
    private static int outsideTicks;
    private static float scale = 1.0f;
    private static float waveSeconds = 6.0f;
    /** Now and then the voices give way to something else close by. */
    private static final float POLTER_CHANCE = 0.2f;
    private static int nextVoices = 0;
    @Nullable
    private static Voice left;
    @Nullable
    private static Voice right;
    @Nullable
    private static Voice polter;

    private PsiClient() {
    }

    /** 0..~1.5: how strongly the zone works on the senses right now. */
    public static float strength(float partial) {
        return Mth.lerp(partial, prevLevel, level) * scale;
    }

    /** 0..1: the current wave of the worst swimming. */
    public static float wave(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0f;
        float period = Math.max(20.0f, waveSeconds * 20.0f);
        float t = ((mc.level.getGameTime() % 72000L) + partial) % period;
        float len = Math.min(60.0f, period * 0.6f);
        if (t > len) return 0.0f;
        float s = Mth.sin((float) Math.PI * t / len);
        return s * s;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        prevLevel = level;
        if (world == null || mc.player == null) {
            level = 0.0f;
            insideTicks = 0;
            return;
        }
        if (mc.isPaused()) return;
        Vec3 at = mc.player.getBoundingBox().getCenter();
        boolean inside = false;
        boolean gm = mc.player.isSpectator() || mc.player.isCreative();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(world.dimension())) {
            if (!AnomalyTypeIds.PSI.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.box(entry);
            if (!zone.contains(at) || (gm && !entry.targetsGm())) continue;
            inside = true;
            scale = Mth.clamp(entry.intensity() / 3.0f, 0.3f, 1.6f);
            waveSeconds = entry.cooldown() > 0.0f ? entry.cooldown() : ModCommonConfig.PSI_WAVE_SECONDS.get().floatValue();
            break;
        }
        if (inside) {
            insideTicks++;
            outsideTicks = 0;
            if (insideTicks > DELAY) level = Math.min(1.0f, level + RAMP);
        } else {
            outsideTicks++;
            insideTicks = 0;
            if (outsideTicks > DELAY) level = Math.max(0.0f, level - RAMP);
        }

        // Voices in the head: the left channel in the left ear, the right in the right; after each
        // round a short pause, and now and then something else instead.
        if (level > 0.02f) {
            SoundManager sounds = mc.getSoundManager();
            boolean playing = active(sounds, left) || active(sounds, right) || active(sounds, polter);
            if (!playing && --nextVoices <= 0) {
                if (RANDOM.nextFloat() < POLTER_CHANCE) {
                    polter = new Voice(ModSounds.PSI_POLTER.get(), 0.0);
                    sounds.play(polter);
                } else {
                    left = new Voice(ModSounds.PSI_VOICES_L.get(), -1.5);
                    right = new Voice(ModSounds.PSI_VOICES_R.get(), 1.5);
                    sounds.play(left);
                    sounds.play(right);
                }
                nextVoices = 20 + RANDOM.nextInt(80);
            }
        } else {
            nextVoices = 0;
        }
    }

    private static boolean active(SoundManager sounds, @Nullable Voice v) {
        return v != null && !v.isStopped() && sounds.isActive(v);
    }

    /** From outside one can't see the zone itself — only the air through it swims a little, colours apart. */
    public static void collect(List<Distortion.Patch> out, long now, float partial, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = (now % 72000L) + partial;
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(mc.level.dimension())) {
            if (!AnomalyTypeIds.PSI.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.box(entry);
            if (zone.contains(cam)) continue;
            float k = Mth.clamp(entry.intensity() / 3.0f, 0.3f, 1.6f);
            Vec3 base = new Vec3((zone.minX + zone.maxX) * 0.5, zone.minY, (zone.minZ + zone.maxZ) * 0.5);
            double width = Math.max(zone.getXsize(), zone.getZsize());
            out.add(new Distortion.Haze(base, width, zone.getYsize(), 0.02 + 0.015 * k, time * 0.012, 1.3, true, 0.9f,
                    entry.pos().hashCode() * 0.01).chroma(0.0025 * k));
        }
    }

    /** A voice heard inside the head, from one side ({@code side} &lt; 0 left, &gt; 0 right, 0 middle):
     *  its loudness follows the zone's hold on the senses; it falls silent and stops once that's gone. */
    private static final class Voice extends AbstractTickableSoundInstance {
        private int silent;

        Voice(SoundEvent sound, double side) {
            super(sound, SoundSource.AMBIENT, RandomSource.create());
            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = side;
            this.y = 0.0;
            this.z = 0.0;
            this.volume = 0.0f;
        }

        @Override
        public void tick() {
            float want = 0.9f * Math.min(1.0f, level * scale);
            this.volume += (want - this.volume) * 0.08f;
            if (want <= 0.001f && this.volume < 0.01f) {
                if (++silent > 20) stop();
            } else {
                silent = 0;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** Heavy legs. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (level <= 0.0f) return;
        float slow = (float) Mth.clamp(ModCommonConfig.PSI_SLOWDOWN.get() * scale * level, 0.0, 0.9);
        Input input = event.getInput();
        input.forwardImpulse *= 1.0f - slow;
        input.leftImpulse *= 1.0f - slow;
    }
}
