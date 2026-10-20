# Twilight Cycle — заметки для Claude

Нативное Android-приложение для отслеживания менструального цикла с партнёрским режимом, питомцем-маскотом и темами. Kotlin, Jetpack Compose, Material 3.
Полное ТЗ и 13 этапов разработки — `docs/spec.md`. Каждый этап — отдельный PR `feat:` (minor-релиз), после каждого этапа приложение собирается и запускается.

## Жёсткие правила продукта

- **Без рекламы, аналитики, трекеров и Google Play Services.** Не добавлять SDK Firebase, Crashlytics, AdMob и любые библиотеки, которые отправляют данные.
- **Local-first.** Всё работает офлайн. Данные о здоровье хранятся на устройстве (Room + SQLCipher). Наружу они уходят только в партнёрском режиме и в бэкапе — по явному согласию и **со сквозным шифрованием**: Supabase хранит зашифрованные данные, но прочитать их не может. Облачный бэкап Android отключён (`data_extraction_rules.xml`) и должен оставаться отключённым.
- Новые разрешения в `AndroidManifest.xml` добавлять, только когда без них нельзя, и объяснять в PR.
- Тексты интерфейса задаются в `res/values/strings.xml` (русский, по умолчанию) и `res/values-en/strings.xml` (английский). Строки в Kotlin-коде не хардкодить.
- Прогнозы цикла приблизительные. Не обещать контрацепцию и не давать медицинских советов.

## Структура

Пакет `io.github.zaxar1231.mycycle` и `applicationId` не меняются (иначе обновления не встанут поверх).

- `:app` — `TwilightApp` (Hilt), `MainActivity`, навигация (`navigation/`: нижний бар `Календарь · Статистика · [+] · Питомец · Настройки`), Hilt-модули (`di/`)
- `:core:domain` — чистый Kotlin без Android: модели (`Cycle`, `DayLog`, `Medication`, `Pet`, `PartnerLink`, `UserSettings`…), интерфейсы репозиториев, use case'ы, `CycleConstants` (все числа цикла — только здесь), `DateProvider`, позже `CyclePredictor`. Тесты JUnit 5
- `:core:ui` — тема `TwilightTheme` (Material You по умолчанию), `CycleColors` (цвета фаз берутся из темы, не хардкодятся), общие компоненты и иконки
- `:core:update` — самообновление из GitHub Releases (`UpdateChecker`, `UpdateInstaller`, `UpdateWorker`, `Version`, UI карточки и диалога)
- `:core:data` — Room, зашифрованный SQLCipher (пароль БД случайный, сам зашифрован ключом из Android Keystore, лежит в no-backup), DataStore для настроек, реализации репозиториев. Любое изменение схемы — новая `version` и миграция в `Migrations`, без destructive fallback
- `:feature:*` — экраны: `onboarding`, `calendar`, `stats`, `daylog` (FAB «+»), `pet`, `settings`. Каждый модуль отдаёт `@Serializable` маршрут и `NavGraphBuilder.xxxScreen()`
- Общая конфигурация Android-библиотек и JVM-модулей — в корневом `build.gradle.kts`, уровни SDK — в `gradle/libs.versions.toml`

## Сборка и проверки

В облачной сессии Android SDK нет, а `dl.google.com` заблокирован, поэтому локальная сборка не работает. Проверкой служит CI на PR. Подробности в скиле `android-ci`.

```bash
./gradlew test lintDebug assembleDebug assembleRelease   # то же, что делает CI
```

Стек: AGP 9 со встроенным Kotlin (плагин `kotlin-android` в модулях не подключается), Gradle 9, compileSdk 37, minSdk 26, targetSdk 35, Hilt + KSP, Navigation Compose (type-safe маршруты), JUnit 5. В AGP 9 unit-тесты есть только для debug; запускай `test`, чтобы прогнать и JVM-модули.

## Принятые решения

- Название на экране — **Twilight Cycle** (ru и en одинаково).
- Партнёрский режим и бэкап — со сквозным шифрованием (см. «Local-first» выше), а не только RLS.
- Релиз после каждого этапа: PR `feat: этап N — …` → автомерж → minor-версия.
- Тесты — JUnit 5 (+ Turbine для Flow) во всех модулях.

## Процесс

- Ветка, затем PR в `main`. Merge commit, без squash и rebase.
- **Заголовок PR задаёт следующую версию:** `fix:` → patch, `feat:` → minor, `feat!:`/`BREAKING CHANGE` → major, `#norelease` → без релиза. Подробности в скиле `release`.
- PR из веток `claude/*` и от Dependabot вливаются автоматически после зелёного CI, затем автоматически выходит релиз. Незаконченную работу открывай как draft или с меткой `no-automerge`.
- Не коммитить keystore и пароли: подпись берётся из секретов репозитория.

## Скилы и агенты

- Скилы (`.claude/skills/`): `add-feature`, `release`, `android-ci`, `steward`.
- Агенты (`.claude/agents/`): `feature-planner`, `android-reviewer`, `privacy-auditor`.

Перед открытием PR с изменениями в коде прогони `android-reviewer`. Если меняются сеть, хранение данных или разрешения, прогони ещё и `privacy-auditor`.
