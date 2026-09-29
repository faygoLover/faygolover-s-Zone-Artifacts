package faygolover.zoneartifacts.anomaly;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.util.DatapackParseException;
import faygolover.zoneartifacts.util.JsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static faygolover.zoneartifacts.util.JsonHelper.*;

/**
 * Loads and validates {@code data/<namespace>/anomaly_types/*.json}.
 * <p>
 * A malformed file is logged with its resource location and the offending field, then skipped —
 * one bad file never aborts the whole datapack reload, so the rest of the pack still works. See
 * {@code TeslaTypeManager} for the sibling loader that shares these JSON conventions (via {@link
 * JsonHelper}) for Tesla's own, differently-shaped config.
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
                    throw new DatapackParseException("<root>", "not a JSON object");
                }
                parsed.put(fileId, parse(fileId, entry.getValue().getAsJsonObject()));
            } catch (DatapackParseException e) {
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
            throw new DatapackParseException("schema_version",
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
            default -> throw new DatapackParseException("shape.type", "unknown shape type '" + typeStr + "' (only 'cube' is supported so far)");
        };

        if (!shapeObj.has("sizes_by_level") || !shapeObj.get("sizes_by_level").isJsonArray()) {
            throw new DatapackParseException("shape.sizes_by_level", "missing or not an array (one block size per level, e.g. [1, 2, 3])");
        }
        JsonArray sizesArr = shapeObj.getAsJsonArray("sizes_by_level");
        if (sizesArr.isEmpty()) {
            throw new DatapackParseException("shape.sizes_by_level", "must have at least one entry");
        }
        List<Integer> sizes = new ArrayList<>();
        for (JsonElement el : sizesArr) {
            int size = el.getAsInt();
            if (size < 1) {
                throw new DatapackParseException("shape.sizes_by_level", "sizes must be >= 1, got " + size);
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
            default -> throw new DatapackParseException("trigger.type", "unknown trigger type '" + typeStr + "'");
        };
        int cooldown = getInt(triggerObj, "cooldown_ticks", 20);
        if (cooldown < 0) {
            throw new DatapackParseException("trigger.cooldown_ticks", "must be >= 0, got " + cooldown);
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
        ResourceLocation damageType = requireResourceLocation(effectObj, "damage_type", "effect");

        if (!effectObj.has("damage_by_level") || !effectObj.get("damage_by_level").isJsonArray()) {
            throw new DatapackParseException("effect.damage_by_level", "missing or not an array (one value per level)");
        }
        JsonArray dmgArr = effectObj.getAsJsonArray("damage_by_level");
        List<Float> damages = new ArrayList<>();
        for (JsonElement el : dmgArr) {
            damages.add(el.getAsFloat());
        }
        if (damages.size() != maxLevel) {
            throw new DatapackParseException("effect.damage_by_level",
                    "must have exactly " + maxLevel + " entries to match shape.sizes_by_level, got " + damages.size());
        }
        return new AnomalyEffect(damageType, List.copyOf(damages));
    }

    private static AnomalyVisualSound parseVisualSound(JsonObject obj, String path) {
        ResourceLocation sound = parseOptionalSound(obj, "sound", path);
        float soundVolume = getFloat(obj, "sound_volume", 1.0f);
        float soundPitch = getFloat(obj, "sound_pitch", 1.0f);
        return new AnomalyVisualSound(sound, soundVolume, soundPitch);
    }

    private static AnomalyArcEffect parseArcEffect(JsonObject obj) {
        String path = "arc";
        int bundleCount = getInt(obj, "bundle_count", 3);
        if (bundleCount < 1) {
            throw new DatapackParseException(path + ".bundle_count", "must be >= 1, got " + bundleCount);
        }
        int pointsPerBundle = getInt(obj, "points_per_bundle", 4);
        if (pointsPerBundle < 2) {
            throw new DatapackParseException(path + ".points_per_bundle", "must be >= 2 (need at least 2 points to draw an arc), got " + pointsPerBundle);
        }
        int minLifetimeTicks = getInt(obj, "min_lifetime_ticks", 15);
        int maxLifetimeTicks = getInt(obj, "max_lifetime_ticks", 25);
        if (minLifetimeTicks < 1 || maxLifetimeTicks < minLifetimeTicks) {
            throw new DatapackParseException(path + ".min_lifetime_ticks",
                    "must have 1 <= min_lifetime_ticks <= max_lifetime_ticks, got min=" + minLifetimeTicks + " max=" + maxLifetimeTicks);
        }
        int color = parseColor(obj, path);
        return new AnomalyArcEffect(bundleCount, pointsPerBundle, minLifetimeTicks, maxLifetimeTicks, color);
    }

    private static AnomalyTriggerEffect parseTriggerEffect(JsonObject obj) {
        String path = "trigger_effect";

        ResourceLocation livingSound = parseOptionalSound(obj, "living_sound", path);
        ResourceLocation projectileSound = parseOptionalSound(obj, "projectile_sound", path);
        float soundVolume = getFloat(obj, "sound_volume", 1.0f);
        float soundPitch = getFloat(obj, "sound_pitch", 1.0f);

        List<ResourceLocation> hitSounds = new ArrayList<>();
        if (obj.has("hit_sounds")) {
            if (!obj.get("hit_sounds").isJsonArray()) {
                throw new DatapackParseException(path + ".hit_sounds", "must be an array of sound ids");
            }
            for (JsonElement el : obj.getAsJsonArray("hit_sounds")) {
                ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
                if (id == null) {
                    throw new DatapackParseException(path + ".hit_sounds", "not a valid resource location: '" + el.getAsString() + "'");
                }
                hitSounds.add(id);
            }
        }
        float hitSoundVolume = getFloat(obj, "hit_sound_volume", 1.0f);
        float hitSoundPitch = getFloat(obj, "hit_sound_pitch", 1.0f);

        return new AnomalyTriggerEffect(livingSound, projectileSound,
                soundVolume, soundPitch, List.copyOf(hitSounds), hitSoundVolume, hitSoundPitch);
    }
}
