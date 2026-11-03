package net.kdt.pojavlaunch.lime

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Клиент каталога модов Modrinth (открытый API, ключ не нужен). */
object LimeModrinth {
    private const val BASE = "https://api.modrinth.com/v2"
    private const val UA = "LimeLauncher/0.1 (github.com/Sp1KLe/LimeLauncher)"

    class Hit(
        val id: String,
        val title: String,
        val description: String,
        val author: String,
        val downloads: Long,
        val iconUrl: String?,
    )

    class SearchResult(val hits: List<Hit>, val total: Int)

    class ModFile(val url: String, val filename: String, val sha1: String?, val size: Long)

    class Resolved(val versionNumber: String, val file: ModFile, val requiredDeps: List<String>)

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    private fun getText(url: String): String {
        val c = open(url)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("Сервер Modrinth ответил кодом $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = Uri.encode(s)

    /** Загрузчики, которые подходят выбранному: Quilt умеет запускать моды Fabric. */
    fun compatibleLoaders(loader: String): List<String> =
        if (loader == "quilt") listOf("quilt", "fabric") else listOf(loader)

    fun search(query: String, mcVersion: String, loader: String, offset: Int, index: String): SearchResult {
        val facets = JSONArray()
        facets.put(JSONArray().put("project_type:mod"))
        if (mcVersion.isNotBlank()) facets.put(JSONArray().put("versions:$mcVersion"))
        val loaders = JSONArray()
        compatibleLoaders(loader).forEach { loaders.put("categories:$it") }
        facets.put(loaders)

        val url = "$BASE/search?query=${enc(query)}&facets=${enc(facets.toString())}" +
            "&limit=20&offset=$offset&index=$index"
        val json = JSONObject(getText(url))
        val arr = json.getJSONArray("hits")
        val hits = ArrayList<Hit>(arr.length())
        for (i in 0 until arr.length()) {
            val h = arr.getJSONObject(i)
            hits.add(
                Hit(
                    id = h.getString("project_id"),
                    title = h.optString("title"),
                    description = h.optString("description"),
                    author = h.optString("author"),
                    downloads = h.optLong("downloads"),
                    iconUrl = h.optString("icon_url").takeIf { it.isNotBlank() && it != "null" },
                ),
            )
        }
        return SearchResult(hits, json.optInt("total_hits", hits.size))
    }

    /** Подбирает самую свежую подходящую версию мода (предпочитая релизы). */
    fun resolve(projectId: String, mcVersion: String, loader: String): Resolved? {
        val gv = JSONArray().put(mcVersion).toString()
        val ld = JSONArray().also { a -> compatibleLoaders(loader).forEach { a.put(it) } }.toString()
        val url = "$BASE/project/$projectId/version?game_versions=${enc(gv)}&loaders=${enc(ld)}"
        val versions = JSONArray(getText(url))
        if (versions.length() == 0) return null

        var chosen: JSONObject? = null
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.optString("version_type") == "release") {
                chosen = v
                break
            }
        }
        val v = chosen ?: versions.getJSONObject(0)

        val files = v.getJSONArray("files")
        if (files.length() == 0) return null
        var f = files.getJSONObject(0)
        for (i in 0 until files.length()) {
            val cand = files.getJSONObject(i)
            if (cand.optBoolean("primary")) {
                f = cand
                break
            }
        }
        val sha1 = f.optJSONObject("hashes")?.optString("sha1")?.takeIf { it.isNotBlank() }
        val file = ModFile(
            url = f.getString("url"),
            filename = f.getString("filename"),
            sha1 = sha1,
            size = f.optLong("size"),
        )

        val deps = ArrayList<String>()
        val depArr = v.optJSONArray("dependencies")
        if (depArr != null) {
            for (i in 0 until depArr.length()) {
                val d = depArr.getJSONObject(i)
                if (d.optString("dependency_type") == "required") {
                    val pid = d.optString("project_id")
                    if (pid.isNotBlank() && pid != "null") deps.add(pid)
                }
            }
        }
        return Resolved(v.optString("version_number"), file, deps)
    }

    fun sha1Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Скачивает файл через .part и проверяет SHA-1, поэтому оборванная загрузка не оставляет битых модов. */
    fun download(file: ModFile, dest: File, onPercent: (Int) -> Unit) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        val c = open(file.url)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("Не удалось скачать ${file.filename}: код $code")
            val total = if (c.contentLength > 0) c.contentLength.toLong() else file.size
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onPercent(pct)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            c.disconnect()
        }
        if (file.sha1 != null && !sha1Of(part).equals(file.sha1, ignoreCase = true)) {
            part.delete()
            throw IOException("Файл ${file.filename} повреждён при загрузке (не совпала сумма SHA-1)")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.delete()
            throw IOException("Не удалось сохранить ${dest.name}")
        }
    }
}

/** Маленький кэш иконок модов в памяти. */
object LimeImageCache {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(80) {}

    fun get(url: String?): ImageBitmap? = if (url == null) null else cache.get(url)

    fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "LimeLauncher/0.1 (github.com/Sp1KLe/LimeLauncher)")
            val bmp = try {
                c.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                c.disconnect()
            }
            val img = bmp?.asImageBitmap()
            if (img != null) cache.put(url, img)
            img
        } catch (e: Exception) {
            null
        }
    }
}
