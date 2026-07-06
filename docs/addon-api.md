# SFX Addon API

SFX addons are split into configuration content and Java capabilities.

Configuration content declares items, recipes, machines, UI, energy entries, researches, and language text. Java addons register runtime capabilities through the SFX API. All content still compiles into explicit runtime data before it is loaded.

## Built-in Basic Expansion

`sfx:basic_expansion` is the default bundled addon. It owns SFX-specific additions that are not classic Slimefun4 behavior, including the fuel jetpack, Auto Brewer II, Bio Reactor II, advanced cargo input, generator and machine balance changes, GPS transmitter UI behavior, jetpack and jetboots rework, radiation rework, and Android woodcutter batch replant behavior.

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
enabled: true
main: com.example.sfxaddon.ExampleAddon
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
    public void onLoad(SfxAddonContext context) {
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
- `configBoolean`, `configInt`, `configDouble`, and `configString` for addon-owned configuration values.

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

Java addons can register behavior providers for runtime capability points, including enhanced furnace fuel scaling, Android woodcutter behavior, radiation rules, cargo input transfer, GPS transmitter interaction, technical gadget rules, rechargeable item definitions, energy balance rules, area-machine rules, utility rules, and localized list post-processing.

Providers receive the current rule or decision and may return an adjusted one. The core runtime supplies classic defaults; addons layer their behavior through these APIs.

Rechargeable item providers may register new rechargeable items or override classic SF rechargeable definitions. Basic Expansion uses this to provide the SFX jetpack and jetboots rework, plus the fuel jetpack definition, while core keeps the classic SF definitions.

Technical gadget behavior providers own the active SFX jetpack and jetboots behavior: velocity calculation, hover movement, air jumps, fall-damage reduction, and particles/sounds. Core owns only input polling, player tick scheduling, energy/fuel storage, and state cleanup.

GPS transmitter status view providers own the SFX transmitter status UI model: title, slots, materials, language keys, and placeholders. Core owns only opening the inventory and supplying transmitter telemetry.

Auto Brewer behavior providers own the SFX Auto Brewer UI/input surface: input slots, fuel display constants, and slot validation rules. Core owns only inventory rendering calls, potion classification, and machine state persistence.

Radiation symptom handlers own addon-specific radiation symptom effects. Core owns exposure accounting, classic SF symptoms, stage announcement state, persistence, and death attribution hooks.

Electric special provider key policies may redirect a compiled `sf:special_provider` binding from a classic provider key to an addon provider key. Basic Expansion uses this for Auto Brewer, growth machines, Produce Collector, Auto Breeder, XP Collector, and Fluid Pump behavior without hard-coding those SFX branches in the core provider implementations.

Electric special provider factories may register the runtime provider behind an addon provider key. The factory receives the active plugin, item registry, and block-data service when electric machine definitions are built, so addon jars can provide Java-backed machine executors while the core only resolves the compiled provider contract.

Localized list post-processors can adjust generated item lore and other language lists after the core language lookup is complete. The context exposes the requested language key plus raw text/list lookup helpers so addon-owned text stays in addon language files.
