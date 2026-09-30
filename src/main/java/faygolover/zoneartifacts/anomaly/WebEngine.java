package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.SyncWebsPacket;
import faygolover.zoneartifacts.network.WebStrandPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * The Web, server side: a whole thread that touches living flesh cuts it (through any armour) and
 * snaps; it grows back after the web's regrow time. Nothing else sets it off (snowballs pass).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class WebEngine {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_web");
    public static final ResourceLocation SNAP_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "web_snap");

    private WebEngine() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        WebSavedData data = WebSavedData.get(level);
        if (data.webs().isEmpty()) return;
        long now = level.getGameTime();
        for (WebSavedData.Web web : data.webs()) {
            for (int i = 0; i < web.strands.size(); i++) {
                WebSavedData.Strand s = web.strands.get(i);
                if (s.brokenUntil > 0) {
                    if (now >= s.brokenUntil) {
                        s.brokenUntil = 0;
                        WebStrandPacket.send(level, web.id, i, false, s.a.add(s.b).scale(0.5));
                    }
                    continue;
                }
                if (!level.isLoaded(BlockPos.containing(s.a)) || !level.isLoaded(BlockPos.containing(s.b))) continue;
                AABB box = new AABB(s.a, s.b).inflate(0.05);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !AnomalyCombat.spectatorExempt(x))) {
                    if (AnomalyCombat.creativeExempt(e)) continue;
                    AABB body = e.getBoundingBox();
                    Optional<Vec3> cut = body.clip(s.a, s.b);
                    Vec3 at = cut.orElse(body.contains(s.a) ? s.a : body.contains(s.b) ? s.b : null);
                    if (at == null) continue;
                    e.invulnerableTime = 0;
                    AnomalyCombat.hurt(level, e, DAMAGE_TYPE, web.damage);
                    s.brokenUntil = now + AnomalyDefaults.ticks(web.regrowSeconds);
                    AnomalyCombat.playSound(level, at, SNAP_SOUND, 0.9f, 0.9f + level.random.nextFloat() * 0.3f);
                    WebStrandPacket.send(level, web.id, i, true, at);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SyncWebsPacket.sendTo(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SyncWebsPacket.sendTo(player);
    }
}
