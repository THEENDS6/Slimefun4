# SFX Addon API

Chinese version: [addon-api.zh-CN.md](addon-api.zh-CN.md). The current addon API version is `2`; API v1 addons remain supported through the scoped compatibility adapter.

SFX addons are split into configuration content and Java capabilities.

Configuration content declares items, recipes, machines, UI, energy entries, researches, and language text. Java addons register runtime capabilities through the SFX API. All content still compiles into explicit runtime data before it is loaded.

## Built-in Basic Expansion

`sfx:basic_expansion` is the default bundled addon. It owns SFX-specific additions that are not classic Slimefun4 behavior, including the fuel jetpack, Auto Brewer II, Bio Reactor II, advanced cargo input, non-owning cargo filter markers, generator and machine balance changes, GPS transmitter UI behavior, jetpack and jetboots rework, radiation rework, and Android woodcutter batch replant behavior.

The core runtime must not hard-code Basic Expansion item ids, language keys, or SFX-specific config paths. Those belong under:

```text
addons/basic-expansion/src/main/java/cc/theends6/sfx/addons/basic
addons/basic-expansion/src/main/resources
```

Build output packages this addon as a complete jar and embeds it into the SFX plugin:

```text
SlimeFunX.jar
  bundled-addons/sfx-basic-expansion.jar

sfx-basic-expansion.jar
  addon.yml
  content/...
  lang/...
  cc/theends6/sfx/addons/basic/...
```

At runtime SFX expands the bundled addon defaults into:

```text
plugins/SlimeFunX/addons/basic_expansion/addon.yml
plugins/SlimeFunX/addons/basic_expansion/content/...
plugins/SlimeFunX/addons/basic_expansion/lang/...
plugins/SlimeFunX/addons/basic_expansion/sfx-basic-expansion.jar
```

The expanded files are the editable server-side content source. The addon jar remains the Java code source and default-resource source.

## Java Addon Jar

External Java addons can use the same directory model:

```text
plugins/SlimeFunX/addons/<addon-id>/
  addon.yml
  content/...
  lang/...
  addon.jar
```

Jar-only addons are also supported from `plugins/SlimeFunX/addons/*.jar`.

Each Java addon jar must contain `addon.yml`:

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

The `main` class must implement `cc.theends6.sfx.api.addon.SfxAddon`.

```java
public final class ExampleAddon implements SfxAddon {
    @Override
    public String id() {
        return "example:demo";
    }

    @Override
    public void onRegister(SfxAddonContext context) {
        context.features().registerBoolean(
                "example:demo_feature",
                "addons.example.demo-feature",
                true
        );
    }
}
```

`SfxAddonContext` exposes:

- `api()` for the public SFX API.
- `features()` for feature registration.
- `behaviors()` for behavior/capability providers.
- `overrides()` for complete, exclusive component replacements declared by the addon manifest.
- `resources()` for addon-owned listeners, Folia tasks, and closeable runtime resources; `scheduler()` is its scheduling-only view.
- `items()`, `machines()`, and `cargo()` for owner-scoped domain registration.
- `blocks()`, `randomTicks()`, `displays()`, `containers()`, `continuousMachines()`, and `power()` for owner-scoped domain definitions.
- `worldActions()` and `protection()` for region-safe protected world mutation; `components()` aliases declared component overrides.
- `api().addonRuntime()` consumes registered definitions: it creates virtual containers, advances continuous machines,
  applies powered-item charge/use rules, and exposes `displays()` for PacketEvents client-only item/block display sessions.
- `dataDirectory()` for `plugins/SlimeFunX/addons/<addon-id>/`.
- `config()` plus `configBoolean`, `configInt`, `configDouble`, and `configString` for the addon's own `config.yml`; these never read the core config.

## Exclusive Component Overrides

An addon may replace a complete component only when SFX publishes that component as a typed override target. The addon must claim the target in `addon.yml`:

```yaml
overrides:
  - target: sfx:research-payment
    contract-version: 2
```

It then installs the full implementation during `onLoad`:

```java
context.overrides().replace(
        SfxComponentOverrideTargets.RESEARCH_PAYMENT,
        new CustomResearchPayment()
);
```

Component overrides are exclusive. If two enabled addons claim the same target, addon loading fails with both addon ids instead of selecting an implementation by load order. A declared target without an installed implementation also fails loading. SFX removes the implementation before closing the old addon classloader during a complete runtime reload.

The implementation owns the entire published contract. For `sfx:research-payment` contract version 2, this includes the player-aware displayed price, affordability decision and atomic charge. Research persistence and the rest of the guide remain outside that component boundary. Addons cannot replace core classes by shipping the same class name and still may not reference `cc.theends6.sfx.internal`.

## Content And Language

Addon content may be shipped inside the addon jar or placed in the plugin data directory.

Data-directory configuration addons must also contain `addon.yml`, but they do not need a Java `main` class:

```yaml
id: example:config_pack
name: Example Config Pack
enabled: true
features:
- id: example:config_pack_content
  config: addons.example.config-pack.enabled
  default-enabled: true
```

Jar-root layout:

```text
content/templates/...
content/items/...
content/researches/...
lang/en-US.yml
lang/zh-CN.yml
```

Bundled-style layout:

```text
content/addons/<addon-id>/content/templates/...
content/addons/<addon-id>/content/items/...
content/addons/<addon-id>/content/researches/...
content/addons/<addon-id>/lang/en-US.yml
content/addons/<addon-id>/lang/zh-CN.yml
```

Data-directory layout:

```text
plugins/SlimeFunX/addons/<addon-id>/addon.yml
plugins/SlimeFunX/addons/<addon-id>/content/...
plugins/SlimeFunX/addons/<addon-id>/lang/<language>.yml
```

Bundled addon content is overlaid before runtime compilation. External addon jar content is overlaid from successfully loaded addon jars. Language lookup order is:

```text
custom server lang > addon lang > bundled core lang
```

## Feature Gates

Compiled content can require an addon feature:

```yaml
requires-feature: example:demo_feature
```

Compiled content can also be excluded when a feature is enabled:

```yaml
excludes-feature: sfx:generator_balance
```

Feature-gated content is rejected unless the feature is registered and enabled. Runtime loading does not guess from legacy config paths.

## Behavior Providers

Java addons can register behavior providers for runtime capability points, including enhanced furnace fuel scaling, Android woodcutter behavior, entity-drop chances, radiation rules, cargo input transfer, cargo filter-interface rules, GPS transmitter interaction, technical gadget rules, rechargeable item definitions, energy balance rules, area-machine rules, utility rules, and localized list post-processing.

The public API also exposes three composable contracts used by the official `sfx:example` addon:

- `SfxCyclingBlockDefinition` declares a material sequence and interval; the shared decoration service owns scheduling, chunks, persistence, destruction, and custom-item drops.
- `SfxCargoNodeDefinition` registers cargo managers/connectors/terminals. Area managers resolve their X/Y/Z range from the SFX block index during topology rebuilds, never by scanning world blocks each tick.
- `SfxDynamicEnergyGeneratorProvider` may declare dynamic consumption and accept grid energy, allowing one persistent node to switch safely between producer and consumer modes.

Item YAML may declare separate `permission` and `use-permission` values. The first controls guide discovery and the second is checked at commands, placement, item behavior, GUI, and network interaction boundaries. A vanilla shared cooldown component is declared with:

```yaml
components:
  use-cooldown:
    seconds: 1.0
    group: example:shared_tool
```

Providers receive the current rule or decision and may return an adjusted one. The core runtime supplies classic defaults; addons layer their behavior through these APIs.

Entity-drop chance policies receive the drop definition, output item, entity type, attributed death source, player killer, and Looting level. The core clamps the final chance, performs exactly one roll, and inserts a successful result into `EntityDeathEvent#getDrops()`. `SFX_ANDROID` is transient attribution for the damage call that actually caused death; it does not persist on surviving entities. Basic Expansion uses this policy to rebalance the Basic Circuit Board while core retains the classic player-only 75% default.

Rechargeable item providers may register new rechargeable items or override classic SF rechargeable definitions. Basic Expansion uses this to provide the SFX jetpack and jetboots rework, plus the fuel jetpack definition, while core keeps the classic SF definitions.

Technical gadget behavior providers own the active SFX jetpack and jetboots behavior: velocity calculation, hover movement, air jumps, fall-damage reduction, and particles/sounds. Core owns only input polling, player tick scheduling, energy/fuel storage, and state cleanup.

GPS transmitter status view providers own the SFX transmitter status UI model: title, slots, materials, language keys, and placeholders. Core owns only opening the inventory and supplying transmitter telemetry.

Auto Brewer behavior providers own the SFX Auto Brewer UI/input surface: input slots, fuel display constants, and slot validation rules. Core owns only inventory rendering calls, potion classification, and machine state persistence.

Radiation symptom handlers own addon-specific radiation symptom effects. Core owns exposure accounting, classic SF symptoms, stage announcement state, persistence, and death attribution hooks.

Electric special provider key policies may redirect a compiled `sf:special_provider` binding from a classic provider key to an addon provider key. Basic Expansion uses this for Auto Brewer, growth machines, Produce Collector, Auto Breeder, XP Collector, and Fluid Pump behavior without hard-coding those SFX branches in the core provider implementations.

Electric special provider factories may register the runtime provider behind an addon provider key. The factory receives the active plugin, item registry, and block-data service when electric machine definitions are built, so addon jars can provide Java-backed machine executors while the core only resolves the compiled provider contract.

Localized list post-processors can adjust generated item lore and other language lists after the core language lookup is complete. The context exposes the requested language key plus raw text/list lookup helpers so addon-owned text stays in addon language files.

## Lifecycle

`onRegister(context)` runs after every manifest has been scanned and dependency order has been resolved. Registrations are owner-scoped and are rolled back together if the callback fails. `onEnable(context)` starts runtime resources only after declarations commit. `onDisable()` runs in reverse load order before core-owned resources are automatically released. API v1 `onLoad(context)` is bridged into the same registration transaction.

The registration transaction is staged, not compensating: declarations and owned listeners/tasks remain invisible and inactive until commit. A callback failure discards the staged entries; a commit or enable failure additionally invokes `unregisterAll(addonId)`. Custom block lifecycle callbacks are dispatched by the core for placement, interaction, physics, neighbours, piston/fluid/vanilla transforms, chunk/world load boundaries and destruction. State decoding or lifecycle failures quarantine the affected instance instead of repeatedly crashing the tick loop.

`api().addonRuntime().displays()` owns client-only display sessions. It allocates virtual entity ids, supports item and block displays with complete translation/scale/quaternion transforms, observes registered view distance and update throttling, persists per-player category switches, destroys projections when players leave range or change world, and clears every projection during addon runtime unload. Display data is a projection only; machine and block state remains authoritative elsewhere. Cross-block reads and atomic typed state updates use `addonRuntime().blockStates()`; addons do not open the core database or cast its implementation.

Registered powered items use `addonRuntime().poweredItems()` for normalized PDC state and consistent charge/use rules. `addonRuntime().inventoryPower()` scans a player's storage inventory at most once per server tick, invalidates that snapshot on inventory mutations, and settles portable generators before battery discharge through the transactional power router. Transactional ports and containers implement `prepareInsert`/`prepareExtract`; the core prepares every participant before commit and restores implementation-owned exact snapshots on failure instead of calling a one-way port backwards.

`addonRuntime().continuousMachines().createManaged(...)` creates a core-owned persistent instance. Managed instances advance on their location's scheduler region while the chunk is loaded, use the persistent active-tick clock (server downtime never advances them), survive runtime shutdown, and expose bounded player input through `applyManagedInput(...)`. `api().worldActions().breakBlocks(...)` provides the electric-tool batch boundary: it deduplicates and caps targets, rejects cross-world or cross-region batches, preflights protection events, requests a reservation for the exact prepared count, then commits resources before mutation. A partial result is explicit if an unexpected world mutation fails.

Plain `/slimefunx reload` only reloads core configuration and language. `/slimefunx reload runtime` and `/slimefunx reload all` perform a complete runtime reload: addons receive `onDisable()`, core runs `unregisterAll(addonId)`, runtime modules stop, and fresh addon instances/classloaders are created before content and services restart. Independent hot installation/removal of one Java addon is not supported yet.

An addon-defined cargo manager may attach an `SfxCargoManagerProvider` through `SfxCargoNodeDefinition` to supply a custom menu plus network enable and speed controls. A manager declared with `coexistsWithManagers=true` does not create a multiple-controller conflict with a normal Cargo Manager: the normal manager remains the network anchor when present, while the compatible manager remains a control surface and can also dispatch by itself.

Dynamic energy providers may fully own the top inventory with `customMenuLayout()`, expose persisted dynamic capacity through `effectiveCapacity()`, and safely fill or clear the connected grid through `SfxEnergyGeneratorAccess.fillGridEnergy()` / `clearGridEnergy()`. To retain an edible vanilla material without allowing consumption, declare `components.consumable: false` on the item.

## Manifest And Identity

- `id` must be lowercase `namespace:name` and must equal `SfxAddon.id()`.
- `api-version` may be `1` or `2`; unsupported versions are rejected before Java class loading.
- Java addons require `main`; ids, features, providers, items, language, permissions, and PDC keys must be namespace-owned and globally unique.
- `depends`, `soft-depends`, `load-after`, and `conflicts` are resolved before Java class loading. Missing hard dependencies, conflicts, and cycles fail with the involved addon ids. Version ranges are not supported yet.

## Public API Boundary

Addon source and bytecode must not reference `cc.theends6.sfx.internal`. Bundled and external addons follow the same rule. Missing capabilities must become general public APIs; do not use reflection, internal casts, or core checks for a particular addon/item id. Do not shade SFX API or server-provided Paper classes into addon jars.

Runtime SPI packages are split by responsibility:

- `api.machine.runtime`: electric recipes, stacks, definitions and state, tick results, UI models, provider contracts, and safe output insertion.
- `api.machine.manual`: manual-machine definitions, operations, recipes, and outputs used by `SfxManualMachineRegistry`.
- `api.energy.runtime`: energy definitions and state, generator access, provider contracts, and energy UI models.
- `api.block`: block instance, anchor, lifecycle value objects, and cycling-block declarations.
- `api.testkit.SfxAddonTestKit`: server-free assertions for duplicate ids, state migration, virtual-fluid simulation, power routing, active-tick settlement, and exact prepared-transaction rollback. The core `validateSfxAddonLifecycleSmoke` task additionally exercises staged commit/rollback, owner cleanup and transactional rollback.
- `api.cargo`: public cargo node kinds and indexed area-topology declarations.
- `api.runtime.SfxRuntime`: Folia-aware scheduling and audited block mutations.
- `api.localization.SfxLocalizationView`: read-only access to the active merged core/addon language layer.

Provider implementations belong to the addon's own package. Bundled implementations use `cc.theends6.sfx.addons.<addon>` and must not declare an `internal` package.

## Folia, Security, And Validation

World, entity, inventory and player work must use the scheduler for the owning region/entity. Revalidate state after asynchronous work, make shared state thread-safe, and avoid one repeating task per placed block. Hiding guide content is not authorization: check permission at every command, use, placement, GUI, chat-input, and network effect. Treat configuration, persisted state, and user input as untrusted and clamp values again before applying them.

The core does not currently declare Folia support in `plugin.yml`: cross-region cargo and energy networks and runtime reload still need a complete region-ownership migration. Addons must nevertheless use the public scheduler API and must not access Bukkit objects across regions directly.

Programmatic world changes must go through `api().permissions()` or the higher-level `worldActions()` service. `SfxActionActor` represents online players, offline owners, machines, and ownerless system actions without forging a Bukkit player identity. Resolution checks registered `SfxProtectionAdapter` providers first (any `DENY` wins), then fires the purpose-built `SfxWorldActionPermissionEvent`, and only then applies configured fallback policy. Never forge vanilla `BlockBreakEvent` or `BlockPlaceEvent` instances to probe protection plugins.

Adapters are registered through Bukkit's `ServicesManager`. New adapters should implement the actor-aware `canPerform(...)` method; legacy `Player` hooks remain supported for online-player actions. The core includes a Towny adapter. Offline owners, unowned machines, and `SYSTEM` actions are denied by default and require the respective `permissions.allow-when-owner-offline`, `allow-unowned-machines`, or `allow-system-actions` opt-in. `permissions.fallback-allow` is the undecided baseline for normal online-player actions.

## Build And Verification

Package `addon.yml`, optional `config.yml`, `content/`, and `lang/` at the jar root and install the jar under `plugins/SlimeFunX/addons/`. Verify load, content compilation, interaction, chunk unload/reload, shutdown, and a second startup. Bundled tasks are `basicExpansionJar`, `contentExpansionJar`, and `exampleAddonJar`; `check` runs contract and isolated linkage validation for all three.

API version 2 remains experimental and does not yet promise long-term binary compatibility or independent per-addon hot loading. Persistent block/player data is retained when an addon unloads; `unregisterAll` removes runtime ownership, not world saves.
