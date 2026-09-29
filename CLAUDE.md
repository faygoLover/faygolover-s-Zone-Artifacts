# CLAUDE.md — faygolover's Zone Artifacts (`fl_zone_arts`)

Мод Minecraft **Forge 1.20.1 (47.4.10)**, Java 17, маппинги Mojang (official).
Для Full RP сервера **«Департамент»** (сеттинг Control / SCP, аномалии вдохновлены S.T.A.L.K.E.R.).
Аномалии, артефакты, пояса, сопротивления, аномальная энергия. Сейчас реализованы только аномалии.

- modid `fl_zone_arts`, пакет `faygolover.zoneartifacts`, автор `faygolover`.
- Текущая версия: `mod_version` в `gradle.properties` (на момент переноса — **0.1.34.0**).
- Подробные ТЗ по каждой аномалии — `docs/specs/` (00 — общий дизайн, 08 — третья волна). Там же
  все числа, решения пользователя и «почему так». Перед правкой аномалии — прочитай её спеку.
- История изменений для игроков/ГМ — `README.md` (раздел «Версии»).

## Правила работы (от пользователя — соблюдать)

- **Общаться по-русски.**
- **«Пока не кодь»** = только обсуждение/проектирование, файлы не трогать.
- **Не переписывать работающий код без нужды.** Минимальные точечные правки.
- **Версия** `a.x.y.z` поднимается с **каждой** поставкой: `y` — новый элемент(ы) (новая аномалия и т.п.,
  `y` += число новых), `z` — исправления (сбрасывается в 0 при росте `y`). Меняется `mod_version`
  в `gradle.properties` + новая запись вверху раздела «Версии» в `README.md` (по-русски, для ГМ).
- При изменении формата пакетов — **поднять `PROTOCOL_VERSION`** в `network/ModNetwork.java` (сейчас `"14"`).
- Спеки: при заметном изменении аномалии — обновить её файл в `docs/specs/`.
- **Без миксинов** (до сих пор ни одного; для Дымки пользователь явно отказался). Всё — через события Forge.
- Экранные пси-эффекты — «проверенным путём»: копия кадра + сетки, **без своих шейдеров** (`client/fx/ScreenFx`).
- Звуки — синтезировать самим (numpy/scipy + ffmpeg libvorbis, см. `tools/sounds/`) или найти свободные.
  Иконки — процедурно PIL в стиле медальона (`tools/textures/`).
- После изменений Java — **собрать**: `./gradlew compileJava` (Windows: `gradlew.bat compileJava`),
  исправить ошибки. Раньше код писался без доступа к MC-зависимостям, поэтому **всё с ~0.1.28 ни разу
  не компилировалось** — первая сборка почти наверняка покажет ошибки, их нужно чинить аккуратно,
  не меняя задуманного поведения.
- Коммиты — только когда пользователь попросит.

## Сборка и запуск

- `./gradlew build` → `build/libs/fl_zone_arts-<версия>.jar`.
- `./gradlew runClient`, `./gradlew runServer` (рабочие папки `run/`, `run-data/` в .gitignore).
- Сервер должен иметь `allow-flight=true` (гравитационные аномалии, Лифт) — иначе кик за полёт.
- Конфиги: `config/fl_zone_arts-common.toml` (`config/ModCommonConfig`, стандарты новых аномалий,
  лимиты тюнеров) и `fl_zone_arts-client.toml` (`config/ModClientConfig`, `maxEffectIntensity`,
  `airDistortion`; эффективные значения — `ModClientConfig.effective`).

## Архитектура (`src/main/java/faygolover/zoneartifacts/`)

- `ZoneArtifacts` — главный класс мода, регистрации.
- `registry/` — `ModItems` (установщики `placer(name, typeId)`), `ModCreativeTabs` (список `output.accept`),
  `ModBlocks`, `ModBlockEntities`, `ModEntities`, `ModParticles`, `ModSounds`.
- `anomaly/` — **зонные аномалии** (сервер + общая логика):
  - `AnomalyInstance` — одна зона: typeId, pos, size, cooldownSeconds, damage, intensity, speed, range, yaw,
    onCooldown/active. Хранилище — `AnomalySavedData` (на измерение).
  - `AnomalyTypeIds` — строковые id типов (`electra`, `zharka`, `iney`, `razlom`, `cold_razlom`, `plesh`,
    `voronka`, `karusel`, `podushka`, `lift`, `kisel`, `acid_fog`, `amoeba`, `tryasina`, `dymka`, `sumrak`,
    `psi_zone`, `poppy_field`, `rust`, `soap_bubbles`, `khlopushka`, `svetlyachok`, `kamerton`,
    `fantom_light`; `pautina` — только имя/диспетчер тюнера).
  - `AnomalyDefaults` — размер/дальность/перезарядка/урон/насыщенность по типу, что тюнится, ключи настроек.
  - `AnomalyEngine` — серверный тик, **диспетчер по typeId** → `*Engine` (ThermalEngine, RazlomEngine,
    KiselEngine, AcidFogEngine, AmoebaEngine, SwampEngine, PsiEngine, PoppyEngine, RustEngine, BubbleEngine,
    KhlopushkaEngine, KamertonEngine, LiftEngine, GravityEngine…). Чисто клиентские (Дымка, Сумрак,
    Светлячок, Фантом) на сервере ничего не делают.
  - `AnomalyGeometry.zoneAabb(typeId, pos, size)` — область зоны (у Трясины своя).
  - `AnomalyCombat.hurt(..., from)` — урон + бегство мобов. Константы аномалий — классы `Electra`, `Gravity`,
    `Thermal`, `Razlom`, `Kisel`, `AcidFog`, `Amoeba`, `Lift`, `Pukh`, `Swamp`…
  - Паутина — отдельное хранилище `WebSavedData` + `WebEngine` (нити, не зоны).
- `tesla/` — **маршрутные** аномалии: `TeslaEntity` и наследники (`CometEntity`, `ColdCometEntity`,
  `ChemCometEntity`, `GraviEntity`), `RouteKind` (новые — только в конец, передаётся по ordinal),
  `TeslaRoute`/`TeslaRouteSavedData`, `TeslaRouteService`, `TeslaDrafts`, `TeslaRouteWatchdog`, команды.
- `block/` — `PukhBlock`(+BE, `PukhLayout`), `EzhikBlock`(+BE).
- `item/` — `AnomalyPlacerItem`, `TeslaRoutePlacerItem(RouteKind)`, `WebPlacerItem`, `AnomalyTunerItem`,
  `ItemTooltips` (описания из lang `…desc.N`, до 10 строк).
- `tuner/` — `TunerKind` (SIZE, SPEED, COOLDOWN, INTENSITY, DAMAGE, TARGETING; по ordinal), `TunerService`
  (зоны, маршруты, блоки Пух/Ёжик, Паутина).
- `network/` — `ModNetwork` (SimpleChannel, регистрация всех пакетов), `SyncAnomaliesPacket.Entry(typeId, pos,
  size, intensity, onCooldown, active, speed, cooldown, damage, range, yaw)`, по пакету на событие аномалии.
- `client/` — `ClientAnomalyCache` (зоны на клиенте), `TunerClientHandler`, `AnomalyHighlightRenderer`,
  `GlowRenderType` (`GLOW` — аддитивный, без записи глубины), `ZoneLoopSound`; по папке на аномалию
  (`client/<name>/<Name>Client` — `@Mod.EventBusSubscriber(value = Dist.CLIENT)`).
  - `client/distortion/` — искажение воздуха (копия кадра, `Lens`, `Haze`), источники — `DistortionSources.collect`.
  - `client/chem/Gas` — общий рендер газовых комков / `FramePuff`, `Gas.lateStagePose`.
  - `client/fx/` — `SoundFx` (глушение/тон всех звуков), `ScreenFx` (туман, оверлеи, пси), `HumLoop` («звук в голове»).
  - `client/gravity/GoreClient.addStain` — пятна (кровь, кислота).

## Правила рендера (важно, уже наступали)

- `AFTER_TRANSLUCENT_BLOCKS`: ванилла оставляет depth test выключенным → явно
  `RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515);`.
- `AFTER_WEATHER`: ModelView уже содержит поворот камеры → позу брать через `Gas.lateStagePose(event)`.
- `GlowRenderType.GLOW` в Fabulous рисуется в weather target (виден только на фоне неба); у пользователя Fancy.
- Маки и мох Ржавчины — `AFTER_CUTOUT_BLOCKS`.
- Звуки из `SoundFx`: оборачиваются только **нетикающие** (иначе ломаются stop/isActive по идентичности).

## Чек-лист: новая зонная аномалия

1. `AnomalyTypeIds` — константа id.
2. `AnomalyDefaults` — size/range/cooldown/damage/intensity, какие тюнеры работают, ключи настроек.
3. `ModCommonConfig` — секция со стандартами и константами (+ значения по умолчанию в спеке).
4. Сервер: `anomaly/<Name>Engine` + ветка в `AnomalyEngine`; урон — через `AnomalyCombat`.
   Новый тип урона — `data/fl_zone_arts/damage_type/anomaly_<x>.json` (`scaling: never`!) + при надобности
   теги в `data/minecraft/tags/damage_type/` (`bypasses_armor`, `is_fire`, `is_freezing`) + сообщения смерти в lang.
5. Пакеты — класс в `network/`, регистрация в `ModNetwork`, поднять `PROTOCOL_VERSION`.
6. Клиент: `client/<name>/<Name>Client`; при искажении — хук в `DistortionSources`; при влиянии на звук/экран —
   в `SoundFx`/`ScreenFx`.
7. Установщик: `ModItems.placer("<id>_placer", AnomalyTypeIds.X)` + `ModCreativeTabs` + модель
   `models/item/<id>_placer.json` + иконка `textures/item/<id>_placer.png` (скрипт в `tools/textures/`).
8. Lang: `ru_ru.json` и `en_us.json` — имя предмета, `…desc.0..N`, сообщения, смерть.
9. Звуки: `.ogg` в `assets/fl_zone_arts/sounds/`, запись в `sounds.json` **и** регистрация в `ModSounds`
   (без неё звук молчит — уже было).
10. `README.md` (версии), `gradle.properties` (версия), спека в `docs/specs/`.

## Инструменты (`tools/`)

- `tools/sounds/gen_0128.py`, `gen_0134.py` — синтез звуков волны 3 (numpy, scipy, ffmpeg c libvorbis).
  Пишут прямо в `src/main/resources/assets/fl_zone_arts/sounds/` (пути от расположения скрипта).
  Шаблон для новых звуков: helpers `lp/hp/bp/env/reverb/loopify/save`.
- `tools/textures/icons*.py` — иконки установщиков (16×16 медальон). Цепочка: `icons4` подключает
  `icons3` → `icons2` → `icons`. Запуск: `python icons4.py soap_bubbles khlopushka …` (имена функций).
  `tex3.py` — текстуры мха Ржавчины и лепестков мака; `lace.py` — `pukh_lace`.
- `tools/check.sh` — старый статический прогон javac без MC-зависимостей (нужен был, пока не было сборки).
  Теперь не нужен — используй `./gradlew compileJava`.

## Известные хвосты

- Весь код с 0.1.28 (волна 3, 13 аномалий) не компилировался и не тестировался — пользователь
  принесёт список правок после первого запуска.
- `amoeba_gather` — ванильная заглушка; `podushka_bounce` — заглушка.
- Старые сохранения Амёбы могут нести длинные перезарядки из прежней версии.
- Сумрак и Фантомный свет позже получат пси-составляющую; артефакты, пояса, сопротивления, аномальная
  энергия — ещё не начаты (см. `docs/specs/00_design_overview.md`).
