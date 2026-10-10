# 0.1.36.0: bolts and nuts — textures (new steel / rust) and the simple 3D item models.
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'fl_zone_arts')
TEX = os.path.join(ASSETS, 'textures', 'item')
MOD = os.path.join(ASSETS, 'models', 'item')


def metal(name, base, dark, light, rust=False, seed=1):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            t = 0.5 + 0.35 * math.sin(x * 0.7 + y * 0.35) + rnd.uniform(-0.25, 0.25)
            if rust and rnd.random() < 0.28:
                t -= 0.45
            t = max(0.0, min(1.0, t))
            c = tuple(int(dark[i] + (light[i] - dark[i]) * t) for i in range(3))
            if rust and rnd.random() < 0.12:
                c = tuple(int(v * 0.8) for v in base)
            im.putpixel((x, y), c + (255,))
    # thread grooves along the shaft area (rows 0..8 of the texture)
    for y in range(0, 9, 2):
        for x in range(0, 16):
            r, g, b, a = im.getpixel((x, y))
            im.putpixel((x, y), (int(r * 0.78), int(g * 0.78), int(b * 0.78), a))
    im.save(os.path.join(TEX, name + '.png'))


def box(frm, to, tex='#metal'):
    faces = {}
    sx, sy, sz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
    for f, (w, h) in dims.items():
        faces[f] = {'uv': [0, 0, max(1, w), max(1, h)], 'texture': tex}
    return {'from': frm, 'to': to, 'faces': faces}


DISPLAY = {
    'gui': {'rotation': [25, 45, 0], 'translation': [0, 0, 0], 'scale': [1.1, 1.1, 1.1]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 1, 0], 'scale': [0.15, 0.15, 0.15]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.25, 0.25, 0.25]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.13, 0.13, 0.13]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.13, 0.13, 0.13]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 3, 0], 'scale': [0.14, 0.14, 0.14]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 3, 0], 'scale': [0.14, 0.14, 0.14]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 13, 7], 'scale': [0.15, 0.15, 0.15]},
}


def model(name, elements, texture):
    json.dump({'textures': {'metal': texture, 'particle': texture}, 'elements': elements, 'display': DISPLAY},
              open(os.path.join(MOD, name + '.json'), 'w', encoding='utf8'), indent=2)


def child(name, parent, texture):
    json.dump({'parent': 'fl_zone_arts:item/' + parent, 'textures': {'metal': texture, 'particle': texture}},
              open(os.path.join(MOD, name + '.json'), 'w', encoding='utf8'), indent=2)


metal('gear_steel', (150, 156, 164), (92, 98, 108), (214, 220, 228), seed=3)
metal('gear_rust', (120, 64, 34), (70, 38, 22), (176, 98, 50), rust=True, seed=5)

# Bolt: a hex-ish head (two crossed blocks) on a threaded shaft.
bolt = [box([7, 2, 7], [9, 11, 9]), box([5, 11, 6], [11, 14, 10]), box([6, 11.1, 5], [10, 13.9, 11])]
model('bolt', bolt, 'fl_zone_arts:item/gear_steel')
child('bolt_rusty', 'bolt', 'fl_zone_arts:item/gear_rust')
# Nut: a square ring with a hole.
nut = [box([4, 6, 4], [12, 9, 6]), box([4, 6, 10], [12, 9, 12]), box([4, 6, 6], [6, 9, 10]), box([10, 6, 6], [12, 9, 10]),
       box([5, 6, 3], [11, 9, 4]), box([5, 6, 12], [11, 9, 13]), box([3, 6, 5], [4, 9, 11]), box([12, 6, 5], [13, 9, 11])]
model('nut', nut, 'fl_zone_arts:item/gear_steel')
child('nut_rusty', 'nut', 'fl_zone_arts:item/gear_rust')
print('done')
