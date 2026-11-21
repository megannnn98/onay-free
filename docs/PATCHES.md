# Изменения оригинального APK

Все изменения оригинальной сборки ONAY! 3.6.5 перечислены здесь. Оригинальные APK не изменяются:
`scripts/build_apk.sh` каждый раз заново декодирует `base.apk` в чистую папку.

## Этап 2

| Что | Где | Зачем |
|---|---|---|
| Новый dex `classes12.dex` (17 классов: `kz.onay.nearby.logic.*`, `kz.onay.nearby.ui.*`) | добавляется в `base.apk` скриптом `scripts/add_dex.py` | новый код экрана и логики |
| `<activity android:name="kz.onay.nearby.ui.NearbyStopDebugActivity" android:exported="true" .../>` | `AndroidManifest.xml`, перед `</application>` (`scripts/patch_manifest.py`) | временная точка входа для проверки через `adb shell am start`; прошивка Poco X5 не даёт запускать неэкспортированную активность из shell |

Smali и ресурсы на этом этапе **не менялись**. Вёрстка экрана создаётся в коде, поэтому новые id ресурсов не нужны.
Отладочная Activity (`exported="true"`) будет удалена на этапе 6, когда экран встроится во вкладку транспорта.

## Сборка новых классов

- Компиляция: `javac --release 11` против `android.jar` (API 36) и classpath приложения (`scripts/prepare_classpath.sh`).
  Classpath получается конвертацией dex в jar через `enjarify`; он нужен только для компиляции и в APK не попадает.
- Особенности classpath: `enjarify` пишет class-файлы версии 49 (javac отвергает default-методы интерфейсов), поэтому версия поднимается до 52; атрибута `InnerClasses` нет, поэтому вложенные классы вроде `LatLngBounds.Builder` по имени недоступны (используется `var`).
- R8 расширил доступ у части методов приложения (например `FragmentActivity.onCreate` стал `public`); переопределения в новом коде должны быть не слабее.
- Конвертация в dex: `d8 --release --min-api 24`, классы приложения не включаются.

## Зависимости новых классов от внутренностей приложения

Новый код не меняет smali, но опирается на эти элементы версии 3.6.5 (при смене версии приложения их надо перепроверить):
- `OnayApp.get().getRouteComponent()` (`getRouteDatabase()`, `getManagedChannel()`); `OnayApp.get().getAccountManager().getAccount()`.
- Поле `provideInterceptorProvider` в сгенерированном классе компонента Dagger (чтение рефлексией, `AppDataSource.interceptorOf`).
- Room: `stopDao().getListRx()`, `localizationDao().getList("Stop", id)`.
- gRPC и protobuf: `stop.StopServiceGrpc`, `route.RouteServiceGrpc`, их `OuterClass` и сообщения (имена вложенных классов через `$`).
- `kz.onay.ui.routes2.shared.MyLocationRequestHelper` (`getLocation()`).
