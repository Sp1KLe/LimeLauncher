#!/usr/bin/env python3
"""Собирает zip с модами для FPS под выбранную версию Minecraft (Fabric) через API Modrinth.
Использование: make_modpack.py <версия Minecraft> <папка-вывода>"""
import hashlib, json, os, sys, urllib.parse, urllib.request, zipfile

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "LimeLauncher-modpack-builder/1.0 (github.com/Sp1KLe/LimeLauncher)"}

# Только клиентские моды, повышающие FPS. Sodium работает с рендерером LTW (OpenGL ES 3, 1.17+).
MODS = [
    "fabric-api", "sodium", "lithium", "ferrite-core", "entityculling",
    "immediatelyfast", "modernfix", "moreculling", "dynamic-fps",
]


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def resolve(slug, mc):
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
    versions = json.loads(get(f"{API}/project/{slug}/version?{q}"))
    if not versions:
        return None
    rank = {"release": 0, "beta": 1, "alpha": 2}
    versions.sort(key=lambda v: (rank.get(v["version_type"], 3), -0))  # стабильная сортировка: свежие сверху
    v = versions[0]
    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
    return v, f


def main():
    mc, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    zpath = os.path.join(out, f"LimeLauncher-fps-mods-{mc}.zip")
    report, got, seen, files = [], [], set(), set()

    def add(slug, depth, z):
        if slug in seen or depth > 2:
            return
        seen.add(slug)
        try:
            r = resolve(slug, mc)
        except Exception as e:
            report.append(f"- {slug}: ошибка запроса ({e})")
            return
        if r is None:
            report.append(f"- {slug}: нет версии под Minecraft {mc} (Fabric)")
            return
        v, f = r
        data = get(f["url"])
        sha1 = f.get("hashes", {}).get("sha1")
        if sha1 and hashlib.sha1(data).hexdigest() != sha1:
            report.append(f"- {slug}: не совпала контрольная сумма, пропущен")
            return
        if f["filename"] in files:
            return
        files.add(f["filename"])
        z.writestr("mods/" + f["filename"], data)
        got.append(f"{slug} -> {f['filename']}")
        for d in v.get("dependencies", []):
            if d.get("dependency_type") == "required" and d.get("project_id"):
                add(d["project_id"], depth + 1, z)

    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for slug in MODS:
            add(slug, 0, z)
        readme = (
            f"LimeLauncher: моды для FPS, Minecraft {mc}, Fabric\n\n"
            "Как поставить:\n"
            f"1. В лаунчере создай версию Fabric {mc} (Создать версию -> Fabric).\n"
            "2. Нажми «Открыть папку игры», распакуй этот архив туда: папка mods должна лечь в папку игры.\n"
            "3. В настройках видео выбери рендерер LTW (OpenGL ES 3).\n\n"
            "Установлено:\n" + "\n".join(got) + "\n\n"
            "Не нашлось:\n" + ("\n".join(report) if report else "- всё нашлось") + "\n"
        )
        z.writestr("README.txt", readme)
    print(f"== {mc}: установлено {len(got)} модов")
    print("\n".join(got))
    if report:
        print("Не нашлось:\n" + "\n".join(report))
    if not got:
        sys.exit(1)


main()
