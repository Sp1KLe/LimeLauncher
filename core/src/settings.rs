//! Настройки лаунчера и профили производительности для слабых телефонов.

use crate::controls::ControlsConfig;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PerformanceProfile {
    /// Для телефонов до ~3 ГБ ОЗУ.
    Low,
    /// 4–6 ГБ.
    Balanced,
    /// 8 ГБ и больше.
    High,
}

impl PerformanceProfile {
    pub fn for_total_ram_mb(total_mb: u32) -> Self {
        match total_mb {
            0..=3500 => Self::Low,
            3501..=6500 => Self::Balanced,
            _ => Self::High,
        }
    }

    /// Выделение памяти под игру. Система Android тоже хочет память, поэтому
    /// отдаём игре не больше разумной доли — иначе будут вылеты и лаги.
    pub fn heap_mb(self, total_mb: u32) -> u32 {
        let (share, min, max) = match self {
            Self::Low => (0.35, 512, 1280),
            Self::Balanced => (0.40, 1024, 2560),
            Self::High => (0.45, 2048, 4096),
        };
        ((total_mb as f32 * share) as u32).clamp(min, max)
    }

    /// Рекомендуемая дальность прорисовки (в чанках) для настроек игры.
    pub fn render_distance(self) -> u8 {
        match self {
            Self::Low => 4,
            Self::Balanced => 8,
            Self::High => 12,
        }
    }

    /// Флаги JVM, подобранные под профиль: меньше пауз сборщика мусора,
    /// быстрый старт, без лишней нагрузки на слабый процессор.
    pub fn jvm_flags(self) -> Vec<String> {
        let mut f: Vec<&str> = vec![
            "-XX:+DisableExplicitGC",
            "-XX:+UseStringDeduplication",
            "-Xshare:auto",
            "-Dfile.encoding=UTF-8",
        ];
        match self {
            // Одно-двухъядерные нагрузки: простой сборщик, меньше потоков.
            Self::Low => f.extend([
                "-XX:+UseSerialGC",
                "-XX:TieredStopAtLevel=1",
                "-XX:CICompilerCount=2",
            ]),
            Self::Balanced => f.extend([
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=40",
                "-XX:G1NewSizePercent=30",
            ]),
            Self::High => f.extend([
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=30",
                "-XX:G1NewSizePercent=30",
                "-XX:G1ReservePercent=20",
            ]),
        }
        f.into_iter().map(String::from).collect()
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub profile: Option<PerformanceProfile>,
    /// Если `None` — подбирается автоматически по ОЗУ телефона.
    pub heap_mb: Option<u32>,
    pub extra_jvm_args: Vec<String>,
    pub game_dir: String,
    pub username: String,
    pub controls: ControlsConfig,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            profile: None,
            heap_mb: None,
            extra_jvm_args: Vec::new(),
            game_dir: String::from("minecraft"),
            username: String::from("Player"),
            controls: ControlsConfig::default(),
        }
    }
}

impl Settings {
    pub fn from_json(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string_pretty(self).unwrap_or_default()
    }

    pub fn effective_profile(&self, total_ram_mb: u32) -> PerformanceProfile {
        self.profile
            .unwrap_or_else(|| PerformanceProfile::for_total_ram_mb(total_ram_mb))
    }

    pub fn effective_heap_mb(&self, total_ram_mb: u32) -> u32 {
        self.heap_mb
            .unwrap_or_else(|| self.effective_profile(total_ram_mb).heap_mb(total_ram_mb))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn three_gb_phone_gets_low_profile_and_safe_heap() {
        let p = PerformanceProfile::for_total_ram_mb(3000);
        assert_eq!(p, PerformanceProfile::Low);
        let heap = p.heap_mb(3000);
        assert!((512..=1280).contains(&heap));
        assert!(heap < 3000 / 2);
    }

    #[test]
    fn profiles_scale_with_ram() {
        assert_eq!(PerformanceProfile::for_total_ram_mb(4096), PerformanceProfile::Balanced);
        assert_eq!(PerformanceProfile::for_total_ram_mb(12000), PerformanceProfile::High);
        assert!(PerformanceProfile::High.heap_mb(12000) > PerformanceProfile::Low.heap_mb(3000));
    }

    #[test]
    fn low_profile_uses_serial_gc() {
        let f = PerformanceProfile::Low.jvm_flags();
        assert!(f.iter().any(|x| x == "-XX:+UseSerialGC"));
        assert!(!f.iter().any(|x| x.contains("G1")));
    }

    #[test]
    fn manual_heap_overrides_auto() {
        let s = Settings { heap_mb: Some(900), ..Default::default() };
        assert_eq!(s.effective_heap_mb(8000), 900);
        assert_ne!(Settings::default().effective_heap_mb(8000), 900);
    }

    #[test]
    fn settings_json_roundtrip() {
        let s = Settings::default();
        let back = Settings::from_json(&s.to_json()).unwrap();
        assert_eq!(s, back);
        assert!(Settings::from_json("{}").is_ok());
    }
}
