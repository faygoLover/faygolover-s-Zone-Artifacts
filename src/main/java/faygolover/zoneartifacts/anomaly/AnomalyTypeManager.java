package faygolover.zoneartifacts.anomaly;

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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and validates {@code data/<namespace>/anomaly_types/*.json}.
 * <p>
 * A malformed file is logged with its resource location and the offending field, then skipped —
 * one bad file never aborts the whole datapack reload, so the rest of the pack still works.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalyTypeManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "anomaly_types";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static Map<ResourceLocation, AnomalyType> TYPES = Map.of();

    public AnomalyTypeManager() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AnomalyTypeManager());
    }

    @Nullable
    public static AnomalyType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static Map<ResourceLocation, AnomalyType> all() {
        return TYPES;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, AnomalyType> parsed = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) {
                    throw new AnomalyTypeParseException("<root>", "not a JSON object");
                }
                parsed.put(fileId, parse(fileId, entry.getValue().getAsJsonObject()));
            } catch (AnomalyTypeParseException e) {
                LOGGER.error("fl_zone_arts: skipping anomaly type '{}' — invalid field '{}': {}",
                        fileId, e.field, e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.error("fl_zone_arts: skipping anomaly type '{}' — failed to parse: {}", fileId, e.toString());
            }
        }

        TYPES = Map.copyOf(parsed);
        LOGGER.info("fl_zone_arts: loaded {} anomaly type(s): {}", TYPES.size(), TYPES.keySet());
    }

    // ---- parsing ---------------------------------------------------------

    private static AnomalyType parse(ResourceLocation id, JsonObject root) {
        int schemaVersion = getInt(root, "schema_version", -1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new AnomalyTypeParseException("schema_version",
                    "unsupported value " + schemaVersion + " (this build only understands " + SUPPORTED_SCHEMA_VERSION + ")");
        }

        AnomalyShape shape = parseShape(getObject(root, "shape"));
        AnomalyTrigger trigger = parseTrigger(getObject(root, "trigger"));
        AnomalyDetect detect = root.has("detect") ? parseDetect(root.getAsJsonObject("detect")) : new AnomalyDetect(true, true, false);
        AnomalyEffect effect = parseEffect(getObject(root, "effect"), shape.maxLevel());
        AnomalyVisualSound ambient = root.has("ambient") ? parseVisualSound(root.getAsJsonObject("ambient"), "ambient") : null;
        AnomalyArcEffect arc = root.has("arc") ? parseArcEffect(root.getAsJsonObject("arc")) : null;
        AnomalyTriggerEffect triggerEffect = root.has("trigger_effect") ? parseTriggerEffect(root.getAsJsonObject("trigger_effect")) : null;

        return new AnomalyType(id, shape, trigger, detect, effect, ambient, arc, triggerEffect);
    }

    private static AnomalyShape parseShape(JsonObject shapeObj) {
        String typeStr = getString(shapeObj, "type", "shape.type");
        AnomalyShape.ShapeType type = switch (typeStr) {
            case "cube" -> AnomalyShape.ShapeType.CUBE;
            default -> throw new AnomalyTypeParseException("shape.type", "unknown shape type '" + typeStr + "' (only 'cube' is supported so far)");
        };

        if (!shapeObj.has("sizes_by_level") || !shapeObj.get("sizes_by_level").isJsonArray()) {
            throw new AnomalyTypeParseException("shape.sizes_by_level", "missing or not an array (one block size per level, e.g. [1, 2, 3])");
        }
        JsonArray sizesArr = shapeObj.getAsJsonArray("sizes_by_level");
        if (sizesArr.isEmpty()) {
            throw new AnomalyTypeParseException("shape.sizes_by_level", "must have at least one entry");
        }
        List<Integer> sizes = new ArrayList<>();
        for (JsonElement el : sizesArr) {
            int size = el.getAsInt();
            if (size < 1) {
                throw new AnomalyTypeParseException("shape.sizes_by_level", "sizes must be >= 1, got " + size);
            }
            sizes.add(size);
        }
        return new AnomalyShape(type, List.copyOf(sizes));
    }

    private static AnomalyTrigger parseTrigger(JsonObject triggerObj) {
        String typeStr = getString(triggerObj, "type", "trigger.type");
        AnomalyTrigger.TriggerType type = switch (typeStr) {
            case "burst" -> AnomalyTrigger.TriggerType.BURST;
            case "passive_field" -> AnomalyTrigger.TriggerType.PASSIVE_FIELD;
            case "phased" -> AnomalyTrigger.TriggerType.PHASED;
            default -> throw new AnomalyTypeParseException("trigger.type", "unknown trigger type '" + typeStr + "'");
        };
        int cooldown = getInt(triggerObj, "cooldown_ticks", 20);
        if (cooldown < 0) {
            throw new AnomalyTypeParseException("trigger.cooldown_ticks", "must be >= 0, got " + cooldown);
        }
        return new AnomalyTrigger(type, cooldown);
    }

    private static AnomalyDetect parseDetect(JsonObject detectObj) {
        boolean players = getBool(detectObj, "players", true);
        boolean mobs = getBool(detectObj, "mobs", true);
        boolean thrown = getBool(detectObj, "thrown_projectiles", false);
        return new AnomalyDetect(players, mobs, thrown);
    }

    private static AnomalyEffect parseEffect(JsonObject effectObj, int maxLevel) {
        String damageTypeStr = getString(effectObj, "damage_type", "effect.damage_type");
        ResourceLocation damageType = ResourceLocation.tryParse(damageTypeStr);
        if (damageType == null) {
            throw new AnomalyTypeParseException("effect.damage_type", "not a valid resource location: '" + damageTypeStr + "'");
        }

        if (!effectObj.has("damage_by_level") || !effectObj.get("damage_by_level").isJsonArray()) {
            throw new AnomalyTypeParseException("effect.damage_by_level", "missing or not an array (one value per level)");
        }
        JsonArray dmgArr = effectObj.getAsJsonArray("damage_by_level");
        List<Float> damages = new ArrayList<>();
        for (JsonElement el : dmgArr) {
            damages.add(el.getAsFloat());
        }
        if (damages.size() != maxLevel) {
            throw new AnomalyTypeParseException("effect.damage_by_level",
                    "must have exactly " + maxLevel + " entries to match shape.sizes_by_level, got " + damages.size());
        }
        return new AnomalyEffect(damageType, List.copyOf(damages));
    }

    private static AnomalyVisualSound parseVisualSound(JsonObject obj, String path) {
        ResourceLocation sound = null;
        if (obj.has("sound")) {
            sound = ResourceLocation.tryParse(obj.get("sound").getAsString());
            if (sound == null) {
                throw new AnomalyTypeParseException(path + ".sound", "not a valid resource location");
            }
        }
        float soundVolume = obj.has("sound_volume") ? obj.get("sound_volume").getAsFloat() : 1.0f;
        float soundPitch = obj.has("sound_pitch") ? obj.get("sound_pitch").getAsFloat() : 1.0f;

        return new AnomalyVisualSound(sound, soundVolume, soundPitch);
    }

    private static AnomalyArcEffect parseArcEffect(JsonObject obj) {
        String path = "arc";
        int bundleCount = getInt(obj, "bundle_count", 3);
        if (bundleCount < 1) {
            throw new AnomalyTypeParseException(path + ".bundle_count", "must be >= 1, got " + bundleCount);
        }
        int pointsPerBundle = getInt(obj, "points_per_bundle", 4);
        if (pointsPerBundle < 2) {
            throw new AnomalyTypeParseException(path + ".points_per_bundle", "must be >= 2 (need at least 2 points to draw an arc), got " + pointsPerBundle);
        }
        int minLifetimeTicks = getInt(obj, "min_lifetime_ticks", 15);
        int maxLifetimeTicks = getInt(obj, "max_lifetime_ticks", 25);
        if (minLifetimeTicks < 1 || maxLifetimeTicks < minLifetimeTicks) {
            throw new AnomalyTypeParseException(path + ".min_lifetime_ticks",
                    "must have 1 <= min_lifetime_ticks <= max_lifetime_ticks, got min=" + minLifetimeTicks + " max=" + maxLifetimeTicks);
        }
        String colorStr = obj.has("color") ? obj.get("color").getAsString() : "B8E8FF";
        int color;
        try {
            color = Integer.parseInt(colorStr, 16);
        } catch (NumberFormatException e) {
            throw new AnomalyTypeParseException(path + ".color", "not a valid hex RGB color (e.g. \"B8E8FF\"): '" + colorStr + "'");
        }
        return new AnomalyArcEffect(bundleCount, pointsPerBundle, minLifetimeTicks, maxLifetimeTicks, color);
    }

    private static AnomalyTriggerEffect parseTriggerEffect(JsonObject obj) {
        String path = "trigger_effect";

        ResourceLocation particle = null;
        if (obj.has("particle")) {
            particle = ResourceLocation.tryParse(obj.get("particle").getAsString());
            if (particle == null) {
                throw new AnomalyTypeParseException(path + ".particle", "not a valid resource location");
            }
        }
        int particleCount = getInt(obj, "particle_count", 1);

        ResourceLocation livingSound = parseOptionalSound(obj, "living_sound", path);
        ResourceLocation projectileSound = parseOptionalSound(obj, "projectile_sound", path);
        float soundVolume = obj.has("sound_volume") ? obj.get("sound_volume").getAsFloat() : 1.0f;
        float soundPitch = obj.has("sound_pitch") ? obj.get("sound_pitch").getAsFloat() : 1.0f;

        List<ResourceLocation> hitSounds = new ArrayList<>();
        if (obj.has("hit_sounds")) {
            if (!obj.get("hit_sounds").isJsonArray()) {
                throw new AnomalyTypeParseException(path + ".hit_sounds", "must be an array of sound ids");
            }
            for (JsonElement el : obj.getAsJsonArray("hit_sounds")) {
                ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
                if (id == null) {
                    throw new AnomalyTypeParseException(path + ".hit_sounds", "not a valid resource location: '" + el.getAsString() + "'");
                }
                hitSounds.add(id);
            }
        }
        float hitSoundVolume = obj.has("hit_sound_volume") ? obj.get("hit_sound_volume").getAsFloat() : 1.0f;
        float hitSoundPitch = obj.has("hit_sound_pitch") ? obj.get("hit_sound_pitch").getAsFloat() : 1.0f;

        return new AnomalyTriggerEffect(particle, particleCount, livingSound, projectileSound,
                soundVolume, soundPitch, List.copyOf(hitSounds), hitSoundVolume, hitSoundPitch);
    }

    @Nullable
    private static ResourceLocation parseOptionalSound(JsonObject obj, String field, String path) {
        if (!obj.has(field)) return null;
        ResourceLocation id = ResourceLocation.tryParse(obj.get(field).getAsString());
        if (id == null) {
            throw new AnomalyTypeParseException(path + "." + field, "not a valid resource location");
        }
        return id;
    }

    // ---- small JSON helpers with clear "file + field" errors --------------

    private static JsonObject getObject(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonObject()) {
            throw new AnomalyTypeParseException(field, "missing or not an object");
        }
        return root.getAsJsonObject(field);
    }

    private static String getString(JsonObject obj, String field, String path) {
        if (!obj.has(field) || !obj.get(field).isJsonPrimitive()) {
            throw new AnomalyTypeParseException(path, "missing string field");
        }
        return obj.get(field).getAsString();
    }

    private static int getInt(JsonObject obj, String field, int def) {
        return obj.has(field) ? obj.get(field).getAsInt() : def;
    }

    private static boolean getBool(JsonObject obj, String field, boolean def) {
        return obj.has(field) ? obj.get(field).getAsBoolean() : def;
    }

    private static final class AnomalyTypeParseException extends RuntimeException {
        final String field;

        AnomalyTypeParseException(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}
