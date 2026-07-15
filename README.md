# SlimeFunX

[English](#slimefunx) | [简体中文](#简体中文) | [Download latest release](https://github.com/THEENDS6/Slimefun4/releases/latest)

> Put simply: SlimeFunX is a ground-up reimplementation of Slimefun, written by a veteran player with the help of AI. It is completely incompatible with Slimefun4; instead, it carefully recreates the classic content and improves the underlying systems, interactions, and details.

SlimeFunX is an experimental, heavily diverged fork of Slimefun4 for modern Paper servers.

It tries to keep the classic Slimefun gameplay feel while rebuilding large parts of the runtime underneath it. It is not an official Slimefun4 build and it is not currently intended to be a safe drop-in replacement for an existing production Slimefun4 server.

## Current status

- Version: `SFX-20260714a1`
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
3. [PacketEvents](https://github.com/retrooper/packetevents) `2.12.1` or a compatible newer version

PacketEvents is an external hard dependency. It must be installed as a separate Bukkit/Paper plugin. SlimeFunX does not treat PacketEvents as an optional dependency.

## Installation

Download the current packaged build from [GitHub Releases](https://github.com/THEENDS6/Slimefun4/releases/latest).

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

For the Paper 1.21.8 development runtime, see [Hot reload and runtime reload](docs/hot-reload.md). Bytecode reload through PlugManX is intended only for this local test server; use a full restart for stable servers.

For the SlimeEasy sieve-animation patch:

```powershell
.\gradlew.bat generateSlimeEasyModifiedSource
.\gradlew.bat buildSlimeEasyModified
.\gradlew.bat prepareSlimeEasyPaper262Runtime
.\gradlew.bat runSlimeEasyPaper262
```

The imported source tree lives under `external/SlimeEasy-sieve-animation`. Its test server is the SlimeEasy `run` directory and uses Paper 26.2, Java 25, Slimefun4, and the patched SlimeEasy jar. This is not a SlimeFunX addon test.

For the pinned GuGu Slimefun and JustEnoughGuide stack on the existing Paper 1.21.8 test runtime:

```powershell
.\gradlew.bat deployLegacySlimefunRuntime
.\gradlew.bat runLegacySlimefunWithJustEnoughGuide
```

The default runtime stack is pinned and SHA-256 verified: SlimefunGuguProject Build 254 (`b79ae49-Beta`), JustEnoughGuide 2.1.27, and GuizhanLibPlugin Build 62. JEG 2.1.27 dynamically loads `pinyin` and `opencc4j` but omits their required `heaven` and `nlp-common` runtime jars; `prepareGuguJustEnoughGuideRuntimeJar` adds those exact verified dependencies to the deployed JEG jar. The stack intentionally runs on the existing Paper 1.21.8 runtime even though the two main plugins compile against newer 1.21.x Paper APIs, so this runtime must be smoke-tested after every pinned-version change. The earlier `generateJustEnoughGuideClassicSource`, `buildJustEnoughGuideClassic`, and `deployJustEnoughGuideClassic` tasks remain available only as an EN-22 source-build fallback and are not used by the default start task.

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

See `docs/addon-api.md` for the current addon contract. The complete Chinese version is `docs/addon-api.zh-CN.md`.

### Bundled addons

SlimeFunX uses the same addon boundary for its own expanding feature set. The current distribution includes two bundled addons:

- **Basic Expansion** owns the extended electric-machine, area-machine, cargo, energy, radiation, technical-gadget, and utility behavior that has been moved out of the core runtime.
- **Content Expansion** adds new processing chains and machines, including copper oxidation and waxing, cutting-machine workflows, the oxidizing generator, and related recipes and localized guide content.

Bundled addons are packaged with SlimeFunX and load through the public addon lifecycle. Server owners do not need to download them separately. This structure keeps the core runtime smaller and gives future external addons a supported registration path instead of requiring access to internal classes.

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

---

# 简体中文

[English](#slimefunx) | [下载最新版本](https://github.com/THEENDS6/Slimefun4/releases/latest)

> 直白地说：SFX 是一个粘液科技老玩家在 AI 帮助下，从头开始重写出来的粘液科技。它完全不兼容 Slimefun4；经典内容都经过细致还原，并在此基础上重新实现和优化。

SlimeFunX 是面向现代 Paper 服务端的实验性 Slimefun4 深度分支。它保留经典 Slimefun 的主要玩法体验，同时重写机器、能源、货运、数据存储、指南与扩展加载等底层系统。

SlimeFunX 不是 Slimefun4 官方构建，也不是现阶段可直接替换生产服原版 Slimefun4 的兼容补丁。建议只用于测试、开发或经过充分验证的可控服务器。

## 当前状态

- 版本：`SFX-20260714a1`
- 状态：实验性开发版本
- 目标服务端：Paper `1.21.x`，目前主要围绕 Paper `1.21.8` 开发
- 预期兼容：Paper `26.1` 与 Folia
- Java：`21`
- Addon/API：仍在开发，暂不承诺长期二进制兼容

不要默认认为本版本与上游 Slimefun4、旧版 SlimeFunX 的数据完全兼容。现有服务器测试前必须备份整个服务端目录。

## 下载与依赖

从 [GitHub Releases 下载最新版本](https://github.com/THEENDS6/Slimefun4/releases/latest)。

运行前需要：

1. Java 21
2. 兼容的 Paper 1.21.x 服务端
3. [PacketEvents 官方仓库](https://github.com/retrooper/packetevents) `2.12.1` 或兼容的新版本

PacketEvents 是外置硬依赖，必须作为独立插件放入服务端的 `plugins/` 目录，SlimeFunX 不会把它打包进插件本体。

## 安装

1. 关闭服务端。
2. 将 PacketEvents 放入 `plugins/`。
3. 将 SlimeFunX jar 放入 `plugins/`。
4. 启动服务端并检查控制台是否存在加载错误。
5. 使用 `/slimefunx guide` 或 `/sfx guide` 打开指南。

新服务器建议先使用全新世界和全新插件数据测试；已有服务器必须先完整备份。

## 这一版本有什么不同

SlimeFunX 已不再只是对 Slimefun4 打少量补丁，而是一条独立演进的实现路线。本版本的主要方向包括：

- 明确的机器 tick、运行状态与会话生命周期
- 独立的方块、玩家、GPS 和机器人脚本存储
- 基于注册定义的严格物品身份判断
- 使用虚拟容器管理机器内部物品
- 基于网络拓扑的能源与货运系统
- 重构配方指南、搜索、来源和用途导航
- 降低对脆弱实时 `BlockState` 行为的依赖
- 更清晰的模块启动、运行时重载与失败处理
- 支持拼音的指南搜索与更完整的中英文文本

目标不是为了变化而变化，而是在保留熟悉玩法循环的同时，让旧运行时中难以维护和验证的部分变得更可靠。

## Addon 系统

SlimeFunX 已提供实验性的 Addon 加载框架。Java Addon 实现 `SfxAddon`，并从 `plugins/SlimeFunX/addons/*.jar` 加载；内容配置和语言文件既可以放在 Addon jar 内，也可以放在插件数据目录中。

Addon 应通过 `SfxAddonContext.features()` 和 `SfxAddonContext.behaviors()` 注册能力，不应直接依赖 SlimeFunX 的内部包。当前接口说明见 [`docs/addon-api.md`](docs/addon-api.md)，完整中文版见 [`docs/addon-api.zh-CN.md`](docs/addon-api.zh-CN.md)。

当前发行包内置两个 Addon，无需额外下载：

- **基础扩展（Basic Expansion）**：承接从核心中拆出的扩展电力机器、区域机器、货运、能源、辐射、技术工具与通用玩法行为。
- **内容扩展（Content Expansion）**：加入新的加工链和机器，包括铜氧化与涂蜡、切割机流程、氧化发电机，以及相应配方、研究和本地化指南内容。

内置内容也通过公开 Addon 生命周期加载，使核心运行时与扩展玩法保持清晰边界，并为后续外部 Addon 提供正式接入路径。

## 常用命令

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

可用别名：`/sfx`、`/sf`、`/x`。

## 从源码构建

```powershell
.\gradlew.bat clean build
```

主要源码位于 `main/java` 和 `main/resources`。Paper API 与 PacketEvents 由服务端提供；SQLite JDBC 随 SlimeFunX 构建打包。

## 问题反馈

反馈问题时请提供 SlimeFunX、Paper 和 PacketEvents 版本，说明是否使用 Folia，并附上相关控制台错误、堆栈和复现步骤。

## 许可证

SlimeFunX 是 Slimefun4 的深度分支，项目保留 GPL-3.0 许可证及上游许可背景。
