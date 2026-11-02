//! LimeLauncher core: ядро лаунчера Minecraft: Java Edition для Android.
//!
//! - [`controls`] — сенсорное управление в стиле Bedrock и чувствительность
//! - [`settings`] — настройки и профили производительности для слабых телефонов
//! - [`manifest`] — разбор манифестов версий Mojang
//! - [`launch`] — сборка команды запуска игры
//! - [`download`] — быстрая параллельная загрузка с проверкой SHA-1

pub mod controls;
pub mod download;
pub mod launch;
pub mod manifest;
pub mod settings;

#[cfg(feature = "jni-bridge")]
mod android;
