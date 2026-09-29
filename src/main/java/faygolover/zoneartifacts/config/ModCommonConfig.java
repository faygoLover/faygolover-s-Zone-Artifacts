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

    public static final ForgeConfigSpec.DoubleValue CHEM_COMET_BASE_SPEED;
    public static final ForgeConfigSpec.IntValue CHEM_COMET_RESPAWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHEM_COMET_DAMAGE;
    public static final ForgeConfigSpec.IntValue CHEM_COMET_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue CHEM_COMET_CHASE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CHEM_COMET_CLOUD_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHEM_COMET_CLOUD_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue CHEM_COMET_KILLS_PLANTS;

    public static final ForgeConfigSpec.DoubleValue GRAVI_BASE_SPEED;
    public static final ForgeConfigSpec.DoubleValue GRAVI_DAMAGE;
    public static final ForgeConfigSpec.IntValue GRAVI_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue GRAVI_CHASE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue GRAVI_LEASH;
    public static final ForgeConfigSpec.DoubleValue GRAVI_POPS_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue GRAVI_SELF_POP_SECONDS;
    public static final ForgeConfigSpec.IntValue GRAVI_HIT_INTERVAL_TICKS;

    public static final ForgeConfigSpec.IntValue AMOEBA_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue AMOEBA_DAMAGE;
    public static final ForgeConfigSpec.IntValue AMOEBA_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue AMOEBA_INFLATE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue AMOEBA_CONTACT_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue AMOEBA_CLOUD_SECONDS;
    public static final ForgeConfigSpec.DoubleValue AMOEBA_CLOUD_DAMAGE;

    public static final ForgeConfigSpec.DoubleValue PUKH_LENGTH;
    public static final ForgeConfigSpec.DoubleValue PUKH_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue PUKH_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue PUKH_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue PUKH_RANGE;

    public static final ForgeConfigSpec.DoubleValue KISEL_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue KISEL_DAMAGE;
    public static final ForgeConfigSpec.IntValue KISEL_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue KISEL_ITEM_SECONDS;

    public static final ForgeConfigSpec.DoubleValue FOG_JET_SECONDS;
    public static final ForgeConfigSpec.DoubleValue FOG_JET_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue FOG_DAMAGE;
    public static final ForgeConfigSpec.IntValue FOG_INTENSITY;

    public static final ForgeConfigSpec.IntValue LIFT_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue LIFT_HOVER_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue LIFT_PUSH_OUT_SECONDS;

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

    // gravity
    public static final ForgeConfigSpec.IntValue PLESH_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PLESH_DAMAGE;
    public static final ForgeConfigSpec.IntValue PLESH_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue PLESH_PULL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PLESH_THROW_SPEED;
    public static final ForgeConfigSpec.DoubleValue PLESH_FALL_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue VORONKA_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue VORONKA_DAMAGE;
    public static final ForgeConfigSpec.IntValue VORONKA_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue VORONKA_PULL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue VORONKA_CORE_RADIUS;
    public static final ForgeConfigSpec.IntValue KARUSEL_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue KARUSEL_DAMAGE;
    public static final ForgeConfigSpec.IntValue KARUSEL_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue KARUSEL_SPIN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue KARUSEL_CORE_RADIUS;
    public static final ForgeConfigSpec.IntValue PODUSHKA_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue PODUSHKA_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue PODUSHKA_FALL_DAMAGE_MULTIPLIER;

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

        b.comment("Chemical Comet: a clot of yellow-green gas on a route. On impact it bursts into a heavy cloud",
                "that creeps over the ground, burns anything inside with chemical damage and kills plants.").push("chemComet");
        CHEM_COMET_BASE_SPEED = b.comment("Standard speed (x1.0) in blocks per tick.")
                .defineInRange("baseSpeed", 0.12, 0.01, 2.0);
        CHEM_COMET_RESPAWN_SECONDS = b.defineInRange("respawnSeconds", 8, 1, 3600);
        CHEM_COMET_DAMAGE = b.comment("Damage of the burst itself at the center (30 % at the edge), half-hearts.")
                .defineInRange("damage", 4.0, 0.0, 1000.0);
        CHEM_COMET_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        CHEM_COMET_CHASE_RADIUS = b.defineInRange("chaseRadius", 12.0, 0.0, 128.0);
        CHEM_COMET_CLOUD_SECONDS = b.comment("How long the cloud lingers, seconds.")
                .defineInRange("cloudSeconds", 5.0, 0.5, 60.0);
        CHEM_COMET_CLOUD_DAMAGE = b.comment("Damage every half second to whoever is in the cloud, half-hearts.")
                .defineInRange("cloudDamage", 1.0, 0.0, 1000.0);
        CHEM_COMET_KILLS_PLANTS = b.comment("Grass turns to dirt, plants and leaves die under the cloud.")
                .define("killsPlants", true);
        b.pop();

        b.comment("Gravi: invisible, flies its route through blocks and creatures, never pops. All the way it",
                "sets off small, quick gravitational pops on the surfaces around it (within its size) and in the",
                "air right by it — the only way to track it. Chases like the Tesla, but never goes further than",
                "leash blocks from its route.").push("gravi");
        GRAVI_BASE_SPEED = b.comment("Standard speed (x1.0) in blocks per tick.")
                .defineInRange("baseSpeed", 0.08, 0.01, 2.0);
        GRAVI_DAMAGE = b.comment("Damage of one pop to whoever is within a block of it, half-hearts.")
                .defineInRange("damage", 3.0, 0.0, 1000.0);
        GRAVI_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        GRAVI_CHASE_RADIUS = b.defineInRange("chaseRadius", 12.0, 0.0, 128.0);
        GRAVI_LEASH = b.comment("How far from its nearest route point it may go while chasing, blocks.")
                .defineInRange("leash", 20.0, 1.0, 256.0);
        GRAVI_POPS_PER_SECOND = b.comment("Its footprints: pops on the floor (now and then a wall or ceiling right beside) under",
                        "and a little ahead of it, left and right in turn, per second at effects 3 (x effects / 3; a quarter",
                        "of that while it stands still). The size is how far they scatter and reach.")
                .defineInRange("popsPerSecond", 2.0, 0.0, 20.0);
        GRAVI_SELF_POP_SECONDS = b.comment("While it hangs inside its prey: a pop right by itself (in the air too) this often, seconds. 0 = never.")
                .defineInRange("selfPopSeconds", 1.5, 0.0, 60.0);
        GRAVI_HIT_INTERVAL_TICKS = b.comment("Pops hurt one creature at most this often, ticks.")
                .defineInRange("hitIntervalTicks", 10, 1, 200);
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

        b.comment("Plesh (mosquito bald spot): pulls everything within twice its zone's reach to its center for",
                "pullSeconds (what's already caught circles on a small orbit), then flings it all up and away.",
                "The speed tuner scales the throw.").push("plesh");
        PLESH_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 6, 1, 3600);
        PLESH_DAMAGE = b.comment("Damage for slamming into a wall after the throw, at full speed, half-hearts.")
                .defineInRange("damage", 4.0, 0.0, 1000.0);
        PLESH_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        PLESH_PULL_SECONDS = b.comment("Pull time before the throw (2.5 matches the sound; never shorter than 2).")
                .defineInRange("pullSeconds", 2.5, 0.5, 60.0);
        PLESH_THROW_SPEED = b.comment("Throw speed at x1.0, blocks per tick (1.6 flies roughly 10-12 blocks).")
                .defineInRange("throwSpeed", 1.6, 0.1, 10.0);
        PLESH_FALL_DAMAGE_MULTIPLIER = b.comment("Fall damage after being thrown is multiplied by this.")
                .defineInRange("fallDamageMultiplier", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Voronka (vortex): pulls everything to its center for pullSeconds, then tears space open:",
                "damage falling off with the distance from the center, loot caught in the core destroyed,",
                "anything killed bursts apart. The speed tuner scales the pull.").push("voronka");
        VORONKA_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 6, 1, 3600);
        VORONKA_DAMAGE = b.comment("Damage at the center (30 % at the edge), half-hearts.")
                .defineInRange("damage", 12.0, 0.0, 1000.0);
        VORONKA_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        VORONKA_PULL_SECONDS = b.comment("Pull time before the tear (never shorter than 2, the sound's build-up).")
                .defineInRange("pullSeconds", 3.0, 0.5, 60.0);
        VORONKA_CORE_RADIUS = b.comment("Items within this many blocks of the center are destroyed.")
                .defineInRange("coreRadius", 1.0, 0.1, 16.0);
        b.pop();

        b.comment("Karusel (carousel): a whirlwind — pulls sideways to its axis and spins, no vertical pull.",
                "After spinSeconds everyone in it is hurt (the closer to the axis, the worse) and a flat wave of air",
                "pushes everything out.",
                "Running flat out gets you away; jumping doesn't.",
                "The speed tuner scales the pull.").push("karusel");
        KARUSEL_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 6, 1, 3600);
        KARUSEL_DAMAGE = b.comment("Damage at the axis (a quarter at the edge of the whirl), half-hearts.")
                .defineInRange("damage", 12.0, 0.0, 1000.0);
        KARUSEL_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        KARUSEL_SPIN_SECONDS = b.comment("Spin time from the trigger to the blowout (6.0 matches the sound; never shorter than that).")
                .defineInRange("spinSeconds", 6.0, 0.5, 60.0);
        KARUSEL_CORE_RADIUS = b.comment("Items within this many blocks of the axis are destroyed.")
                .defineInRange("coreRadius", 0.75, 0.1, 16.0);
        b.pop();

        b.comment("Podushka (cushion): harmless. Brakes a fall, then bounces up above its top; sneaking inside",
                "sinks gently instead. The landing after a bounce hurts less. The speed tuner scales the height.").push("podushka");
        PODUSHKA_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        PODUSHKA_HEIGHT = b.comment("Bounce height above the cushion's top at x1.0, blocks.")
                .defineInRange("height", 3.0, 0.5, 64.0);
        PODUSHKA_FALL_DAMAGE_MULTIPLIER = b.comment("Fall damage after bouncing out of it is multiplied by this.")
                .defineInRange("fallDamageMultiplier", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Amoeba: a jelly puddle. When someone steps into its zone it gathers into a dome, rounds into a ball,",
                "lifts off, floats up swelling for inflateSeconds and bursts into a chemical cloud (1.5 times a Chemical",
                "Comet's of the same size). Touching the ball burns. Then a pale puddle seeps back over the cooldown.").push("amoeba");
        AMOEBA_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 25, 1, 3600);
        AMOEBA_DAMAGE = b.comment("Damage of the burst at its middle (30 % at the edge), half-hearts.")
                .defineInRange("damage", 6.0, 0.0, 1000.0);
        AMOEBA_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        AMOEBA_INFLATE_SECONDS = b.comment("From the trigger to the burst, seconds (at least 3.5).")
                .defineInRange("inflateSeconds", 6.0, 3.5, 60.0);
        AMOEBA_CONTACT_DAMAGE = b.comment("Damage every half second while touching the swelling ball, half-hearts.")
                .defineInRange("contactDamage", 2.0, 0.0, 1000.0);
        AMOEBA_CLOUD_SECONDS = b.comment("How long its cloud lingers, seconds.")
                .defineInRange("cloudSeconds", 10.0, 0.5, 120.0);
        AMOEBA_CLOUD_DAMAGE = b.comment("Damage every half second to whoever is in its cloud, half-hearts.")
                .defineInRange("cloudDamage", 1.5, 0.0, 1000.0);
        b.pop();

        b.comment("Burning Fluff (a block): standard values of a newly placed one. Standing in its strands burns",
                "every second; anything coming near fast (running, jumping, falling, thrown) gets a puff of",
                "burning spores shot at it, reaching `range` blocks.").push("pukh");
        PUKH_LENGTH = b.comment("How long the strands hang, blocks (the size tuner).")
                .defineInRange("length", 2.0, 0.5, 4.0);
        PUKH_DAMAGE = b.comment("Damage of the strands (per second) and of a puff, half-hearts.")
                .defineInRange("damage", 2.0, 0.0, 1000.0);
        PUKH_COOLDOWN_SECONDS = b.comment("Pause between puffs, seconds.")
                .defineInRange("cooldownSeconds", 3.0, 0.5, 3600.0);
        PUKH_INTENSITY = b.comment("How dense the fluff is.")
                .defineInRange("intensity", 3, 1, 50);
        PUKH_RANGE = b.comment("How far the puffs reach (the targeting tuner), blocks. 0 = never puffs.")
                .defineInRange("range", 5.0, 0.0, 16.0);
        b.pop();

        b.comment("Kisel: a glowing, bubbling acid puddle. Whatever gets into it makes it seethe, hiss and glow brighter:",
                "every interval items lose one from their stack, creatures are burnt and their boots and leggings",
                "corroded, projectiles melt away.").push("kisel");
        KISEL_INTERVAL_SECONDS = b.comment("How often it eats into what's in it, seconds (the cooldown tuner).")
                .defineInRange("intervalSeconds", 0.5, 0.1, 60.0);
        KISEL_DAMAGE = b.defineInRange("damage", 1.5, 0.0, 1000.0);
        KISEL_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        KISEL_ITEM_SECONDS = b.comment("How long it takes to dissolve one item of a dropped stack, seconds.")
                .defineInRange("itemSeconds", 5.0, 0.5, 600.0);
        b.pop();

        b.comment("Acid Fog: a dense greenish haze over the ground, hard to see by day. Now and then a jet of vapour",
                "and spray bursts straight up out of it, burning and tossing whoever it catches.").push("acidFog");
        FOG_JET_SECONDS = b.comment("About how often a jet goes off, seconds (the cooldown tuner; give or take a third).")
                .defineInRange("jetSeconds", 4.0, 1.0, 3600.0);
        FOG_JET_DAMAGE = b.comment("Damage of a jet, half-hearts (the damage tuner).")
                .defineInRange("damage", 3.0, 0.0, 1000.0);
        FOG_DAMAGE = b.comment("Damage every second just for being in the fog, half-hearts. 0 = none.")
                .defineInRange("fogDamage", 0.5, 0.0, 1000.0);
        FOG_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        b.pop();

        b.comment("Lift: nearly invisible. Switches gravity off: everything in it floats up to hoverHeight above the",
                "ground, moves as if on ice in thick syrup, and after pushOutSeconds is eased out to the edge (the",
                "speed tuner sets how hard; 0 = never). Projectiles get stuck in it. Stacked Lifts act as one. Harmless.").push("lift");
        LIFT_INTENSITY = b.defineInRange("intensity", 3, 1, 50);
        LIFT_HOVER_HEIGHT = b.comment("Hover height above the ground, blocks.")
                .defineInRange("hoverHeight", 1.5, 0.2, 32.0);
        LIFT_PUSH_OUT_SECONDS = b.comment("After this long inside, anything is eased out to the edge, seconds.")
                .defineInRange("pushOutSeconds", 10.0, 0.5, 600.0);
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
