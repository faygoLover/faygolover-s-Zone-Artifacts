# Тесла — техническое ТЗ

Статус: реализовано в **0.1.2.0**, переработано в **0.1.3.0**.

## Что изменилось в 0.1.3.0 (важнее разделов ниже)

- **Датапаков больше нет.** Неизменяемые характеристики — константы в `anomaly/Electra.java` и
  `tesla/Tesla.java`. Стандартные значения новых аномалий, лимиты тюнеров, базовая скорость и радиус
  погони Теслы, длительность электризации — `config/fl_zone_arts-common.toml` (`config/ModCommonConfig`).
  Потолок насыщенности эффектов у игрока — `config/fl_zone_arts-client.toml`
  (`maxEffectIntensity`, `config/ModClientConfig.effective`). Типы урона — JSON в
  `data/fl_zone_arts/damage_type/` (так регистрируются типы урона в 1.20).
- **Настройки у каждой аномалии свои:** зоны — `AnomalyInstance` (size, cooldownSeconds, damage,
  intensity, speed, range, yaw); маршруты — `TeslaRoute` (size, speedMultiplier, respawnSeconds, damage,
  intensity, chaseRadius). Сущность каждый тик читает маршрут; размер и насыщенность синхронизируются
  (`DATA_SIZE`, `DATA_INTENSITY`), хитбокс масштабируется.
- **Тюнеры** (`item/AnomalyTunerItem`, `tuner/TunerKind`, `tuner/TunerService`,
  `client/TunerClientHandler`, `network/TunerClickPacket`): size, speed, cooldown, intensity, damage,
  targeting. ЛКМ −, ПКМ +, Shift — крупный шаг, удержание повторяет раз в 4 тика. С тюнером видны
  все зоны и маршруты в радиусе 16.
- **Тесла:** один удар при касании; электризация — только визуал. Петель = насыщенность.
  «Цепляющиеся» молнии к блокам — чистый визуал.
- **Электризация** — `network/ElectrifyPacket` + `client/ElectrifyRenderer`. Команда
  `/fl_zone_arts electrify <цели> <сек> [урон]`.

## Сущность `TeslaEntity`

Обычная `Entity`: без AI, без гравитации. `hurt` → false, `isPickable` → false, не толкается,
`move` только `MoverType.SELF`, `fireImmune`. Хитбокс 0.8 × size, визуал 1 × size, трекинг каждый тик.
Наследники: `CometEntity`, `ColdCometEntity`, `ChemCometEntity`, `GraviEntity`.

Состояния: `SPAWNING` (20 тиков роста) → `PATROL` ↔ `CHASE` → `DEAD` (после столкновения;
через respawnSeconds — на случайную точку). Креатив и наблюдатели игнорируются.
Каждые 20 тиков — `lastKnownTeslaPos` в маршрут. `TeslaRouteWatchdog` раз в 600 тиков
возрождает убитое командой существо; дубликаты сами удаляются по UUID маршрута.

## Данные

`TeslaRouteSavedData` на измерение (id > 0, точки, UUID, последняя позиция, настройки, `kind`).
Черновики — `TeslaDrafts`, только в памяти, id < 0.

## Установщик маршрута

`TeslaRoutePlacerItem(RouteKind)`. ПКМ: блок → старт/следующая точка; стартовая точка своего
черновика → завершить и заспаунить. ЛКМ (клиент → `TeslaWaypointClickPacket`): без черновика —
удалить маршрут; своя точка — убрать. Во всех сообщениях — координаты старта текущего черновика.

## Команды (уровень 2)

`/fl_zone_arts tesla list | cleanup | flag <игрок> <true|false>`,
`/fl_zone_arts electrify <цели> <секунды> [урон]`.
