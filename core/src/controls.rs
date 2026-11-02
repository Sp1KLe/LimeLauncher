//! Управление в стиле Bedrock: сенсорный обзор с настраиваемой чувствительностью
//! и раскладка экранных кнопок.
//!
//! Движение пальца по экрану превращается в «дельту мыши» для Java-игры.
//! Все параметры настраиваются в лаунчере и применяются без перезапуска.

use serde::{Deserialize, Serialize};

/// Настройки обзора (камеры).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct LookConfig {
    /// Базовая чувствительность. 1.0 = один пиксель касания → один пиксель мыши.
    pub sensitivity: f32,
    /// Отдельный множитель для вертикали (удобно, если вверх/вниз «тяжелее»).
    pub vertical_scale: f32,
    /// Ускорение: чем быстрее свайп, тем сильнее поворот. 0.0 = выключено.
    pub acceleration: f32,
    /// Сглаживание 0.0..=0.95. 0.0 = без сглаживания (минимальная задержка).
    pub smoothing: f32,
    /// Мёртвая зона в пикселях: дрожание пальца меньше этого значения игнорируется.
    pub dead_zone_px: f32,
    pub invert_y: bool,
}

impl Default for LookConfig {
    fn default() -> Self {
        Self {
            sensitivity: 1.0,
            vertical_scale: 1.0,
            acceleration: 0.0,
            smoothing: 0.0,
            dead_zone_px: 0.0,
            invert_y: false,
        }
    }
}

impl LookConfig {
    pub const MIN_SENSITIVITY: f32 = 0.05;
    pub const MAX_SENSITIVITY: f32 = 5.0;

    /// Приводит значения к допустимым диапазонам (защита от кривого ввода).
    pub fn clamped(mut self) -> Self {
        self.sensitivity = self
            .sensitivity
            .clamp(Self::MIN_SENSITIVITY, Self::MAX_SENSITIVITY);
        self.vertical_scale = self.vertical_scale.clamp(0.1, 3.0);
        self.acceleration = self.acceleration.clamp(0.0, 2.0);
        self.smoothing = self.smoothing.clamp(0.0, 0.95);
        self.dead_zone_px = self.dead_zone_px.clamp(0.0, 20.0);
        self
    }
}

/// Преобразователь касаний в движение камеры. Хранит состояние сглаживания
/// и накопленные доли пикселя, чтобы медленные движения не «съедались».
#[derive(Debug, Clone)]
pub struct LookFilter {
    cfg: LookConfig,
    smooth_x: f32,
    smooth_y: f32,
    rem_x: f32,
    rem_y: f32,
}

impl LookFilter {
    pub fn new(cfg: LookConfig) -> Self {
        Self {
            cfg: cfg.clamped(),
            smooth_x: 0.0,
            smooth_y: 0.0,
            rem_x: 0.0,
            rem_y: 0.0,
        }
    }

    pub fn set_config(&mut self, cfg: LookConfig) {
        self.cfg = cfg.clamped();
    }

    pub fn config(&self) -> LookConfig {
        self.cfg
    }

    /// Сброс состояния (например, когда палец отпущен).
    pub fn reset(&mut self) {
        self.smooth_x = 0.0;
        self.smooth_y = 0.0;
        self.rem_x = 0.0;
        self.rem_y = 0.0;
    }

    /// Обрабатывает сдвиг пальца `(dx, dy)` в пикселях и возвращает целую
    /// дельту мыши. Дробный остаток переносится на следующий кадр.
    pub fn process(&mut self, dx: f32, dy: f32) -> (i32, i32) {
        let c = self.cfg;

        // Мёртвая зона по длине вектора сдвига.
        let len = (dx * dx + dy * dy).sqrt();
        if len < c.dead_zone_px {
            return (0, 0);
        }

        // Ускорение: множитель растёт со скоростью свайпа.
        let accel = 1.0 + c.acceleration * (len / 20.0);
        let k = c.sensitivity * accel;
        let mut x = dx * k;
        let mut y = dy * k * c.vertical_scale;
        if c.invert_y {
            y = -y;
        }

        // Экспоненциальное сглаживание (0 = выключено, без задержки).
        if c.smoothing > 0.0 {
            self.smooth_x = self.smooth_x * c.smoothing + x * (1.0 - c.smoothing);
            self.smooth_y = self.smooth_y * c.smoothing + y * (1.0 - c.smoothing);
            x = self.smooth_x;
            y = self.smooth_y;
        }

        // Копим дробную часть, отдаём целую.
        self.rem_x += x;
        self.rem_y += y;
        let ox = self.rem_x.trunc();
        let oy = self.rem_y.trunc();
        self.rem_x -= ox;
        self.rem_y -= oy;
        (ox as i32, oy as i32)
    }
}

/// Действие, которое выполняет экранная кнопка.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Action {
    Jump,
    Sneak,
    Sprint,
    Inventory,
    Chat,
    Pause,
    Attack,
    UseItem,
    DropItem,
    PerspectiveToggle,
}

/// Экранная кнопка. Координаты нормированы (0.0..=1.0 от ширины/высоты экрана),
/// поэтому раскладка работает на любом разрешении.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Button {
    pub action: Action,
    pub x: f32,
    pub y: f32,
    /// Размер в долях высоты экрана.
    pub size: f32,
    pub opacity: f32,
}

/// Полный набор настроек управления.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct ControlsConfig {
    pub look: LookConfig,
    pub buttons: Vec<Button>,
}

impl Default for ControlsConfig {
    fn default() -> Self {
        Self {
            look: LookConfig::default(),
            buttons: bedrock_layout(),
        }
    }
}

/// Раскладка по умолчанию, похожая на Bedrock: прыжок справа внизу,
/// приседание рядом, инвентарь и чат сверху.
pub fn bedrock_layout() -> Vec<Button> {
    let b = |action, x, y, size| Button {
        action,
        x,
        y,
        size,
        opacity: 0.6,
    };
    vec![
        b(Action::Jump, 0.88, 0.72, 0.16),
        b(Action::Sneak, 0.76, 0.84, 0.12),
        b(Action::Attack, 0.70, 0.62, 0.12),
        b(Action::UseItem, 0.92, 0.50, 0.12),
        b(Action::Sprint, 0.12, 0.45, 0.10),
        b(Action::Inventory, 0.55, 0.92, 0.10),
        b(Action::Chat, 0.40, 0.08, 0.09),
        b(Action::Pause, 0.50, 0.06, 0.09),
        b(Action::DropItem, 0.62, 0.92, 0.08),
        b(Action::PerspectiveToggle, 0.60, 0.08, 0.09),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sensitivity_scales_linearly() {
        let mut f = LookFilter::new(LookConfig {
            sensitivity: 2.0,
            ..Default::default()
        });
        assert_eq!(f.process(10.0, 5.0), (20, 10));
    }

    #[test]
    fn fractions_are_not_lost() {
        let mut f = LookFilter::new(LookConfig {
            sensitivity: 0.25,
            ..Default::default()
        });
        let mut sum = 0;
        for _ in 0..8 {
            sum += f.process(1.0, 0.0).0;
        }
        assert_eq!(sum, 2); // 8 * 0.25
    }

    #[test]
    fn dead_zone_ignores_jitter() {
        let mut f = LookFilter::new(LookConfig {
            dead_zone_px: 3.0,
            ..Default::default()
        });
        assert_eq!(f.process(1.0, 1.0), (0, 0));
        assert_ne!(f.process(5.0, 0.0), (0, 0));
    }

    #[test]
    fn invert_y_flips_vertical() {
        let mut f = LookFilter::new(LookConfig {
            invert_y: true,
            ..Default::default()
        });
        assert_eq!(f.process(0.0, 10.0), (0, -10));
    }

    #[test]
    fn acceleration_boosts_fast_swipes() {
        let mut f = LookFilter::new(LookConfig {
            acceleration: 1.0,
            ..Default::default()
        });
        let slow = f.process(2.0, 0.0).0;
        let fast = f.process(40.0, 0.0).0;
        assert!(fast as f32 / 40.0 > slow as f32 / 2.0);
    }

    #[test]
    fn smoothing_delays_but_converges() {
        let mut f = LookFilter::new(LookConfig {
            smoothing: 0.5,
            ..Default::default()
        });
        let first = f.process(10.0, 0.0).0;
        assert!(first < 10);
        let mut last = first;
        for _ in 0..30 {
            last = f.process(10.0, 0.0).0;
        }
        assert!((9..=10).contains(&last));
    }

    #[test]
    fn config_is_clamped() {
        let c = LookConfig {
            sensitivity: 999.0,
            smoothing: 5.0,
            ..Default::default()
        }
        .clamped();
        assert_eq!(c.sensitivity, LookConfig::MAX_SENSITIVITY);
        assert_eq!(c.smoothing, 0.95);
    }

    #[test]
    fn default_layout_has_core_buttons() {
        let l = bedrock_layout();
        assert!(l.iter().any(|b| b.action == Action::Jump));
        assert!(l.iter().all(|b| (0.0..=1.0).contains(&b.x) && (0.0..=1.0).contains(&b.y)));
    }

    #[test]
    fn controls_roundtrip_json_and_fill_defaults() {
        let c = ControlsConfig::default();
        let s = serde_json::to_string(&c).unwrap();
        let back: ControlsConfig = serde_json::from_str(&s).unwrap();
        assert_eq!(c, back);
        let partial: ControlsConfig = serde_json::from_str(r#"{"look":{"sensitivity":1.5}}"#).unwrap();
        assert_eq!(partial.look.sensitivity, 1.5);
        assert!(!partial.buttons.is_empty());
    }
}
