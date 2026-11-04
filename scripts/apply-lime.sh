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

# Старые экраны: убираем «пиксельные» ползунки/переключатели/кнопки, красим акцент в лайм,
# скругляем окна. Всё остальное (тексты, фон) уже берётся из светлых/тёмных lime_* цветов.
sed -i -E '/minepb_progress/d; /mineknob/d; /trackTintMode/d' "$STYLES"
sed -i -E 's|@drawable/button_flat_toggle|@drawable/lime_button|; s|@drawable/button_flat|@drawable/lime_button|' "$STYLES"
sed -i -E 's|(<item name="dialogCornerRadius">)0dp|\128dp|' "$STYLES"
sed -i -E 's|(<style name="MojoLauncher" parent="[^"]*">)|\1\n        <item name="colorAccent">@color/lime_primary</item>\n        <item name="colorPrimary">@color/lime_primary</item>\n        <item name="colorControlActivated">@color/lime_primary</item>|' "$STYLES"
grep -q "lime_button" "$STYLES" || { echo "Не удалось перекрасить кнопки"; exit 1; }
grep -q "colorAccent" "$STYLES" || { echo "Не удалось задать акцентный цвет"; exit 1; }

# Сохранённая тема применяется при старте приложения (по умолчанию — как в системе).
APP="$POJAV/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/PojavApplication.java"
sed -i 's|^\([[:space:]]*\)ContextExecutor.setApplication(this);|&\n\1net.kdt.pojavlaunch.lime.LimeThemeStore.apply(this);|' "$APP"
grep -q "LimeThemeStore.apply" "$APP" || { echo "Не удалось подключить тему в PojavApplication"; exit 1; }

# 3c. Горизонтальная ориентация для экранов лаунчера.
MANIFEST="$POJAV/app_pojavlauncher/src/main/AndroidManifest.xml"
sed -i 's|\(android:name="net.kdt.pojavlaunch.LauncherActivity"\)|\1\n            android:screenOrientation="sensorLandscape"|' "$MANIFEST"
grep -A1 'net.kdt.pojavlaunch.LauncherActivity' "$MANIFEST" | grep -q sensorLandscape \
  || { echo "Не удалось включить ландшафт для LauncherActivity"; exit 1; }

# 3d. «Чёрный ящик»: GameActivity (процесс игры) ставит метку старта.
GAMEACT="$POJAV/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/GameActivity.java"
sed -i 's|^\([[:space:]]*\)instance = Instances.loadSelectedInstance();|\1net.kdt.pojavlaunch.lime.LimeCrashWatch.gameStarted();\n&|' "$GAMEACT"
grep -q "LimeCrashWatch.gameStarted" "$GAMEACT" || { echo "Не удалось подключить LimeCrashWatch в GameActivity"; exit 1; }
[ "$(grep -c "LimeCrashWatch.gameStarted" "$GAMEACT")" = "1" ] || { echo "LimeCrashWatch вставлен не один раз"; exit 1; }


# 3e. Остальной старый интерфейс: поле ввода в цвет темы, белый текст полосы загрузки -> цвет темы.
MEDIT="$POJAV/app_pojavlauncher/src/main/java/com/kdt/mcgui/MineEditText.java"
sed -i 's|setBackgroundColor(Color.parseColor("#131313"));|setBackgroundColor(androidx.core.content.ContextCompat.getColor(getContext(), net.kdt.pojavlaunch.R.color.lime_bar));|' "$MEDIT"
grep -q "lime_bar" "$MEDIT" || { echo "Не удалось перекрасить MineEditText"; exit 1; }
sed -i 's|android:textColor="@android:color/white"|android:textColor="@color/lime_text"|' "$RES/layout/view_progress.xml"
grep -q "lime_text" "$RES/layout/view_progress.xml" || { echo "Не удалось перекрасить полосу загрузки"; exit 1; }
# Заголовок ползунка в настройках больше не принудительно белый.
sed -i 's|titleTextView.setTextColor(Color.WHITE);|titleTextView.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), net.kdt.pojavlaunch.R.color.lime_text));|' "$POJAV/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/CustomSeekBarPreference.java"
grep -q "lime_text" "$POJAV/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/CustomSeekBarPreference.java" || { echo "Не удалось перекрасить ползунок"; exit 1; }

# 3f. Оптимизация Java: Serial GC (меньше потоков и памяти на слабом телефоне), без perf-данных.
JR="$POJAV/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java"
sed -i 's|^\([[:space:]]*\)runtimeArgs.addAll(getJavaArgs(runtimeHomeDir.getAbsolutePath(), vmArgs));|\1boolean limeOwnGc = false;\n\1for(String limeA : vmArgs) if(limeA.contains("GC") \|\| limeA.startsWith("-Xshare")) limeOwnGc = true;\n&|' "$JR"
sed -i 's|^\([[:space:]]*\)runtimeArgs.add("-XX:ActiveProcessorCount=" + java.lang.Runtime.getRuntime().availableProcessors());|&\n\1if(!limeOwnGc) runtimeArgs.add("-XX:+UseSerialGC");\n\1runtimeArgs.add("-XX:-UsePerfData");|' "$JR"
grep -q "limeOwnGc = false" "$JR" && grep -q "UseSerialGC" "$JR" || { echo "Не удалось добавить флаги JVM"; exit 1; }

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

