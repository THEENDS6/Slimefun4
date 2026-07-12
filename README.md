# SlimeFunX

SlimeFunX is an experimental, heavily diverged fork of Slimefun4 for modern Paper servers.

It tries to keep the classic Slimefun gameplay feel while rebuilding large parts of the runtime underneath it. It is not an official Slimefun4 build and it is not currently intended to be a safe drop-in replacement for an existing production Slimefun4 server.

## Current status

- Version: `SFX-20260524c1`
- Status: experimental development build
- Target server: Paper `1.21.x`, currently developed around Paper `1.21.8`
- Expected compatibility: Paper `26.1` and Folia are intended to be supported by this development line
- Java: `21`
- Folia: `plugin.yml` declares Folia support and the runtime is written with region/player scheduler integration in mind
- Addon/API stability: not stable yet

Use this build for testing, development, and controlled servers. Do not assume data compatibility with upstream Slimefun4 or with older SlimeFunX builds.

## Requirements

Install these before starting the server:

1. Java 21
2. A compatible Paper 1.21.x server
3. PacketEvents `2.12.1` or a compatible newer version

PacketEvents is an external hard dependency. It must be installed as a separate Bukkit/Paper plugin. SlimeFunX does not treat PacketEvents as an optional dependency.

## Installation

1. Stop the server.
2. Install PacketEvents into the server `plugins/` directory.
3. Put the SlimeFunX jar into the same `plugins/` directory.
4. Start the server.
5. Check the console for startup errors.
6. Open the guide with `/slimefunx guide` or `/sfx guide`.

For a new server, test the build with a fresh world and fresh plugin data first. For an existing server, back up the whole server directory before testing.

## Build from source

This source package keeps the main source tree under:

```text
main/java
main/resources
```

In the full Gradle workspace, build with:

```bash
./gradlew clean build
```

The built plugin jar is produced by the Gradle project. If you copy this source package into an existing workspace, make sure the workspace build script matches the dependency policy below.

## Local test flows

SlimeFunX and the imported SlimeEasy patch are intentionally tested as separate plugin lines.

For SlimeFunX on the local Paper 26.1 runtime:

```powershell
.\gradlew.bat check
.\gradlew.bat deployToPaper261Runtime
.\gradlew.bat runPaper261
```

The Paper 26.1 runtime lives under `run-paper-26.1` and expects `paper-26.1.jar` plus PacketEvents in `plugins`.

For the SlimeEasy sieve-animation patch:

```powershell
.\gradlew.bat generateSlimeEasyModifiedSource
.\gradlew.bat buildSlimeEasyModified
.\gradlew.bat prepareSlimeEasyPaper262Runtime
.\gradlew.bat runSlimeEasyPaper262
```

The imported source tree lives under `external/SlimeEasy-sieve-animation`. Its test server is the SlimeEasy `run` directory and uses Paper 26.2, Java 25, Slimefun4, and the patched SlimeEasy jar. This is not a SlimeFunX addon test.

## Dependency policy

The intended dependency policy is:

- Paper API: `compileOnly`, provided by the server
- PacketEvents: `compileOnly`, provided by the external PacketEvents plugin
- SQLite JDBC: bundled or shaded by the SlimeFunX jar, because the server does not provide it

If your workspace `build.gradle` still shades PacketEvents, remove that shade entry. PacketEvents should be installed as a separate plugin instead.

## Basic commands

```text
/slimefunx guide
/slimefunx cheatguide
/slimefunx book
/slimefunx give
/slimefunx research
/slimefunx backpack
/slimefunx inspect
/slimefunx list
/slimefunx reload [config|runtime]
```

Aliases:

```text
/sfx
/sf
/x
```

## Relationship with Slimefun4

SlimeFunX comes from Slimefun4, but it has diverged far enough that it should be treated as its own implementation line.

Formally, it is still a fork. Practically, it no longer tries to preserve the upstream code structure or behave as a lightweight compatibility patch set.

If you need something that tracks upstream Slimefun4 closely, this repository is not that. If you want to test a version of the classic Slimefun experience with a rebuilt runtime, data layer, machine model, and network model, that is the purpose of SlimeFunX.

## What is different

Major areas being rebuilt or replaced include:

- explicit machine tick, state, and session logic
- dedicated block data, player data, GPS data, and Android script storage
- stricter item identity through registered item definitions
- virtual-container-backed machine internals
- topology-driven energy and cargo behavior
- rebuilt guide and machine UI flows
- reduced dependence on fragile live `BlockState` behavior
- clearer failure handling during runtime reloads and module startup

The goal is not to change Slimefun for the sake of novelty. The goal is to keep the gameplay loop familiar while replacing parts of the old runtime that were difficult to reason about or maintain.

## API and additions

The public API is not stable yet. The current `api` package separates intended external access from internal implementation, but no long-term compatibility promise is made for this development line.

SFX now has experimental addon support. Java addons implement `SfxAddon` and are loaded from `plugins/SlimeFunX/addons/*.jar`; configuration content and language files can be shipped inside addon jars or under the plugin data directory. Behavior should be registered through `SfxAddonContext.features()` and `SfxAddonContext.behaviors()`, not by depending on internal packages.

See `docs/addon-api.md` for the current addon contract.

## Feedback

This is currently a small, fast-moving project. Paper `26.1` and Folia are expected to work in this development line, but if they do not, contact THEENDS6 directly. Formal issue templates, pull request rules, and security reporting rules may be added later if the project grows.

When reporting a problem, include:

- SlimeFunX version
- Paper version
- PacketEvents version
- whether Folia is being used
- whether the server is Paper `26.1`
- the relevant console error or stack trace
- reproduction steps, if possible

## License

SlimeFunX is a heavily diverged fork of Slimefun4. This package includes a GPL-3.0 license file to preserve the upstream licensing context.
