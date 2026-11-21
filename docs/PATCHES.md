# Изменения оригинального APK

Все изменения оригинальной сборки ONAY! 3.6.5 перечислены здесь. Оригинальные APK не изменяются:
`scripts/build_apk.sh` каждый раз заново декодирует `base.apk` в чистую папку.

## Итоговый перечень изменений оригинального APK (этап 6)

| # | Что | Где | Зачем |
|---|---|---|---|
| 1 | Новый dex `classes12.dex` (34 класса: `kz.onay.nearby.logic.*`, `kz.onay.nearby.ui.*`) | добавляется в `base.apk` скриптом `scripts/add_dex.py` | код экрана «Рядом» и логики |
| 2 | **Smali**: в `MainActivity.Q0(int)` ветка вкладки 0 вызывает `NearbyStopFragment.newInstance(null)` вместо `RoutesFragment.newInstance()` | `smali_classes11/kz/onay/ui/main/MainActivity.smali`, `scripts/patch_smali.py` | при открытии вкладки транспорта показывается «Рядом» |
| 3 | **Smali**: в `MainActivity.Q0(int)` ветка вкладки 2 (Life) вызывает `ProfileFragment.newInstance()` вместо `LifeFragment.newInstance()` | тот же `MainActivity.smali`, `scripts/patch_smali.py` | вкладка Life показывает тот же экран, что Cards; раздел купонов и партнёров Kwaaka больше не открывается через навигацию (код Life и Kwaaka не удалён) |
| 4 | **Разметка**: `android:visibility="gone"` у `ComposeView compose_view_banner` | `res/layout/fragment_profile_main_cards.xml`, `scripts/patch_layout.py` | убран рекламный баннер на экране карт (виден и на Cards, и на Life); остальное на экране поднимается выше |
| 5 | Отладочная Activity `kz.onay.nearby.ui.NearbyStopDebugActivity` (`exported="true"`) | `AndroidManifest.xml`, `scripts/patch_manifest.py` | **только отладочная сборка** (`ONAY_DEBUG_ACTIVITY=1`): запуск через adb с тестовыми данными и подменой координаты. В релизной сборке отсутствует (проверено по манифесту) |

Правка 2 в smali (вкладка 0): заменена одна инструкция вызова
```
-    invoke-static {}, Lkz/onay/ui/routes2/frontscreen/RoutesFragment;->newInstance()Lkz/onay/ui/routes2/frontscreen/RoutesFragment;
+    const/4 v0, 0x0
+    invoke-static {v0}, Lkz/onay/nearby/ui/NearbyStopFragment;->newInstance(Landroid/os/Bundle;)Lkz/onay/nearby/ui/NearbyStopFragment;
```
(регистр `v0` в этой ветке свободен, у метода `.locals 1`). Скрипт останавливает сборку, если вызов не найден ровно один раз внутри `Q0(I)` или файл уже изменён.

Оригинальный экран транспорта остаётся доступным: кнопка «Обычная карта» на новом экране вызывает `MainActivity.M1(RoutesFragment.newInstance())` (так же, как главный экран меняет вкладки; обновляется `currentFragment`, работает обратная навигация). Вернуться к «Рядом» можно, переключив вкладку и вернувшись на «Routes». Платежи, авторизация, купоны, профиль, остальные вкладки не менялись.

Отдельное замечание: нижняя навигация и круглая кнопка QR главного экрана заходят на контейнер фрагмента, поэтому в `MainActivity` экран имеет нижний отступ 44 dp (в отладочной Activity его нет).

Остаточное свойство сборки: тестовые данные (`TestData`) остаются в dex, но достижимы только по аргументу `data=test`, который передаёт лишь отладочная Activity; релизная вкладка аргументов не передаёт.

## Сборка новых классов

- Компиляция: `javac --release 11` против `android.jar` (API 36) и classpath приложения (`scripts/prepare_classpath.sh`).
  Classpath получается конвертацией dex в jar через `enjarify`; он нужен только для компиляции и в APK не попадает.
- Особенности classpath: `enjarify` пишет class-файлы версии 49 (javac отвергает default-методы интерфейсов), поэтому версия поднимается до 52; атрибута `InnerClasses` нет, поэтому вложенные классы вроде `LatLngBounds.Builder` по имени недоступны (используется `var`).
- R8 расширил доступ у части методов приложения (например `FragmentActivity.onCreate` стал `public`); переопределения в новом коде должны быть не слабее.
- Конвертация в dex: `d8 --release --min-api 24`, классы приложения не включаются.

## Зависимости новых классов от внутренностей приложения

Новый код не меняет smali, но опирается на эти элементы версии 3.6.5 (при смене версии приложения их надо перепроверить):
- `kz.onay.ui.main.MainActivity.M1(Fragment)` (имя изменено R8, кнопка «Обычная карта») и `RoutesFragment.newInstance()`; `OnayApp.get().getRouteComponent()` (`getRouteDatabase()`, `getManagedChannel()`); `OnayApp.get().getAccountManager().getAccount()`.
- Поле `provideInterceptorProvider` в сгенерированном классе компонента Dagger (чтение рефлексией, `AppDataSource.interceptorOf`).
- Room: `stopDao().getListRx()`, `localizationDao().getList("Stop", id)`.
- gRPC и protobuf: `stop.StopServiceGrpc`, `route.RouteServiceGrpc`, `position.PositionServiceGrpc`, их `OuterClass` и сообщения (имена вложенных классов через `$`).
- `kz.onay.ui.routes2.shared.MyLocationRequestHelper` (`getLocation()`).
