package dev.limelauncher

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Первый экран: песочница управления. Двигайте пальцем по серой области —
 * ядро на Rust превращает движение в дельту мыши с вашей чувствительностью.
 * Так можно подобрать чувствительность ещё до запуска игры.
 */
class MainActivity : Activity() {
    private var handle = 0L
    private var sensitivity = 1.0f
    private lateinit var info: TextView
    private lateinit var delta: TextView

    private fun recreateFilter() {
        if (handle != 0L) Native.lookDestroy(handle)
        handle = Native.lookCreate(sensitivity, 0f, 0f, 0f, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mem = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        val totalMb = (mem.totalMem / (1024 * 1024)).toInt()
        val heap = Native.recommendedHeapMb(totalMb)

        info = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            text = "LimeLauncher • ОЗУ ${totalMb} МБ • память под игру ${heap} МБ"
        }
        delta = TextView(this).apply {
            setTextColor(Color.parseColor("#9BE564"))
            textSize = 20f
            text = "Двигайте пальцем по области ниже"
        }
        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "Чувствительность: 1.00"
        }
        val seek = SeekBar(this).apply {
            max = 495 // 0.05 .. 5.00
            progress = 95 // 1.00
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    sensitivity = (p + 5) / 100f
                    label.text = "Чувствительность: %.2f".format(sensitivity)
                    recreateFilter()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        var lastX = 0f
        var lastY = 0f
        val pad = View(this).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = e.x; lastY = e.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val packed = Native.lookProcess(handle, e.x - lastX, e.y - lastY)
                        lastX = e.x; lastY = e.y
                        val dx = (packed shr 32).toInt()
                        val dy = packed.toInt()
                        delta.text = "мышь: dx=$dx  dy=$dy"
                    }
                }
                true
            }
        }

        recreateFilter()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B1B"))
            setPadding(24, 24, 24, 24)
            addView(info)
            addView(label)
            addView(seek)
            addView(delta)
            addView(pad, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    override fun onDestroy() {
        if (handle != 0L) Native.lookDestroy(handle)
        handle = 0L
        super.onDestroy()
    }
}
