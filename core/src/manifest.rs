//! Разбор манифестов версий Minecraft (формат Mojang) и список файлов для загрузки.

use serde::Deserialize;
use std::collections::HashMap;

pub const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, Clone, Deserialize)]
pub struct VersionManifest {
    pub latest: Latest,
    pub versions: Vec<VersionEntry>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    #[serde(default)]
    pub sha1: String,
}

impl VersionManifest {
    pub fn parse(json: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json)
    }

    pub fn find(&self, id: &str) -> Option<&VersionEntry> {
        self.versions.iter().find(|v| v.id == id)
    }

    pub fn releases(&self) -> impl Iterator<Item = &VersionEntry> {
        self.versions.iter().filter(|v| v.kind == "release")
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct FileInfo {
    #[serde(default)]
    pub path: Option<String>,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<HashMap<String, bool>>,
}

/// Окружение, для которого вычисляются правила. На Android это Linux/aarch64.
#[derive(Debug, Clone)]
pub struct Env {
    pub os: String,
    pub arch: String,
}

impl Env {
    pub fn android_arm64() -> Self {
        Self { os: "linux".into(), arch: "aarch64".into() }
    }
}

/// Правила Mojang: последнее подходящее правило решает. Пустой список = разрешено.
/// Правила с `features` (демо-режим, своё разрешение) считаются неподходящими.
pub fn rules_allow(rules: &[Rule], env: &Env) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for r in rules {
        if r.features.is_some() {
            continue;
        }
        let matches = match &r.os {
            None => true,
            Some(os) => {
                os.name.as_deref().map_or(true, |n| n == env.os)
                    && os.arch.as_deref().map_or(true, |a| a == env.arch)
            }
        };
        if matches {
            allowed = r.action == "allow";
        }
    }
    allowed
}

#[derive(Debug, Clone, Deserialize)]
pub struct LibraryDownloads {
    #[serde(default)]
    pub artifact: Option<FileInfo>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<LibraryDownloads>,
    #[serde(default)]
    pub rules: Vec<Rule>,
}

/// Аргумент в новом формате: строка либо объект с правилами.
#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum Arg {
    Plain(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Arg>,
    #[serde(default)]
    pub jvm: Vec<Arg>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ClientDownload {
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionDownloads {
    pub client: ClientDownload,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionJson {
    pub id: String,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    #[serde(rename = "assetIndex")]
    pub asset_index: AssetIndexRef,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    /// Старый формат (до 1.13): одна строка с аргументами.
    #[serde(default, rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    pub libraries: Vec<Library>,
    pub downloads: VersionDownloads,
}

impl VersionJson {
    pub fn parse(json: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json)
    }

    /// Библиотеки, подходящие для данного окружения и имеющие файл для загрузки.
    pub fn active_libraries<'a>(&'a self, env: &'a Env) -> impl Iterator<Item = &'a FileInfo> + 'a {
        self.libraries
            .iter()
            .filter(move |l| rules_allow(&l.rules, env))
            .filter_map(|l| l.downloads.as_ref()?.artifact.as_ref())
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

impl AssetIndex {
    pub fn parse(json: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json)
    }
}

impl AssetObject {
    /// Относительный путь в хранилище ассетов: `ab/abcdef…`.
    pub fn store_path(&self) -> String {
        format!("{}/{}", &self.hash[..2], self.hash)
    }

    pub fn url(&self) -> String {
        format!("https://resources.download.minecraft.net/{}", self.store_path())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const VERSION: &str = r#"{
      "id": "1.21",
      "mainClass": "net.minecraft.client.main.Main",
      "assetIndex": {"id":"17","sha1":"aa","size":1,"url":"https://x/17.json"},
      "downloads": {"client": {"sha1":"bb","size":2,"url":"https://x/client.jar"}},
      "arguments": {
        "game": ["--username", "${auth_player_name}",
                 {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"}],
        "jvm": [{"rules":[{"action":"allow","os":{"name":"windows"}}],"value":"-XX:HeapDumpPath=x"},
                "-cp", "${classpath}"]
      },
      "libraries": [
        {"name":"a:b:1","downloads":{"artifact":{"path":"a/b/1/b-1.jar","sha1":"c","size":3,"url":"https://x/b.jar"}}},
        {"name":"win:only:1","rules":[{"action":"allow","os":{"name":"windows"}}],
         "downloads":{"artifact":{"path":"w.jar","sha1":"d","size":4,"url":"https://x/w.jar"}}},
        {"name":"natives:only:1"}
      ]
    }"#;

    #[test]
    fn parses_version_json() {
        let v = VersionJson::parse(VERSION).unwrap();
        assert_eq!(v.main_class, "net.minecraft.client.main.Main");
        assert_eq!(v.libraries.len(), 3);
    }

    #[test]
    fn filters_libraries_by_os_rules() {
        let v = VersionJson::parse(VERSION).unwrap();
        let env = Env::android_arm64();
        let libs: Vec<_> = v.active_libraries(&env).collect();
        assert_eq!(libs.len(), 1);
        assert_eq!(libs[0].path.as_deref(), Some("a/b/1/b-1.jar"));
    }

    #[test]
    fn rules_last_match_wins() {
        let env = Env::android_arm64();
        let rules: Vec<Rule> = serde_json::from_str(
            r#"[{"action":"allow"},{"action":"disallow","os":{"name":"linux"}}]"#,
        )
        .unwrap();
        assert!(!rules_allow(&rules, &env));
    }

    #[test]
    fn parses_manifest_and_finds_version() {
        let m = VersionManifest::parse(
            r#"{"latest":{"release":"1.21","snapshot":"24w"},
                "versions":[{"id":"1.21","type":"release","url":"u","sha1":"s"},
                            {"id":"24w","type":"snapshot","url":"u2"}]}"#,
        )
        .unwrap();
        assert_eq!(m.find("1.21").unwrap().url, "u");
        assert_eq!(m.releases().count(), 1);
    }

    #[test]
    fn asset_path_and_url() {
        let o = AssetObject { hash: "abcdef0123".into(), size: 1 };
        assert_eq!(o.store_path(), "ab/abcdef0123");
        assert!(o.url().ends_with("/ab/abcdef0123"));
    }
}
