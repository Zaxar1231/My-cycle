<div align="center">

# Twilight Cycle

Нативное Android-приложение для отслеживания менструального цикла с партнёрским режимом, питомцем-маскотом и темами.
**Без рекламы, без трекеров, без Google Play Services** — данные остаются только на устройстве.

[![Последний релиз](https://img.shields.io/github/v/release/Zaxar1231/Twilight-cycle?label=версия&color=c2416b)](https://github.com/Zaxar1231/Twilight-cycle/releases/latest)
[![Скачивания](https://img.shields.io/github/downloads/Zaxar1231/Twilight-cycle/total?label=скачивания)](https://github.com/Zaxar1231/Twilight-cycle/releases)
[![CI](https://github.com/Zaxar1231/Twilight-cycle/actions/workflows/ci.yml/badge.svg)](https://github.com/Zaxar1231/Twilight-cycle/actions/workflows/ci.yml)
[![Release](https://github.com/Zaxar1231/Twilight-cycle/actions/workflows/release.yml/badge.svg)](https://github.com/Zaxar1231/Twilight-cycle/actions/workflows/release.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
[![Лицензия MIT](https://img.shields.io/github/license/Zaxar1231/Twilight-cycle?label=лицензия)](LICENSE)

### [Скачать APK (последняя версия)](https://github.com/Zaxar1231/Twilight-cycle/releases/latest/download/my-cycle.apk)

[Все версии](https://github.com/Zaxar1231/Twilight-cycle/releases) · [Что нового](https://github.com/Zaxar1231/Twilight-cycle/releases/latest) · [Сообщить об ошибке](https://github.com/Zaxar1231/Twilight-cycle/issues/new/choose)

</div>

## Установка

1. Скачайте [`my-cycle.apk`](https://github.com/Zaxar1231/Twilight-cycle/releases/latest/download/my-cycle.apk) на телефон.
2. Откройте файл и разрешите установку из этого источника, если Android попросит.
3. Дальше приложение обновляется само — повторно скачивать APK не нужно.

Контрольные суммы каждой сборки лежат в `SHA256SUMS.txt` в релизе.

## Обновления

Приложение ищет новые версии в [GitHub Releases](https://github.com/Zaxar1231/Twilight-cycle/releases):

- **автоматически** — при запуске и в фоне раз в 12 часов (WorkManager), с уведомлением о новой версии;
- **по кнопке** «Проверить обновления» на главном экране.

Найдя новую версию, приложение показывает описание релиза, скачивает APK с прогресс-баром и открывает системный установщик. Автопроверку можно выключить переключателем.

## Функционал

Разработка идёт по 13 этапам из [ТЗ](docs/spec.md), после каждого выходит релиз.

- [x] **Этап 1.** Модульный каркас, Hilt, навигация (Календарь · Статистика · [+] · Питомец · Настройки), Material You и тёмная тема
- [x] **Этап 2.** Зашифрованная база (Room + SQLCipher), модель данных, онбординг, выбор темы
- [ ] 3. Прогнозы (`CyclePredictor`), календарь, карточка «сегодня»
- [ ] 4. Запись дня, контент по фазам
- [ ] 5. Статистика и история циклов
- [ ] 6. Гормоны, анализ фаз, вероятность беременности
- [ ] 7. Медитация и аффирмации
- [ ] 8. PIN/биометрия, Liquid Glass и другие стили
- [ ] 9. Аккаунты и партнёрский режим (сквозное шифрование)
- [ ] 10. Питомец
- [ ] 11. Виджеты и уведомления
- [ ] 12. PDF-отчёт для врача, экспорт/импорт
- [ ] 13. Полировка, доступность, тесты

## Как устроены релизы

Всё происходит автоматически:

| Событие | Что делает CI |
| --- | --- |
| Открыт / обновлён PR | [`ci.yml`](.github/workflows/ci.yml): unit-тесты, Android lint, сборка debug APK (лежит в артефактах запуска) |
| PR влит в `main` | [`release.yml`](.github/workflows/release.yml): считает следующую версию, собирает подписанный release APK, создаёт тег и GitHub Release с `my-cycle.apk`, `my-cycle-vX.Y.Z.apk` и `SHA256SUMS.txt` |
| Раз в неделю | Dependabot открывает PR с обновлениями зависимостей и actions; если CI зелёный, PR вливается автоматически и выходит patch-релиз |
| PR из веток `claude/*` (Claude Code) | после зелёного CI вливается автоматически, версия — по заголовку PR. Метка `no-automerge` оставляет PR открытым |

Номер версии определяется заголовком PR / сообщением коммита:

| Заголовок | Версия |
| --- | --- |
| `fix: …`, `chore: …` и любой другой | patch: 0.1.0 → 0.1.1 |
| `feat: …` или `#minor` | minor: 0.1.0 → 0.2.0 |
| `feat!: …`, `BREAKING CHANGE` или `#major` | major: 0.1.0 → 1.0.0 |
| `#norelease` или `[skip release]` | релиз не создаётся |

Первый релиз берёт версию из [`version.properties`](version.properties) (`0.1.0`). Если поднять там `VERSION_NAME` выше последнего тега, будет использовано это значение. Также релиз можно запустить вручную: **Actions → Release → Run workflow** (`auto` = patch).

`versionCode` вычисляется из версии (`1.2.3` → `1002003`), поэтому каждая новая версия ставится поверх старой.

### Подпись релизов

Обновление поверх установленного приложения возможно, только если все релизы подписаны **одним и тем же ключом**. Ключ хранится в секретах репозитория (**Settings → Secrets and variables → Actions**):

| Секрет | Значение |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | keystore в base64 (`base64 -w0 release.jks`) |
| `SIGNING_STORE_PASSWORD` | пароль keystore |
| `SIGNING_KEY_ALIAS` | alias ключа |
| `SIGNING_KEY_PASSWORD` | пароль ключа |

Создать свой ключ:

```bash
keytool -genkeypair -v -keystore release.jks -alias mycycle -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks
```

Храните `release.jks` и пароли в надёжном месте: если ключ потерять, пользователям придётся удалить приложение и поставить заново.

## Сборка локально

Нужны JDK 17+ и Android SDK (проще всего — Android Studio).

```bash
./gradlew test                # unit-тесты всех модулей
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

## Технологии

Kotlin · Jetpack Compose · Material 3 · WorkManager · minSdk 26 (Android 8.0) · targetSdk 35

Нет рекламных SDK, аналитики и Google Play Services. Разрешения: интернет (только для проверки обновлений на GitHub), установка пакетов (для самообновления), уведомления (о новой версии). Резервное копирование в облако отключено.

## Дисклеймер

Прогнозы приложения будут приблизительными и не являются медицинской рекомендацией или средством контрацепции.

## Лицензия

[MIT](LICENSE)
