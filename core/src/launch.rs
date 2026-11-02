//! Сборка команды запуска Java Edition из version.json и настроек.

use crate::manifest::{rules_allow, Arg, ArgValue, Env, VersionJson};
use crate::settings::Settings;
use std::collections::HashMap;

/// Относительный путь jar-файла библиотеки в папке `libraries/`.
pub fn library_path(maven_name: &str) -> Option<String> {
    let mut parts = maven_name.split(':');
    let group = parts.next()?;
    let artifact = parts.next()?;
    let version = parts.next()?;
    let classifier = parts.next();
    let file = match classifier {
        Some(c) => format!("{artifact}-{version}-{c}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };
    Some(format!("{}/{artifact}/{version}/{file}", group.replace('.', "/")))
}

fn expand(s: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = s.to_string();
    for (k, v) in vars {
        out = out.replace(&format!("${{{k}}}"), v);
    }
    out
}

fn flatten(args: &[Arg], env: &Env, vars: &HashMap<&str, String>) -> Vec<String> {
    let mut out = Vec::new();
    for a in args {
        match a {
            Arg::Plain(s) => out.push(expand(s, vars)),
            Arg::Conditional { rules, value } => {
                if rules_allow(rules, env) {
                    match value {
                        ArgValue::One(s) => out.push(expand(s, vars)),
                        ArgValue::Many(v) => out.extend(v.iter().map(|s| expand(s, vars))),
                    }
                }
            }
        }
    }
    out
}

pub struct LaunchContext<'a> {
    pub version: &'a VersionJson,
    pub settings: &'a Settings,
    pub env: &'a Env,
    /// Корневая папка лаунчера (там лежат libraries/, assets/, versions/).
    pub root: &'a str,
    pub total_ram_mb: u32,
    pub java_path: &'a str,
}

/// Возвращает полную команду: `[java, аргументы JVM…, mainClass, аргументы игры…]`.
pub fn build_command(ctx: &LaunchContext) -> Vec<String> {
    let v = ctx.version;
    let sep = ":"; // Android = Linux

    // Classpath: подходящие библиотеки + клиентский jar.
    let mut cp: Vec<String> = v
        .active_libraries(ctx.env)
        .filter_map(|f| f.path.clone())
        .map(|p| format!("{}/libraries/{}", ctx.root, p))
        .collect();
    cp.push(format!("{}/versions/{id}/{id}.jar", ctx.root, id = v.id));
    let classpath = cp.join(sep);

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", ctx.settings.username.clone());
    vars.insert("version_name", v.id.clone());
    vars.insert("game_directory", format!("{}/{}", ctx.root, ctx.settings.game_dir));
    vars.insert("assets_root", format!("{}/assets", ctx.root));
    vars.insert("assets_index_name", v.asset_index.id.clone());
    vars.insert("auth_uuid", "00000000-0000-0000-0000-000000000000".into());
    vars.insert("auth_access_token", "0".into());
    vars.insert("user_type", "legacy".into());
    vars.insert("version_type", "release".into());
    vars.insert("classpath", classpath.clone());
    vars.insert("natives_directory", format!("{}/natives/{}", ctx.root, v.id));
    vars.insert("launcher_name", "LimeLauncher".into());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").into());

    let heap = ctx.settings.effective_heap_mb(ctx.total_ram_mb);
    let profile = ctx.settings.effective_profile(ctx.total_ram_mb);

    let mut cmd = vec![ctx.java_path.to_string(), format!("-Xmx{heap}M"), format!("-Xms{}M", heap / 2)];
    cmd.extend(profile.jvm_flags());
    cmd.extend(ctx.settings.extra_jvm_args.iter().cloned());

    let mut has_cp = false;
    match &v.arguments {
        Some(a) => {
            let jvm = flatten(&a.jvm, ctx.env, &vars);
            has_cp = jvm.iter().any(|x| x == "-cp");
            cmd.extend(jvm);
        }
        None => {
            cmd.push(format!("-Djava.library.path={}", vars["natives_directory"]));
        }
    }
    if !has_cp {
        cmd.push("-cp".into());
        cmd.push(classpath);
    }

    cmd.push(v.main_class.clone());

    match (&v.arguments, &v.minecraft_arguments) {
        (Some(a), _) => cmd.extend(flatten(&a.game, ctx.env, &vars)),
        (None, Some(old)) => cmd.extend(old.split_whitespace().map(|s| expand(s, &vars))),
        _ => {}
    }
    cmd
}

#[cfg(test)]
mod tests {
    use super::*;

    fn version(json: &str) -> VersionJson {
        VersionJson::parse(json).unwrap()
    }

    const NEW: &str = r#"{
      "id":"1.21","mainClass":"net.minecraft.client.main.Main",
      "assetIndex":{"id":"17","sha1":"a","size":1,"url":"u"},
      "downloads":{"client":{"sha1":"b","size":1,"url":"u"}},
      "arguments":{"game":["--username","${auth_player_name}","--version","${version_name}"],
                   "jvm":["-cp","${classpath}"]},
      "libraries":[{"name":"a:b:1","downloads":{"artifact":{"path":"a/b/1/b-1.jar","sha1":"c","size":1,"url":"u"}}}]
    }"#;

    #[test]
    fn maven_name_to_path() {
        assert_eq!(
            library_path("org.lwjgl:lwjgl:3.3.3:natives-linux").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"
        );
        assert_eq!(library_path("a.b:c:1").unwrap(), "a/b/c/1/c-1.jar");
        assert!(library_path("broken").is_none());
    }

    #[test]
    fn builds_modern_command() {
        let v = version(NEW);
        let s = Settings { username: "Steve".into(), ..Default::default() };
        let env = Env::android_arm64();
        let cmd = build_command(&LaunchContext {
            version: &v, settings: &s, env: &env, root: "/data/lime",
            total_ram_mb: 3000, java_path: "java",
        });
        assert_eq!(cmd[0], "java");
        assert!(cmd.iter().any(|a| a.starts_with("-Xmx")));
        assert!(cmd.iter().any(|a| a == "-XX:+UseSerialGC")); // профиль Low для 3 ГБ
        let cp_idx = cmd.iter().position(|a| a == "-cp").unwrap();
        assert!(cmd[cp_idx + 1].contains("/data/lime/libraries/a/b/1/b-1.jar"));
        assert!(cmd[cp_idx + 1].ends_with("/versions/1.21/1.21.jar"));
        let main_idx = cmd.iter().position(|a| a == "net.minecraft.client.main.Main").unwrap();
        assert!(main_idx > cp_idx);
        assert_eq!(&cmd[main_idx + 1..main_idx + 5], ["--username", "Steve", "--version", "1.21"]);
    }

    #[test]
    fn classpath_is_not_duplicated() {
        let v = version(NEW);
        let s = Settings::default();
        let env = Env::android_arm64();
        let cmd = build_command(&LaunchContext {
            version: &v, settings: &s, env: &env, root: "/r", total_ram_mb: 3000, java_path: "java",
        });
        assert_eq!(cmd.iter().filter(|a| *a == "-cp").count(), 1);
    }

    #[test]
    fn builds_legacy_command() {
        let v = version(r#"{
          "id":"1.8.9","mainClass":"net.minecraft.client.main.Main",
          "assetIndex":{"id":"1.8","sha1":"a","size":1,"url":"u"},
          "downloads":{"client":{"sha1":"b","size":1,"url":"u"}},
          "minecraftArguments":"--username ${auth_player_name} --gameDir ${game_directory}",
          "libraries":[]}"#);
        let s = Settings::default();
        let env = Env::android_arm64();
        let cmd = build_command(&LaunchContext {
            version: &v, settings: &s, env: &env, root: "/r", total_ram_mb: 3000, java_path: "java",
        });
        assert!(cmd.iter().any(|a| a.starts_with("-Djava.library.path=")));
        assert!(cmd.contains(&"--gameDir".to_string()));
        assert!(cmd.contains(&"/r/minecraft".to_string()));
    }
}
