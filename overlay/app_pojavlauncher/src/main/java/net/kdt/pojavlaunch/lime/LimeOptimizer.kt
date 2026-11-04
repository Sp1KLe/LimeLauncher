package net.kdt.pojavlaunch.lime

import java.io.File

/** Профиль настроек Minecraft для слабых телефонов: правит options.txt, не трогая остальное. */
object LimeOptimizer {
    private val FAST = linkedMapOf(
        "renderDistance" to "6",
        "simulationDistance" to "5",
        "fancyGraphics" to "false",
        "graphicsMode" to "0",
        "particles" to "2",
        "renderClouds" to "false",
        "ao" to "false",
        "biomeBlendRadius" to "0",
        "mipmapLevels" to "0",
        "entityShadows" to "false",
        "entityDistanceScaling" to "0.75",
        "maxFps" to "60",
        "enableVsync" to "false",
        "bobView" to "false",
    )

    /** @param force false — только если options.txt ещё нет (первый запуск). */
    @JvmStatic
    fun apply(gameDir: File, force: Boolean): Boolean {
        return try {
            gameDir.mkdirs()
            val f = File(gameDir, "options.txt")
            if (f.exists() && !force) return false
            val lines = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
            val pending = LinkedHashMap(FAST)
            for (i in lines.indices) {
                val key = lines[i].substringBefore(':', "")
                val v = pending.remove(key)
                if (v != null) lines[i] = "$key:$v"
            }
            // graphicsMode есть только в новых версиях; в 1.18.2 он не нужен
            pending.remove("graphicsMode")
            for ((k, v) in pending) lines.add("$k:$v")
            f.writeText(lines.joinToString("\n") + "\n")
            true
        } catch (e: Exception) {
            false
        }
    }
}
