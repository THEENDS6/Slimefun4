# SFX Addon API（中文版）

当前 Addon API 版本为 `1`。Java API 仍处于实验阶段，但本文规则是版本 1 的正式边界。

## 定位与边界

配置负责物品、分类、配方、研究、机器定义、GUI、能源条目和语言文本；Java 只负责世界交互、实体、动态 GUI、网络与配置无法表达的行为。

Addon 不得引用 `cc.theends6.sfx.internal`，也不得通过反射或强制转换绕过公开 API。内置 Addon 与第三方 Addon 遵守相同规则。缺少能力时先补通用公开 API，核心不能按某个 Addon 或物品 ID 写特殊判断。

公开运行时 SPI 按职责拆分：

- `api.machine.runtime`：电力配方、物品栈、机器定义与状态、tick 结果、UI 模型、Provider 和安全输出插入；
- `api.machine.manual`：供 `SfxManualMachineRegistry` 使用的手动机器定义、操作类型、配方和产物；
- `api.energy.runtime`：能源组件定义与状态、发电访问接口、Provider 和能源 UI；
- `api.block`：方块实例、锚点、生命周期值对象与循环方块声明；
- `api.cargo`：运输节点类型与基于索引的区域拓扑声明；
- `api.runtime.SfxRuntime`：Folia 调度与经过机器审计链的方块修改。
- `api.localization.SfxLocalizationView`：只读访问当前合并后的核心与 addon 语言层。

Addon 实现必须位于自己的命名空间；内置实现使用 `cc.theends6.sfx.addons.<addon>`，不得声明 `internal` 包。

## JAR 与数据目录

JAR 根目录可以包含：

```text
addon.yml
config.yml
content/templates/...
content/items/...
content/researches/...
lang/en-US.yml
lang/zh-CN.yml
```

JAR 安装在 `plugins/SlimeFunX/addons/*.jar`。运行时独立数据目录为 `plugins/SlimeFunX/addons/<addon-id>/`。语言优先级是：服务器自定义语言 > Addon 语言 > 核心语言。

## addon.yml

```yaml
id: example:demo
name: Example Demo
version: 1.0.0
api-version: 1
enabled: true
main: com.example.sfxaddon.ExampleAddon
java:
  jar: example-demo.jar
```

- `id` 必须是小写 `namespace:name`，并与 Java `id()` 完全一致；
- Java Addon 必须提供实现 `SfxAddon` 的 `main`；
- 不支持的 `api-version` 会在 `onLoad` 前被拒绝；
- feature、provider、物品、语言、权限和 PDC key 都必须使用自己的命名空间；
- 版本 1 暂无依赖排序和版本范围，需要时必须在 `onLoad` 明确检查。

## 生命周期

```java
public final class ExampleAddon implements SfxAddon {
    public String id() { return "example:demo"; }

    public void onLoad(SfxAddonContext context) {
        // 注册 feature 和 provider。
    }

    public void onDisable() {
        // 清理任务、监听器、实体、会话、网络节点和缓存。
    }
}
```

`onLoad` 在 manifest 与配置校验后调用。`onDisable` 在 classloader 关闭前按加载逆序调用，此时 `onLoad` 期间可用的核心服务仍然有效；清理逻辑必须可重复执行。

普通 `/slimefunx reload` 只刷新核心配置和语言。`/slimefunx reload runtime` 与 `/slimefunx reload all` 会执行完整运行时重载：先调用 Addon 的 `onDisable()`，丢弃旧 Provider 与 Feature 注册，再关闭核心运行时模块，随后创建新的 Addon 实例和 classloader，并重建内容与服务。版本 1 不支持单独安装、卸载或热重载某一个 Java Addon。

Addon 可通过 `SfxCargoNodeDefinition` 为运输调度器提供 `SfxCargoManagerProvider` 自定义菜单和启停、速度倍率控制。`coexistsWithManagers=true` 的调度器不会与普通调度器形成多控制器冲突：有普通调度器时由普通调度器承担网络锚点，自定义调度器仍可作为控制面；只有它自身时也可独立调度。

动态能源 Provider 可用 `customMenuLayout()` 完整接管顶部菜单，通过 `effectiveCapacity()` 向电网公开持久化的动态容量，并通过 `SfxEnergyGeneratorAccess.fillGridEnergy()` / `clearGridEnergy()` 安全修改当前连接电网。需要保留原版食物材质但禁止食用时，在物品 `components` 中声明 `consumable: false`。

## SfxAddonContext

- `api()`：公开 SFX API；
- `features()`：注册和查询功能；
- `behaviors()`：注册运行时 Provider，包括实体掉落概率策略；
- `overrides()`：安装已在 Addon manifest 中声明的完整独占组件替换；
- `dataDirectory()`：独立数据目录；
- `config()` 与 `configBoolean/Int/Double/String()`：读取 Addon 自己的 `config.yml`，不会读取核心配置。

## 独占组件 Override

只有核心明确发布为强类型 Override 目标的完整组件才能被替换。Addon 必须先在 `addon.yml` 中声明占用目标：

```yaml
overrides:
  - target: sfx:research-payment
    contract-version: 2
```

然后在 `onLoad` 中安装完整实现：

```java
context.overrides().replace(
        SfxComponentOverrideTargets.RESEARCH_PAYMENT,
        new CustomResearchPayment()
);
```

组件 Override 是独占的。两个启用的 Addon 声明同一个目标时，Addon 加载会直接失败并列出双方 ID，不会按加载顺序选择胜者。只声明目标却没有安装实现同样会失败。完整运行时重载会先移除旧实现，再关闭旧 Addon classloader。

实现正确性由 Addon 作者负责。对于 `sfx:research-payment` 合约版本 2，完整职责包括根据当前玩家生成费用展示、支付能力判断和原子扣费；研究存档和指南的其他部分仍在该组件边界之外。Addon 不能通过放置同名 class 覆盖核心类，也仍然不得引用 `cc.theends6.sfx.internal`。

内容可使用 `requires-feature` 和 `excludes-feature`。普通物品、配方、名称、Lore 和固定数值优先写 YAML；只有世界、实体、网络、动态 GUI 和复杂状态使用 Java。

官方 `sfx:example` 同时验证三个可组合公开能力：

- `SfxCyclingBlockDefinition` 只声明材质序列与间隔，核心统一负责调度、区块、持久化、破坏和自定义掉落；
- `SfxCargoNodeDefinition` 声明运输管理器、连接器或终端，区域管理器只在拓扑重建时查询 SFX 方块索引，不在每 tick 扫描世界方块；
- `SfxDynamicEnergyGeneratorProvider` 可声明动态用电需求并接收电网能量，使同一持久化节点能在发电与用电模式间切换。

物品 YAML 的 `permission` 控制指南可见性，`use-permission` 控制命令获取、放置、物品行为、GUI 与网络操作。原版共享冷却组件写法：

```yaml
components:
  use-cooldown:
    seconds: 1.0
    group: example:shared_tool
```

实体掉落概率策略会收到掉落定义、输出物品、实体类型、死亡归属、玩家击杀者和抢夺等级。核心会把最终概率限制到 `0..1`、只掷一次随机数，并把成功产物加入 `EntityDeathEvent#getDrops()`。`SFX_ANDROID` 只在真正造成死亡的同步伤害调用期间存在，不会残留到存活实体上。基础扩展通过该策略调整基础电路板概率；关闭基础扩展后，核心保留 Classic 的“仅玩家击杀、75%”默认行为。

## Folia、权限与数据安全

- 世界、实体、容器和玩家操作使用所属区域或实体调度器；
- 异步返回后重新确认位置、实体、权限和状态；
- 跨区域状态必须线程安全，不要为每个方块创建独立循环任务；
- 隐藏分类不能代替权限校验，命令、物品使用、放置、GUI、聊天输入和网络操作都在生效点检查权限；
- 配置、持久化数据和用户输入均不可信，范围、速度、容量和持续时间必须重新限制。

## 构建与验证

Paper 与 SFX API 使用 `compileOnly`，不要把 SFX API 类打进 Addon JAR。内置 Addon 构建任务：

```powershell
.\gradlew.bat basicExpansionJar
.\gradlew.bat contentExpansionJar
.\gradlew.bat exampleAddonJar
.\gradlew.bat check
```

至少验证加载、内容编译、权限、交互、区块卸载/加载、状态恢复、Folia 调度、插件关闭和第二次启动。

版本 1 尚不承诺长期二进制兼容、单个 Addon 独立热装卸、依赖图，也未提供每一种特殊世界交互机器所需的完整公开扩展面。
