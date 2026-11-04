package net.kdt.pojavlaunch.lime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.Tools
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Состояние экрана модов: поиск, установка, список установленных. */
class LimeModsModel(
    private val gameDir: File?,
    initialVersion: String,
    initialLoader: String?,
    val instanceLabel: String,
    /** Есть ли у выбранной версии загрузчик модов (иначе моды не загрузятся). */
    val hasLoader: Boolean,
) {
    var query by mutableStateOf("")
    var mcVersion by mutableStateOf(initialVersion)
    var loader by mutableStateOf(initialLoader ?: "fabric")
    var sortIndex by mutableStateOf("downloads")

    var hits by mutableStateOf<List<LimeModrinth.Hit>>(emptyList())
    var total by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    var installedIds by mutableStateOf<Set<String>>(emptySet())
    var installedFiles by mutableStateOf<List<File>>(emptyList())

    /** project_id -> текст статуса установки (пока мод ставится). */
    val progress = mutableStateMapOf<String, String>()

    private var searchGen = 0
    private val registryLock = Any()

    private fun ui(block: () -> Unit) = Tools.runOnUiThread { block() }

    private fun modsDir(): File? = gameDir?.let { File(it, "mods") }
    private fun registryFile(): File? = modsDir()?.let { File(it, ".lime_mods.json") }

    private fun readRegistry(): JSONObject {
        synchronized(registryLock) {
            val f = registryFile()
            return try {
                if (f != null && f.exists()) JSONObject(f.readText()) else JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }
        }
    }

    private fun writeRegistry(obj: JSONObject) {
        synchronized(registryLock) {
            val f = registryFile() ?: return
            f.parentFile?.mkdirs()
            f.writeText(obj.toString())
        }
    }

    fun refreshInstalled() {
        PojavApplication.sExecutorService.execute {
            val dir = modsDir()
            val files = dir?.listFiles { x -> x.isFile && x.name.endsWith(".jar") }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
            val reg = readRegistry()
            val ids = HashSet<String>()
            val names = files.map { it.name }.toSet()
            for (key in reg.keys()) {
                if (reg.optString(key) in names) ids.add(key)
            }
            ui {
                installedFiles = files
                installedIds = ids
            }
        }
    }

    fun search(reset: Boolean) {
        val gen = ++searchGen
        val offset = if (reset) 0 else hits.size
        val q = query
        val v = mcVersion
        val l = loader
        val idx = sortIndex
        loading = true
        error = null
        if (reset) {
            hits = emptyList()
            total = 0
        }
        PojavApplication.sExecutorService.execute {
            try {
                val r = LimeModrinth.search(q, v, l, offset, idx)
                ui {
                    if (gen == searchGen) {
                        hits = if (reset) r.hits else hits + r.hits
                        total = r.total
                        loading = false
                    }
                }
            } catch (e: Exception) {
                ui {
                    if (gen == searchGen) {
                        error = "Не удалось загрузить каталог: ${e.message ?: e.javaClass.simpleName}. Проверьте интернет."
                        loading = false
                    }
                }
            }
        }
    }

    fun install(hit: LimeModrinth.Hit) {
        if (progress.containsKey(hit.id)) return
        val dir = modsDir()
        if (dir == null) {
            notice = "Не выбрана версия игры. Выберите её на главном экране."
            return
        }
        val v = mcVersion
        val l = loader
        progress[hit.id] = "Подготовка…"
        PojavApplication.sExecutorService.execute {
            try {
                dir.mkdirs()
                val installed = ArrayList<String>()
                installProject(hit.id, HashSet(), 0, hit.id, dir, v, l, installed)
                ui {
                    notice = if (installed.size <= 1) "Установлено: ${installed.firstOrNull() ?: hit.title}"
                    else "Установлено ${installed.size} файла: " + installed.joinToString(", ")
                }
            } catch (e: Exception) {
                ui { notice = "Не удалось установить «${hit.title}»: ${e.message ?: e.javaClass.simpleName}" }
            } finally {
                ui { progress.remove(hit.id) }
                refreshInstalled()
            }
        }
    }

    /** Пакет оптимизации для Fabric: моды, повышающие FPS без Sodium (ему нужен OpenGL 3.2). */
    fun installPerformancePack() {
        if (progress.containsKey("pack")) return
        val dir = modsDir()
        if (dir == null) {
            notice = "Не выбрана версия игры. Выберите её на главном экране."
            return
        }
        if (loader != "fabric" && loader != "quilt") {
            notice = "Пакет оптимизации нужен для Fabric. Установите Fabric 1.18.2 для этой версии."
            return
        }
        val v = mcVersion
        val l = loader
        progress["pack"] = "Подготовка…"
        PojavApplication.sExecutorService.execute {
            val ok = ArrayList<String>()
            val failed = ArrayList<String>()
            dir.mkdirs()
            for (slug in listOf("lithium", "ferrite-core", "starlight", "entityculling", "modernfix")) {
                try {
                    installProject(slug, HashSet(), 0, "pack", dir, v, l, ArrayList())
                    ok.add(slug)
                } catch (e: Exception) {
                    failed.add(slug)
                }
            }
            ui {
                progress.remove("pack")
                notice = "Установлено: ${ok.joinToString(", ").ifEmpty { "ничего" }}" +
                    if (failed.isEmpty()) "" else ". Не нашлось для $v: ${failed.joinToString(", ")}"
            }
            refreshInstalled()
        }
    }

    private fun installProject(
        id: String, visited: MutableSet<String>, depth: Int, rootId: String,
        dir: File, v: String, l: String, installed: MutableList<String>,
    ) {
        if (!visited.add(id)) return
        ui { progress[rootId] = if (depth == 0) "Поиск версии…" else "Нужная зависимость…" }

        val r = LimeModrinth.resolve(id, v, l)
        if (r == null) {
            if (depth == 0) throw IOException("нет подходящей версии для Minecraft $v ($l)")
            return // необязательная для нас зависимость без версии — пропускаем
        }
        if (depth < 3) {
            for (dep in r.requiredDeps) installProject(dep, visited, depth + 1, rootId, dir, v, l, installed)
        }

        val dest = File(dir, r.file.filename)
        val already = dest.exists() && (r.file.sha1 == null || LimeModrinth.sha1Of(dest).equals(r.file.sha1, true))
        if (!already) {
            LimeModrinth.download(r.file, dest) { pct ->
                ui { progress[rootId] = "Скачивание $pct%" }
            }
        }

        val reg = readRegistry()
        val old = reg.optString(id)
        if (old.isNotEmpty() && old != dest.name) File(dir, old).delete()
        reg.put(id, dest.name)
        writeRegistry(reg)
        installed.add(dest.name)
    }

    fun remove(file: File) {
        PojavApplication.sExecutorService.execute {
            file.delete()
            val reg = readRegistry()
            val dead = reg.keys().asSequence().filter { reg.optString(it) == file.name }.toList()
            dead.forEach { reg.remove(it) }
            writeRegistry(reg)
            refreshInstalled()
        }
    }

    companion object {
        /** Определяет загрузчик по названию установленной версии (fabric-loader-…, …-forge-…). */
        fun detectLoader(versionId: String?): String? {
            val s = (versionId ?: return null).lowercase()
            return when {
                "neoforge" in s -> "neoforge"
                "quilt" in s -> "quilt"
                "fabric" in s -> "fabric"
                "forge" in s -> "forge"
                else -> null
            }
        }

        /** Достаёт версию Minecraft из названия, например fabric-loader-0.15.11-1.18.2 -> 1.18.2. */
        fun detectMcVersion(versionId: String?): String {
            val s = versionId ?: return ""
            val all = Regex("""\d+\.\d+(?:\.\d+)?""").findAll(s).map { it.value }.toList()
            return all.firstOrNull { it.startsWith("1.") && (it.split(".")[1].toIntOrNull() ?: -1) in 0..40 }
                ?: all.lastOrNull() ?: ""
        }

        fun formatCount(n: Long): String = when {
            n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
            n >= 1_000 -> "%.1f тыс.".format(n / 1_000.0)
            else -> n.toString()
        }

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / 1048576.0)
            else -> "%d КБ".format(bytes / 1024)
        }
    }
}
