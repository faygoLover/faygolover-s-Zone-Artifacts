# Искажение воздуха и рендер-фиксы (0.1.14.2+)

## Искажение воздуха (`client/distortion/Distortion`)
- Без своих шейдеров. На стадии `AFTER_TRANSLUCENT_BLOCKS` (приоритет LOW) основной кадр копируется `glBlitFramebuffer` в `TextureTarget` (альфа принудительно 1). Поверх рисуются сетки `position_tex_color`, в каждой точке — копия кадра из смещённой точки. UV = Proj × ModelView × pose × точка_выборки. Прежние привязки framebuffer и viewport восстанавливаются.
- Копия раз в кадр, только если есть участки: не дальше 48 блоков, не позади камеры, не больше 16 ближайших.
- `Lens` — круглая, к камере: pinch, twist, ripple, bump, размытие (4 прохода). `Lens.shimmer(...)` — простое дрожание.
- `Haze` — вертикальный лист к камере: марево или закрученные полосы.
- Выключатель: `airDistortion` в `fl_zone_arts-client.toml`.
- Источники собирает `DistortionSources.collect` (Воронка, Грави, Лифт, Жарка/Иней, Разлом, Кометы, Ржавчина, Пузыри, Хлопушка, Камертон).

## Рендер-правила (важно!)
- **Тест глубины:** после прозрачного слоя ванилла оставляет depth test выключенным. Немедленные проходы на `AFTER_TRANSLUCENT_BLOCKS` должны явно включать `RenderSystem.enableDepthTest()` + `depthFunc(515)` (LEQUAL).
- **AFTER_WEATHER:** матрица ModelView уже содержит поворот камеры → использовать `Gas.lateStagePose(event)` (= inverse(MV) × pose события), иначе объект «уезжает» при повороте камеры.
- `GlowRenderType.GLOW` — аддитивный, без записи глубины. В Fabulous рисуется в weather target (виден только на фоне неба) — у пользователя Fancy.
- Экранные эффекты «под HUD» (`client/fx/ScreenFx`) — копия кадра в GUI-оверлее `registerBelowAll("senses")`.
