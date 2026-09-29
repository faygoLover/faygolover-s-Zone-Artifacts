package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stage-1 stand-in for "this player is carrying an artifact that draws a Tesla's attention" — per
 * the design notes, set/cleared by a command for now (see {@code ModCommands}) rather than by any
 * real artifact item, since the artifact system itself hasn't been built yet. Stored directly on
 * the player's own persistent NBT ({@code Entity#getPersistentData()}, a Forge addition), which
 * survives logout/login like any other entity data. It does <em>not</em> survive death on its own
 * though — respawning builds a brand new {@link Player} instance, and persistent data isn't part
 * of what vanilla copies across that — so {@link #onClone} copies it over by hand, the normal
 * Forge hook for exactly this problem.
 */
public final class ArtifactFlag {

    private static final String KEY = "fl_zone_arts_artifact_equipped";

    public static boolean isEquipped(Player player) {
        return player.getPersistentData().getBoolean(KEY);
    }

    public static void setEquipped(Player player, boolean equipped) {
        player.getPersistentData().putBoolean(KEY, equipped);
    }

    private ArtifactFlag() {
    }

    @Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
    public static final class RespawnCopyListener {
        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            if (isEquipped(event.getOriginal())) {
                setEquipped(event.getEntity(), true);
            }
        }
    }
}
