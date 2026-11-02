//! JNI-мост: Kotlin-приложение вызывает ядро через класс `dev.limelauncher.Native`.

use crate::controls::{LookConfig, LookFilter};
use crate::settings::PerformanceProfile;
use jni::objects::JClass;
use jni::sys::{jfloat, jint, jlong};
use jni::JNIEnv;

/// Создаёт фильтр обзора и возвращает указатель на него (хранится в Kotlin как Long).
#[no_mangle]
pub extern "system" fn Java_dev_limelauncher_Native_lookCreate(
    _env: JNIEnv,
    _class: JClass,
    sensitivity: jfloat,
    acceleration: jfloat,
    smoothing: jfloat,
    dead_zone: jfloat,
    invert_y: jint,
) -> jlong {
    let cfg = LookConfig {
        sensitivity,
        acceleration,
        smoothing,
        dead_zone_px: dead_zone,
        invert_y: invert_y != 0,
        ..Default::default()
    };
    Box::into_raw(Box::new(LookFilter::new(cfg))) as jlong
}

/// Обработка сдвига пальца. Результат упакован: старшие 32 бита = dx, младшие = dy.
#[no_mangle]
pub extern "system" fn Java_dev_limelauncher_Native_lookProcess(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    dx: jfloat,
    dy: jfloat,
) -> jlong {
    if handle == 0 {
        return 0;
    }
    let f = unsafe { &mut *(handle as *mut LookFilter) };
    let (x, y) = f.process(dx, dy);
    ((x as i64) << 32) | (y as u32 as i64)
}

#[no_mangle]
pub extern "system" fn Java_dev_limelauncher_Native_lookDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut LookFilter) });
    }
}

/// Рекомендуемая память под игру (МБ) по объёму ОЗУ телефона.
#[no_mangle]
pub extern "system" fn Java_dev_limelauncher_Native_recommendedHeapMb(
    _env: JNIEnv,
    _class: JClass,
    total_ram_mb: jint,
) -> jint {
    let total = total_ram_mb.max(0) as u32;
    PerformanceProfile::for_total_ram_mb(total).heap_mb(total) as jint
}
