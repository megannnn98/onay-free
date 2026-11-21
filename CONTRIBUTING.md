# Как вносить изменения

Проект: неофициальная модификация ONAY! 3.6.5 (экран «Рядом»). Код приложения не собирается через Gradle: новые классы компилируются в `classes12.dex` и добавляются к декодированному приложению патчами (`scripts/patch_*.py`).

## Проверки перед PR
```bash
python3 -I -m unittest discover -s tests      # упаковка, патчи, безопасность workflow
bash scripts/run_unit_tests.sh                # JUnit логики (нужен work/libs/junit-console.jar)
bash scripts/mutation_check.sh                # каждый мутант должен быть убит
```
Те же проверки запускает CI (`.github/workflows/ci.yml`) на каждый PR и на `master`.

## Правила
- Работа идёт в ветке и через PR; в `master` напрямую не коммитим. Слияние только squash.
- Любое правило логики сопровождается тестом и мутантом в `scripts/mutation_check.sh`. Мутанты запускаются на копии, по одному.
- Патчи (`scripts/patch_*.py`) обязаны падать, если место правки найдено не ровно один раз: изменившееся приложение не должно патчиться молча.
- `scripts/build_dex.sh` ошибку компиляции не скрывает, но не прячьте её каналами (`| tail`): проверяйте код выхода.
- Ключи, пароли, `.apk`, `.apks`, содержимое `work/` и личные скриншоты в Git не попадают (см. `.gitignore`).
- Изменения в APK проверяйте на устройстве; если устройства не было, так и напишите в PR.

## Сборка и релиз
См. раздел «Выпуск версии» в `README.md` и `docs/PATCHES.md`. Релиз публикуется только после проверки APK `scripts/verify_universal_apk.sh` и установки на устройство; до проверки выпуск помечается как pre-release.

## Настройки репозитория
Применено: squash-слияние, автоудаление веток после слияния, обновление ветки PR, оповещения Dependabot, Dependabot для GitHub Actions, действия в workflow закреплены по SHA.
Недоступно на бесплатном плане для приватного репозитория: защита ветки/rulesets, code scanning (CodeQL), secret scanning. Если репозиторий станет публичным или будет план с этими функциями, включите: защиту `master` (обязательный PR и проверки `Scripts and packaging tests`, `JVM unit tests and mutation check`, запрет force-push), secret scanning с push protection, CodeQL и OpenSSF Scorecard.
