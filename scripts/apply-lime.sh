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

# 3b. Светлая/тёмная тема для старых экранов: цвета Pojav заменяются ссылками на
#     lime_* (у них есть светлый и тёмный вариант), базовая тема становится DayNight.
COLORS="$RES/values/colors.xml"
remap() { sed -i -E "s|(<color name=\"$1\">)[^<]*|\1@color/$2|" "$COLORS"; }
remap background_app lime_bg
remap minebutton_secondary lime_surface_hi
remap background_overlay lime_outline
remap background_status_bar lime_bar
remap background_bottom_bar lime_bar
remap primary_text lime_text
remap secondary_text lime_text2
remap icon_outline_color lime_text
grep -q "@color/lime_bg" "$COLORS" || { echo "Не удалось перекрасить цвета"; exit 1; }

STYLES="$RES/values/styles.xml"
sed -i 's|parent="@style/Theme.AppCompat.NoActionBar"|parent="@style/Theme.AppCompat.DayNight.NoActionBar"|' "$STYLES"
grep -q "Theme.AppCompat.DayNight.NoActionBar" "$STYLES" || { echo "Не удалось включить DayNight"; exit 1; }

# Сохранённая тема применяется при старте приложения (по умолчанию — как в системе).
APP="$POJAV/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/PojavApplication.java"
sed -i 's|^\([[:space:]]*\)ContextExecutor.setApplication(this);|&\n\1net.kdt.pojavlaunch.lime.LimeThemeStore.apply(this);|' "$APP"
grep -q "LimeThemeStore.apply" "$APP" || { echo "Не удалось подключить тему в PojavApplication"; exit 1; }

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

