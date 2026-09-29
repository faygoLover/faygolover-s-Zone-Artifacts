package faygolover.zoneartifacts.client.psi;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.fx.HumLoop;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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

/**
 * The psi zone on its player: nothing to see from outside. Inside, after half a second the effect
 * builds up over another half second (and on leaving, half a second later it ebbs over half a
 * second): the legs grow heavy, a hum fills the head and whispers come from close by, and the sight
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
    private static int nextWhisper = 60;
    @Nullable
    private static HumLoop hum;

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
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(world.dimension())) {
            if (!AnomalyTypeIds.PSI.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            if (!zone.contains(at)) continue;
            inside = true;
            scale = Mth.clamp(entry.intensity() / 3.0f, 0.3f, 1.6f);
            waveSeconds = entry.cooldown() > 0.0f ? entry.cooldown() : ModCommonConfig.PSI_WAVE_SECONDS.get().floatValue();
            break;
        }
        if (mc.player.isSpectator() || mc.player.isCreative()) inside = false;
        if (inside) {
            insideTicks++;
            outsideTicks = 0;
            if (insideTicks > DELAY) level = Math.min(1.0f, level + RAMP);
        } else {
            outsideTicks++;
            insideTicks = 0;
            if (outsideTicks > DELAY) level = Math.max(0.0f, level - RAMP);
        }

        if (level > 0.02f && (hum == null || hum.isStopped())) {
            hum = new HumLoop(ModSounds.PSI_HUM.get(), () -> 0.55 * Math.min(1.0f, level * scale));
            mc.getSoundManager().play(hum);
        }
        if (level > 0.6f) {
            if (--nextWhisper <= 0) {
                nextWhisper = 70 + RANDOM.nextInt(110);
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                Vec3 eye = mc.player.getEyePosition();
                Vec3 p = eye.add(Math.cos(a) * 1.8, (RANDOM.nextDouble() - 0.5) * 0.8, Math.sin(a) * 1.8);
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.PSI_WHISPER.get(), SoundSource.AMBIENT,
                        0.45f + 0.3f * RANDOM.nextFloat(), 0.85f + RANDOM.nextFloat() * 0.3f, RANDOM, p.x, p.y, p.z));
            }
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
