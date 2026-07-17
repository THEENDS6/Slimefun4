# Paper 1.21.8 survival test runtime

Runtime directory: `run-paper-slimefunx-survive-1.21.8`

The runtime is isolated from the normal SFX test server. It uses game port `25567` and MRC port `8772`. Runtime files, worlds, plugin databases and generated configuration are intentionally ignored by Git.

## SFX workflow

```powershell
.\gradlew.bat deployToSurviveRuntime --console=plain
.\gradlew.bat runSurvivePaper --console=plain
.\gradlew.bat surviveMrc -Pcmd="list" --console=plain
.\gradlew.bat reloadSurviveRuntime --console=plain
.\gradlew.bat plugmanReloadSurviveRuntime --console=plain
```

Use `reloadSurviveRuntime` for configuration, language and runtime-content changes. Use `plugmanReloadSurviveRuntime` for Java bytecode or bundled-resource changes. Installing or updating server-wide plugins still requires a complete restart.

For a complete restart after staging the current SFX JAR, run:

```powershell
.\gradlew.bat restartSurviveRuntime --console=plain
```

The task calls the persistent local MRC controller, which sends a normal stop when possible and owns the new `start-paper.ps1` process. Gradle then verifies both the MRC listener and the SFX enable log. The controller is configured with `autoInstall: false`, so it cannot overwrite server plugin JARs.

The controller must already be listening on `127.0.0.1:8765`. Its local checkout is `external/MinecraftRemoteConsole`; keep `autoInstall: false` when rebuilding or changing the controller configuration so the verified runtime MRC JAR remains under this workspace's deployment control.

## Installed plugin sources

- LuckPerms 5.5.53: official Modrinth release for Paper 1.21.8.
- Vault 1.7.3: official MilkBowl GitHub release.
- Chunky 1.4.40: official Modrinth release for Paper 1.21.8.
- WorldEdit 7.3.19: official Modrinth release for Paper 1.21.8; this is the newest tested line here compiled for Java 21. WorldEdit 7.4.4 requires Java 25.
- CoreProtect CE 24.0: official Modrinth release for Paper 1.21.8.
- Towny 0.103.0.0: official TownyAdvanced GitHub release; TownyChat is not installed.
- PacketEvents 2.12.1: required SlimeFunX runtime dependency, reused from the verified Paper 1.21.8 test runtime.
- MinecraftRemoteConsole 0.5.0-SNAPSHOT: local workspace build used by the verified Paper 1.21.8 test runtime.
- PlugManX 3.0.4: reused from the existing SFX Paper runtime.
- SlimeFunX: always produced by the current workspace build.

Paper 1.21.8 already bundles spark. A separate `spark.jar` is not installed.

## BasicTeleporter independent repository

`external/BasicTeleporter` is an independent Git repository imported from `BasicTeleporter-src-1.0.1.zip`. The main SlimeFunX repository ignores the whole directory; its source history and build outputs stay separate.

```powershell
.\gradlew.bat buildBasicTeleporter --console=plain
.\gradlew.bat deployBasicTeleporterToSurviveRuntime --console=plain
.\gradlew.bat reloadBasicTeleporterConfig --console=plain
.\gradlew.bat plugmanReloadBasicTeleporter --console=plain
```

Use a complete `restartSurviveRuntime` for first installation or dependency changes. The config reload task is sufficient for normal `plugins/BasicTeleporter/config.yml` edits. Java changes can use the PlugManX task on this test server.
