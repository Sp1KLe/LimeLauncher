#!/usr/bin/env bash
# Применяет настройки LimeLauncher поверх исходников PojavLauncher (LGPL-3.0).
# Использование: LIME_BUILD=<номер> scripts/apply-lime.sh <папка-с-PojavLauncher>
set -euo pipefail

POJAV="${1:?укажите папку с PojavLauncher}"
BUILD="${LIME_BUILD:-dev}"
RES="$POJAV/app_pojavlauncher/src/main/res"

# 1. Название приложения с номером сборки, чтобы сразу видеть, какая версия установлена.
for f in "$RES"/values/strings.xml "$RES"/values/strings_untranslatable.xml; do
  [ -f "$f" ] || continue
  sed -i -E "s|(<string name=\"app_short_name\"[^>]*>)PojavLauncher|\1LimeLauncher|" "$f"
done
# Подпись под иконкой задаётся в build.gradle (resValue app_name) для каждого типа сборки.
sed -i -E "s|(resValue \"string\", \"app_name\", \")PojavLauncher[^\"]*\"|\1LimeLauncher #${BUILD}\"|" \
  "$POJAV/app_pojavlauncher/build.gradle"
grep -n 'resValue "string", "app_name"' "$POJAV/app_pojavlauncher/build.gradle"
grep -q "LimeLauncher #${BUILD}" "$POJAV/app_pojavlauncher/build.gradle" || { echo "Не удалось переименовать приложение"; exit 1; }

# 2. Интерфейс Material 3: файлы из overlay/ копируются поверх исходников Pojav
#    (новый домашний экран на Compose, верхняя панель, обновлённый MainMenuFragment).
OVERLAY="$(dirname "$0")/../overlay"
if [ -d "$OVERLAY" ]; then
  cp -r "$OVERLAY"/. "$POJAV"/
  echo "Overlay применён."
fi

# 3. Иконки Compose (версия берётся из BOM, который уже подключён в проекте).
GRADLE="$POJAV/app_pojavlauncher/build.gradle"
if ! grep -q "material-icons-core" "$GRADLE"; then
  sed -i "s#^\([[:space:]]*\)implementation 'androidx.compose.material3:material3'#&\n\1implementation 'androidx.compose.material:material-icons-core'#" "$GRADLE"
fi
grep -n "material-icons-core" "$GRADLE" || { echo "Не удалось добавить material-icons-core"; exit 1; }

# 4. Патчи из папки patches/ (применяются по алфавиту).
PATCHES="$(dirname "$0")/../patches"
if [ -d "$PATCHES" ]; then
  for p in "$PATCHES"/*.patch; do
    [ -e "$p" ] || continue
    echo "Применяю патч: $p"
    git -C "$POJAV" apply --whitespace=nowarn "$(realpath "$p")"
  done
fi

echo "LimeLauncher #${BUILD}: настройки применены."

