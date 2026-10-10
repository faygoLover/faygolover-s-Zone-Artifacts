package faygolover.zoneartifacts.gear;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Which sound a bolt makes on which block: six groups (concrete, dirt, metal, water, wood, default).
 * The settings live in their own file, {@code config/fl_zone_arts-bolts.json}:
 * <ul>
 *     <li>{@code useScan} — use the scanned table (below); off: only the tag rules, straight from the blocks' tags;</li>
 *     <li>{@code scanOnStart} — at the next server start go through every block there is (other mods' too), sort each
 *     by its name ({@code nameRules}) or else its tags ({@code tagRules}) into a group, write the table and turn
 *     itself off (set it back, or {@code /fl_zone_arts bolts rescan}, to scan again);</li>
 *     <li>{@code blocks} — the table: block id → group, free to edit by hand.</li>
 * </ul>
 * A block not in the table (added later) goes by the tag rules, then "default". The lookup on a hit is
 * one map read; the scan is one pass over the block registry.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class BoltSounds {

    private static final Logger LOG = LoggerFactory.getLogger("fl_zone_arts/bolts");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum Group {
        CONCRETE(ModSounds.BOLT_HIT_CONCRETE), DIRT(ModSounds.BOLT_HIT_DIRT), METAL(ModSounds.BOLT_HIT_METAL),
        WATER(ModSounds.BOLT_HIT_WATER), WOOD(ModSounds.BOLT_HIT_WOOD), DEFAULT(ModSounds.BOLT_HIT_DEFAULT);

        private final Supplier<SoundEvent> sound;

        Group(Supplier<SoundEvent> sound) {
            this.sound = sound;
        }

        public SoundEvent sound() {
            return sound.get();
        }

        static Group of(String name) {
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return DEFAULT;
            }
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record TagRule(TagKey<Block> tag, Group group) {
    }

    private static boolean useScan = true;
    private static Map<ResourceLocation, Group> blocks = Map.of();
    /** Filled from the file at server start; until then (and if it has none) the defaults — built on first use,
     *  as the default table below is initialised after this field. */
    private static List<TagRule> tagRules;

    private BoltSounds() {
    }

    /** The group of the block a bolt struck. */
    public static Group group(BlockState state) {
        if (useScan) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            Group g = id != null ? blocks.get(id) : null;
            if (g != null) return g;
        }
        for (TagRule rule : rules()) {
            if (state.is(rule.tag())) return rule.group();
        }
        return Group.DEFAULT;
    }

    private static List<TagRule> rules() {
        if (tagRules == null) tagRules = defaultTagRules();
        return tagRules;
    }

    // ---- the file --------------------------------------------------------------------------------

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("fl_zone_arts-bolts.json");
    }

    /** Blocks' tags are known only once the server's data is loaded: then read (and maybe scan). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(false);
    }

    /** Reads the file (writing the defaults if there's none); scans if it says so, or {@code forceScan}. Returns blocks scanned. */
    public static int load(boolean forceScan) {
        JsonObject json = read();
        useScan = !json.has("useScan") || json.get("useScan").getAsBoolean();
        tagRules = parseTagRules(json);
        Map<String, List<String>> nameRules = parseNameRules(json);
        int scanned = 0;
        boolean scan = forceScan || !json.has("scanOnStart") || json.get("scanOnStart").getAsBoolean();
        if (scan) {
            JsonObject table = new JsonObject();
            for (Map.Entry<ResourceLocation, Group> e : scan(nameRules).entrySet()) table.addProperty(e.getKey().toString(), e.getValue().key());
            json.add("blocks", table);
            json.addProperty("scanOnStart", false);
            scanned = table.size();
            write(json);
            LOG.info("Bolt sounds: scanned {} blocks into {}", scanned, file().getFileName());
        }
        Map<ResourceLocation, Group> map = new HashMap<>();
        if (json.has("blocks")) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("blocks").entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (id != null) map.put(id, Group.of(e.getValue().getAsString()));
            }
        }
        blocks = map;
        return scanned;
    }

    private static JsonObject read() {
        Path path = file();
        if (Files.exists(path)) {
            try {
                return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                LOG.error("Bolt sounds: can't read {}, using the defaults: {}", path, e.toString());
                return defaults();
            }
        }
        JsonObject json = defaults();
        write(json);
        return json;
    }

    private static void write(JsonObject json) {
        try {
            Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Bolt sounds: can't write {}: {}", file(), e.toString());
        }
    }

    private static JsonObject defaults() {
        JsonObject json = new JsonObject();
        json.addProperty("_about", "Bolt / nut hit sounds. Groups: concrete, dirt, metal, water, wood, default. "
                + "useScan: use the 'blocks' table (off: only 'tagRules'). scanOnStart: at the next server start sort every block "
                + "by nameRules (words of its id) or else tagRules, write 'blocks' and turn itself off. "
                + "'blocks' can be edited by hand. /fl_zone_arts bolts rescan scans again.");
        json.addProperty("useScan", true);
        json.addProperty("scanOnStart", true);
        JsonObject names = new JsonObject();
        DEFAULT_NAMES.forEach((group, words) -> {
            JsonArray arr = new JsonArray();
            words.forEach(arr::add);
            names.add(group, arr);
        });
        json.add("nameRules", names);
        JsonArray tags = new JsonArray();
        for (String[] rule : DEFAULT_TAGS) {
            JsonObject r = new JsonObject();
            r.addProperty("tag", rule[0]);
            r.addProperty("group", rule[1]);
            tags.add(r);
        }
        json.add("tagRules", tags);
        return json;
    }

    // ---- rules -----------------------------------------------------------------------------------

    /** Words of block ids, by group, tried in this order (so "sandstone" is stone before "sand" is dirt). */
    private static final Map<String, List<String>> DEFAULT_NAMES = new LinkedHashMap<>();

    static {
        DEFAULT_NAMES.put("metal", List.of("netherite", "iron", "gold", "golden", "copper", "steel", "metal", "anvil", "chain", "bars",
                "lantern", "cauldron", "hopper", "rail", "bell", "tin", "bronze", "aluminum", "aluminium", "lead", "silver", "grate",
                "pipe", "machine", "girder"));
        DEFAULT_NAMES.put("concrete", List.of("sandstone", "glass", "pane", "ice", "concrete", "brick", "bricks", "stone", "cobblestone",
                "deepslate", "andesite", "diorite", "granite", "tuff", "basalt", "blackstone", "terracotta", "obsidian", "quartz",
                "prismarine", "purpur", "calcite", "tile", "tiles", "ore", "amethyst", "bedrock", "netherrack", "glazed", "slate",
                "marble", "asphalt", "cement", "rock"));
        DEFAULT_NAMES.put("wood", List.of("planks", "plank", "log", "wood", "stem", "hyphae", "bamboo", "barrel", "chest", "bookshelf",
                "ladder", "scaffolding", "sign", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "crimson",
                "warped", "lectern", "loom", "composter", "crate", "table", "shelf", "wooden"));
        DEFAULT_NAMES.put("dirt", List.of("dirt", "grass", "sand", "gravel", "mud", "soil", "clay", "farmland", "podzol", "mycelium", "moss",
                "snow", "wool", "carpet", "leaves", "hay", "sponge", "slime", "honey", "bed", "mushroom", "wart", "vine", "vines", "roots",
                "sculk", "powder", "cloth", "fabric", "cushion"));
    }

    /** Tag → group, first match wins; the broad tool tags last, as a fallback. */
    private static final String[][] DEFAULT_TAGS = {
            {"forge:storage_blocks/iron", "metal"}, {"forge:storage_blocks/gold", "metal"}, {"forge:storage_blocks/copper", "metal"},
            {"forge:storage_blocks/netherite", "metal"}, {"minecraft:anvil", "metal"}, {"minecraft:rails", "metal"},
            {"minecraft:cauldrons", "metal"},
            {"minecraft:logs", "wood"}, {"minecraft:planks", "wood"}, {"minecraft:wooden_slabs", "wood"}, {"minecraft:wooden_stairs", "wood"},
            {"minecraft:wooden_doors", "wood"}, {"minecraft:wooden_trapdoors", "wood"}, {"minecraft:wooden_fences", "wood"},
            {"minecraft:fence_gates", "wood"}, {"minecraft:signs", "wood"}, {"minecraft:all_hanging_signs", "wood"},
            {"forge:chests/wooden", "wood"}, {"forge:barrels/wooden", "wood"}, {"minecraft:bamboo_blocks", "wood"},
            {"minecraft:dirt", "dirt"}, {"minecraft:sand", "dirt"}, {"minecraft:wool", "dirt"}, {"minecraft:wool_carpets", "dirt"},
            {"minecraft:leaves", "dirt"}, {"minecraft:snow", "dirt"}, {"minecraft:beds", "dirt"}, {"forge:gravel", "dirt"},
            {"forge:sand", "dirt"},
            {"minecraft:base_stone_overworld", "concrete"}, {"minecraft:base_stone_nether", "concrete"}, {"forge:stone", "concrete"},
            {"forge:cobblestone", "concrete"}, {"minecraft:stone_bricks", "concrete"}, {"forge:glass", "concrete"},
            {"forge:glass_panes", "concrete"}, {"minecraft:ice", "concrete"}, {"minecraft:terracotta", "concrete"},
            {"forge:sandstone", "concrete"}, {"forge:ores", "concrete"},
            {"minecraft:mineable/shovel", "dirt"}, {"minecraft:mineable/hoe", "dirt"}, {"minecraft:mineable/axe", "wood"},
            {"minecraft:mineable/pickaxe", "concrete"},
    };

    private static List<TagRule> defaultTagRules() {
        List<TagRule> out = new ArrayList<>();
        for (String[] r : DEFAULT_TAGS) out.add(new TagRule(TagKey.create(Registries.BLOCK, new ResourceLocation(r[0])), Group.of(r[1])));
        return out;
    }

    private static List<TagRule> parseTagRules(JsonObject json) {
        if (!json.has("tagRules")) return defaultTagRules();
        List<TagRule> out = new ArrayList<>();
        for (com.google.gson.JsonElement e : json.getAsJsonArray("tagRules")) {
            JsonObject r = e.getAsJsonObject();
            ResourceLocation id = ResourceLocation.tryParse(r.get("tag").getAsString().replace("#", ""));
            if (id != null) out.add(new TagRule(TagKey.create(Registries.BLOCK, id), Group.of(r.get("group").getAsString())));
        }
        return out;
    }

    private static Map<String, List<String>> parseNameRules(JsonObject json) {
        if (!json.has("nameRules")) return DEFAULT_NAMES;
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("nameRules").entrySet()) {
            List<String> words = new ArrayList<>();
            e.getValue().getAsJsonArray().forEach(w -> words.add(w.getAsString().toLowerCase(Locale.ROOT)));
            out.put(e.getKey(), words);
        }
        return out;
    }

    /** Every block there is: by the words of its id, else its tags, else default. */
    private static Map<ResourceLocation, Group> scan(Map<String, List<String>> nameRules) {
        Map<ResourceLocation, Group> out = new TreeMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> e : ForgeRegistries.BLOCKS.getEntries()) {
            ResourceLocation id = e.getKey().location();
            Block block = e.getValue();
            if (block.defaultBlockState().isAir()) continue;
            Group g = byName(id.getPath(), nameRules);
            if (g == null) {
                BlockState state = block.defaultBlockState();
                for (TagRule rule : rules()) {
                    if (state.is(rule.tag())) {
                        g = rule.group();
                        break;
                    }
                }
            }
            out.put(id, g != null ? g : Group.DEFAULT);
        }
        return out;
    }

    /** From the last word of the id back (the thing itself comes last: oak_LEAVES, gold_ORE, iron_BARS). */
    private static Group byName(String path, Map<String, List<String>> nameRules) {
        String[] words = path.toLowerCase(Locale.ROOT).split("[_/.\\-]");
        for (int i = words.length - 1; i >= 0; i--) {
            String w = words[i];
            for (Map.Entry<String, List<String>> rule : nameRules.entrySet()) {
                for (String kw : rule.getValue()) {
                    if (w.equals(kw) || w.equals(kw + "s") || w.equals(kw + "es")) return Group.of(rule.getKey());
                }
            }
        }
        return null;
    }
}
