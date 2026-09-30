package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.PoppyPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of the poppy field. Every living thing in it gets a micro-sleep every {@code cooldown}
 * seconds (its eyes close, and for {@code poppyField.episodeSeconds} it walks off somewhere); the
 * count ebbs away outside just as fast. At {@code intensity} (the effects tuner) episodes it falls
 * asleep for good: lies down, and after {@code fullSleepSeconds} takes {@code damage} (then again, as
 * long as it sleeps on). A hit from anyone takes 2 episodes off a sleeper — it wakes and gets up.
 * <p>
 * Players' eyes, legs and hearing are their client's ({@link PoppyPacket}); mobs are steered here
 * (their goals switched off meanwhile).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class PoppyEngine {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_sleep");
    /** Eyelids closing before a micro-sleep's walk, and opening after it, ticks. */
    public static final int CLOSE_TICKS = 24;
    public static final int OPEN_TICKS = 14;
    public static final int WAKE_TICKS = 24;

    public enum Phase { NONE, EPISODE, ASLEEP, WAKING }

    private static final class State {
        int count;
        int timer;
        long lastInside = -1;
        double interval = 12.0;
        int max = 4;
        float damage = 1000.0f;
        Phase phase = Phase.NONE;
        int phaseTicks;
        int phaseTotal;
        int sleepTicks;
        Vec3 wander = Vec3.ZERO;
        boolean mobControlled;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    private PoppyEngine() {
    }

    /** Called per field by the anomaly engine: who is in it now. */
    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        long now = level.getGameTime();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, PoppyEngine::affected)) {
            State s = STATES.computeIfAbsent(e, k -> new State());
            s.lastInside = now;
            s.interval = instance.cooldownSeconds();
            s.max = Math.max(1, instance.intensity());
            s.damage = instance.damage();
        }
    }

    private static boolean affected(LivingEntity e) {
        if (!e.isAlive() || AnomalyCombat.spectatorExempt(e)) return false;
        if (AnomalyCombat.creativeExempt(e)) return false;
        return e instanceof Player || e instanceof Mob;
    }

    public static boolean asleep(LivingEntity e) {
        State s = STATES.get(e);
        return s != null && s.phase == Phase.ASLEEP;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || STATES.isEmpty()) return;
        long now = level.getGameTime();
        for (Map.Entry<LivingEntity, State> entry : new ArrayList<>(STATES.entrySet())) {
            LivingEntity e = entry.getKey();
            State s = entry.getValue();
            if (e.level() != level) continue;
            if (!e.isAlive()) {
                release(e, s);
                STATES.remove(e);
                continue;
            }
            boolean inside = s.lastInside == now;
            int every = AnomalyDefaults.ticks(s.interval);
            // The count: up inside, down outside, at the same pace.
            if (++s.timer >= every) {
                s.timer = 0;
                if (inside && s.phase != Phase.ASLEEP) {
                    s.count++;
                    if (s.count >= s.max) sleep(level, e, s);
                    else if (s.phase == Phase.NONE) episode(level, e, s);
                } else if (!inside) {
                    s.count = Math.max(0, s.count - 1);
                }
            }
            switch (s.phase) {
                case EPISODE -> {
                    if (--s.phaseTicks <= 0) set(level, e, s, Phase.NONE, 0);
                    else if (e instanceof Mob mob) steer(mob, s);
                }
                case ASLEEP -> {
                    if (e instanceof Mob mob) hold(mob, s);
                    if (s.count < s.max) {
                        set(level, e, s, Phase.WAKING, WAKE_TICKS);
                    } else if (++s.sleepTicks >= AnomalyDefaults.ticks(ModCommonConfig.POPPY_FULL_SLEEP_SECONDS.get())) {
                        s.sleepTicks = 0;
                        e.invulnerableTime = 0;
                        AnomalyCombat.hurt(level, e, DAMAGE_TYPE, s.damage);
                    }
                }
                case WAKING -> {
                    if (e instanceof Mob mob) hold(mob, s);
                    if (--s.phaseTicks <= 0) set(level, e, s, Phase.NONE, 0);
                }
                default -> {
                }
            }
            if (s.phase == Phase.NONE) {
                release(e, s);
                if (s.count == 0 && !inside) STATES.remove(e);
            }
        }
    }

    private static void episode(ServerLevel level, LivingEntity e, State s) {
        int walk = AnomalyDefaults.ticks(ModCommonConfig.POPPY_EPISODE_SECONDS.get());
        double a = level.random.nextDouble() * Math.PI * 2.0;
        s.wander = e.position().add(Math.cos(a) * 8.0, 0.0, Math.sin(a) * 8.0);
        set(level, e, s, Phase.EPISODE, CLOSE_TICKS + walk + OPEN_TICKS);
    }

    private static void sleep(ServerLevel level, LivingEntity e, State s) {
        s.sleepTicks = 0;
        set(level, e, s, Phase.ASLEEP, 0);
    }

    private static void set(ServerLevel level, LivingEntity e, State s, Phase phase, int ticks) {
        s.phase = phase;
        s.phaseTicks = ticks;
        s.phaseTotal = ticks;
        PoppyPacket.send(e, phase, ticks);
    }

    /** A mob in a micro-sleep: its own will off, walking off somewhere (after its eyes closed). */
    private static void steer(Mob mob, State s) {
        control(mob, s);
        int elapsed = s.phaseTotal - s.phaseTicks;
        if (elapsed >= CLOSE_TICKS && s.phaseTicks > OPEN_TICKS) {
            mob.getMoveControl().setWantedPosition(s.wander.x, mob.getY(), s.wander.z, 0.8);
        } else {
            mob.getNavigation().stop();
        }
    }

    /** A sleeping mob: lies still. */
    private static void hold(Mob mob, State s) {
        control(mob, s);
        mob.getNavigation().stop();
        mob.setZza(0.0f);
        mob.setXxa(0.0f);
        mob.setJumping(false);
    }

    private static void control(Mob mob, State s) {
        if (s.mobControlled) return;
        s.mobControlled = true;
        mob.setTarget(null);
        mob.getNavigation().stop();
        for (Goal.Flag flag : Goal.Flag.values()) {
            mob.goalSelector.disableControlFlag(flag);
            mob.targetSelector.disableControlFlag(flag);
        }
    }

    private static void release(LivingEntity e, State s) {
        if (!s.mobControlled || !(e instanceof Mob mob)) return;
        s.mobControlled = false;
        for (Goal.Flag flag : Goal.Flag.values()) {
            mob.goalSelector.enableControlFlag(flag);
            mob.targetSelector.enableControlFlag(flag);
        }
    }

    /** A hit wakes a sleeper: two episodes off. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel)) return;
        State s = STATES.get(e);
        if (s == null || s.phase != Phase.ASLEEP) return;
        DamageSource source = event.getSource();
        if (source.getEntity() == null && source.getDirectEntity() == null) return;
        s.count = Math.max(0, s.count - 2);
    }

    /** Someone comes into view of a sleeper: lay it down for them too. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity e) || !(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATES.get(e);
        if (s != null && s.phase == Phase.ASLEEP) PoppyPacket.sendTo(player, e, Phase.ASLEEP, 0);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        State s = STATES.remove(e);
        if (s != null && s.phase != Phase.NONE && e.level() instanceof ServerLevel) {
            release(e, s);
            PoppyPacket.send(e, Phase.NONE, 0);
        }
    }
}
