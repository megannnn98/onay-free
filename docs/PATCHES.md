# Изменения оригинального APK

Все изменения оригинальной сборки ONAY! 3.6.5 перечислены здесь. Оригинальные APK не изменяются:
`scripts/build_apk.sh` каждый раз заново декодирует `base.apk` в чистую папку.

## Итоговый перечень изменений оригинального APK

| # | Что | Где | Зачем |
|---|---|---|---|
| 1 | Новый dex `classes12.dex` (39 классов, включая вложенные: `kz.onay.nearby.logic.*`, `kz.onay.nearby.ui.*`) | добавляется в `base.apk` скриптом `scripts/add_dex.py` | код экрана «Рядом» и логики |
| 2 | **Smali**: в `MainActivity.Q0(int)` ветка вкладки 2 (Life) вызывает `NearbyStopFragment.newInstance(null)` вместо `LifeFragment.newInstance()` | `smali_classes11/kz/onay/ui/main/MainActivity.smali`, `scripts/patch_smali.py` | экран «Рядом» открывается на вкладке Life; раздел купонов и партнёров Kwaaka не открывается из навигации (код Life и Kwaaka не удалён) |
| 2a | **Smali**: в `MainActivity.onCreate(Bundle)` при холодном старте (`savedInstanceState == null`) вызывается `x1(2)` вместо `x1(1)`; регистр после вызова возвращается к 1 | `smali_classes11/kz/onay/ui/main/MainActivity.smali`, `scripts/patch_smali.py`, `tests/test_patch_smali_start_tab.py` | приложение сразу открывает вкладку Life («Рядом») вместо Cards; при повороте экрана и восстановлении процесса остаётся сохранённая вкладка |
| 2b | **Layout**: в `res/layout/activity_main.xml` нижняя панель (`nav_routes`, `nav_profile`, `nav_life`, `nav_life_hint`, `nav_settings`), круглая кнопка QR (`nav_image_qr`, `nav_qr`) и тени (`viewShadow`, `viewBottomBorder`) получают `visibility=gone` | `scripts/patch_layout.py` | экран «Рядом» занимает весь экран; **только для варианта `kz.onay.free`** (`ONAY_VARIANT=map`); в `kz.onay` (`ONAY_VARIANT=full`, `patch_layout.py --keep-navigation`) панель и оплата остаются; кнопки возврата панели нет: панель показывается только пока чистой установке нужен штатный Routes (первый запуск, запасная кнопка «Показать вкладки» при ошибке) и скрывается после загрузки остановок; код оплаты и меню в APK остаётся, но из этого интерфейса недоступен |
| 2c | **Иконка**: файлы `ic_launcher_autumn`, `_round`, `_foreground` во всех `mipmap-*dpi` заменены, добавлен `_monochrome` (тематические значки Android 13+), фон адаптивной иконки `drawable/ic_launcher_autumn_background` — однотонный `#141A23`; XML `mipmap-anydpi-v26/ic_launcher_autumn*.xml` переписаны. Сборка останавливается, если манифест ссылается на другие иконки или формат ресурсов изменился | `scripts/patch_app_icon.py`, ресурсы `assets/icon/res` (генератор `scripts/generate_icon_resources.py`, исходник `assets/icon/onay-free-icon-source.png`) | иконка приложения: чёрная с белыми остановкой и автобусом и лаймовой линией маршрута; сезонные иконки ONAY! не используются в манифесте |
| 2d | **Код быстрого доступа (PIN)**: `MainActivity.showAccessCodeSetDialog()` (окно «Создайте код быстрого доступа» через секунду после запуска) и `BaseSecondaryActivity.Q()` (окно ввода кода при возврате из фона) сразу возвращаются | `scripts/patch_smali.py` (`patch_access_code`) | при первом запуске и при возврате в приложение нет ни предложения создать код, ни запроса кода; вход по телефону и паролю не затронут |
| 3 | **Smali**: `FeatureCheckerImpl.isFeatureEnabled(String)` возвращает `false` для рекламных флагов Firebase Remote Config: `isYandexAdsEnabled`, `isRoutesNativeInterstitialAdsEnabled`, `partnerTilesMainScreenEnabled`, `partnerTilesMyTicketsEnabled` | `smali_classes11/kz/onay/firebase/feature_checker/FeatureCheckerImpl.smali`, `scripts/patch_ads.py` | экраны, проверяющие эти флаги (оплата проезда, транспортная карта и схема, всплывающая реклама на экране маршрутов, партнёрские плитки на главной и в билетах), не создают рекламные блоки. Функциональные флаги (такси, метро, биометрия и т. д.) не затронуты |
| 4 | **Smali**: пустые тела (`return-void`) методов `loadAd` у `NativeAdLoader` (2 варианта), `BannerAdView` (1) и `SliderAdLoader` (2) из SDK Yandex Ads | `smali_classes9/com/yandex/mobile/ads/...`, `scripts/patch_ads.py` | объявление не запрашивается и не показывается нигде, где приложение вызывает эти загрузчики: карусель на Cards, нативные блоки, статус оплаты, баннеры. Слушатели загрузки не вызываются |
| 5 | **Разметка**: `android:visibility="gone"` у контейнеров баннеров: `compose_view_banner` (экран Cards), `yandexBannerView` (панель фирменных остановок), `composeViewBanner` (панель партнёрских точек) | `res/layout/fragment_profile_main_cards.xml`, `bottom_sheet_branded_stops.xml`, `bottom_sheet_poi.xml`, `scripts/patch_layout.py` | нет пустых мест на месте рекламы |
| 6 | Отладочная Activity `kz.onay.nearby.ui.NearbyStopDebugActivity` (`exported="true"`) | `AndroidManifest.xml`, `scripts/patch_manifest.py` | **только отладочная сборка** (`ONAY_DEBUG_ACTIVITY=1`): запуск через adb с тестовыми данными и подменой координаты. В релизной сборке отсутствует (проверено по манифесту) |

Правка 2 в smali: заменена одна инструкция вызова
```
-    invoke-static {}, Lkz/onay/ui/main/life/LifeFragment;->newInstance()Lkz/onay/ui/main/life/LifeFragment;
+    const/4 v0, 0x0
+    invoke-static {v0}, Lkz/onay/nearby/ui/NearbyStopFragment;->newInstance(Landroid/os/Bundle;)Lkz/onay/nearby/ui/NearbyStopFragment;
```
(регистр `v0` в этой ветке свободен, у метода `.locals 1`). Скрипт останавливает сборку, если вызов не найден ровно один раз внутри `Q0(I)` или файл уже изменён.

Вкладки Routes (оригинальный экран транспорта), Cards (кроме убранного баннера), Menu, а также оплата, авторизация, купоны (код), профиль не менялись. Раздел «Discounts» (льготы) не менялся: это функция приложения, а не реклама.

Нижняя навигация и круглая кнопка QR главного экрана заходят на контейнер фрагмента, поэтому в `MainActivity` экран имеет нижний отступ 44 dp (в отладочной Activity его нет).

Остаточное свойство сборки: тестовые данные (`TestData`) остаются в dex, но достижимы только по аргументу `data=test`, который передаёт лишь отладочная Activity; релизная вкладка аргументов не передаёт.

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
- gRPC и protobuf: `stop.StopServiceGrpc`, `route.RouteServiceGrpc`, `position.PositionServiceGrpc`, их `OuterClass` и сообщения (имена вложенных классов через `$`).
- `kz.onay.ui.routes2.shared.MyLocationRequestHelper` (`getLocation()`).


## Отличие самостоятельного APK от прежних split-APK

После объединения split-ов в единый APK `scripts/build_universal_apk.sh` по умолчанию переупаковывает только **установочный идентификатор** из `kz.onay` в `kz.onay.free` с помощью `scripts/patch_clone_manifest.py`.

- В манифесте исходные относительные Java-компоненты (`.OnayApp`, `.ui.*`) разворачиваются до абсолютных имён в `kz.onay.*`.
- Собственные provider authorities, custom permissions, process/task affinities переводятся на независимый пакет, чтобы не конфликтовать с ONAY! из Play.
- Итоговый APK сохраняет побайтово DEX, `resources.arsc`, ресурсы, assets и native-библиотеки. Apktool компилирует новый бинарный манифест, но в исходный APK переносится **только** `AndroidManifest.xml` (`replace_compiled_manifest.py`). Сохранность файлов проверяет `scripts/verify_clone_payload.py`.
- `work/out/*.apk` по-прежнему содержит старый пакет `kz.onay` и **не предназначен для одновременной установки с официальным ONAY!**.
- Не все пакетно-зависимые сторонние интеграции можно исправить переименованием манифеста; обязательны реальные проверки из [CLONE_COMPATIBILITY.md](CLONE_COMPATIBILITY.md).

## Исправление первого запуска на независимом пакете

При чистой установке Android-пакета `kz.onay.free` Room БД пуста — это ожидаемо, так как официальное ONAY! (`kz.onay`) хранит свои данные отдельно. До появления остановок `NearbyStopFragment` скрывает пустую карту и нижний док вместо отображения Африки, показывает сообщение о необходимости загрузки, предлагает «Открыть транспорт» и «Повторить».

Переход в оригинальную вкладку Routes выполняется через **`MainActivity.x1(0)`** (метод переключения вкладок ONAY! 3.6.5). Предыдущая попытка `performClick()` по скрытому view `nav_routes` не гарантировала вызова навигации, поскольку обработчик может быть привязан к контейнеру. При ошибке отражённого вызова отображается явное сообщение, предоставляется доступ к штатным вкладкам; пользователь может повторить переход.

Остаётся проверить на реальном устройстве, что оригинальная вкладка Routes **действительно синхронизирует** остановки после смены Android package ID: возможны ограничения Firebase, города, гRPC или авторизации. Это изменение само по себе не устраняет зависимости данных транспорта от серверной авторизации. Баг: [#12](https://github.com/megannnn98/onay-free/issues/12).
