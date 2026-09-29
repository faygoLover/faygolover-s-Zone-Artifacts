package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of the swamp ({@link Swamp}). Mobs are moved here ({@link SwampPhysics}); players move
 * themselves (their client does it), so for them the server only lets them through the blocks (or it
 * would pull them back out) and keeps track of how deep they are. For everyone: mud tires (hunger),
 * squelches now and then, and a head under the surface chokes ({@code damage} every {@code cooldown}).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class SwampEngine {

    private static final Map<LivingEntity, SwampPhysics.State> STATES = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> TICKED = new WeakHashMap<>();
    private static final Map<ServerLevel, List<SwampPhysics.Zone>> ZONES = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> ZONES_AT = new WeakHashMap<>();

    private SwampEngine() {
    }

    /** Called for each swamp by the anomaly engine: nothing to do per zone (it all happens per entity). */
    public static void tick(ServerLevel level, AnomalyInstance instance) {
    }

    private static List<SwampPhysics.Zone> zones(ServerLevel level) {
        long now = level.getGameTime();
        Long at = ZONES_AT.get(level);
        List<SwampPhysics.Zone> cached = ZONES.get(level);
        if (cached != null && at != null && at == now) return cached;
        List<SwampPhysics.Zone> list = new ArrayList<>();
        for (AnomalyInstance instance : AnomalySavedData.get(level).instances()) {
            if (!AnomalyTypeIds.SWAMP.equals(instance.typeId())) continue;
            list.add(new SwampPhysics.Zone(instance.pos(), instance.size(), instance.speed(), instance.damage(), instance.cooldownSeconds()));
        }
        ZONES.put(level, list);
        ZONES_AT.put(level, now);
        return list;
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level)) return;
        List<SwampPhysics.Zone> zones = zones(level);
        if (zones.isEmpty() && !STATES.containsKey(e)) return;

        SwampPhysics.State s;
        if (e instanceof Player) {
            // Its client moves it; here it only has to be let through the blocks.
            SwampPhysics.Zone zone = SwampPhysics.eligible(e) ? SwampPhysics.find(level, e, zones, STATES.get(e)) : null;
            s = STATES.get(e);
            if (zone == null) {
                if (s != null) {
                    SwampPhysics.release(e);
                    STATES.remove(e);
                }
                return;
            }
            if (s == null) {
                s = new SwampPhysics.State();
                STATES.put(e, s);
            }
            s.zone = zone;
            e.noPhysics = true;
            Swamp.Columns cols = Swamp.columns(level, zone.pos(), zone.size());
            s.depth = Math.max(0.0, cols.surface() - e.getY());
        } else {
            s = SwampPhysics.begin(e, zones, STATES);
            if (s == null) return;
            TICKED.put(e, level.getGameTime());
        }
        effects(level, e, s);
    }

    /** Mobs: our collision after everyone has moved (before their new positions go out). */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || STATES.isEmpty()) return;
        long now = level.getGameTime();
        for (Map.Entry<LivingEntity, SwampPhysics.State> entry : new ArrayList<>(STATES.entrySet())) {
            LivingEntity e = entry.getKey();
            if (e instanceof Player || e.level() != level) continue;
            if (!e.isAlive()) {
                STATES.remove(e);
                continue;
            }
            Long ticked = TICKED.get(e);
            if (ticked == null || ticked != now) continue;
            SwampPhysics.finish(e, entry.getValue());
        }
    }

    private static void effects(ServerLevel level, LivingEntity e, SwampPhysics.State s) {
        Swamp.Columns cols = Swamp.columns(level, s.zone.pos(), s.zone.size());
        double depth = Math.max(0.0, cols.surface() - e.getY());
        if (e instanceof Player player && depth > 0.2) {
            player.causeFoodExhaustion(0.01f * (float) Math.min(depth, 2.0));
        }
        if (depth > 0.15 && --s.soundTicks <= 0) {
            s.soundTicks = 40 + level.random.nextInt(40);
            AnomalyCombat.playSound(level, e.position(), Swamp.SQUELCH_SOUND, 0.6f, 0.8f + level.random.nextFloat() * 0.3f);
        }
        if (SwampPhysics.submerged(e, cols)) {
            s.submergedTicks++;
            int every = AnomalyDefaults.ticks(s.zone.cooldownSeconds());
            if (s.submergedTicks % every == 0 && !(e instanceof Player p && p.isCreative())) {
                e.invulnerableTime = 0;
                AnomalyCombat.hurt(level, e, Swamp.DAMAGE_TYPE, s.zone.damage());
            }
        } else {
            s.submergedTicks = 0;
        }
    }
}
