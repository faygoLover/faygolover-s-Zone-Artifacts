"""Витрина аномалий для проверки с шейдерами: суперплоский ночной мир, белая бетонная платформа и стена,
перед стеной в ряд все аномалии Zone Artifacts с табличками.

Образец level.dat — любой суперплоский мир этой же сборки (там верные данные Forge и список датапаков).
Аномалии, маршруты (Тесла, кометы, Грави) и паутина пишутся прямо в data/ мира. Платформу, стену, таблички,
Пух и Ёжика ставит датапак za_showcase при первом заходе (чанки сперва грузятся через forceload).

    python tools/make_showcase_world.py <папка saves> [образец] [имя]
"""
import json
import os
import shutil
import sys

import nbtlib
from nbtlib.tag import Byte, Compound, Double, Float, Int, List, Long, LongArray, String

saves = sys.argv[1]
src = sys.argv[2] if len(sys.argv) > 2 else "flat"
name = sys.argv[3] if len(sys.argv) > 3 else "ZA витрина"

# ---- раскладка -----------------------------------------------------------------------------------
FLOOR = -53          # верх платформы (суперплоский: бедрок + 10 камня + трава)
Y = FLOOR + 1        # где стоят аномалии
WALL_Z = 8           # стена z = 8..9
SIGN_Z = -6          # таблички перед аномалиями
VIEW_Z = -9          # точка появления

# (что, id/вид, размер по умолчанию, подпись)
STATIONS = [
    ("zone", "electra", 1, "Электра"),
    ("zone", "zharka", 1, "Жарка"),
    ("zone", "iney", 1, "Иней"),
    ("zone", "razlom", 1, "Разлом"),
    ("zone", "cold_razlom", 1, "Холодный разлом"),
    ("zone", "plesh", 1, "Комариная плешь"),
    ("zone", "voronka", 1, "Воронка"),
    ("zone", "karusel", 1, "Карусель"),
    ("zone", "podushka", 1, "Подушка"),
    ("zone", "lift", 1, "Лифт"),
    ("zone", "kisel", 1, "Кисель"),
    ("zone", "acid_fog", 1, "Кислотный туман"),
    ("zone", "amoeba", 1, "Амёба"),
    ("zone", "tryasina", 5, "Трясина"),
    ("zone", "dymka", 8, "Дымка"),
    ("zone", "sumrak", 6, "Сумрак"),
    ("zone", "psi_zone", 8, "Пси-зона"),
    ("zone", "poppy_field", 6, "Маковое поле"),
    ("zone", "rust", 6, "Ржавчина"),
    ("zone", "soap_bubbles", 6, "Мыльные пузыри"),
    ("zone", "khlopushka", 8, "Хлопушка"),
    ("zone", "svetlyachok", 8, "Светлячок"),
    ("zone", "kamerton", 5, "Камертон"),
    ("zone", "fantom_light", 4, "Фантомный свет"),
    ("route", "tesla", 6, "Тесла"),
    ("route", "comet", 6, "Комета"),
    ("route", "cold_comet", 6, "Холодная комета"),
    ("route", "chem_comet", 6, "Химическая комета"),
    ("route", "gravi", 6, "Грави"),
    ("pukh", None, 3, "Жгучий пух"),
    ("ezhik", None, 3, "Ёжик"),
    ("web", None, 4, "Паутина"),
]

x = 0
placed = []
for kind, tid, size, label in STATIONS:
    width = max(size, 2) + 6
    cx = x + width // 2
    placed.append((kind, tid, size, label, cx))
    x += width
X0, X1 = -4, x + 4

# ---- мир -----------------------------------------------------------------------------------------
out = os.path.join(saves, name)
if os.path.exists(out):
    shutil.rmtree(out)
os.makedirs(os.path.join(out, "data"))

f = nbtlib.load(os.path.join(saves, src, "level.dat"))
d = f["Data"]
data_version = int(d["DataVersion"])
d["LevelName"] = String(name)
d["allowCommands"] = Byte(1)
d["GameType"] = Int(1)
d["DayTime"] = Long(18000)          # полночь
d["Time"] = Long(0)
d["raining"] = Byte(0)
d["thundering"] = Byte(0)
d["rainTime"] = Int(0)
d["thunderTime"] = Int(0)
d["clearWeatherTime"] = Int(1_000_000)
d["SpawnX"], d["SpawnY"], d["SpawnZ"] = Int(0), Int(Y), Int(VIEW_Z)
d["SpawnAngle"] = Float(0.0)        # лицом к стене (на юг)
d.pop("Player", None)
d.pop("ScheduledEvents", None)
d["ScheduledEvents"] = List[Compound]([])

wg = d["WorldGenSettings"]
wg["generate_features"] = Byte(0)
gen = wg["dimensions"]["minecraft:overworld"]["generator"]
if gen["type"] != "minecraft:flat":
    sys.exit("Образец должен быть суперплоским миром")
st = gen["settings"]
st["structure_overrides"] = List[String]([])
st["features"] = Byte(0)
st["lakes"] = Byte(0)
st["layers"] = List[Compound]([
    Compound({"block": String("minecraft:bedrock"), "height": Int(1)}),
    Compound({"block": String("minecraft:stone"), "height": Int(10)}),
    Compound({"block": String("minecraft:grass_block"), "height": Int(1)}),
])

rules = d["GameRules"]
for rule in ("doDaylightCycle", "doWeatherCycle", "doMobSpawning", "doPatrolSpawning", "doTraderSpawning",
             "doInsomnia", "doWardenSpawning", "doFireTick"):
    rules[rule] = String("false")
rules["spawnRadius"] = String("0")

packs = d["DataPacks"]["Enabled"]
if "file/za_showcase" not in [str(p) for p in packs]:
    packs.append(String("file/za_showcase"))
f.save(os.path.join(out, "level.dat"))


def save_data(file_name, payload):
    nbtlib.File({"data": payload, "DataVersion": Int(data_version)}, gzipped=True).save(
        os.path.join(out, "data", file_name + ".dat"))


# ---- аномалии, маршруты, паутина -------------------------------------------------------------------
instances = []
routes = []
webs = []
for kind, tid, size, label, cx in placed:
    if kind == "zone":
        y = FLOOR if tid == "tryasina" else Y   # трясина ставится в сам блок поверхности
        instances.append(Compound({
            "type": String("fl_zone_arts:" + tid),
            "x": Int(cx), "y": Int(y), "z": Int(0),
            "size": Double(size),
            "yaw": Float(0.0),
            "enabled": Byte(1), "harmful": Byte(1), "audible": Byte(1), "visible": Byte(1),
            "targets_gm": Byte(0),
        }))
    elif kind == "route":
        h = Y + 2
        pts = [(cx - 2, h, -2), (cx + 2, h, -2), (cx + 2, h + 1, 2), (cx - 2, h + 1, 2)]
        packed = [((px & 0x3FFFFFF) << 38) | ((pz & 0x3FFFFFF) << 12) | (py & 0xFFF) for px, py, pz in pts]
        packed = [p - (1 << 64) if p >= (1 << 63) else p for p in packed]
        routes.append(Compound({"id": Int(len(routes) + 1), "kind": String(tid),
                                "waypoints": LongArray(packed)}))
    elif kind == "web":
        top = Y + 4
        strands = []
        for a, b in [((cx - 2, top, WALL_Z), (cx + 2, Y, 0)), ((cx + 2, top, WALL_Z), (cx - 2, Y, 0)),
                     ((cx - 2, Y + 1.5, 0), (cx + 2, Y + 1.5, 0)), ((cx, top + 1, WALL_Z), (cx, Y, -1))]:
            strands.append(Compound({"ax": Double(a[0] + 0.5), "ay": Double(a[1]), "az": Double(a[2]),
                                     "bx": Double(b[0] + 0.5), "by": Double(b[1]), "bz": Double(b[2])}))
        webs.append(Compound({"id": Int(1), "strands": List[Compound](strands)}))

save_data("fl_zone_arts_anomalies", Compound({"instances": List[Compound](instances)}))
save_data("fl_zone_arts_tesla_routes", Compound({"routes": List[Compound](routes), "next_id": Int(len(routes) + 1)}))
save_data("fl_zone_arts_webs", Compound({"webs": List[Compound](webs), "next_id": Int(2)}))

# ---- датапак: платформа, стена, таблички, Пух, Ёжик -------------------------------------------------
dp = os.path.join(out, "datapacks", "za_showcase")
fn = os.path.join(dp, "data", "za_showcase", "functions")
os.makedirs(fn)
os.makedirs(os.path.join(dp, "data", "minecraft", "tags", "functions"))
with open(os.path.join(dp, "pack.mcmeta"), "w", encoding="utf-8") as fp:
    json.dump({"pack": {"pack_format": 15, "description": "ZA витрина: платформа и таблички"}}, fp, ensure_ascii=False)
with open(os.path.join(dp, "data", "minecraft", "tags", "functions", "load.json"), "w", encoding="utf-8") as fp:
    json.dump({"values": ["za_showcase:load"]}, fp)


def write(fname, lines):
    with open(os.path.join(fn, fname + ".mcfunction"), "w", encoding="utf-8", newline="\n") as fp:
        fp.write("\n".join(lines) + "\n")


ZA, ZB = VIEW_Z - 3, WALL_Z + 1
write("load", [
    "# Один раз: если на месте появления ещё трава, а не бетон — строим.",
    f"execute unless block 0 {FLOOR} {VIEW_Z} minecraft:white_concrete run function za_showcase:prepare",
])
write("prepare", [
    f"forceload add {X0} {ZA} {X1} {ZB}",
    "schedule function za_showcase:build 40t",
])
checks = " ".join(f"if loaded {px} {FLOOR} 0" for px in range(X0, X1 + 1, 16))
# Всё загружено — строим; какой-то чанк ещё нет — пробуем снова через секунду.
build = [f"execute {checks} run function za_showcase:do_build"]
for px in range(X0, X1 + 1, 16):
    build.append(f"execute unless loaded {px} {FLOOR} 0 run schedule function za_showcase:build 20t replace")
write("build", build)

do = []
for sx in range(X0, X1 + 1, 40):
    ex = min(sx + 39, X1)
    do.append(f"fill {sx} {FLOOR - 5} {ZA} {ex} {FLOOR} {ZB} minecraft:white_concrete")
    do.append(f"fill {sx} {FLOOR + 1} {WALL_Z} {ex} {FLOOR + 14} {WALL_Z + 1} minecraft:white_concrete")
for kind, tid, size, label, cx in placed:
    text = json.dumps({"text": label}, ensure_ascii=False).replace("'", "\\'")
    do.append(f"setblock {cx} {Y} {SIGN_Z} minecraft:oak_sign[rotation=8]{{is_waxed:1b,front_text:{{has_glowing_text:1b,"
              f"color:\"white\",messages:['{text}','\"\"','\"\"','\"\"']}}}} replace")
    if kind == "pukh":
        do.append(f"setblock {cx - 1} {Y + 3} {WALL_Z - 1} fl_zone_arts:pukh[facing=north] replace")
        do.append(f"setblock {cx + 1} {Y + 3} {WALL_Z - 1} fl_zone_arts:pukh[facing=north] replace")
    if kind == "ezhik":
        do.append(f"setblock {cx} {Y} 0 fl_zone_arts:ezhik[facing=up] replace")
        do.append(f"setblock {cx} {Y + 2} {WALL_Z - 1} fl_zone_arts:ezhik[facing=north] replace")
do += [
    "forceload remove all",
    "time set 18000",
    "weather clear",
    'tellraw @a {"text":"ZA витрина построена","color":"green"}',
]
write("do_build", do)

print("Мир готов:", os.path.abspath(out))
print(f"Платформа x {X0}..{X1}, станций: {len(placed)}")
for kind, tid, size, label, cx in placed:
    print(f"  x={cx:4d}  {label}")
