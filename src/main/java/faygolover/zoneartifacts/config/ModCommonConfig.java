package faygolover.zoneartifacts.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * {@code config/fl_zone_arts-common.toml} — global settings, read by the server.
 * <p>
 * Replaces the old anomaly datapacks: each anomaly now keeps its own settings, changed in game
 * with the tuners, and this file only holds the <em>defaults</em> a newly placed anomaly starts
 * with, plus the tuners' upper limits. Changing a default here does not touch anomalies already
 * placed — they keep whatever they were tuned to.
 */
public final class ModCommonConfig {

    public static final ForgeConfigSpec SPEC;

    // limits
    public static final ForgeConfigSpec.DoubleValue MAX_SIZE;
    public static final ForgeConfigSpec.DoubleValue MAX_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue MAX_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MAX_CHASE_RADIUS;

    // electra
    public static final ForgeConfigSpec.IntValue ELECTRA_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ELECTRA_DAMAGE;
    public static final ForgeConfigSpec.IntValue ELECTRA_INTENSITY;

    // zharka / iney
    public static final ForgeConfigSpec.DoubleValue ZHARKA_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ZHARKA_DAMAGE;
    public static final ForgeConfigSpec.IntValue ZHARKA_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue ZHARKA_IGNITE_CHANCE;
    public static final ForgeConfigSpec.IntValue ZHARKA_IGNITE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ZHARKA_ALTERS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue INEY_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue INEY_DAMAGE;
    public static final ForgeConfigSpec.IntValue INEY_INTENSITY;
    public static final ForgeConfigSpec.IntValue INEY_FREEZE_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue INEY_ALTERS_BLOCKS;
    public static final ForgeConfigSpec.IntValue THERMAL_BLOCK_RADIUS;

    // tesla
    public static final ForgeConfigSpec.DoubleValue TESLA_BASE_SPEED;
    public static final ForgeConfigSpec.IntValue TESLA_RESPAWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TESLA_DAMAGE;
    public static final ForgeConfigSpec.IntValue TESLA_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue TESLA_CHASE_RADIUS;

    // comet
    public static final ForgeConfigSpec.DoubleValue COMET_BASE_SPEED;
    public static final ForgeConfigSpec.IntValue COMET_RESPAWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue COMET_DAMAGE;
    public static final ForgeConfigSpec.IntValue COMET_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue COMET_CHASE_RADIUS;
    public static final ForgeConfigSpec.IntValue COMET_IGNITE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue COMET_IGNITES_BLOCKS;

    // cold comet
    public static final ForgeConfigSpec.DoubleValue COLD_COMET_BASE_SPEED;
    public static final ForgeConfigSpec.IntValue COLD_COMET_RESPAWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue COLD_COMET_DAMAGE;
    public static final ForgeConfigSpec.IntValue COLD_COMET_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue COLD_COMET_CHASE_RADIUS;
    public static final ForgeConfigSpec.IntValue COLD_COMET_FREEZE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue COLD_COMET_ALTERS_BLOCKS;

    // cold razlom
    public static final ForgeConfigSpec.IntValue COLD_RAZLOM_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue COLD_RAZLOM_DAMAGE;
    public static final ForgeConfigSpec.IntValue COLD_RAZLOM_INTENSITY;
    public static final ForgeConfigSpec.IntValue COLD_RAZLOM_FREEZE_SECONDS;

    // razlom
    public static final ForgeConfigSpec.IntValue RAZLOM_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RAZLOM_DAMAGE;
    public static final ForgeConfigSpec.IntValue RAZLOM_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue RAZLOM_JET_SECONDS;
    public static final ForgeConfigSpec.IntValue RAZLOM_HIT_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue RAZLOM_JET_RANGE;
    public static final ForgeConfigSpec.IntValue RAZLOM_IGNITE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RAZLOM_BLOCK_IGNITE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RAZLOM_AIM_SPEED;

    // effects
    public static final ForgeConfigSpec.DoubleValue ELECTRIFY_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Upper limits of the tuners (the lower limits are fixed: size 1, speed x0, cooldown 1 s",
                        "(Zharka/Iney damage interval 0.1 s), intensity 1, damage 0, targeting distance 0).")
                .push("limits");
        MAX_SIZE = b.comment("Max anomaly size in blocks (size 1 = one block).")
                .defineInRange("maxSize", 10.0, 1.0, 64.0);
        MAX_SPEED_MULTIPLIER = b.comment("Max speed multiplier for moving anomalies (1.0 = standard speed).")
                .defineInRange("maxSpeedMultiplier", 10.0, 0.0, 100.0);
        MAX_COOLDOWN_SECONDS = b.comment("Max cooldown in seconds (for the Tesla: its respawn delay).")
                .defineInRange("maxCooldownSeconds", 60, 1, 3600);
        MAX_INTENSITY = b.comment("Max visual intensity (number of arcs/loops the anomaly draws).")
                .defineInRange("maxIntensity", 10, 1, 50);
        MAX_DAMAGE = b.comment("Max damage per hit, in half-hearts (2 = one heart).")
                .defineInRange("maxDamage", 40.0, 0.0, 1000.0);
        MAX_CHASE_RADIUS = b.comment("Max targeting distance of homing anomalies (the Tesla's chase radius), blocks.")
                .defineInRange("maxTargetingDistance", 64.0, 0.0, 256.0);
        b.pop();

        b.comment("Defaults for a newly placed Electra. Its size always starts at 1.").push("electra");
        ELECTRA_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 5, 1, 3600);
        ELECTRA_DAMAGE = b.defineInRange("damage", 3.0, 0.0, 1000.0);
        ELECTRA_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        b.pop();

        b.comment("Defaults for a newly placed Zharka (heat). Its 'cooldown' is the damage interval:",
                "while anyone is inside, everyone inside takes damage once per interval.").push("zharka");
        ZHARKA_INTERVAL_SECONDS = b.comment("Seconds, may be below 1 (min 0.1): hits more often, set the damage lower then.")
                .defineInRange("intervalSeconds", 1.0, 0.1, 3600.0);
        ZHARKA_DAMAGE = b.defineInRange("damage", 2.0, 0.0, 1000.0);
        ZHARKA_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        ZHARKA_IGNITE_CHANCE = b.comment("Chance (0..1) that a damage pulse also sets the victim on fire.")
                .defineInRange("igniteChance", 0.25, 0.0, 1.0);
        ZHARKA_IGNITE_SECONDS = b.comment("How long a victim burns when set on fire, seconds.")
                .defineInRange("igniteSeconds", 3, 1, 60);
        ZHARKA_ALTERS_BLOCKS = b.comment("While active, burn flammable blocks, melt ice and snow, evaporate water, dry out grass and mud.")
                .define("altersBlocks", true);
        b.pop();

        b.comment("Defaults for a newly placed Iney (frost). Its 'cooldown' is the damage interval.").push("iney");
        INEY_INTERVAL_SECONDS = b.comment("Seconds, may be below 1 (min 0.1).")
                .defineInRange("intervalSeconds", 1.0, 0.1, 3600.0);
        INEY_DAMAGE = b.defineInRange("damage", 1.0, 0.0, 1000.0);
        INEY_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        INEY_FREEZE_PER_TICK = b.comment("Vanilla freezing ticks added per tick inside (vanilla thaws 2 per tick,",
                        "full freeze is 140): 4 means fully frozen in about 3.5 s.")
                .defineInRange("freezePerTick", 4, 3, 50);
        INEY_ALTERS_BLOCKS = b.comment("While active, freeze water (and lava), put out fires and campfires, kill leaves and plants, lay snow.")
                .define("altersBlocks", true);
        b.pop();

        b.push("thermal");
        THERMAL_BLOCK_RADIUS = b.comment("How far beyond the zone Zharka and Iney alter blocks, blocks.")
                .defineInRange("blockRadius", 2, 0, 16);
        b.pop();

        b.comment("Tesla settings. A new route starts with size 1 and speed x1.0.").push("tesla");
        TESLA_BASE_SPEED = b.comment("Standard speed (x1.0) in blocks per tick.")
                .defineInRange("baseSpeed", 0.12, 0.01, 2.0);
        TESLA_RESPAWN_SECONDS = b.comment("Default respawn delay of a new route, seconds.")
                .defineInRange("respawnSeconds", 4, 1, 3600);
        TESLA_DAMAGE = b.defineInRange("damage", 3.0, 0.0, 1000.0);
        TESLA_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        TESLA_CHASE_RADIUS = b.comment("Default targeting distance of a new route: radius in which a player flagged",
                        "artifact_equipped is chased, blocks. Changed per route with the targeting tuner.")
                .defineInRange("chaseRadius", 10.0, 0.0, 128.0);
        b.pop();

        b.comment("Comet settings (a fireball on a route, like the Tesla). A new route starts with size 1 and speed x1.0.").push("comet");
        COMET_BASE_SPEED = b.comment("Standard speed (x1.0) in blocks per tick.")
                .defineInRange("baseSpeed", 0.15, 0.01, 2.0);
        COMET_RESPAWN_SECONDS = b.comment("Default respawn delay of a new route, seconds.")
                .defineInRange("respawnSeconds", 6, 1, 3600);
        COMET_DAMAGE = b.comment("Explosion damage at the center (and to whatever it flew into), half-hearts.",
                        "Falls off to 30 % at the edge of the blast.")
                .defineInRange("damage", 6.0, 0.0, 1000.0);
        COMET_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        COMET_CHASE_RADIUS = b.comment("Default targeting distance of a new route, blocks.")
                .defineInRange("chaseRadius", 12.0, 0.0, 128.0);
        COMET_IGNITE_SECONDS = b.comment("How long entities caught by the explosion burn, seconds.")
                .defineInRange("igniteSeconds", 4, 0, 60);
        COMET_IGNITES_BLOCKS = b.comment("The explosion sets fire around the impact (never breaks blocks).")
                .define("ignitesBlocks", true);
        b.pop();

        b.comment("Cold Comet: the Comet in soul fire. Freezes instead of burning (vanilla freezing + Slowness II),",
                "and instead of fires leaves ice, snow and put-out flames around the impact.").push("coldComet");
        COLD_COMET_BASE_SPEED = b.comment("Standard speed (x1.0) in blocks per tick.")
                .defineInRange("baseSpeed", 0.15, 0.01, 2.0);
        COLD_COMET_RESPAWN_SECONDS = b.defineInRange("respawnSeconds", 6, 1, 3600);
        COLD_COMET_DAMAGE = b.comment("Explosion damage at the center, half-hearts (30 % at the edge).")
                .defineInRange("damage", 5.0, 0.0, 1000.0);
        COLD_COMET_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        COLD_COMET_CHASE_RADIUS = b.defineInRange("chaseRadius", 12.0, 0.0, 128.0);
        COLD_COMET_FREEZE_SECONDS = b.comment("How long entities caught by the explosion stay frozen, seconds.")
                .defineInRange("freezeSeconds", 4, 0, 60);
        COLD_COMET_ALTERS_BLOCKS = b.comment("The explosion freezes a few spots around the impact (never breaks blocks).")
                .define("altersBlocks", true);
        b.pop();

        b.comment("Defaults for a newly placed Cold Razlom (soul-fire rift). Jet timing, reach, aim speed and",
                "block chance are shared with the Razlom section.").push("coldRazlom");
        COLD_RAZLOM_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 4, 1, 3600);
        COLD_RAZLOM_DAMAGE = b.comment("Damage per jet hit, half-hearts.")
                .defineInRange("damage", 1.5, 0.0, 1000.0);
        COLD_RAZLOM_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        COLD_RAZLOM_FREEZE_SECONDS = b.comment("How long a target hit by the jet stays frozen, seconds.")
                .defineInRange("freezeSeconds", 3, 0, 60);
        b.pop();

        b.comment("Defaults for a newly placed Razlom (rift). Its cooldown is the pause after a fire jet.").push("razlom");
        RAZLOM_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 4, 1, 3600);
        RAZLOM_DAMAGE = b.comment("Damage per jet hit, half-hearts.")
                .defineInRange("damage", 1.5, 0.0, 1000.0);
        RAZLOM_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        RAZLOM_JET_SECONDS = b.comment("How long one fire jet lasts, seconds.")
                .defineInRange("jetSeconds", 5.5, 0.5, 60.0);
        RAZLOM_HIT_INTERVAL_TICKS = b.comment("Ticks between jet hits (20 = 1 s).")
                .defineInRange("hitIntervalTicks", 10, 1, 200);
        RAZLOM_JET_RANGE = b.comment("How far beyond its zone the jet keeps following a target, blocks.")
                .defineInRange("jetRange", 4.0, 0.0, 64.0);
        RAZLOM_IGNITE_SECONDS = b.comment("How long a target hit by the jet burns, seconds.")
                .defineInRange("igniteSeconds", 3, 0, 60);
        RAZLOM_AIM_SPEED = b.comment("How fast the end of the jet turns after its target, blocks per tick. A sprinting",
                        "player (~0.28) can barely outrun the standard 0.3.")
                .defineInRange("aimSpeed", 0.3, 0.02, 2.0);
        RAZLOM_BLOCK_IGNITE_CHANCE = b.comment("Chance per jet hit to set a block on fire: next to the target when it hits,",
                        "or the block in the way when something blocks it. 0 = never.")
                .defineInRange("blockIgniteChance", 0.04, 0.0, 1.0);
        b.pop();

        b.push("effects");
        ELECTRIFY_SECONDS = b.comment("How long the electrification visual lasts after an Electra or Tesla hit, seconds.")
                .defineInRange("electrifySeconds", 1.0, 0.05, 60.0);
        b.pop();

        SPEC = b.build();
    }

    private ModCommonConfig() {
    }

    public static int electrifyTicks() {
        return Math.max(1, (int) Math.round(ELECTRIFY_SECONDS.get() * 20.0));
    }
}
