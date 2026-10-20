---
name: release
description: Как в «Моём цикле» устроены версии, релизы, подпись APK и самообновление. Используй, когда нужно выпустить версию, понять, почему релиз не вышел или вышел не с той версией, поменять схему версий или разобраться с обновлением в приложении.
---

# Релизы

## Поток

1. PR влит в `main`. Запускается `.github/workflows/release.yml`; изменения только в `*.md`, `docs/` и шаблонах `.github` его не запускают.
2. Шаг «Compute version» берёт последний тег `vX.Y.Z` и сообщение merge-коммита, в котором есть заголовок PR:
   - `BREAKING CHANGE`, `#major`, `feat!:` → major;
   - `feat:`, `#minor` → minor;
   - всё остальное → patch;
   - `#norelease`, `[skip release]` → релиза не будет.
   Если тегов ещё нет, берётся `VERSION_NAME` из `version.properties`. Если поднять там версию выше последнего тега, она будет использована как есть.
3. Gradle собирает `assembleRelease -PappVersionName=X.Y.Z`. `versionCode` = major·1 000 000 + minor·1 000 + patch.
4. APK подписывается ключом из секретов `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Подпись проверяется через `apksigner verify`.
5. `gh release create` создаёт тег и релиз с файлами `my-cycle.apk`, `my-cycle-vX.Y.Z.apk` и `SHA256SUMS.txt`.

Ручной запуск: Actions → Release → Run workflow (patch/minor/major). Merge, сделанный через `GITHUB_TOKEN` (автомерж Dependabot и `claude/*`), push-воркфлоу не запускает. Поэтому джоба `automerge` стартует релиз через `gh workflow run release.yml -f bump=auto -f message=<заголовок PR>`: версия считается по заголовку, как при обычном merge. PR, в которых изменились только документы, релиз не запускают.

## Самообновление в приложении

`UpdateChecker` запрашивает `api.github.com/repos/Zaxar1231/Twilight-cycle/releases/latest` и сравнивает `tag_name` с версией установленного приложения (`Context.appVersionName()`) через `Version.isNewer`. Затем берёт первый asset `*.apk`. Pre-release и draft туда не попадают. Репозиторий раньше назывался `My-cycle`: старые версии приложения обращаются к старому адресу, а GitHub перенаправляет его на новый (301), и `HttpURLConnection` идёт по перенаправлению. Поэтому `applicationId` и ключ подписи менять нельзя, иначе обновления не встанут поверх установленного приложения. Если поменять имена файлов или формат тегов, обнови и `ReleaseInfo.fromGitHubJson`.

## Типичные поломки

- «Prepare signing key» падает → не заданы секреты.
- `Tag vX.Y.Z already exists` → тег создан вручную; подними версию в `version.properties`.
- Релизы отменяются (`cancelled`) → группа concurrency `release` держит только один ожидающий запуск, старые ожидающие отменяются. Это нормально, последний запуск соберёт актуальный `main`.
- **Ключ подписи менять нельзя:** иначе обновления не установятся поверх уже установленного приложения.
