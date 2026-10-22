package faygolover.zoneartifacts.client.fx;

import com.mojang.logging.LogUtils;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.dymka.DymkaClient;
import faygolover.zoneartifacts.client.sumrak.SumrakClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * Embeddium (Sodium) skips the chunks past the end of the fog ("fog occlusion"). The Haze and the
 * Dusk pull the fog in to a few blocks, so on the way out the world came back in square chunks as
 * the fog drew off. While either thickens the fog, that skipping is off; afterwards it is put back
 * as it was. Without Embeddium nothing happens.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FogCulling {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static Object performance;
    @Nullable
    private static Field useFogOcclusion;
    private static boolean probed;
    /** Turned off by us; the player's own value to put back. */
    private static boolean overridden;
    private static boolean saved;

    private FogCulling() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean thick = Minecraft.getInstance().level != null && (DymkaClient.inside(1.0f) > 0.0f || SumrakClient.inside(1.0f) > 0.0f);
        if (thick == overridden) return;
        if (!probe()) return;
        try {
            if (thick) {
                saved = useFogOcclusion.getBoolean(performance);
                useFogOcclusion.setBoolean(performance, false);
            } else {
                useFogOcclusion.setBoolean(performance, saved);
            }
            overridden = thick;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Zone Artifacts: can't switch Embeddium's fog occlusion", e);
            useFogOcclusion = null;
        }
    }

    private static boolean probe() {
        if (!probed) {
            probed = true;
            try {
                Class<?> mod = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
                Object options = mod.getMethod("options").invoke(null);
                performance = options.getClass().getField("performance").get(options);
                useFogOcclusion = performance.getClass().getField("useFogOcclusion");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // No Embeddium / Sodium.
            }
        }
        return useFogOcclusion != null && performance != null;
    }
}
