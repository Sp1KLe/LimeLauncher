package dev.limelauncher

/** Мост к Rust-ядру (liblime_core.so). Имена методов связаны с функциями в core/src/android.rs. */
object Native {
    init {
        System.loadLibrary("lime_core")
    }

    external fun lookCreate(
        sensitivity: Float,
        acceleration: Float,
        smoothing: Float,
        deadZone: Float,
        invertY: Int,
    ): Long

    /** Возвращает упакованную пару: старшие 32 бита — dx, младшие — dy. */
    external fun lookProcess(handle: Long, dx: Float, dy: Float): Long

    external fun lookDestroy(handle: Long)

    external fun recommendedHeapMb(totalRamMb: Int): Int
}
