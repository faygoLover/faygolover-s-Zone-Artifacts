package faygolover.zoneartifacts.tesla;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/fl_zone_arts/tesla/tesla.json}. Kept separate from {@code AnomalyTypeManager}:
 * the Tesla isn't a volumetric zone, so its file doesn't fit the anomaly-type schema at all.
 * <p>
 * Same error policy as the anomaly loader: a bad field is logged with the file and field name, and
 * the whole Tesla config falls back to {@link TeslaConfig#DEFAULTS} rather than breaking the reload.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaConfigManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "tesla";
    private static final ResourceLocation FILE_ID = new ResourceLocation(ZoneArtifacts.MODID, "tesla");
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static TeslaConfig current = TeslaConfig.DEFAULTS;

    public TeslaConfigManager() {
        super(new Gson(), DIRECTORY);
    }

    public static TeslaConfig get() {
        return current;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TeslaConfigManager());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        JsonElement element = resources.get(FILE_ID);
        if (element == null) {
            LOGGER.warn("fl_zone_arts: no {} found, Tesla uses built-in defaults", FILE_ID);
            current = TeslaConfig.DEFAULTS;
            return;
        }
        try {
            if (!element.isJsonObject()) throw new ParseException("<root>", "not a JSON object");
            current = parse(element.getAsJsonObject());
            LOGGER.info("fl_zone_arts: loaded Tesla config {}", current);
        } catch (ParseException e) {
            LOGGER.error("fl_zone_arts: Tesla config '{}' — invalid field '{}': {}. Using defaults.", FILE_ID, e.field, e.getMessage());
            current = TeslaConfig.DEFAULTS;
        } catch (RuntimeException e) {
            LOGGER.error("fl_zone_arts: Tesla config '{}' failed to parse: {}. Using defaults.", FILE_ID, e.toString());
            current = TeslaConfig.DEFAULTS;
        }
    }

    private static TeslaConfig parse(JsonObject root) {
        TeslaConfig d = TeslaConfig.DEFAULTS;

        int schema = root.has("schema_version") ? root.get("schema_version").getAsInt() : -1;
        if (schema != SUPPORTED_SCHEMA_VERSION) {
            throw new ParseException("schema_version", "unsupported value " + schema + " (expected " + SUPPORTED_SCHEMA_VERSION + ")");
        }

        double speed = getDouble(root, "speed", d.speed());
        if (speed <= 0 || speed > 2) throw new ParseException("speed", "must be in (0, 2] blocks per tick, got " + speed);

        float damage = (float) getDouble(root, "damage", d.damage());
        if (damage < 0) throw new ParseException("damage", "must be >= 0, got " + damage);

        double chaseRadius = getDouble(root, "chase_radius", d.chaseRadius());
        if (chaseRadius < 0) throw new ParseException("chase_radius", "must be >= 0, got " + chaseRadius);

        int respawn = getInt(root, "respawn_delay_ticks", d.respawnDelayTicks());
        int grow = getInt(root, "spawn_grow_ticks", d.spawnGrowTicks());
        int electrify = getInt(root, "electrify_ticks", d.electrifyTicks());
        if (respawn < 1) throw new ParseException("respawn_delay_ticks", "must be >= 1, got " + respawn);
        if (grow < 1) throw new ParseException("spawn_grow_ticks", "must be >= 1, got " + grow);
        if (electrify < 1) throw new ParseException("electrify_ticks", "must be >= 1, got " + electrify);

        List<ResourceLocation> hitSounds = new ArrayList<>();
        if (root.has("hit_sounds")) {
            if (!root.get("hit_sounds").isJsonArray()) throw new ParseException("hit_sounds", "must be an array of sound ids");
            JsonArray arr = root.getAsJsonArray("hit_sounds");
            for (JsonElement el : arr) {
                hitSounds.add(parseId(el.getAsString(), "hit_sounds"));
            }
        } else {
            hitSounds.addAll(d.hitSounds());
        }

        return new TeslaConfig(
                speed,
                damage,
                getId(root, "damage_type", d.damageType()),
                chaseRadius,
                respawn,
                grow,
                electrify,
                getId(root, "idle_sound", d.idleSound()),
                (float) getDouble(root, "idle_volume", d.idleVolume()),
                (float) getDouble(root, "idle_pitch", d.idlePitch()),
                getId(root, "contact_sound", d.contactSound()),
                getId(root, "block_sound", d.blockSound()),
                List.copyOf(hitSounds),
                (float) getDouble(root, "sound_volume", d.soundVolume())
        );
    }

    private static double getDouble(JsonObject obj, String field, double def) {
        return obj.has(field) ? obj.get(field).getAsDouble() : def;
    }

    private static int getInt(JsonObject obj, String field, int def) {
        return obj.has(field) ? obj.get(field).getAsInt() : def;
    }

    private static ResourceLocation getId(JsonObject obj, String field, ResourceLocation def) {
        return obj.has(field) ? parseId(obj.get(field).getAsString(), field) : def;
    }

    private static ResourceLocation parseId(String raw, String field) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new ParseException(field, "not a valid resource location: '" + raw + "'");
        return id;
    }

    private static final class ParseException extends RuntimeException {
        final String field;

        ParseException(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}
