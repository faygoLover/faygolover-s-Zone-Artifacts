#!/usr/bin/env python3
"""Правила RP Medicine для урона аномалий: data/fl_zone_arts/rpmedicine/damage_sources/*.json.

Medicine читает эту папку из любого мода; без Medicine файлы просто лежат. Формат правил —
RP Medicine docs/datapack.md, решения по каждой аномалии — RP Medicine docs/11_anomalies.md.
Запуск из корня репозитория: python tools/gen_medicine_rules.py. Сгенерированное коммитится.

Тяжесть раны = урон × 5 × severity_multiplier; шанс осложнения = (тяжесть − min_severity) × per_severity,
не больше max. Мало урона — мало осложнений.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data",
                    "fl_zone_arts", "rpmedicine", "damage_sources")


def ch(min_sev, per, mx, **extra):
    return dict({"min_severity": min_sev, "per_severity": per, "max": mx}, **extra)


def rule(types, wound, location=None, **fields):
    r = {"priority": 30, "damage_types": ["fl_zone_arts:" + t for t in types], "wound": wound}
    if location:
        r["location"] = location
    r.update(fields)
    return r


# Вдыхаемое: в противогазе (список в конфиге Medicine) не проходит в лёгкие.
def breath(lungs, spo2):
    return {"lungs_per_damage": lungs, "spo2_per_damage": spo2, "inhaled": True}


RULES = {
    # Ток: доля в 1–2 части (слабый ожог), остальное — боль по всему телу без ран; низкий шанс фибрилляции.
    "electra": rule(["anomaly_electra"], "burn", "focus", severity_multiplier=0.8,
                    focus_parts=[1, 2], focus_share=0.6, rest_pain_per_damage=6, rest_pain_seconds=15,
                    complications={"fibrillation": ch(5, 0.002, 0.05)}),
    # Шаровая молния: кучнее, первая часть — куда попал шар.
    "tesla": rule(["anomaly_tesla"], "burn", "focus", severity_multiplier=1.2,
                  focus_parts=[1, 2], focus_share=0.85, rest_pain_per_damage=6, rest_pain_seconds=15,
                  complications={"fibrillation": ch(5, 0.003, 0.08)}),
    # Жар: сначала стопы и ноги, чем дольше стоишь — тем выше; тело греется.
    "zharka": rule(["anomaly_zharka"], "burn", "bottom_up",
                   spread={"start": 0.3, "per_second": 0.05, "reset_seconds": 3},
                   physiology={"body_temp_per_damage": 0.05}),
    "razlom": rule(["anomaly_razlom"], "burn", "hit_point"),
    # Вспышка перед глазами: ожог лица, лёгкая контузия.
    "khlopushka": rule(["anomaly_khlopushka"], "burn", "head",
                       complications={"concussion": ch(0, 0.02, 0.4, amount_per_severity=0.5)}),
    # Огненный шар: ожоги и ушибы со стороны удара.
    "comet": rule(["anomaly_comet"], "burn", "explosion", secondary_wound="bruise", secondary_share=0.4),
    # Холод: кисти и стопы, потом выше; тело остывает.
    "iney": rule(["anomaly_iney"], "frostbite", "extremities",
                 spread={"start": 0.3, "per_second": 0.04, "reset_seconds": 3},
                 physiology={"body_temp_per_damage": -0.08}),
    "cold_razlom": rule(["anomaly_cold_razlom"], "frostbite", "hit_point"),
    "cold_comet": rule(["anomaly_cold_comet"], "frostbite", "explosion"),
    # Бросок Плеши о стену: ушиб в часть, которой ударился; переломы и контузия по силе удара.
    # Приземление — обычное падение (правило fall в Medicine).
    "plesh_impact": rule(["anomaly_plesh_impact"], "bruise", "hit_point",
                         complications={"fracture": ch(10, 0.02, 0.6), "open_fracture_fraction": 0.1,
                                        "concussion": ch(5, 0.04, 0.9)}),
    # Воронка: много частей; ушибы, переломы, внутренние, артерии, отрыв конечности (настройки Medicine) — по урону.
    "voronka": rule(["anomaly_voronka"], "bruise", "explosion",
                    complications={"fracture": ch(10, 0.015, 0.6), "open_fracture_fraction": 0.3,
                                   "internal": ch(10, 0.015, 0.5), "arterial": ch(15, 0.01, 0.3)}),
    # Центр Воронки. С anomaly_true_death в конфиге Medicine — настоящая смерть (метка rpmedicine:true_death),
    # иначе — как Воронка, но тяжелее.
    "voronka_rupture": rule(["anomaly_voronka_rupture"], "bruise", "explosion", priority=31, severity_multiplier=1.3,
                            complications={"fracture": ch(5, 0.02, 0.8), "open_fracture_fraction": 0.4,
                                           "internal": ch(5, 0.02, 0.7), "arterial": ch(10, 0.015, 0.4)}),
    # Волна воздуха Карусели: со стороны волны; ушибы, контузия, внутренние, пневмоторакс и без раны.
    "karusel": rule(["anomaly_karusel"], "bruise", "explosion",
                    complications={"concussion": ch(5, 0.03, 0.8), "internal": ch(10, 0.015, 0.5),
                                   "pneumothorax": ch(20, 0.01, 0.3, blunt=True)}),
    "gravi": rule(["anomaly_gravi"], "bruise", "hit_point", complications={"fracture": ch(10, 0.02, 0.5)}),
    # Слабая взрывная волна пузырей: ушибы, лёгкая контузия.
    "bubbles": rule(["anomaly_bubbles"], "bruise", "explosion",
                    complications={"concussion": ch(5, 0.02, 0.4, amount_per_severity=1.0)}),
    # Разъедает обувь и ноги: только стопы и голени.
    "kisel": rule(["anomaly_kisel"], "burn", "lower_legs"),
    # Сам туман: только лёгкие и кислород, если нет противогаза.
    "acid_fog_gas": rule(["anomaly_acid_fog_gas"], "none", physiology=breath(1.0, 2.0)),
    # Струя из земли: снизу вверх, докуда достаёт струя; основная доля — нижним частям.
    "acid_fog_jet": rule(["anomaly_acid_fog_jet"], "burn", "bottom_up", spread={"from_source": True}),
    "amoeba": rule(["anomaly_amoeba"], "burn", "lower_legs"),
    # Кислотное облако: открытые (не закрытые бронёй) части и лёгкие.
    "amoeba_cloud": rule(["anomaly_amoeba_cloud"], "burn", "exposed", severity_multiplier=0.6,
                         physiology=breath(0.8, 1.5)),
    # Химическая комета и её облако: в основном дыхание, слабо — голова.
    "chem_comet": rule(["anomaly_chem_comet", "anomaly_chem_cloud"], "burn", "head", severity_multiplier=0.3,
                       physiology=breath(1.5, 3.0)),
    "pukh": rule(["anomaly_pukh"], "burn", "hit_point", severity_multiplier=0.5),
    # Едкая пыль Ржавчины у ног: слабые ожоги ног, кашель.
    "rust_dust": rule(["anomaly_rust_dust"], "burn", "lower_legs", severity_multiplier=0.4,
                      physiology=breath(0, 1.0)),
    # Раскалённое пятно: ноги и низ тела, тяжёлые ожоги, потом догорание; выше риск заражения.
    "rust_blast": rule(["anomaly_rust_blast"], "burn", "bottom_up", severity_multiplier=1.2, infection_risk=2.0,
                       spread={"start": 0.6}),
    "rust_burn": rule(["anomaly_rust_burn"], "burn", "bottom_up", infection_risk=2.0, spread={"start": 0.6}),
    # Стеклянные иглы: несколько частей, только порезы (без осколков).
    "kamerton": rule(["anomaly_kamerton"], "cut", "explosion"),
    # Нити: порез в точке касания, шанс задеть артерию.
    "web": rule(["anomaly_web"], "cut", "hit_point", complications={"arterial": ch(5, 0.02, 0.4)}),
    # Трясина: без ран, удушье (как suffocation в Medicine).
    "swamp": rule(["anomaly_swamp"], "none", physiology={"spo2_per_damage": 4}),
    # Маковое поле: без ран, нехватка кислорода и повреждение мозга.
    "poppy": rule(["anomaly_poppy"], "none", physiology={"spo2_per_damage": 0.1, "brain_per_damage": 0.08}),
}

if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    for f in os.listdir(ROOT):
        if f.endswith(".json"):
            os.remove(os.path.join(ROOT, f))
    for name, r in RULES.items():
        with open(os.path.join(ROOT, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(r, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print(len(RULES), "rules")
