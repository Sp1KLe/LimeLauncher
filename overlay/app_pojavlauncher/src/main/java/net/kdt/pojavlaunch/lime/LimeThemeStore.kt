package net.kdt.pojavlaunch.lime

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Хранит выбор темы: «как в системе» (по умолчанию), светлая или тёмная.
 * Переключение через AppCompatDelegate меняет и Compose-экраны, и старые экраны на XML.
 */
object LimeThemeStore {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val FILE = "lime_prefs"
    private const val KEY = "theme"

    @JvmStatic
    fun mode(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    /** Применяет сохранённую тему. Вызывается при старте приложения. */
    @JvmStatic
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(mode(context)))
    }

    /** Сохраняет выбор и сразу применяет (открытые экраны пересоздаются). */
    @JvmStatic
    fun set(context: Context, mode: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(mode))
    }

    private fun toDelegateMode(mode: String): Int = when (mode) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
