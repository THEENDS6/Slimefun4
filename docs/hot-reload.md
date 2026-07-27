# 本地测试服热加载流程

本文只适用于仓库内的 `run-paper-1.21.8` 开发测试服。生产服或需要稳定 tick 的服务器应完整重启，不应使用 PlugManX 热加载。

## 启动与前置条件

```powershell
.\gradlew.bat runPaper --console=plain
```

`runPaper` 会构建并部署 `SlimeFunX.jar`，准备测试服配置，然后启动 Paper。测试配置会启用本机 RCON：`127.0.0.1:25575`。Gradle 客户端强制使用 `Proxy.NO_PROXY` 直连，避免 JVM 全局代理截获二进制 RCON 数据包；它不依赖未提交的本地脚本。

## 选择正确的加载方式

先按变更范围判断：

| 变更内容 | 使用任务 | 原因 |
| --- | --- | --- |
| 外置配置、语言文件，且运行时 reload 会重新读取 | `reloadPaperRuntime` | 只需通过 RCON 执行 `/sfx reload runtime` |
| 新 JAR 只需为下次重启准备 | `stageAndReloadPaperRuntime` | 当前进程仍使用旧字节码 |
| Java、公共 API、addon 类或 JAR 内嵌资源 | `plugmanReloadPaperRuntime` | 必须替换 JAR 并重新加载插件 |

因此，能由 `/sfx reload runtime` 完整生效的修改只执行 RCON，也属于一次完整的 runtime 热加载操作；不要额外卸载插件。凡涉及 Java 或内嵌 addon 资源的修改，都不能降级为 RCON-only。

仅修改外置配置、语言或已加载字节码能够重新读取的内容：

```powershell
.\gradlew.bat reloadPaperRuntime --console=plain
```

该任务只执行 `/sfx reload runtime`，不会替换正在运行的插件 JAR。

希望同时为下次重启准备新 JAR，但本次仍使用旧字节码：

```powershell
.\gradlew.bat stageAndReloadPaperRuntime --console=plain
```

该任务把新 JAR 放入 `plugins/update`，并对当前插件执行 runtime reload。新字节码要到下次服务器重启后才生效。

Java、API、addon 类或内嵌资源发生变化，需要立即在测试服验证：

```powershell
.\gradlew.bat plugmanReloadPaperRuntime --console=plain
```

完整步骤为：

1. 构建核心及所有内嵌 addon；
2. 通过 RCON 确认服务器在线；
3. 使用 PlugManX 卸载 SlimeFunX，触发 addon 和核心关闭生命周期；
4. 用新构建替换 `plugins/SlimeFunX.jar` 并校验文件一致；
5. 重新加载 SlimeFunX，等待 `SFX enabled`；
6. 检查启动失败、缺失语言键和 addon 语言错误；
7. 再次执行 RCON `list`，确认主线程恢复响应。

## 风险与验收

PlugManX 的加载动作在服务器主线程执行。SlimeFunX 启动时需要读取编译内容和恢复服务，可能暂停十余秒并触发 Paper Watchdog 线程转储。任务会报告该情况，但成功重新启用不代表没有卡顿。

热加载完成后至少检查：

```powershell
Get-Content .\run-paper-1.21.8\logs\latest.log -Encoding UTF8 -Tail 200
.\gradlew.bat paperRcon -Pcmd="list" --console=plain
```

有效结果应满足：

- 日志出现 `[SlimeFunX] SFX enabled.`；
- RCON `list` 正常返回；
- 没有 `SlimeFunX failed to start`；
- 没有 `Missing language key`；
- addon 的 `onDisable` 与重新加载均完成。

若出现启用失败、类加载残留、重复监听器、Watchdog 卡顿后服务异常，停止继续热加载并完整重启测试服。
