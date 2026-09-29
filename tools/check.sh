#!/bin/bash
# Parse/semantic check without MC: report errors except missing external symbols.
cd "$1"
shift
javac -Xmaxerrs 100000 -proc:none -implicit:none -sourcepath . -d "${TMPDIR:-/tmp}/fl_check_out" "$@" 2>&1 | python3 -c "
import sys,re
lines=sys.stdin.read().split('\n')
out=set()
internal=re.compile(r'\b(Distortion|Lens|Haze|Patch|VoronkaLens|GravityLoopSound|CometRenderer|DistortionSources|Gas|FramePuff|Puff|ChemClient|ChemComet|ChemClouds|Gravi|Lift|GoreClient|AnomalyCombat|TeslaEntity|ModCommonConfig|ModSounds|ModParticles|ModEntities|ModItems|RouteKind|AnomalyTypeIds|AnomalyDefaults|GravityEngine|Razlom|Amoeba|Pukh|FireDraw|GlowRenderType|ClientAnomalyCache|SyncAnomaliesPacket|AnomalyGeometry|TeslaRoute|TeslaGeometry|ModNetwork|softenFall|chaseDestination|isChasing|addStain|lateStagePose|routeId|flee|Kisel|AcidFog|ZoneLoopSound|KiselEngine|AcidFogEngine|FogJetPacket|AmoebaEngine|LiftEngine|TeslaRouteWatchdog|PukhBlockEntity|TunerClickPacket|KiselBubbleParticle|AcidFogClient|KiselClient|Swamp|SwampPhysics|SwampEngine|SwampClient|DymkaClient|SumrakClient|PsiClient|PsiEngine|PoppyEngine|PoppyClient|PoppyPacket|RustEngine|RustClient|RustPacket|EzhikBlock|EzhikBlockEntity|EzhikRenderer|SoundFx|ScreenFx|HumLoop|PoppyPetalParticle|Zone|State|Columns|Phase|Muffled|EZHIK|SWAMP|DYMKA|SUMRAK|PSI|POPPY|RUST|cooldown|damage|allowed|finish|begin|BubbleEntity|BubbleEngine|BubbleClient|BubbleRenderer|BubblePopPacket|KhlopushkaEngine|KhlopushkaClient|KhlopushkaPacket|FireflyClient|KamertonEngine|KamertonClient|WebSavedData|WebEngine|WebClient|WebPlacerItem|SyncWebsPacket|WebStrandPacket|WebEditPacket|FantomClient|Web|Strand|Hit|BUBBLE|BUBBLES|KHLOPUSHKA|FIREFLY|KAMERTON|FANTOM|WEB|range|yaw)\b')
for i,l in enumerate(lines):
    m=re.match(r'(faygolover/\S+\.java):(\d+): error: (.*)',l)
    if not m: continue
    msg=m.group(3)
    if 'does not exist' in msg: continue
    if 'cannot find symbol' in msg:
        sym=''
        for k in range(i+1,i+6):
            if k<len(lines) and 'symbol:' in lines[k]:
                sym=lines[k].strip(); break
        if not internal.search(sym): continue
        out.add(m.group(1)+':'+m.group(2)+' '+sym)
    elif 'does not override' in msg or 'cannot access' in msg or 'incompatible types' in msg and 'Object' in msg:
        continue
    else:
        out.add(m.group(1)+':'+m.group(2)+' '+msg)
for o in sorted(out): print(o)
"
