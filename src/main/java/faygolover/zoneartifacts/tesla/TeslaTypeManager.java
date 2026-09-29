package faygolover.zoneartifacts.tesla;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.util.DatapackParseException;
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
 * Loads and validates {@code data/<namespace>/tesla_types/*.json} — the sibling loader
 * {@link faygolover.zoneartifacts.anomaly.AnomalyTypeManager}'s own javadoc anticipated, sharing
 * the same {@code util.JsonHelper}/{@code DatapackParseException} conventions for a differently
 * shaped config.
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
                    throw new DatapackParseException("<root>", "not a JSON object");
                }
                parsed.put(fileId, parse(fileId, entry.getValue().getAsJsonObject()));
            } catch (DatapackParseException e) {
                LOGGER.error("fl_zone_arts: skipping tesla type '{}' — invalid field '{}': {}",
                        fileId, e.field, e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.error("fl_zone_arts: skipping tesla type '{}' — failed to parse: {}", fileId, e.toString());
            }
        }

        TYPES = Map.copyOf(parsed);
        LOGGER.info("fl_zone_arts: loaded {} tesla type(s): {}", TYPES.size(), TYPES.keySet());
    }

    private static TeslaType parse(ResourceLocation id, JsonObject root) {
        int schemaVersion = getInt(root, "schema_version", -1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new DatapackParseException("schema_version",
                    "unsupported value " + schemaVersion + " (this build only understands " + SUPPORTED_SCHEMA_VERSION + ")");
        }

        ResourceLocation damageType = requireResourceLocation(root, "damage_type", "<root>");
        float damage = getFloat(root, "damage", 4.0f);
        float speed = getFloat(root, "speed", 0.2f);
        double detectRadius = root.has("detect_radius") ? root.get("detect_radius").getAsDouble() : 10.0;
        int respawnDelayTicks = getInt(root, "respawn_delay_ticks", 80);
        int growTicks = getInt(root, "grow_ticks", 20);
        int color = parseColor(root, "<root>");

        ResourceLocation idleSound = parseOptionalSound(root, "idle_sound", "<root>");
        float idleVolume = getFloat(root, "idle_volume", 1.0f);
        float idlePitch = getFloat(root, "idle_pitch", 1.0f);

        ResourceLocation livingHitSound = parseOptionalSound(root, "living_hit_sound", "<root>");

        List<ResourceLocation> hitSounds = new ArrayList<>();
        if (root.has("hit_sounds")) {
            if (!root.get("hit_sounds").isJsonArray()) {
                throw new DatapackParseException("hit_sounds", "must be an array of sound ids");
            }
            for (JsonElement el : root.getAsJsonArray("hit_sounds")) {
                ResourceLocation soundId = ResourceLocation.tryParse(el.getAsString());
                if (soundId == null) {
                    throw new DatapackParseException("hit_sounds", "not a valid resource location: '" + el.getAsString() + "'");
                }
                hitSounds.add(soundId);
            }
        }
        float hitSoundVolume = getFloat(root, "hit_sound_volume", 1.0f);
        float hitSoundPitch = getFloat(root, "hit_sound_pitch", 1.0f);

        int blockBurstRayCount = getInt(root, "block_burst_ray_count", 10);
        double blockBurstDistance = root.has("block_burst_distance") ? root.get("block_burst_distance").getAsDouble() : 2.5;

        return new TeslaType(id, damageType, damage, speed, detectRadius, respawnDelayTicks, growTicks, color,
                idleSound, idleVolume, idlePitch, livingHitSound, List.copyOf(hitSounds), hitSoundVolume, hitSoundPitch,
                blockBurstRayCount, blockBurstDistance);
    }
}
