# SFX Example Addon

`sfx:example` 是随 SlimeFunX 分发的官方管理员调试附属，也是第三方 Addon API 的可运行参考实现。

它只实现 `SFX-Example-Addon-Design.md` 中的内容，不包含任何 Ex Nihilo 玩法、资源链或离心工业内容。

## 内容

- 仅管理员可见的 DEBUG 分类，不额外添加占位书物品；
- 完全由 YAML 定义且可放置的粘液块 IV；
- 使用独立 UI、聊天数值输入和整网能量控制的 DEBUG 电力单元；
- 可独立调度、可与普通运输调度器共存并控制启停/倍率的 DEBUG 运输调度器；
- 使用原版共享冷却组件、真实鱼实体和线段碰撞且不可食用的 DEBUG 鱼；
- 直接调用公开循环方块 API 的魔法粘液块。

统一权限为 `sfx.example.debug`。分类不可见不等于授权：核心和附属会分别在命令、放置、物品使用、GUI 与网络交互处检查权限。

## 构建

```powershell
.\gradlew.bat exampleAddonJar
```

输出：`build/addons/sfx-example-addon.jar`。主插件 JAR 也会把它作为 `bundled-addons/sfx-example-addon.jar` 嵌入，并通过与外部 Java Addon 相同的 classloader 和生命周期加载。

源码和字节码禁止引用 `cc.theends6.sfx.internal`；`validateSfxAddonPublicApiBoundary` 与 `validateSfxExampleAddonClassLinkage` 会回归验证这一边界。
