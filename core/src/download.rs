//! Быстрая загрузка файлов игры: параллельно, с переиспользованием соединений,
//! проверкой SHA-1 и пропуском уже скачанного. Запись атомарная (через `.part`),
//! поэтому оборванная загрузка не оставляет битых файлов.

use sha1::{Digest, Sha1};
use std::fs::{self, File};
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

#[derive(Debug, Clone)]
pub struct Task {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
}

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Report {
    pub downloaded: usize,
    pub skipped: usize,
    pub failed: Vec<String>,
}

pub fn sha1_of_file(path: &Path) -> io::Result<String> {
    let mut f = File::open(path)?;
    let mut h = Sha1::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(hex(&h.finalize()))
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Файл уже на месте и (если известна сумма) совпадает с ожидаемой.
pub fn is_up_to_date(task: &Task) -> bool {
    if !task.path.is_file() {
        return false;
    }
    match &task.sha1 {
        Some(expected) => sha1_of_file(&task.path)
            .map(|actual| actual.eq_ignore_ascii_case(expected))
            .unwrap_or(false),
        None => true,
    }
}

fn fetch_one(agent: &ureq::Agent, task: &Task) -> Result<(), String> {
    if let Some(parent) = task.path.parent() {
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let part = task.path.with_extension("part");
    let resp = agent.get(&task.url).call().map_err(|e| e.to_string())?;
    {
        let mut out = File::create(&part).map_err(|e| e.to_string())?;
        let mut reader = resp.into_reader();
        io::copy(&mut reader, &mut out).map_err(|e| e.to_string())?;
        out.flush().map_err(|e| e.to_string())?;
    }
    if let Some(expected) = &task.sha1 {
        let actual = sha1_of_file(&part).map_err(|e| e.to_string())?;
        if !actual.eq_ignore_ascii_case(expected) {
            let _ = fs::remove_file(&part);
            return Err(format!("sha1 mismatch for {}", task.url));
        }
    }
    fs::rename(&part, &task.path).map_err(|e| e.to_string())
}

/// Скачивает все задачи в `threads` потоков. Повторяет неудачи до 3 раз.
/// `on_progress(done, total)` вызывается после каждого файла.
pub fn download_all<F>(tasks: Vec<Task>, threads: usize, on_progress: F) -> Report
where
    F: Fn(usize, usize) + Send + Sync + 'static,
{
    let total = tasks.len();
    let tasks = Arc::new(tasks);
    let next = Arc::new(AtomicUsize::new(0));
    let done = Arc::new(AtomicUsize::new(0));
    let on_progress = Arc::new(on_progress);
    let agent = ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(15))
        .timeout_read(Duration::from_secs(60))
        .build();

    let workers = threads.clamp(1, 16).min(total.max(1));
    let mut handles = Vec::new();
    for _ in 0..workers {
        let (tasks, next, done, cb, agent) =
            (tasks.clone(), next.clone(), done.clone(), on_progress.clone(), agent.clone());
        handles.push(thread::spawn(move || {
            let mut rep = Report::default();
            loop {
                let i = next.fetch_add(1, Ordering::Relaxed);
                let Some(task) = tasks.get(i) else { break };
                if is_up_to_date(task) {
                    rep.skipped += 1;
                } else {
                    let mut last_err = String::new();
                    let mut ok = false;
                    for attempt in 0..3u64 {
                        match fetch_one(&agent, task) {
                            Ok(()) => {
                                ok = true;
                                break;
                            }
                            Err(e) => {
                                last_err = e;
                                thread::sleep(Duration::from_millis(300 * (attempt + 1)));
                            }
                        }
                    }
                    if ok {
                        rep.downloaded += 1;
                    } else {
                        rep.failed.push(format!("{}: {}", task.url, last_err));
                    }
                }
                let d = done.fetch_add(1, Ordering::Relaxed) + 1;
                cb(d, total);
            }
            rep
        }));
    }

    let mut total_rep = Report::default();
    for h in handles {
        if let Ok(r) = h.join() {
            total_rep.downloaded += r.downloaded;
            total_rep.skipped += r.skipped;
            total_rep.failed.extend(r.failed);
        }
    }
    total_rep
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmp(name: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!("lime-test-{}-{name}", std::process::id()));
        let _ = fs::remove_dir_all(&d);
        fs::create_dir_all(&d).unwrap();
        d
    }

    #[test]
    fn sha1_matches_known_value() {
        let d = tmp("sha");
        let p = d.join("a.txt");
        fs::write(&p, b"abc").unwrap();
        assert_eq!(sha1_of_file(&p).unwrap(), "a9993e364706816aba3e25717850c26c9cd0d89d");
    }

    #[test]
    fn up_to_date_checks_hash() {
        let d = tmp("utd");
        let p = d.join("f.bin");
        fs::write(&p, b"abc").unwrap();
        let good = Task { url: String::new(), path: p.clone(), sha1: Some("A9993E364706816ABA3E25717850C26C9CD0D89D".into()) };
        let bad = Task { url: String::new(), path: p.clone(), sha1: Some("00".into()) };
        let none = Task { url: String::new(), path: p, sha1: None };
        assert!(is_up_to_date(&good));
        assert!(!is_up_to_date(&bad));
        assert!(is_up_to_date(&none));
        assert!(!is_up_to_date(&Task { url: String::new(), path: d.join("missing"), sha1: None }));
    }

    #[test]
    fn existing_files_are_skipped_without_network() {
        let d = tmp("skip");
        let mut tasks = Vec::new();
        for i in 0..20 {
            let p = d.join(format!("f{i}"));
            fs::write(&p, b"abc").unwrap();
            tasks.push(Task {
                url: "http://127.0.0.1:1/unused".into(),
                path: p,
                sha1: Some("a9993e364706816aba3e25717850c26c9cd0d89d".into()),
            });
        }
        let rep = download_all(tasks, 8, |_, _| {});
        assert_eq!(rep, Report { downloaded: 0, skipped: 20, failed: vec![] });
    }

    #[test]
    fn unreachable_download_is_reported_not_panicking() {
        let d = tmp("fail");
        let t = Task { url: "http://127.0.0.1:1/x".into(), path: d.join("x"), sha1: None };
        let rep = download_all(vec![t], 2, |_, _| {});
        assert_eq!(rep.failed.len(), 1);
        assert!(!d.join("x").exists());
    }
}
