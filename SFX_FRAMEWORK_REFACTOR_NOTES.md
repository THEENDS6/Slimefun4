# SlimeFunX framework refactor notes

Version: SFX-20260524b1

This source-only package keeps the external Gradle project files out of the archive. Copy the `main/` directory into the existing project.

Key changes in b1:

- PacketEvents remains a hard dependency in `plugin.yml`.
- Runtime services now start through `SfxModuleManager` instead of constructor side effects.
- `SfxModule` now supports dependency declarations; `SfxModuleManager` uses topological enable order and reverse shutdown order.
- Machine effect generic fallback remains disabled. Missing declared effect hooks fail the startup validation.
- Domain effect hooks use `registerEffectHookIfAbsent` so electric/configurable/basic service hooks are not overwritten by broad domain hooks.
- Built-in effect hooks no longer provide passive marker fallbacks for domain effects; only `framework:audit-tick` is framework-native.
- Core legacy services now drive their critical operation path through framework phase results using `SfxMachinePipelineGuard`.
- `reload runtime` / `reload all` now returns a success/failure status and no longer reports success after a failed runtime rebuild.
- `SfxApi#machineRuntime()` is now a default method to reduce breakage for external API implementations.
- YAML item definitions can declare `machine.profile`; legacy hardcoded profiles remain as a compatibility fallback.

The package has been statically checked for source structure and declared effect coverage, but it was not Gradle-compiled in this environment.
