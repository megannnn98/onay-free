# ONAY! NearbyStopFragment — отчёт о реализации

Статус: **этап 0 (проверка предпосылок) выполнен частично**, этапы 1–7 не начаты.
Пометки: **[проверено]** — выполнено и наблюдалось; **[код]** — подтверждено чтением декомпилята/proto; **[не проверено]** — только предположение.

## Этап 0. Предпосылки

### Рабочая сборка [проверено]
- Артефакты предыдущего исследования на месте: `work/apks` (оригинальные split-ы), `work/apktool_full` (декодированное дерево, 2563 ресурса), `work/jadx_out`, `work/rebuilt/*.apk`, `work/keys/test.jks` (вне git).
- На Poco X5 5G (Android 14, arm64) стоит пересобранная apktool и переподписанная тестовым ключом сборка (`kz.onay` 3.6.5); пользователь подтвердил вход, карту, остановки и автобусы.
- Воспроизводимость сборки скриптом пока **не проверена** (скрипт — этап 6; ручная цепочка приведена в `ONAY_REVERSE_ENGINEERING.md` §6, пункты 0a и 0b): `apktool -JXmx10g b -f` с чистой `build/`, `zipalign -p 4`, `apksigner`.

### Как связаны остановка, маршрут и направление [код]
- `stopService.proto`: `Stop{Id, Name map, Point{Latitude,Longitude}, repeated int64 Routes}`; комментарий разработчика: `Routes` — «список идентификаторов маршрутов, связанных с остановкой».
- `routeService.proto`: `Route{Id, TypeId, MainRouteId, Name map, repeated Direction}`; `Direction{Index, repeated Point Line, Duration, Distance, repeated StopPosition Stops}`; `StopPosition{StopId, LineIndex, OffsetDistance}`.
- Следовательно: остановка → `Stop.Routes` (id маршрутов); маршрут → `Directions[]`; направление, обслуживающее остановку, — то, в `Stops` которого есть этот `StopId`. Одна физическая остановка может числиться в нескольких направлениях одного маршрута.
- Тип транспорта: `Route.TypeId` = `routeType` в приложении; по `RoutesResourceHelper` 0 — автобус, 1 — троллейбус, 2 — метро, 3 — трамвай, 4 — микроавтобус. Строгого enum в коде нет, значения выведены из выбора иконок [вывод].

### Источник «всех маршрутов остановки» — подтверждённый способ [код, не запускалось]
1. Ближайшая остановка: Room-таблица `Stop` (`id`, `lat`, `lng`), доступна через `RouteComponent.getRouteDatabase()`. Полнота таблицы на устройстве **не проверена** (синхронизация через `StopService.list(updatedAfter)`).
2. Маршруты остановки: `StopService.get(stopId).Routes`, поскольку в Room `Stop.routes` не сохраняется. Локальная таблица `RouteDirectionStop` для этого **не годится**: она заполняется только для маршрутов, чьи детали уже загружались (`RouteRepository.getItem` грузит лениво, если строк <3).
3. Геометрия и направления: `RouteService.get(routeId)` отдаёт полный `Route` с `Directions[].Line` и `Stops[]` — то есть геометрию для ранее не открывавшихся маршрутов получить можно **[код]**; реальный запрос не выполнялся **[не проверено]**.

### Как обращаться к данным приложения из нового фрагмента [код]
- `OnayApp.get().getRouteComponent()` возвращает `RouteComponent` (может быть `null`, если город не выбран).
- `RouteComponent` отдаёт только `getConfigurationRepository()`, `getManagedChannel()`, `getRouteDatabase()` и методы `inject(...)` для конкретных классов. Геттеров `StopRepository`/`RouteRepository`/`PositionRepository` **нет**; добавить `inject(NearbyStopFragment)` значило бы править сгенерированный Dagger-код.
- `ManagedChannel` построен с `GrpcHeaderClientInterceptor`, который сам добавляет заголовки (в том числе `X-Auth-Token` из `GrpcTokens`). Поэтому рекомендованный способ: создавать gRPC-стабы (`StopServiceGrpc.newBlockingStub(channel)` и т.п.) на **существующем канале приложения**. Токены не извлекаются и не копируются, второго клиента нет.
- Для позиций автобусов: `PositionServiceGrpc` на том же канале (`PositionService.list(routes[])`), либо повтор существующего механизма — решить на этапе 5.

### Блокеры сборки нового кода [проверено]
- Есть: `javac` 27, `d8`, `zipalign`, `apksigner`, `adb`, `apktool`.
- Нет: `android.jar` (в `/opt/android-sdk` только `build-tools`, нет `platforms/`), `kotlinc`, инструмента dex→jar для classpath приложения (`enjarify` есть в `extra`).
- Решение (предлагается): писать новый код на **Java** (не требует Kotlin); `android.jar` — `platforms;android-36` через `sdkmanager` (пакет `android-sdk-cmdline-tools-latest` из AUR); classpath приложения — `enjarify` из `base.apk`/dex только для компиляции (классы приложения в итоговый dex не включаются).

### Открытые вопросы этапа 0
- Полнота Room `Stop` на устройстве и работоспособность `StopService.get/RouteService.get` через канал приложения — проверяются только после появления рабочего кода (этап 3–4).
- Формат `ArrivalBoard.Vehicles.Data`, `libdda7`/`liboz_native`/`libozdat`, `ways.json` — не влияют на текущий план.
- Формат диаграммы: в задании Mermaid, в глобальных правилах пользователя PlantUML — нужно решение.
