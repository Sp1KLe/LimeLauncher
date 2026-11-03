package net.kdt.pojavlaunch.lime

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.instances.Instances
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import java.io.File
import java.io.RandomAccessFile

/**
 * «Чёрный ящик» запуска игры.
 *
 * Игра работает в отдельном процессе (:game). Если она закрылась сразу после запуска
 * (в Pojav выход с кодом 0 или убитый процесс тихо возвращают на главный экран),
 * пользователь не видит причины. Здесь мы:
 *  1. при старте игры ставим метку (вызывается из GameActivity в процессе игры);
 *  2. при возврате в лаунчер проверяем метку и лог, и если игра закрылась неожиданно,
 *     показываем окно с причиной и кнопкой «Поделиться логом».
 */
object LimeCrashWatch {
    private const val MARKER = "lime_game_started"
    private const val LOG = "latestlog.txt"
    private const val SHORT_SESSION_MS = 90_000L
    private const val TAIL_BYTES = 24 * 1024
    private const val TAIL_LINES = 70

    private val CRASH_SIGNS = listOf(
        "Crash report saved",
        "A fatal error has been detected",
        "SIGSEGV",
        "SIGABRT",
        "OutOfMemoryError",
        "Could not create the Java Virtual Machine",
        "UnsatisfiedLinkError",
        "NoClassDefFoundError",
        "Exception in thread \"main\"",
        "Failed to initialize",
        "GLFW error",
    )

    private fun marker() = File(Tools.DIR_GAME_HOME, MARKER)
    private fun logFile() = File(Tools.DIR_GAME_HOME, LOG)

    /** Вызывается в процессе игры в самом начале GameActivity.onCreate. */
    @JvmStatic
    fun gameStarted() {
        try {
            marker().writeText(System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    private fun gameProcessAlive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.runningAppProcesses?.any { it.processName.endsWith(":game") } == true
    }

    /** Вызывается при возврате на главный экран лаунчера. */
    @JvmStatic
    fun checkAfterGame(activity: Activity, attempt: Int = 0) {
        val marker = marker()
        if (!marker.exists()) return

        // Процесс игры мог ещё не успеть завершиться — даём ему пару секунд.
        if (gameProcessAlive(activity)) {
            if (attempt < 6) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!activity.isFinishing) checkAfterGame(activity, attempt + 1)
                }, 1500)
            }
            return
        }

        val startedAt = marker.readText().trim().toLongOrNull() ?: 0L
        marker.delete()

        val log = logFile()
        val tail = readTail(log)
        val loggedUntil = if (log.exists()) log.lastModified() else 0L
        val sessionMs = if (loggedUntil > startedAt) loggedUntil - startedAt else 0L

        val cleanQuit = tail.contains("Stopping!")
        val crashSign = CRASH_SIGNS.firstOrNull { tail.contains(it) }
        val shortSession = sessionMs < SHORT_SESSION_MS

        if (crashSign == null && (cleanQuit || !shortSession)) return
        if (activity.isFinishing) return

        showDialog(activity, tail, crashSign, sessionMs)
    }

    private fun readTail(file: File): String {
        if (!file.exists() || file.length() == 0L) return ""
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val start = maxOf(0L, len - TAIL_BYTES)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun hint(tail: String, crashSign: String?): String = when {
        tail.isBlank() ->
            "Игра закрылась, не успев ничего записать в лог. Чаще всего это нехватка памяти " +
                "или несовместимый рендерер. Попробуйте уменьшить выделение памяти и сменить рендерер " +
                "(Настройки → Расширенные настройки → Видео)."
        crashSign == "OutOfMemoryError" ->
            "Не хватило памяти. Уменьшите дальность прорисовки в игре или поставьте меньше памяти для Java."
        crashSign == "UnsatisfiedLinkError" || crashSign == "Could not create the Java Virtual Machine" ->
            "Не запустилась Java. Попробуйте другой рантайм Java в настройках версии."
        crashSign == "SIGSEGV" || crashSign == "SIGABRT" || crashSign == "A fatal error has been detected" ->
            "Сбой в нативной части (чаще всего рендерер или драйвер видеокарты). Смените рендерер в настройках."
        crashSign != null ->
            "В логе есть признак сбоя: «$crashSign». Отправьте лог, и я разберусь."
        else ->
            "Игра закрылась сразу после запуска без явной ошибки. Ниже конец лога."
    }

    private fun showDialog(activity: Activity, tail: String, crashSign: String?, sessionMs: Long) {
        val info = buildString {
            append("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            try {
                append("ОЗУ: ${Tools.getTotalDeviceMemory(activity)} МБ, выделено для игры: ${LauncherPreferences.PREF_RAM_ALLOCATION} МБ\n")
                val inst = Instances.loadSelectedInstance()
                if (inst != null) {
                    append("Версия: ${inst.versionId}, рендерер: ${inst.launchRenderer}\n")
                }
            } catch (_: Exception) {
            }
            append("Игра работала: ${sessionMs / 1000} с\n")
        }
        val lines = tail.lines().takeLast(TAIL_LINES).joinToString("\n")
        val text = hint(tail, crashSign) + "\n\n" + info + "\n— конец лога —\n" + lines

        val tv = TextView(activity).apply {
            this.text = text
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(40, 24, 40, 24)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(activity).apply { addView(tv) }

        AlertDialog.Builder(activity)
            .setTitle("Игра закрылась")
            .setView(scroll)
            .setPositiveButton("Поделиться логом") { _, _ -> Tools.shareLog(activity) }
            .setNegativeButton("Закрыть", null)
            .show()
    }
}
