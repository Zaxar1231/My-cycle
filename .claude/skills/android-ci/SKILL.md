---
name: android-ci
description: Сборка и CI Android-проекта «Мой цикл» — что делать, когда сборка или GitHub Actions падают, как проверять изменения без локального Android SDK, особенности AGP 9 и обновлений Dependabot. Используй при красном CI, обновлении Gradle/AGP/Kotlin/AndroidX или перед push изменений в сборку.
---

# Сборка и CI

## Локально в облачной сессии сборки нет

Android SDK не установлен, `dl.google.com` и `maven.google.com` заблокированы. Единственная настоящая проверка — CI на PR (`.github/workflows/ci.yml`): `testDebugUnitTest lintDebug assembleDebug assembleRelease`. Release собирается без подписи, но с R8.

Порядок работы: push → PR → дождаться CI. Статус без авторизации можно смотреть через публичный API:

```bash
curl -sS "https://api.github.com/repos/Zaxar1231/Twilight-cycle/actions/workflows/ci.yml/runs?branch=<ветка>&per_page=1"
```

Логи упавшей джобы: инструмент `mcp__github__get_job_logs` (`failed_only: true`, `return_content: true`). Ошибка Gradle обычно находится перед строкой `BUILD FAILED`. Хвост лога — это служебный вывод кеша, поэтому `tail_lines` бери с запасом, около 300.

## Особенности стека

- **AGP 9:** Kotlin встроен. Плагин `org.jetbrains.kotlin.android` в модуле `app` не подключать; в корневом `build.gradle.kts` он объявлен с `apply false`, только чтобы зафиксировать версию Kotlin. `jvmTarget` берётся из `compileOptions`.
- **AGP 9:** unit-тесты создаются только для debug, `testReleaseUnitTest` не существует.
- Новые AndroidX часто требуют свежий AGP и compileSdk. Ошибка вида «Dependency X requires Android Gradle plugin N or higher / compile against version M» лечится обновлением AGP, Gradle и compileSdk вместе.
- Минимальную версию Gradle для AGP подсказывает ошибка «Minimum supported Gradle version is …».

## Dependabot

- AGP, Gradle wrapper и AndroidX сгруппированы в `android-toolchain`, Kotlin — в группу `kotlin` (`.github/dependabot.yml`).
- Зелёные PR вливаются сами (джоба `dependabot-automerge` в `ci.yml`), после чего выходит patch-релиз.
- Если Dependabot-PR красный из-за несовместимых версий, сделай один PR, который поднимает связку целиком, а устаревшие Dependabot-PR закрой с комментарием.
- PR CI собирает merge-коммит с `main`. Если `main` сломан, краснеют все PR: сначала чини `main`.
