# SFX Addon API（中文版）

当前 Addon API 版本为 `2`，运行时继续兼容 API v1 Addon。v2 引入 owner-scoped 注册、失败回滚、依赖排序和领域化基础设施。

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
api-version: 2
enabled: true
main: com.example.sfxaddon.ExampleAddon
depends: []
soft-depends: []
load-after: []
conflicts: []
java:
  jar: example-demo.jar
```

- `id` 必须是小写 `namespace:name`，并与 Java `id()` 完全一致；
- Java Addon 必须提供实现 `SfxAddon` 的 `main`；
- 不支持的 `api-version` 会在 Java 类加载前被拒绝；
- feature、provider、物品、语言、权限和 PDC key 都必须使用自己的命名空间；
- `depends` 是硬依赖，缺失时拒绝加载；`soft-depends` 与 `load-after` 在目标存在时建立顺序；`conflicts` 命中时拒绝双方同时加载；循环依赖会直接失败。

## 生命周期

```java
public final class ExampleAddon implements SfxAddon {
    public String id() { return "example:demo"; }

    public void onRegister(SfxAddonContext context) {
        // 只声明 feature、provider、方块类型等 owner-scoped 定义。
    }

    public void onEnable(SfxAddonContext context) {
        // 声明提交成功后再启动运行时资源。
        context.resources().registerListener(new ExampleListener());
    }

    public void onDisable() {
        // 只清理未交给 context.resources() 的外部资源。
    }
}
```

`onRegister` 在全部 manifest 预检与依赖拓扑排序后调用。所有注册都记录 Addon owner；回调失败时核心统一撤销。声明验证成功后调用 `onEnable`。`onDisable` 在 classloader 关闭前按加载逆序调用，之后核心自动取消 owner 任务、注销监听器并移除运行时注册；清理逻辑仍应可重复执行。API v1 的 `onLoad` 会由 v2 适配器转发到同一注册事务。

普通 `/slimefunx reload` 只刷新核心配置和语言。`/slimefunx reload runtime` 与 `/slimefunx reload all` 会执行完整运行时重载：先调用 Addon 的 `onDisable()` 和核心 `unregisterAll(addonId)`，再关闭运行时模块，随后创建新的 Addon 实例和 classloader，并重建内容与服务。当前仍不支持单独热装卸某一个 Java Addon。

Addon 可通过 `SfxCargoNodeDefinition` 为运输调度器提供 `SfxCargoManagerProvider` 自定义菜单和启停、速度倍率控制。`coexistsWithManagers=true` 的调度器不会与普通调度器形成多控制器冲突：有普通调度器时由普通调度器承担网络锚点，自定义调度器仍可作为控制面；只有它自身时也可独立调度。

动态能源 Provider 可用 `customMenuLayout()` 完整接管顶部菜单，通过 `effectiveCapacity()` 向电网公开持久化的动态容量，并通过 `SfxEnergyGeneratorAccess.fillGridEnergy()` / `clearGridEnergy()` 安全修改当前连接电网。需要保留原版食物材质但禁止食用时，在物品 `components` 中声明 `consumable: false`。

## SfxAddonContext

- `api()`：公开 SFX API；
- `features()`：注册和查询功能；
- `behaviors()`：注册运行时 Provider，包括实体掉落概率策略；
- `overrides()`：安装已在 Addon manifest 中声明的完整独占组件替换；
- `resources()`、`scheduler()`：注册自动归属 Addon 的 Listener、Folia 任务和 `AutoCloseable`；
- `items()`、`machines()`、`cargo()`：注册物品、手动机器和运输节点；
- `blocks()`、`randomTicks()`：注册带 Schema 的自定义方块和索引式随机刻；
- `displays()`、`containers()`：注册客户端显示分类、显示类型以及虚拟物品/流体容器；
- `continuousMachines()`、`power()`：注册连续型手动机器与通用电力物品；
- `worldActions()`、`protection()`：执行经过区域调度和 Bukkit 保护事件的世界操作；
- `components()`：`overrides()` 的领域化别名，安装清单中声明的组件替换；
- `api().activeClock()`、`powerRouter()`：读取持久服务器活动 Tick，并执行模拟后提交的统一电力结算；
- `api().addonRuntime()`：消费已经注册的定义，创建虚拟容器、推进连续机器、执行电力物品规则；其中 `displays()` 管理纯客户端 PacketEvents 物品/方块 Display 会话；
- `dataDirectory()`：独立数据目录；
- `config()` 与 `configBoolean/Int/Double/String()`：读取 Addon 自己的 `config.yml`，不会读取核心配置。

## 独占组件 Override

只有核心明确发布为强类型 Override 目标的完整组件才能被替换。Addon 必须先在 `addon.yml` 中声明占用目标：

```yaml
overrides:
  - target: sfx:research-payment
    contract-version: 2
```

然后在 `onRegister` 中安装完整实现：

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

注册使用真正的暂存事务：声明、Listener 和任务在提交前对外不可见且不会启动；注册回调失败时直接丢弃暂存内容，提交或启用失败时再由核心执行 `unregisterAll(addonId)`。自定义方块的放置、交互、物理/邻居更新、活塞、流体、原版转化、区块/世界边界和破坏均由核心派发生命周期；状态解码或回调异常会隔离单个实例，避免每 Tick 重复报错。

`api().addonRuntime().displays()` 分配虚拟实体 ID，支持带完整平移、缩放和四元数旋转的物品/方块 Display，按显示类型的视距和节流策略更新，持久保存每名玩家的分类开关，并在离开视距、切换世界或 Addon 运行时卸载时销毁投影。Display 只表示客户端视觉，不能作为机器或方块真实状态。跨方块读取和类型化原子状态更新统一使用 `addonRuntime().blockStates()`，Addon 不接触核心数据库或内部实现。

注册后的电力物品通过 `addonRuntime().poweredItems()` 统一读写 PDC 电量状态并执行充电、使用和超频规则。`addonRuntime().inventoryPower()` 每个服务器 Tick 最多扫描一次玩家背包，在物品栏变化时让缓存失效，并按“便携电源优先、再由电池放电”的顺序走事务式电力结算。事务端口和容器通过 `prepareInsert`/`prepareExtract` 提供实现自己持有的精确快照；核心在提交前准备全部参与者，失败时恢复快照，不再对单向端口调用反向操作。

`addonRuntime().continuousMachines().createManaged(...)` 创建由核心持久化和调度的连续机器实例：只在所在区块已加载时按区域线程推进，结算使用持久服务器活动 Tick，停服时间不会推进，并通过 `applyManagedInput(...)` 统一执行输入限流。`api().worldActions().breakBlocks(...)` 是电力范围工具的安全批处理边界：去重并限制目标数量，拒绝跨世界/跨区域请求，先完成保护事件预检，再按实际目标数预留和提交能源；若世界突变意外失败会明确返回部分完成结果。

第三方 Addon 可直接使用公开的 `api.testkit.SfxAddonTestKit` 做无服务器契约断言，包括精确的预备事务回滚；核心的 `validateSfxAddonLifecycleSmoke` 还会验证暂存提交/回滚、所有者清理和事务回滚。

Paper 与 SFX API 使用 `compileOnly`，不要把 SFX API 类打进 Addon JAR。内置 Addon 构建任务：

```powershell
.\gradlew.bat basicExpansionJar
.\gradlew.bat contentExpansionJar
.\gradlew.bat exampleAddonJar
.\gradlew.bat check
```

至少验证加载、内容编译、权限、交互、区块卸载/加载、状态恢复、Folia 调度、插件关闭和第二次启动。

API v2 仍处于实验阶段，暂不承诺长期二进制兼容或单个 Addon 独立热装卸；持久方块状态在 Addon 暂时缺失时保留，不会被 `unregisterAll` 当作运行时资源删除。
