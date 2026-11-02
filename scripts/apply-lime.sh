#!/usr/bin/env bash
# Применяет настройки LimeLauncher поверх исходников PojavLauncher (LGPL-3.0).
# Использование: scripts/apply-lime.sh <папка-с-PojavLauncher>
set -euo pipefail

POJAV="${1:?укажите папку с PojavLauncher}"
RES="$POJAV/app_pojavlauncher/src/main/res"

# 1. Название приложения.
for f in "$RES"/values/strings.xml "$RES"/values/strings_untranslatable.xml; do
  [ -f "$f" ] || continue
  sed -i -E 's#(<string name="app(_short)?_name"[^>]*>)PojavLauncher#\1LimeLauncher#' "$f"
done

# 2. Место для будущих правок: сюда добавляются патчи из папки patches/.
PATCHES="$(dirname "$0")/../patches"
if [ -d "$PATCHES" ]; then
  for p in "$PATCHES"/*.patch; do
    [ -e "$p" ] || continue
    echo "Применяю патч: $p"
    git -C "$POJAV" apply --whitespace=nowarn "$(realpath "$p")"
  done
fi

echo "LimeLauncher: настройки применены."
