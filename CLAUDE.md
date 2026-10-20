# Мой цикл — заметки для Claude

Нативное Android-приложение для отслеживания менструального цикла. Kotlin, Jetpack Compose, Material 3.
Сейчас это каркас с самообновлением. Функции трекера добавляются отдельными PR.

## Жёсткие правила продукта

- **Без рекламы, аналитики, трекеров и Google Play Services.** Не добавлять SDK Firebase, Crashlytics, AdMob и любые библиотеки, которые отправляют данные.
- **Данные о здоровье не покидают устройство.** Сеть используется только для проверки GitHub Releases и загрузки APK. Облачный бэкап отключён (`data_extraction_rules.xml`) и должен оставаться отключённым.
- Новые разрешения в `AndroidManifest.xml` добавлять, только когда без них нельзя, и объяснять в PR.
- Тексты интерфейса задаются в `res/values/strings.xml` (русский, по умолчанию) и `res/values-en/strings.xml` (английский). Строки в Kotlin-коде не хардкодить.
- Прогнозы цикла приблизительные. Не обещать контрацепцию и не давать медицинских советов.

## Структура

- `app/src/main/java/io/github/zaxar1231/mycycle/`
  - `MainActivity.kt`, `MyCycleApp.kt` — точка входа
  - `ui/` — Compose-экраны, `ui/theme/` — тема
  - `update/` — самообновление: `UpdateChecker` (GitHub API), `UpdateInstaller` (загрузка и установка APK), `UpdateWorker` (фоновая проверка), `Version` (сравнение версий)
- `app/src/test/` — JVM unit-тесты (JUnit 4)
- `version.properties` — базовая версия; релизные версии считает CI

## Сборка и проверки

В облачной сессии Android SDK нет, а `dl.google.com` заблокирован, поэтому локальная сборка не работает. Проверкой служит CI на PR. Подробности в скиле `android-ci`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease   # то же, что делает CI
```

Стек: AGP 9 со встроенным Kotlin (плагин `kotlin-android` в модуле не подключается), Gradle 9, compileSdk 37, minSdk 26, targetSdk 35. В AGP 9 unit-тесты есть только для debug: задачи `testReleaseUnitTest` нет.

## Процесс

- Ветка, затем PR в `main`. Merge commit, без squash и rebase.
- **Заголовок PR задаёт следующую версию:** `fix:` → patch, `feat:` → minor, `feat!:`/`BREAKING CHANGE` → major, `#norelease` → без релиза. Подробности в скиле `release`.
- После merge в `main` релиз собирается и публикуется автоматически. PR от Dependabot вливаются сами, если CI зелёный.
- Не коммитить keystore и пароли: подпись берётся из секретов репозитория.

## Скилы и агенты

- Скилы (`.claude/skills/`): `add-feature`, `release`, `android-ci`, `steward`.
- Агенты (`.claude/agents/`): `feature-planner`, `android-reviewer`, `privacy-auditor`.

Перед открытием PR с изменениями в коде прогони `android-reviewer`. Если меняются сеть, хранение данных или разрешения, прогони ещё и `privacy-auditor`.
