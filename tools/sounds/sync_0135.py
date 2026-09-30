# 0.1.34.2: the mod's sounds rebuilt from the user's anomaly_sfx/approved (and anomaly_sfx/processed,
# see clean_0135.py): every file copied under the event's own name, sounds.json, ModSounds and the
# subtitles brought in line, files no event uses any more removed.
import json
import os
import re
import shutil
import subprocess

import soundfile as sf

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'fl_zone_arts')
SND = os.path.join(RES, 'sounds')
MODSOUNDS = os.path.join(ROOT, 'src', 'main', 'java', 'faygolover', 'zoneartifacts', 'registry', 'ModSounds.java')
import os as _os
# The sound sources: a folder next to the repository (anomaly_sfx/approved, …/processed), or ZONE_SFX.
SFX = _os.environ.get('ZONE_SFX', _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), '..', '..', '..', 'anomaly_sfx'))
A = SFX + '/approved/'
P = SFX + '/processed/'


def v(name, *files):
    return [(name, f) for f in files]


# event -> list of source files (None: keep the vanilla-event entries as they are)
EVENTS = {
    'electra_idle': [A + 'electra/electra_idle1.ogg', A + 'electra/electra_idle2.ogg'],
    'electra_blast_nut': [A + 'electra/electra_blast_item1.ogg', A + 'electra/electra_blast_ipem2.ogg'],
    'electra_blast_living': [A + 'electra/electra_blast_living.ogg'],
    'electra_hit': [A + 'electra/electra_hit1.ogg'],
    'electra_hit1': [A + 'electra/electra_hit2.ogg'],
    'tesla_idle': [A + 'tesla/tesla_idle.ogg'],
    'tesla_blast': [A + 'tesla/tesla_blast.ogg'],
    'zharka_idle': [A + 'zharka/zharka_idle.ogg'],
    'zharka_blow': [A + 'zharka/zharka_blow.ogg'],
    'iney_idle': None,
    'iney_enter': [A + 'iney/iney_enter.ogg'],
    'razlom_idle': [A + 'razlom/razlom_idle.ogg'],
    'razlom_jet': [A + 'razlom/razlom_blow.ogg'],
    'comet_idle': [A + 'comet/comet_idle.ogg'],
    'comet_explode': [A + 'comet/comet_explode1.ogg', A + 'comet/comet_explode2.ogg'],
    'plesh_blowout': [A + 'plesh/plesh_blowout1.ogg'],
    'plesh_idle': [A + f'plesh/plesh_idle{i}.ogg' for i in (1, 2, 3)],
    # 0.1.35.2: the low hum plays all the time; the gusts above only now and then.
    'plesh_hum': [A + 'plesh/plesh_idle4.ogg'],
    'karusel_hum': [A + 'plesh/plesh_idle4.ogg'],
    'voronka_blowout': [P + 'voronka/voronka_blowout.ogg'],
    'voronka_idle': [A + 'voronka/voronka_idle.ogg'],
    'karusel_blowout': [A + 'karusel/karusel_blowout.ogg', A + 'karusel/karusel_blowout1.ogg'],
    'karusel_idle': [A + f'karusel/karusel_idle{i}.ogg' for i in (1, 2, 3)],
    'podushka_bounce': None,
    'anomaly_body_tear': [A + 'gravity_all/gravity_all_body_tear.ogg'],
    'gravy_hit': [P + f'gravi/gravi_pop{i}.ogg' for i in (1, 2, 3, 4)],
    'gravi_idle': [P + 'gravi/gravi_idle.ogg'],
    'chem_comet_idle': [A + 'chem_comet/chem_comet_idle.ogg'],
    'chem_comet_burst': [A + 'chem_comet/chem_comet_burst.ogg'],
    'amoeba_gather': None,
    'amoeba_pop': [A + 'amoeba/amoeba_pop.ogg'],
    'pukh_puff': [A + 'pukh/pukh_puff.ogg'],
    'kisel_idle': [A + 'kisel/buzz_idle.ogg'],
    'kisel_hit': [A + 'kisel/buzz_hit.ogg'],
    'fog_idle': [A + 'acid_fog/acid_fog_idle.ogg'],
    'fog_jet': [A + 'acid_fog/acid_fog_jet1.ogg', A + 'acid_fog/acid_fog_jet2.ogg'],
    'dymka_distant': [A + f'dymka/dymka_distant{i}.ogg' for i in (1, 2, 4, 5, 6)],
    'dymka_inside': [P + 'dymka/dymka_inside.ogg'],
    'sumrak_drone': [A + 'sumrak/sumrak_drone.ogg'],
    'poppy_hum': [A + 'poppy_field/poppy_field_hum.ogg'],
    'rust_blast': [A + 'rust/rust_blast.ogg'],
    'rust_hiss': [A + 'rust/rust_hiss.ogg'],
    'rust_crackle': [A + 'rust/rust_crackle.ogg'],
    'bubble_pop': [A + 'soap_bubbles/soap_bubbles_pop1.ogg', A + 'soap_bubbles/soap_bubbles_pop2.ogg']
                  + [P + f'soap_bubbles/soap_bubbles_pop{i}.ogg' for i in (3, 4, 5)],
    'khlopushka_charge': [P + f'khlopushka/khlopushka_charge{i}.ogg' for i in (1, 2, 3, 4)],
    'khlopushka_bang': [P + f'khlopushka/khlopushka_bang{i}.ogg' for i in (1, 2, 3, 4)],
    'kamerton_ring': [P + 'kamerton/kamerton_idle.ogg'],
    'kamerton_cut': [P + f'kamerton/kamerton_hit{i}.ogg' for i in range(1, 7)],
    'kamerton_shatter': [P + f'kamerton/kamerton_hit{i}.ogg' for i in range(1, 7)],
    'web_snap': [A + f'pautina/pautina_snap{i}.ogg' for i in (1, 2, 3)],
    'psi_voices_l': [A + 'psi_zone/psi_zone_voices_left.ogg'],
    'psi_voices_r': [A + 'psi_zone/psi_zone_voices_right.ogg'],
    'psi_polter': [A + 'psi_zone/psi_zone_polter.ogg'],
}
# Extra per-entry fields.
EXTRA = {
    'dymka_distant': {'attenuation_distance': 48},
    # Kamerton: quiet — heard only close to it, normally inside.
    'kamerton_ring': {'attenuation_distance': 8},
    'kamerton_cut': {'attenuation_distance': 10},
    'kamerton_shatter': {'attenuation_distance': 10},
}
NEW_SUBTITLES = {
    'tesla_blast': ('Тесла бьёт разрядом', 'Tesla discharges'),
    'zharka_blow': ('Жарка вспыхивает', 'Zharka flares up'),
    'razlom_idle': ('Потрескивает огонь Разлома', 'Razlom fire crackles'),
    'gravi_idle': ('Гудит Грави', 'Gravi hums'),
    'dymka_inside': ('Глухо шумит дымка', 'The haze rumbles'),
    'plesh_hum': ('Гудит Комариная плешь', 'Mosquito Bald hums'),
    'karusel_hum': ('Гудит Карусель', 'Karusel hums'),
}

old = json.load(open(os.path.join(RES, 'sounds.json'), encoding='utf8'))
new = {}
keep_files = set()
for key, files in EVENTS.items():
    if files is None:
        new[key] = old[key]
        continue
    entries = []
    for i, src in enumerate(files, 1):
        name = key if len(files) == 1 else f'{key}{i}'
        dst = os.path.join(SND, name + '.ogg')
        shutil.copyfile(src, dst)
        keep_files.add(name + '.ogg')
        e = {'name': f'fl_zone_arts:{name}'}
        if sf.info(dst).duration > 15.0:
            e['stream'] = True
        e.update(EXTRA.get(key, {}))
        entries.append(e if len(e) > 1 else e['name'])
    new[key] = {'sounds': entries, 'subtitle': old.get(key, {}).get('subtitle', f'subtitles.fl_zone_arts.{key}')}
json.dump(new, open(os.path.join(RES, 'sounds.json'), 'w', encoding='utf8'), ensure_ascii=False, indent=2)
open(os.path.join(RES, 'sounds.json'), 'a', encoding='utf8').write('\n')

for f in sorted(os.listdir(SND)):
    if f.endswith('.ogg') and f not in keep_files:
        subprocess.run(['git', 'rm', '-q', '-f', '--ignore-unmatch', os.path.join(SND, f)], cwd=ROOT)
        if os.path.exists(os.path.join(SND, f)):
            os.remove(os.path.join(SND, f))
        print('removed', f)

gone = [k for k in old if k not in new]
for lang, idx in (('ru_ru', 0), ('en_us', 1)):
    p = os.path.join(RES, 'lang', lang + '.json')
    d = json.load(open(p, encoding='utf8'))
    for k in gone:
        d.pop(f'subtitles.fl_zone_arts.{k}', None)
    for k, texts in NEW_SUBTITLES.items():
        d[f'subtitles.fl_zone_arts.{k}'] = texts[idx]
    json.dump(d, open(p, 'w', encoding='utf8'), ensure_ascii=False, indent=2)
    open(p, 'a', encoding='utf8').write('\n')

src = open(MODSOUNDS, encoding='utf8').read()
for k in gone:
    src = re.sub(r'\n    public static final RegistryObject<SoundEvent> \w+ = register\("' + re.escape(k) + r'"\);', '', src)
for k in NEW_SUBTITLES:
    if f'register("{k}")' in src:
        continue
    last = [m.end() for m in re.finditer(r'\n    public static final RegistryObject<SoundEvent> \w+ = register\("[^"]+"\);', src)][-1]
    src = src[:last] + f'\n    public static final RegistryObject<SoundEvent> {k.upper()} = register("{k}");' + src[last:]
open(MODSOUNDS, 'w', encoding='utf8').write(src)
print('events:', len(new), 'removed events:', gone, 'files:', len(keep_files))
