# faygolover's Zone Artifacts — указатель кода

Создано `python _family/tools/mapgen.py`, версия мода 0.1.41.4.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 210 файлов, 29171 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.zoneartifacts` | 1 | 48 |
| `faygolover.zoneartifacts.anomaly` | 43 | 6083 |
| `faygolover.zoneartifacts.block` | 5 | 758 |
| `faygolover.zoneartifacts.client` | 12 | 1501 |
| `faygolover.zoneartifacts.client.amoeba` | 1 | 332 |
| `faygolover.zoneartifacts.client.bubbles` | 3 | 291 |
| `faygolover.zoneartifacts.client.chem` | 4 | 694 |
| `faygolover.zoneartifacts.client.distortion` | 2 | 579 |
| `faygolover.zoneartifacts.client.dymka` | 1 | 179 |
| `faygolover.zoneartifacts.client.ezhik` | 1 | 202 |
| `faygolover.zoneartifacts.client.fantom` | 1 | 130 |
| `faygolover.zoneartifacts.client.firefly` | 1 | 177 |
| `faygolover.zoneartifacts.client.fog` | 1 | 186 |
| `faygolover.zoneartifacts.client.fx` | 4 | 761 |
| `faygolover.zoneartifacts.client.gear` | 1 | 45 |
| `faygolover.zoneartifacts.client.gravi` | 2 | 186 |
| `faygolover.zoneartifacts.client.gravity` | 7 | 1628 |
| `faygolover.zoneartifacts.client.kamerton` | 1 | 256 |
| `faygolover.zoneartifacts.client.khlopushka` | 1 | 202 |
| `faygolover.zoneartifacts.client.kisel` | 2 | 479 |
| `faygolover.zoneartifacts.client.lift` | 1 | 205 |
| `faygolover.zoneartifacts.client.pda` | 2 | 396 |
| `faygolover.zoneartifacts.client.poppy` | 2 | 503 |
| `faygolover.zoneartifacts.client.psi` | 1 | 246 |
| `faygolover.zoneartifacts.client.pukh` | 3 | 363 |
| `faygolover.zoneartifacts.client.razlom` | 4 | 878 |
| `faygolover.zoneartifacts.client.render` | 7 | 804 |
| `faygolover.zoneartifacts.client.rust` | 2 | 538 |
| `faygolover.zoneartifacts.client.sumrak` | 1 | 241 |
| `faygolover.zoneartifacts.client.swamp` | 1 | 275 |
| `faygolover.zoneartifacts.client.tesla` | 12 | 1803 |
| `faygolover.zoneartifacts.client.thermal` | 5 | 788 |
| `faygolover.zoneartifacts.client.web` | 1 | 360 |
| `faygolover.zoneartifacts.compat` | 2 | 89 |
| `faygolover.zoneartifacts.config` | 2 | 775 |
| `faygolover.zoneartifacts.gear` | 4 | 532 |
| `faygolover.zoneartifacts.item` | 6 | 397 |
| `faygolover.zoneartifacts.network` | 30 | 1920 |
| `faygolover.zoneartifacts.pda` | 4 | 453 |
| `faygolover.zoneartifacts.registry` | 7 | 490 |
| `faygolover.zoneartifacts.tesla` | 18 | 2348 |
| `faygolover.zoneartifacts.tuner` | 1 | 50 |

## 2. Классы

### `faygolover.zoneartifacts`
- `ZoneArtifacts`, 48 — Mod entry point.

### `faygolover.zoneartifacts.anomaly`
- `AcidFog`, 31 — Acid Fog: a dense greenish haze of acid vapour hovering over the ground, hard to see by day.
- `AcidFogEngine`, 83 — Server side of the Acid Fog (AcidFog): slow burns inside, and a jet every cooldown seconds (give or take a third) at a random spot of it.
- `Amoeba`, 79 — The Amoeba: a puddle of translucent jelly on the ground.
- `AmoebaEngine`, 116 — Server side of the Amoeba (Amoeba): the trigger, contact burns while it swells, the burst (damage around + a lingering cloud, ChemClouds) and the cooldown.
- `AnomalyCombat`, 153 — Damage and sound helpers shared by all anomalies (Electra, Tesla, the electrify command).
- `AnomalyDefaults`, 243 — Per-type standard values for zone anomalies (from the common config), plus display names.
- `AnomalyEngine`, 158 — Server-side tick logic for placed zone anomalies.
- `AnomalyGeometry`, 68 — Shared geometry helpers, used by the tick engine, the server-side click raytrace, and the client-side renderers, so all of them agree on exactly where an anomaly's zone is.
- `AnomalyInstance`, 333 — One zone anomaly placed in the world, with its own settings changed with the tuners: size in blocks, cooldown in seconds (for Zharka and Iney: the damage interval), damage per hit, visual intensity and — for the gravitational ones…
- `AnomalyInteractionHandler`, 37 — Right-click with a placer while aiming into an existing anomaly's zone does nothing: anomalies are placed only on blocks, never into or onto another anomaly.
- `AnomalySavedData`, 59 — Per-dimension persistence for placed anomalies.
- `AnomalyTargeting`, 50 — Server-side "am I looking at an anomaly" check, independent of the block grid — a zone isn't a block, so vanilla's block raytrace can't find it.
- `AnomalyTypeIds`, 72 — Ids of the zone anomalies stored in AnomalySavedData.
- `BubbleEngine`, 117 — Soap bubbles: keeps each zone stocked with bubbles (BubbleEntity) — after its first pause (the cooldown tuner) one every respawnSeconds until there are as many as the effects tuner says; after a burst the pause starts again.
- `BubbleEntity`, 163 — One soap bubble (BubbleEngine): a gravitational knot drifting about its zone, pushing off the other bubbles and off blocks.
- `ChemClouds`, 151 — Lingering chemical clouds (the Chemical Comet's burst).
- `ColdEffects`, 102 — What the soul-fire (cold) anomalies do instead of setting things on fire: freeze.
- `Electra`, 48 — Electra's fixed characteristics — what used to live in anomaly_types/electra.json.
- `ElectrifyCommand`, 67 — /fl_zone_arts electrify [damage] (permission level 2) — puts the electrification effect on any entities: arcs crawling over them for seconds.
- `Gravity`, 305 — Shared physics of the gravitational anomalies — used by the server for mobs, items and projectiles, and by each client for its own player (whose movement the client owns), so both move the same way.
- `GravityEngine`, 493 — Server side of the gravitational anomalies (see Gravity for the shared physics), called from AnomalyEngine every tick.
- `KamertonEngine`, 99 — Kamerton: a ball of glass needles (diameter = size).
- `KhlopushkaEngine`, 140 — Khlopushka: a quarter of a second after someone (a player or a mob) walks into the zone, and then cooldown seconds after each blast while anyone is still in it, a glowing clot appears in the open before a random one of them, gathe…
- `Kisel`, 90 — Kisel: a puddle of bubbling, bright green, glowing liquid.
- `KiselEngine`, 140 — Server side of Kisel (Kisel).
- `Lift`, 140 — The Lift's physics, shared by the server (mobs, items, projectiles) and each client (its own player, whose movement it owns).
- `LiftEngine`, 85 — Server side of the Lift (Lift): always on, harmless.
- `Motion`, 58 — How far something moved since the last tick, as the anomalies see it.
- `PoppyEngine`, 236 — Server side of the poppy field.
- `PsiEngine`, 41 — Server side of the psi zone: mobs don't like it there — not afraid, they just walk out at their usual pace (and not back in on their own, since they keep being steered out).
- `Pukh`, 101 — Burning Fluff (Жгучий пух): a plant-like anomaly, a real block.
- `PukhSpores`, 116 — Burning Fluff's spore puffs, server side: each flies straight at where its target was, slowing down, swelling, and stops at the first block it meets.
- `PukhVolley`, 103 — Burning Fluff firing together: whichever strand notices something coming fast, the whole growth answers — but only the strands that can hit it (in range, nothing in between), one after another: the nearest first, then every #GAP t…
- `Razlom`, 176 — The Razlom (rift): glowing cracks in the ground under it, crossing at one point, and a small flame hovering over that point.
- `RazlomEngine`, 267 — Server side of the Razlom and the Cold Razlom (soul fire: cold damage and freezing instead of fire damage and burning, frozen spots instead of fires), called from AnomalyEngine every tick.
- `RustEngine`, 190 — Server side of Rust.
- `Swamp`, 180 — Trjasina (the swamp): ground that looks like any other — only unusually clean, and quiet under foot — but whoever walks out onto it far enough from its edge slowly sinks into the very blocks.
- `SwampEngine`, 125 — Server side of the swamp (Swamp).
- `SwampPhysics`, 282 — How a living thing moves in the swamp (Swamp), shared by the server (mobs) and the client (its own player — players move themselves).
- `Thermal`, 67 — Fixed characteristics of the thermal anomalies — Zharka (heat) and Iney (frost).
- `ThermalEngine`, 293 — Server-side logic of Zharka and Iney, called from AnomalyEngine every tick.
- `WebEngine`, 84 — The Web, server side: a whole thread that touches living flesh cuts it (through any armour) and snaps; it grows back after the web's regrow time.
- `WebSavedData`, 142 — The Webs of one dimension: each a set of threads (straight segments between exact points on blocks — not tied to blocks, so any number of webs can share a block) with its own settings.

### `faygolover.zoneartifacts.block`
- `EzhikBlock`, 104 — The Hedgehog (ezhik): sits on any face of a block (facing = away from it) and makes that surface rise into lumps and spikes of the very same block, slowly growing out, sinking back, trembling and pulsing — like a ferrofluid.
- `EzhikBlockEntity`, 116 — The Hedgehog's settings: the patch's radius (and the spikes' length with it), how many spikes, how fast they move, how deep its centre sits.
- `PukhBlock`, 142 — Burning Fluff (Pukh) as a block: only the fleshy base is a block; the strands hanging from it are drawn and handled by its block entity (PukhBlockEntity) and can be walked through.
- `PukhBlockEntity`, 317 — Burning Fluff's settings and behaviour (Pukh).
- `PukhLayout`, 79 — Where Burning Fluff's lace sheets hang, in block-local coordinates (shared by the renderer and the parting of the strands).

### `faygolover.zoneartifacts.client`
- `AnomalyAmbientSoundHandler`, 125 — Runs each anomaly's idle hum entirely client-side as a real, individually-stoppable SimpleSoundInstance — replacing the earlier approach of the server periodically calling ServerLevel.playSound, which turned out to be un-fixable f…
- `AnomalyArcRenderer`, 534 — Electra's lightning-arc visuals, entirely client-side (purely cosmetic — the server doesn't need to agree on exactly where every arc bends or strikes).
- `AnomalyClientTargeting`, 59 — Client-side mirror of the server's AnomalyTargeting raytrace, run against the synced ClientAnomalyCache instead of the real (server-only) saved data.
- `AnomalyHighlightRenderer`, 214 — Draws anomaly zones as translucent volumes with opaque edges: with a placer in hand — every anomaly of that type within #VISIBLE_RADIUS, plus a placement preview (default size) where a right-click would put a new one, unless the p…
- `Breath`, 22 — Slow, smooth, never-repeating breathing for fire and glow — like a gas flame or a ball of boiling magma: it swells and eases, but doesn't flicker.
- `ClientAnomalyCache`, 100 — Client-side snapshot of "what anomalies exist and where", refreshed wholesale whenever a SyncAnomaliesPacket arrives (join, dimension change, or any placement/removal/level change) — or patched in place, one entry at a time, whene…
- `ClientAnomalyInputHandler`, 52 — Left-click, while holding a placer item, anywhere inside an already-placed anomaly of the same type removes it (like clicking a light block).
- `ElectrifyRenderer`, 176 — Electrification: arcs crawling over an entity's surface for a while, following it as it moves — after a Tesla touch, an Electra hit, or the /fl_zone_arts electrify command.
- `GlowBatch`, 39 — Glows collected while drawing flames and bolts, then drawn together in one GlowRenderType#GLOW pass — for renderers that loop over many things, each with both.
- `GlowRenderType`, 76 — Vanilla's lightning look (position + colour, additive blending, unlit, depth tested; our shader, so it fades in the fog) — but without writing depth .
- `ZoneLight`, 31 — How lit a non-glowing anomaly is where it stands (gas, jelly, glass, film, mist): 0.03..1.
- `ZoneLoopSound`, 73 — An anomaly's standing loop whose volume follows volume (smoothly) and which stops once alive says the anomaly is gone or out of range.

### `faygolover.zoneartifacts.client.amoeba`
- `AmoebaClient`, 332 — The Amoeba's look: a translucent jelly (plain alpha blending, lit by the world) — a flat, slowly rippling puddle at rest; when it wakes it draws together into a quivering dome, rounds into a ball, lifts off and floats up swelling,…

### `faygolover.zoneartifacts.client.bubbles`
- `BubbleClient`, 66 — Soap bubbles' extras: the shimmer of the air round each bubble, and a burst's flash, rippling shock and spray of film.
- `BubbleRenderer`, 171 — A soap bubble: a wobbling ball of film, nearly clear in the middle and dense towards its outline, its thin-film colours shifting with the angle, lit by the world like the Amoeba's jelly; a bright little highlight up to one side.
- `FilmRenderType`, 54 — A soap film: position + colour, ordinary alpha blending (like the Amoeba's jelly), both sides, depth tested but not written — the far side of the bubble shows through the near one, and the bubble never hides what's drawn after it.

### `faygolover.zoneartifacts.client.chem`
- `ChemClient`, 225 — The Chemical Comet's look (Gas): the body — a clot of yellow-green gas: a dense, darker middle with several puffs rolling over each other around it, turning slowly; the trail — wisps left behind, sinking (the gas is heavy), now an…
- `ChemCometRenderer`, 32 — The Chemical Comet draws nothing itself: its gas body is handed to Gas by ChemClient.
- `ChemDropParticle`, 66 — A heavy drop of the Chemical Comet's gas: falls, and where it lands leaves a small pale stain.
- `Gas`, 371 — Gas: soft, ragged, non-glowing puffs (plain alpha blending, lit by the world's light, so they look like heavy vapour rather than light).

### `faygolover.zoneartifacts.client.distortion`
- `Distortion`, 474 — Air distortion without shaders of our own: once a frame (only while some patch is in view) the frame drawn so far is copied into a texture, and each patch is drawn as a mesh showing that copy — every point of it shows the picture …
- `DistortionSources`, 105 — Where the air is bent (Distortion): gravitational anomalies — VoronkaLens; Zharka — heat shimmer rising through the zone, stronger when it flares; Iney — a faint, slow shimmer of cold air; Razlom — heat shimmer over its flame (not…

### `faygolover.zoneartifacts.client.dymka`
- `DymkaClient`, 179 — The Haze: from outside a grey mist hanging in the zone; inside, the fog closes in to a few blocks (ScreenFx) a second after one walks in, sounds come muffled and a little lower (SoundFx), and now and then something is heard far of…

### `faygolover.zoneartifacts.client.ezhik`
- `EzhikRenderer`, 202 — The Hedgehog: an urchin buried in the host block — every spike runs out of one centre under the face, all alike, and only the part that pierces the face is seen: long in the middle, the rim ones mere stubs.

### `faygolover.zoneartifacts.client.fantom`
- `FantomClient`, 130 — Phantom light: pale blue-white lights, each drawn out upright a little, set tightly in five close upright lines (the two outer ones shorter), the row across the way the GM faced when placing it, standing on the anomaly's block.

### `faygolover.zoneartifacts.client.firefly`
- `FireflyClient`, 177 — The Firefly: small lights wandering about their zone on slow, looping paths, never higher than the targeting tuner above the ground, now and then dimming; each lays a faint glow on the ground under it.

### `faygolover.zoneartifacts.client.fog`
- `AcidFogClient`, 186 — Acid Fog's look and sound: a low, dense, greenish haze made of many slow Gas puffs that keeps itself topped up over the ground of the zone (faintly glowing, so it shows at night, and fading by day), a quiet hissing loop, and the j…

### `faygolover.zoneartifacts.client.fx`
- `FogCulling`, 75 — Embeddium (Sodium) skips the chunks past the end of the fog ("fog occlusion").
- `HumLoop`, 72 — A loop heard "inside the head" (not from anywhere): its volume follows volume, and it stops itself once that has stayed at nothing for two seconds.
- `ScreenFx`, 413 — What the senses-dulling anomalies do to the picture: fog — the Haze closes it in to a few blocks of grey, the Dusk to black at arm's length; under everything else on screen: the psi zone's swimming, split, blurred sight (the frame…
- `SoundFx`, 201 — Muffling of the world's sounds by the anomalies that dull the senses — without touching the sound engine: every sound that starts (a plain one-shot, not a looping managed one) is wrapped so that its volume and pitch follow, tick b…

### `faygolover.zoneartifacts.client.gear`
- `BoltRenderer`, 45 — A thrown bolt or nut: its own 3D item model, tumbling as it flies.

### `faygolover.zoneartifacts.client.gravi`
- `GraviClient`, 125 — Gravi's pops, as each client sees them: a tiny Voronka.
- `GraviRenderer`, 61 — Gravi is invisible.

### `faygolover.zoneartifacts.client.gravity`
- `BloodParticle`, 64 — A drop of blood: flies where it's sent, falls, and stays put a moment where it lands.
- `GoreClient`, 216 — Gore for Voronka and Karusel kills: the body vanishes at once (instead of the vanilla death tilt), drops of blood fly off, and blotches of blood are left on the blocks around, fading after a minute or so.
- `GravityClientHandler`, 713 — Client side of the gravitational anomalies: your own player's movement (the client owns it, so the pull, the whirl and the cushion are applied here, with the same physics the server uses for mobs — Gravity); throws, damage and sho…
- `GravityLoopSound`, 52 — A gravitational anomaly's idle loop (Voronka's hum).
- `GravityRenderer`, 388 — The gravitational anomalies' look (no shaders — the "distortion" is faked): debris — tiny spinning blocks, drawn with the real block models; Plesh — a flattened, dusty patch on the ground; while pulling, rings of pressure closing …
- `KaruselIdleSound`, 58 — Karusel's idle: a very quiet, slowly swelling rustle of wind.
- `VoronkaLens`, 137 — The gravitational anomalies' air distortion (drawn by Distortion): Voronka — a gravitational lens: at rest a plainly visible pinch, the air inside boiling, its edge — a band of stronger refraction — running in waves and breathing;…

### `faygolover.zoneartifacts.client.kamerton`
- `KamertonClient`, 256 — Kamerton's look: a lumpy cloud of thin glass needles, each pointing its own way — pale, translucent, glinting as the eye moves (brightest seen side-on), twinkling here and there.

### `faygolover.zoneartifacts.client.khlopushka`
- `KhlopushkaClient`, 202 — Khlopushka's look: the clot — a small white-hot spark growing into a trembling ball of light, spitting sparks faster and faster — and its blast: a flash of light swelling out, sparks, smoke, a ripple through the air.

### `faygolover.zoneartifacts.client.kisel`
- `KiselBubbleParticle`, 70 — A glowing green bubble in Kisel: swells on the surface, drifts a little and bursts.
- `KiselClient`, 409 — Kisel's look and sound: a puddle of thick, dark green liquid.

### `faygolover.zoneartifacts.client.lift`
- `LiftClient`, 205 — Client side of the Lift: your own player floating in it (Lift; jump to rise, sneak to sink, let go to drift back to the hover height), and its barely-there look — a few specks of dust drifting up from the ground and the faintest s…

### `faygolover.zoneartifacts.client.pda`
- `PdaClientHandler`, 157 — The KPK in hand: a right click on an anomaly asks the server for its settings (the window opens when they arrive, PdaScreen).
- `PdaScreen`, 239 — The KPK window for one anomaly: a slider per setting (with − / + for exact steps; the standard value in its tooltip), the four switches, moving it a block at a time, and Apply / Defaults / Done / Cancel.

### `faygolover.zoneartifacts.client.poppy`
- `PoppyClient`, 428 — The poppy field on the client: thick poppies over the ground of the field (drawn, not placed), swaying; swarms of petals drifting over it with trails of petals behind them; its own player's micro-sleeps (from the server, PoppyEngi…
- `PoppyPetalParticle`, 75 — A poppy petal: drifts, flutters and turns over as it sinks, and fades before it lands.

### `faygolover.zoneartifacts.client.psi`
- `PsiClient`, 246 — The psi zone on its player: nothing to see from outside.

### `faygolover.zoneartifacts.client.pukh`
- `PukhClient`, 126 — Burning Fluff's puffs as the client sees them: a small grey-green cloud of spores flying the same path as on the server (PukhSpores#step), swelling, and where it hits a surface it leaves dark burns that fade after a while.
- `PukhParticle`, 82 — Burning Fluff's bits: a dark flake drifting down, swaying — or a pale, sickly spore in a puff.
- `PukhRenderer`, 155 — Burning Fluff's hanging strands: sheets of lacy, cobweb-like fibre (a cut-out texture, lit by the world light at each height) hanging from the base, swaying more the lower they hang, pushed aside where something goes through (Pukh…

### `faygolover.zoneartifacts.client.razlom`
- `RazlomClientHandler`, 463 — Client side of the Razlom: the crack pattern (seeded from the position, so it never changes shape; heights re-read from the terrain every few seconds), the flame's position, particles, the quiet hum and the jet (target + sound) fr…
- `RazlomJetSound`, 52 — The roar of the Razlom's fire jet (crack_blow).
- `RazlomLoopSound`, 47 — The Razlom flame's quiet hum (Zharka's loop, softer), a little louder while the jet burns.
- `RazlomRenderer`, 316 — Draws every Razlom in range (the Cold Razlom in soul-fire colours, see Palette): cracks — dark jagged bands lying on the ground (plain alpha blending, so they really darken the blocks), each with a thin glowing seam of fire along …

### `faygolover.zoneartifacts.client.render`
- `Halo`, 145 — The glow of the air round the fireballs — the Comets, the Tesla: a wide, soft halo (a gaussian per pixel) breathing with the ball, faint enough that the ball stays a dense ball of fire and doesn't turn into a glowing dot.
- `ScreenFog`, 71 — The Haze's and the Dusk's fog with a shader pack on.
- `Smoke`, 144 — The Dusk and the Haze as smoke with depth (volumetric; HIGH quality and up, finer at ULTRA) in place of their flat puffs: each zone's box, ray-marched per pixel in the smoke shader — the Dusk a near-solid black mass with torn, cur…
- `ZaLatePass`, 111 — The anomalies on FL Render's shared passes.
- `ZaLights`, 67 — The light burning and glowing anomalies cast on the world round them — the Comets, the Tesla, the Kisel's puddles, the Zharka at work, the fireflies and the phantoms — handed to FL Render's WorldLight, which draws it (for the pict…
- `ZaShaders`, 236 — The anomalies' own two shaders — vanilla's position_color and lightning, but both sink into the world's fog (the Haze closing in, the far plane): #soft() for alpha-blended shapes (they fade to the fog colour), #glow() for additive…
- `ZaTechniques`, 30 — The anomalies' graphics techniques in FL Render's settings tab (names in our lang: flrender.technique.fl_zone_arts.

### `faygolover.zoneartifacts.client.rust`
- `MossRenderType`, 45 — Rust's moss layers: drawn like cutout blocks (the block shader — world light from the lightmap, the face's shade baked into the colour, no per-camera lighting), with the layer's own texture and both sides.
- `RustClient`, 493 — Rust's look: every open face of the blocks in the zone is overgrown with rusty moss — a mat with bald patches and two thin layers of fuzz over it (tiled textures, lit by the world) — rusty dust kicked up by anyone walking in it, a…

### `faygolover.zoneartifacts.client.sumrak`
- `SumrakClient`, 241 — The Dusk: a solid mass of churning darkness (black Gas puffs overlapping into one, those at its edges curling out and back like smoke).

### `faygolover.zoneartifacts.client.swamp`
- `SwampClient`, 275 — The swamp on the client: the own player's sinking (SwampPhysics, players move themselves), the dark when one's head goes under (see #darkness), slow rings running over the surface from whatever moves on it or falls onto it, the su…

### `faygolover.zoneartifacts.client.tesla`
- `CometEffectRenderer`, 209 — The Comet's explosion (the Comet itself is already gone): a flash, a fireball swelling to the blast radius and fading from yellow to dark red, tongues of flame shooting out (away from the wall it hit, stopping at blocks) and a spr…
- `CometLateRender`, 23 — Draws the Comets after the entities and the clouds (see CometRenderer#render).
- `CometRenderer`, 414 — The Comet: a blazing point at the center that slowly shifts between orange and yellow, wrapped in a halo (the Cold Comet: the same in soul-fire blue and cyan), with prominences — arches of flame that rise off the surface and sink …
- `FireDraw`, 93 — Fire geometry for the Comet and the Razlom, drawn into a GlowRenderType.ARC buffer (additive, unlit, back faces culled) like LightningDraw, but with a width and a colour per point, so tongues of flame can taper and shade from a ho…
- `LightningDraw`, 113 — Lightning geometry for the Tesla, drawn into a GlowRenderType.ARC buffer (additive, unlit, back faces culled).
- `TeslaClientCache`, 53 — Client-side copy of the Tesla routes in the current dimension.
- `TeslaClientHandler`, 153 — Client-side Tesla behaviour: starting each visible Tesla's idle loop, and turning left-clicks on waypoints into TeslaWaypointClickPackets.
- `TeslaClientSetup`, 34 — Mod-bus client setup: every entity type needs a renderer registered, or the client crashes the moment one is sent to it.
- `TeslaEffectRenderer`, 151 — The burst of a popping Tesla (it's already gone when this plays).
- `TeslaIdleSound`, 49 — The Tesla's hum: a looping sound that follows the Tesla every tick (unlike Electra's idle loop, which stands still), and stops itself the moment the Tesla pops or disappears.
- `TeslaRenderer`, 262 — The Tesla itself: a tight ball of lightning around a bright glowing core, size blocks across.
- `TeslaRouteRenderer`, 249 — While the Tesla placer (or, within a smaller radius, a tuner) is in hand: every route around the player as small waypoint cubes joined by route lines — yellow while still being built, cyan once completed (a completed route also ge…

### `faygolover.zoneartifacts.client.thermal`
- `IneyFrostRenderer`, 158 — Iney's frost: small glowing six-pointed crystals lying flat on the block faces inside the zone.
- `ThermalClientHandler`, 380 — Client side of Zharka and Iney: the activity ramp (0..1, up in 0.2 s, down in 3 s, driven by the synced active flag), the cached block faces inside each zone, particles and sounds.
- `ThermalClientSetup`, 32 — Hooks the thermal particles' look (ThermalParticle) to their registered types.
- `ThermalLoopSound`, 53 — A thermal anomaly's standing loop (Zharka's fire hum, Iney's ice loop).
- `ThermalParticle`, 165 — The thermal anomalies' slow, long-lived particles.

### `faygolover.zoneartifacts.client.web`
- `WebClient`, 360 — The Web on the client: threads almost invisible in the dark that catch the light — a faint sheen where there's light enough, sparks of glint running along them, and a bright gleam where a light held in someone's hand points at the…

### `faygolover.zoneartifacts.compat`
- `CoreLight`, 27 — RP Core (optional): the family-wide answer to "does this entity carry a switched-on light".
- `CuriosWorn`, 62 — Items worn in Curios slots (RP Lights headlamp, belt lamp).

### `faygolover.zoneartifacts.config`
- `ModClientConfig`, 76 — config/fl_zone_arts-client.toml — per-player visual settings, a plain file that works with any launcher or config-menu mod.
- `ModCommonConfig`, 699 — config/fl_zone_arts-common.toml — global settings, read by the server.

### `faygolover.zoneartifacts.gear`
- `BoltCommands`, 29 — /fl_zone_arts bolts rescan: sort every block into a bolt sound group again, now (BoltSounds).
- `BoltEntity`, 138 — A thrown bolt or nut.
- `BoltItem`, 58 — A bolt or a nut (new or rusty — only the look differs): thrown like a snowball, not as far (BoltEntity).
- `BoltSounds`, 307 — Which sound a bolt makes on which block: six groups (concrete, dirt, metal, water, wood, default).

### `faygolover.zoneartifacts.item`
- `AnomalyPlacerItem`, 105 — One instance of this item exists per zone anomaly type (see ModItems).
- `ItemTooltips`, 35 — Item descriptions from the lang files: lines .0, .1, ...
- `PdaItem`, 33 — The anomaly KPK: used on an anomaly (a zone, a route point, Burning Fluff, a Hedgehog, a Web thread) it opens its settings window — sliders, switches, moving it.
- `PukhBlockItem`, 25 — Places Burning Fluff (on the underside of a block or on a wall), with its description.
- `TeslaRoutePlacerItem`, 87 — Builds routes for a route anomaly — one item per RouteKind (Tesla, Comet); a placer only sees and edits routes of its own kind.
- `WebPlacerItem`, 112 — Stretches Webs.

### `faygolover.zoneartifacts.network`
- `AmoebaEventPacket`, 50 — Server -> client: the Amoeba at pos starts gathering itself up, or bursts.
- `AnomalyStrikePacket`, 60 — Server -> client, sent once per struck target the instant a zone anomaly fires: draw bolts converging from the zone's surface points onto targetEntityId (see AnomalyArcRenderer#onStrike).
- `AnomalySyncHandler`, 66 — Keeps ClientAnomalyCache (via SyncAnomaliesPacket) up to date: a full resync to one player on join/dimension change, and a broadcast to everyone in a dimension whenever that dimension's anomalies change structurally (see the call …
- `BubblePopPacket`, 48 — Server -> clients: a soap bubble burst at at with blast radius.
- `ChemBurstPacket`, 53 — Server -> client: a Chemical Comet burst at center; its cloud settles on groundY, spreads to radius and lingers seconds.
- `CometBurstPacket`, 67 — Server -> client: a Comet (or, cold, a Cold Comet) just exploded at center.
- `ElectrifyPacket`, 59 — Server -> client: draw arcs crawling over entity entityId's surface for durationTicks, starting after delayTicks (Electra waits for its strike bolts to connect; the Tesla and the command start at once).
- `FogJetPacket`, 48 — Server -> client: a jet of acid vapour bursts up out of an Acid Fog at at (ground level).
- `GorePacket`, 61 — Server -> client: entity entityId was just torn apart by a Voronka or a Karusel — its body is hidden and bursts into blood: droplets flying off and splashes on the blocks around.
- `GraviPopPacket`, 54 — Server -> client: a Gravi pop starts sucking in at pos (on a surface facing normal made of block state stateId, or in the air).
- `GravityEventPacket`, 73 — Server -> client: something happened to the gravitational anomaly at pos: #START (a pull/spin phase began, with the phase's random seed — orbits are derived from it), #RELEASE (Plesh throw / Voronka tear / Karusel hit) or #BOUNCE …
- `KhlopushkaPacket`, 71 — Server -> clients: a Khlopushka clot appeared (ticks to its blast), blasted (value = radius), or — to one player — its flash blinded them (value = strength).
- `ModNetwork`, 91 — *без описания*
- `PdaApplyPacket` (record), 58 — Client -> server: the KPK window's values for target (by setting id), its switches (-1: none), a move by one block along each axis given (-1, 0, 1), or reset to the standards; or delete: remove the anomaly altogether.
- `PdaDataPacket` (record), 34 — Server -> client: what the KPK window shows (opens it, or refreshes the open one).
- `PdaOpenPacket` (record), 30 — Client -> server: the KPK was used on target; the server answers with a PdaDataPacket.
- `PoppyPacket`, 56 — Server -> clients: a creature's sleep on the poppy field changed (a micro-sleep of ticks, full sleep, waking up over ticks, or awake).
- `PukhEventPacket`, 87 — Server -> client, Burning Fluff: a spore puff shot from a at b (range blocks), or its strands crumbling away (pos, facing, range = length).
- `RazlomJetPacket`, 67 — Server -> client, every tick while a Razlom's jet burns: where the jet's end is aimed right now (aim), at whom (targetId, -1 = nobody, it's coasting) and for how many more ticks.
- `RemoveAnomalyPacket`, 83 — Client -&gt; server: "the client's own (non-authoritative) raytrace says I left-clicked anomaly typeId at pos; please remove it." Left-click needs this client-detect-then-send round trip for the same reason cycling used to: the an…
- `RustPacket`, 61 — Server -> clients: Rust's red-hot patch — heating up (#SPOT, age ticks old), gone cold (#CLEAR), gone off (#BLAST) or cooled by a snowball (#DISCHARGE).
- `SyncAnomaliesPacket`, 141 — Server -> client: "here is the full list of placed anomalies in this dimension right now." Sent on join/dimension change and whenever an anomaly is placed, removed or tuned.
- `SyncAnomalyCooldownPacket`, 61 — Server -> client, sent instead of a full SyncAnomaliesPacket whenever a single placed anomaly's cooldown flag flips (a burst firing, or its cooldown running out).
- `SyncTeslaRoutesPacket`, 76 — Server -> client: every Tesla route in a dimension — completed ones and drafts still being built — so a GM holding the placer can see and click them.
- `SyncWebsPacket`, 103 — Server -> clients: every Web of a dimension (threads, which are snapped, glint strength).
- `TeslaBurstPacket`, 64 — Server -> client: a Tesla just popped at center.
- `TeslaSync`, 39 — Builds and sends SyncTeslaRoutesPackets.
- `TeslaWaypointClickPacket`, 51 — Client -> server: "I left-clicked waypoint index of route routeId, which sits at pos".
- `WebEditPacket`, 55 — Client -> server: with the Web tool, left-click on a thread — remove it, or (sneaking) its whole web.
- `WebStrandPacket`, 53 — Server -> clients: a thread of a Web snapped (at at) or grew back.

### `faygolover.zoneartifacts.pda`
- `PdaParam` (record), 32 — One setting shown in the KPK: a slider from min to max in steps of step, its current value and the standard one.
- `PdaService`, 334 — The KPK, server-side: what can be set on each kind of anomaly (one list of settings, used both to show the window and to apply it, so the two never disagree), and applying a window's values — clamped to their ranges, whatever the …
- `PdaTarget` (record), 52 — What the KPK is pointed at: a zone anomaly (typeId + anchor pos), a completed route (its id and the clicked point: index + pos), a block anomaly (Burning Fluff, Hedgehog: pos) or a Web (id).
- `PdaView` (record), 35 — What the KPK window shows for one target: its name, its settings, the four switches (packed as bits: 0 enabled, 1 harmful, 2 audible, 3 visible, 4 acts on creative / spectators; -1 = it has none), which of them it has at all (swit…

### `faygolover.zoneartifacts.registry`
- `ModBlockEntities`, 26 — *без описания*
- `ModBlocks`, 41 — Block anomalies.
- `ModCreativeTabs`, 77 — *без описания*
- `ModEntities`, 93 — *без описания*
- `ModItems`, 118 — *без описания*
- `ModParticles`, 44 — The thermal anomalies' own particles (textures in assets/fl_zone_arts/textures/particle, sprite lists in assets/fl_zone_arts/particles).
- `ModSounds`, 91 — Electra's custom sound cues.

### `faygolover.zoneartifacts.tesla`
- `ChemComet`, 45 — The Chemical Comet's fixed characteristics.
- `ChemCometEntity`, 77 — The Chemical Comet: a clot of gas flying a route (all the flying, chasing and respawning is the Tesla's, TeslaEntity).
- `ColdCometEntity`, 18 — The Cold Comet: the CometEntity in soul fire — freezes where the Comet burns.
- `Comet`, 54 — The Comet's fixed characteristics.
- `CometEntity`, 222 — The Comet: a fireball flying a route.
- `Gravi`, 40 — Gravi's fixed characteristics.
- `GraviEntity`, 232 — Gravi (see Gravi): an invisible route anomaly that passes through everything and leaves a chain of small gravitational pops behind it.
- `RouteKind` (enum), 121 — What flies a route: the Tesla (ball of lightning) or the Comet (fireball).
- `Tesla`, 43 — The Tesla's fixed characteristics — what used to live in tesla/tesla.json.
- `TeslaCommands`, 140 — /fl_zone_arts tesla ...
- `TeslaDrafts`, 118 — Routes that are still being built, one per GM at most.
- `TeslaEntity`, 503 — The Tesla: a ball of lightning flying a closed route of waypoints (TeslaRoute).
- `TeslaGeometry`, 77 — Waypoint geometry shared by the server (click resolution) and the client (rendering and the left-click raytrace), so both sides always agree on where a waypoint is and how big it is.
- `TeslaRoute`, 168 — A completed route: a closed loop of waypoints, the anomaly flying it (RouteKind: a Tesla or a Comet — the fields keep the "tesla" names from before the Comet existed), and that anomaly's settings (changed with the tuners).
- `TeslaRouteSavedData`, 80 — Per-dimension persistence for completed Tesla routes.
- `TeslaRouteService`, 276 — Everything a route placer (Tesla, Comet) does, server-side.
- `TeslaRouteWatchdog`, 59 — Every #CHECK_TICKS ticks each route checks that its Tesla / Comet / Gravi still exists — it can be killed with a command.
- `TeslaServerEvents`, 75 — Server-side glue for Tesla routes: resetting unfinished routes when their builder leaves (logout, dimension change, server stop), keeping clients' route lists in sync, and stopping the placer from opening doors/chests when a GM cl…

### `faygolover.zoneartifacts.tuner`
- `TunerKind` (enum), 50 — What a tuner changes, and by how much per click (left-click lowers, right-click raises; the larger step is used while sneaking).

Классов без описания: 5 из 210.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `EntityJoinLevelEvent` | `GravityEngine.onEntityJoin` |
| `EntityRenderersEvent.RegisterRenderers` | `TeslaClientSetup.onRegisterRenderers` |
| `FMLClientSetupEvent` | `ZaLatePass.onClientSetup` |
| `InputEvent.InteractionKeyMappingTriggered` | `PoppyClient.onInteract`, `WebClient.onInteract` |
| `LivingDeathEvent` | `GravityEngine.onLivingDeath`, `PoppyEngine.onDeath` |
| `LivingEvent.LivingTickEvent` | `SwampClient.onLivingTick`, `SwampEngine.onLivingTick` |
| `LivingFallEvent` | `GravityEngine.onLivingFall` |
| `LivingHurtEvent` | `PoppyEngine.onHurt` |
| `MovementInputUpdateEvent` | `PoppyClient.onInput`, `PsiClient.onInput` |
| `PlaySoundEvent` | `SoundFx.onPlaySound`, `SwampClient.onPlaySound` |
| `PlayerEvent.PlayerChangedDimensionEvent` | `AnomalySyncHandler.onPlayerChangedDimension`, `TeslaServerEvents.onPlayerChangedDimension`, `WebEngine.onChangedDimension` |
| `PlayerEvent.PlayerLoggedInEvent` | `AnomalySyncHandler.onPlayerLoggedIn`, `TeslaServerEvents.onPlayerLoggedIn`, `WebEngine.onLogin` |
| `PlayerEvent.PlayerLoggedOutEvent` | `TeslaServerEvents.onPlayerLoggedOut` |
| `PlayerEvent.StartTracking` | `PoppyEngine.onStartTracking` |
| `PlayerInteractEvent.LeftClickBlock` | `ClientAnomalyInputHandler.onLeftClickBlock`, `TeslaClientHandler.onLeftClickBlock` |
| `PlayerInteractEvent.LeftClickEmpty` | `ClientAnomalyInputHandler.onLeftClickEmpty`, `TeslaClientHandler.onLeftClickEmpty` |
| `PlayerInteractEvent.RightClickBlock` | `AnomalyInteractionHandler.onRightClickBlock`, `PdaClientHandler.onRightClickBlock`, `TeslaServerEvents.onRightClickBlock` |
| `PlayerInteractEvent.RightClickItem` | `PdaClientHandler.onRightClickItem` |
| `RegisterCommandsEvent` | `BoltCommands.onRegisterCommands`, `ElectrifyCommand.onRegisterCommands`, `TeslaCommands.onRegisterCommands` |
| `RegisterDimensionSpecialEffectsEvent` | `SumrakClient.onRegisterEffects` |
| `RegisterGuiOverlaysEvent` | `ScreenFx.onRegisterOverlays` |
| `RegisterParticleProvidersEvent` | `ThermalClientSetup.onRegisterParticleProviders` |
| `RegisterShadersEvent` | `ZaShaders.onRegisterShaders` |
| `RenderLevelStageEvent` | `AmoebaClient.onRenderLevelStage`, `AnomalyArcRenderer.onRenderLevelStage`, `AnomalyHighlightRenderer.onRenderLevelStage`, `CometEffectRenderer.onRenderLevelStage`, `CometLateRender.onRenderLevelStage`, `CometRenderer.renderPending`, `Distortion.onRenderLevelStage`, `ElectrifyRenderer.onRenderLevelStage`, `FantomClient.onRenderLevelStage`, `FireflyClient.onRenderLevelStage`, `Gas.onRenderLevelStage`, `GoreClient.onRenderLevelStage`, `GravityRenderer.onRenderLevelStage`, `GravityRenderer.onRenderShapes`, `Halo.onRenderLevelStage`, `IneyFrostRenderer.onRenderLevelStage`, `KamertonClient.onRenderLevelStage`, `KhlopushkaClient.onRenderLevelStage`, `KiselClient.onRenderLevelStage`, `PoppyClient.onRenderLevelStage`, `RazlomRenderer.onRenderLevelStage`, `RazlomRenderer.renderDark`, `RustClient.onRenderLevelStage`, `ScreenFx.onRenderLevelStage`, `Smoke.onRenderLevelStage`, `SumrakClient.onRenderStage`, `SwampClient.onRenderLevelStage`, `TeslaEffectRenderer.onRenderLevelStage`, `TeslaRouteRenderer.onRenderLevelStage`, `WebClient.onRenderLevelStage`, `ZaLatePass.onRenderLevelStage` |
| `RenderLivingEvent.Post` | `PoppyClient.onRenderLivingPost` |
| `RenderLivingEvent.Pre` | `GoreClient.onRenderLiving`, `PoppyClient.onRenderLivingPre`, `SumrakClient.onRenderLiving` |
| `RenderNameTagEvent` | `SumrakClient.onNameTag` |
| `ServerStartedEvent` | `BoltSounds.onServerStarted` |
| `ServerStoppingEvent` | `ChemClouds.onServerStopping`, `PukhSpores.onServerStopping`, `TeslaServerEvents.onServerStopping` |
| `TickEvent.ClientTickEvent` | `AcidFogClient.onClientTick`, `AmoebaClient.onClientTick`, `AnomalyAmbientSoundHandler.onClientTick`, `ChemClient.onClientTick`, `DymkaClient.onClientTick`, `FireflyClient.onClientTick`, `FogCulling.onClientTick`, `Gas.onClientTick`, `GraviClient.onClientTick`, `GravityClientHandler.onClientTickEnd`, `GravityClientHandler.onClientTickStart`, `KamertonClient.onClientTick`, `KhlopushkaClient.onClientTick`, `KiselClient.onClientTick`, `LiftClient.onClientTickEnd`, `LiftClient.onClientTickStart`, `PoppyClient.onClientTick`, `PsiClient.onClientTick`, `PukhClient.onClientTick`, `RazlomClientHandler.onClientTick`, `RustClient.onClientTick`, `SumrakClient.onClientTick`, `SwampClient.onClientTick`, `TeslaClientHandler.onClientTick`, `ThermalClientHandler.onClientTick` |
| `TickEvent.LevelTickEvent` | `AnomalyEngine.onLevelTick`, `ChemClouds.onLevelTick`, `GravityEngine.onLevelTick`, `PoppyEngine.onLevelTick`, `PukhSpores.onLevelTick`, `PukhVolley.onLevelTick`, `RustEngine.onLevelTick`, `SwampEngine.onLevelTick`, `TeslaRouteWatchdog.onLevelTick`, `WebEngine.onLevelTick` |
| `TickEvent.PlayerTickEvent` | `SwampClient.onPlayerTick` |
| `TickEvent.RenderTickEvent` | `PoppyClient.onRenderTick` |
| `ViewportEvent.ComputeFogColor` | `ScreenFx.onFogColor` |
| `ViewportEvent.RenderFog` | `ScreenFx.onRenderFog` |

## 4. Сеть

Канал: `faygolover/zoneartifacts/network/ModNetwork.java`, имя `main`, версия протокола `16`.
- `SyncAnomaliesPacket` (направление не указано в регистрации)
- `RemoveAnomalyPacket` (направление не указано в регистрации)
- `AnomalyStrikePacket` (направление не указано в регистрации)
- `SyncAnomalyCooldownPacket` (направление не указано в регистрации)
- `SyncTeslaRoutesPacket` (направление не указано в регистрации)
- `TeslaWaypointClickPacket` (направление не указано в регистрации)
- `TeslaBurstPacket` (направление не указано в регистрации)
- `ElectrifyPacket` (направление не указано в регистрации)
- `PdaOpenPacket` (направление не указано в регистрации)
- `CometBurstPacket` (направление не указано в регистрации)
- `RazlomJetPacket` (направление не указано в регистрации)
- `GravityEventPacket` (направление не указано в регистрации)
- `GorePacket` (направление не указано в регистрации)
- `ChemBurstPacket` (направление не указано в регистрации)
- `GraviPopPacket` (направление не указано в регистрации)
- `AmoebaEventPacket` (направление не указано в регистрации)
- `PukhEventPacket` (направление не указано в регистрации)
- `FogJetPacket` (направление не указано в регистрации)
- `PoppyPacket` (направление не указано в регистрации)
- `RustPacket` (направление не указано в регистрации)
- `BubblePopPacket` (направление не указано в регистрации)
- `KhlopushkaPacket` (направление не указано в регистрации)
- `SyncWebsPacket` (направление не указано в регистрации)
- `WebStrandPacket` (направление не указано в регистрации)
- `WebEditPacket` (направление не указано в регистрации)
- `PdaDataPacket` (направление не указано в регистрации)
- `PdaApplyPacket` (направление не указано в регистрации)

Классы с обработчиками пакетов (27): `AmoebaEventPacket`, `AnomalyStrikePacket`, `BubblePopPacket`, `ChemBurstPacket`, `CometBurstPacket`, `ElectrifyPacket`, `FogJetPacket`, `GorePacket`, `GraviPopPacket`, `GravityEventPacket`, `KhlopushkaPacket`, `PdaApplyPacket`, `PdaDataPacket`, `PdaOpenPacket`, `PoppyPacket`, `PukhEventPacket`, `RazlomJetPacket`, `RemoveAnomalyPacket`, `RustPacket`, `SyncAnomaliesPacket`, `SyncAnomalyCooldownPacket`, `SyncTeslaRoutesPacket`, `SyncWebsPacket`, `TeslaBurstPacket`, `TeslaWaypointClickPacket`, `WebEditPacket`, `WebStrandPacket`.

## 5. Команды

- `faygolover/zoneartifacts/anomaly/ElectrifyCommand.java`: `fl_zone_arts` `electrify`
- `faygolover/zoneartifacts/gear/BoltCommands.java`: `fl_zone_arts` `bolts` `rescan`
- `faygolover/zoneartifacts/tesla/TeslaCommands.java`: `fl_zone_arts` `tesla` `list` `cleanup` `flag`

## 6. Данные

- getPersistentData: `AnomalyCombat` ×1, теги: `fl_zone_arts_fled`
- SavedData: `AnomalySavedData`, файл "fl_zone_arts_anomalies"
- getPersistentData: `GravityEngine` ×3
- getPersistentData: `Lift` ×1
- getPersistentData: `LiftEngine` ×2
- SavedData: `WebSavedData`, файл "fl_zone_arts_webs"
- свои файлы на диске: `BoltSounds`
- getPersistentData: `TeslaEntity` ×2
- SavedData: `TeslaRouteSavedData`, файл "fl_zone_arts_tesla_routes"

## 7. Конфиги

- common: `ModCommonConfig`, вызовов define 178
- client: `ModClientConfig`, вызовов define 1

## 8. Что берёт из RP Core и что ему даёт

Методы `RpCoreAPI` (сколько вызовов): `carriesLight` 1.

## 9. Связи с другими модами

Проверки `isLoaded`: `curios` (CuriosWorn); `rpcore` (CoreLight).

Отражение (`Class.forName`):
- `FogCulling` → `me.jellysquid.mods.sodium.client.SodiumClientMod`
- `CuriosWorn` → `top.theillusivec4.curios.api.CuriosApi`
- `CuriosWorn` → `top.theillusivec4.curios.api.type.capability.ICuriosItemHandler`
- `CuriosWorn` → `net.minecraftforge.items.IItemHandlerModifiable`

Пакеты других модов семейства в коде: `flrender` — 35 файлов; `rpcore` — 1 файлов.

## 10. Зарегистрированные id

- `faygolover/zoneartifacts/registry/ModBlockEntities.java` (2): `pukh` `ezhik`
- `faygolover/zoneartifacts/registry/ModBlocks.java` (2): `pukh` `ezhik`
- `faygolover/zoneartifacts/registry/ModCreativeTabs.java` (2): `anomalies` `gear`
- `faygolover/zoneartifacts/registry/ModEntities.java` (7): `tesla` `comet` `cold_comet` `chem_comet` `gravi` `soap_bubble` `bolt`
- `faygolover/zoneartifacts/registry/ModItems.java` (23): `electra_placer` `zharka_placer` `iney_placer` `razlom_placer` `cold_razlom_placer` `plesh_placer` `voronka_placer` `karusel_placer` `podushka_placer` `tesla_placer` `comet_placer` `cold_comet_placer` `chem_comet_placer` `gravi_placer` `lift_placer` `amoeba_placer` `kisel_placer` `acid_fog_placer` `pautina_placer` `ezhik` `pukh` `pukh_flesh` `pda`
- `faygolover/zoneartifacts/registry/ModParticles.java` (10): `ember` `heat_smoke` `frost_mist` `grav_dust` `blood` `pukh_flake` `pukh_spore` `kisel_bubble` `chem_drop` `poppy_petal`
