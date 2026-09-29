package faygolover.zoneartifacts.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyVisualSound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and validates {@code data/<namespace>/tesla_types/*.json} — Tesla's counterpart to {@code
 * AnomalyTypeManager}. Same "one bad file never aborts the reload" policy: a malformed entry is
 * logged with its resource location and offending field, then skipped.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaTypeManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "tesla_types";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static Map<ResourceLocation, TeslaType> TYPES = Map.of();

    public TeslaTypeManager() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TeslaTypeManager());
    }

    @Nullable
    public static TeslaType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static Map<ResourceLocation, TeslaType> all() {
        return TYPES;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, TeslaType> parsed = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) {
                    throw new TeslaTypeParseException("<root>", "not a JSON object");
                }
                parsed.put(fileId, parse(fileId, entry.getValue().getAsJsonObject()));
            } catch (TeslaTypeParseException e) {
                LOGGER.error("fl_zone_arts: skipping tesla type '{}' — invalid field '{}': {}",
                        fileId, e.field, e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.error("fl_zone_arts: skipping tesla type '{}' — failed to parse: {}", fileId, e.toString());
            }
        }

        TYPES = Map.copyOf(parsed);
        LOGGER.info("fl_zone_arts: loaded {} tesla type(s): {}", TYPES.size(), TYPES.keySet());
    }

    // ---- parsing ---------------------------------------------------------

    private static TeslaType parse(ResourceLocation id, JsonObject root) {
        int schemaVersion = getInt(root, "schema_version", -1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new TeslaTypeParseException("schema_version",
                    "unsupported value " + schemaVersion + " (this build only understands " + SUPPORTED_SCHEMA_VERSION + ")");
        }

        double speed = getDouble(root, "speed", 0.2);
        if (speed <= 0) {
            throw new TeslaTypeParseException("speed", "must be > 0, got " + speed);
        }
        double aggroRadius = getDouble(root, "aggro_radius", 10.0);
        if (aggroRadius < 0) {
            throw new TeslaTypeParseException("aggro_radius", "must be >= 0, got " + aggroRadius);
        }

        String damageTypeStr = getString(root, "damage_type", "damage_type");
        ResourceLocation damageType = ResourceLocation.tryParse(damageTypeStr);
        if (damageType == null) {
            throw new TeslaTypeParseException("damage_type", "not a valid resource location: '" + damageTypeStr + "'");
        }
        float damage = (float) getDouble(root, "damage", 4.0);

        int respawnTicks = getInt(root, "respawn_ticks", 80);
        if (respawnTicks < 1) {
            throw new TeslaTypeParseException("respawn_ticks", "must be >= 1, got " + respawnTicks);
        }
        int growTicks = getInt(root, "grow_ticks", 20);
        if (growTicks < 0) {
            throw new TeslaTypeParseException("grow_ticks", "must be >= 0, got " + growTicks);
        }
        int electrifyTicks = getInt(root, "electrify_ticks", 20);
        if (electrifyTicks < 0) {
            throw new TeslaTypeParseException("electrify_ticks", "must be >= 0, got " + electrifyTicks);
        }

        TeslaArcVisual arc = parseArc(getObject(root, "arc"));
        TeslaBumpVisual bump = parseBump(getObject(root, "bump"));
        AnomalyVisualSound idle = parseIdle(getObject(root, "idle"));
        TeslaImpactSound impact = parseImpact(getObject(root, "impact"));

        return new TeslaType(id, speed, aggroRadius, damageType, damage, respawnTicks, growTicks, electrifyTicks,
                arc, bump, idle, impact);
    }

    private static TeslaArcVisual parseArc(JsonObject obj) {
        String path = "arc";
        int bundleCount = getInt(obj, "bundle_count", 5);
        if (bundleCount < 1) {
            throw new TeslaTypeParseException(path + ".bundle_count", "must be >= 1, got " + bundleCount);
        }
        int pointsPerBundle = getInt(obj, "points_per_bundle", 5);
        if (pointsPerBundle < 3) {
            // Tesla's bundles are closed loops (no dangling ends) — that needs at least 3 points
            // to actually look like a loop rather than a single line doubled back on itself.
            throw new TeslaTypeParseException(path + ".points_per_bundle", "must be >= 3 (bundles are closed loops), got " + pointsPerBundle);
        }
        int minLifetimeTicks = getInt(obj, "min_lifetime_ticks", 3);
        int maxLifetimeTicks = getInt(obj, "max_lifetime_ticks", 6);
        if (minLifetimeTicks < 1 || maxLifetimeTicks < minLifetimeTicks) {
            throw new TeslaTypeParseException(path + ".min_lifetime_ticks",
                    "must have 1 <= min_lifetime_ticks <= max_lifetime_ticks, got min=" + minLifetimeTicks + " max=" + maxLifetimeTicks);
        }
        String colorStr = obj.has("color") ? obj.get("color").getAsString() : "B8E8FF";
        int color;
        try {
            color = Integer.parseInt(colorStr, 16);
        } catch (NumberFormatException e) {
            throw new TeslaTypeParseException(path + ".color", "not a valid hex RGB color (e.g. \"B8E8FF\"): '" + colorStr + "'");
        }
        double radius = getDouble(obj, "radius", 0.4);
        if (radius <= 0) {
            throw new TeslaTypeParseException(path + ".radius", "must be > 0, got " + radius);
        }
        return new TeslaArcVisual(bundleCount, pointsPerBundle, minLifetimeTicks, maxLifetimeTicks, color, radius);
    }

    private static TeslaBumpVisual parseBump(JsonObject obj) {
        String path = "bump";
        int boltCount = getInt(obj, "bolt_count", 6);
        if (boltCount < 1) {
            throw new TeslaTypeParseException(path + ".bolt_count", "must be >= 1, got " + boltCount);
        }
        double reach = getDouble(obj, "reach", 2.5);
        if (reach <= 0) {
            throw new TeslaTypeParseException(path + ".reach", "must be > 0, got " + reach);
        }
        int durationTicks = getInt(obj, "duration_ticks", 6);
        if (durationTicks < 1) {
            throw new TeslaTypeParseException(path + ".duration_ticks", "must be >= 1, got " + durationTicks);
        }
        return new TeslaBumpVisual(boltCount, reach, durationTicks);
    }

    private static AnomalyVisualSound parseIdle(JsonObject obj) {
        ResourceLocation sound = null;
        if (obj.has("sound")) {
            sound = ResourceLocation.tryParse(obj.get("sound").getAsString());
            if (sound == null) {
                throw new TeslaTypeParseException("idle.sound", "not a valid resource location");
            }
        }
        float volume = obj.has("sound_volume") ? obj.get("sound_volume").getAsFloat() : 1.0f;
        float pitch = obj.has("sound_pitch") ? obj.get("sound_pitch").getAsFloat() : 1.0f;
        return new AnomalyVisualSound(sound, volume, pitch);
    }

    private static TeslaImpactSound parseImpact(JsonObject obj) {
        String path = "impact";
        ResourceLocation blastSound = null;
        if (obj.has("blast_sound")) {
            blastSound = ResourceLocation.tryParse(obj.get("blast_sound").getAsString());
            if (blastSound == null) {
                throw new TeslaTypeParseException(path + ".blast_sound", "not a valid resource location");
            }
        }
        float blastVolume = obj.has("blast_volume") ? obj.get("blast_volume").getAsFloat() : 1.0f;
        float blastPitch = obj.has("blast_pitch") ? obj.get("blast_pitch").getAsFloat() : 1.0f;

        List<ResourceLocation> hitSounds = new ArrayList<>();
        if (obj.has("hit_sounds")) {
            if (!obj.get("hit_sounds").isJsonArray()) {
                throw new TeslaTypeParseException(path + ".hit_sounds", "must be an array of sound ids");
            }
            JsonArray arr = obj.getAsJsonArray("hit_sounds");
            for (JsonElement el : arr) {
                ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
                if (id == null) {
                    throw new TeslaTypeParseException(path + ".hit_sounds", "not a valid resource location: '" + el.getAsString() + "'");
                }
                hitSounds.add(id);
            }
        }
        float hitVolume = obj.has("hit_volume") ? obj.get("hit_volume").getAsFloat() : 1.0f;
        float hitPitch = obj.has("hit_pitch") ? obj.get("hit_pitch").getAsFloat() : 1.0f;

        return new TeslaImpactSound(blastSound, blastVolume, blastPitch, List.copyOf(hitSounds), hitVolume, hitPitch);
    }

    // ---- small JSON helpers with clear "file + field" errors --------------

    private static JsonObject getObject(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonObject()) {
            throw new TeslaTypeParseException(field, "missing or not an object");
        }
        return root.getAsJsonObject(field);
    }

    private static String getString(JsonObject obj, String field, String path) {
        if (!obj.has(field) || !obj.get(field).isJsonPrimitive()) {
            throw new TeslaTypeParseException(path, "missing string field");
        }
        return obj.get(field).getAsString();
    }

    private static int getInt(JsonObject obj, String field, int def) {
        return obj.has(field) ? obj.get(field).getAsInt() : def;
    }

    private static double getDouble(JsonObject obj, String field, double def) {
        return obj.has(field) ? obj.get(field).getAsDouble() : def;
    }

    private static final class TeslaTypeParseException extends RuntimeException {
        final String field;

        TeslaTypeParseException(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}
