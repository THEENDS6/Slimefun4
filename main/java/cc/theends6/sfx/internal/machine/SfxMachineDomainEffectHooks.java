package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.block.SfxBlockPlacerService;
import cc.theends6.sfx.internal.block.SfxHologramProjectorService;
import cc.theends6.sfx.internal.block.SfxInfusedHopperService;
import cc.theends6.sfx.internal.block.SfxSpawnerService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.machine.SfxIndustrialMinerService;
import java.util.List;









public final class SfxMachineDomainEffectHooks {
    private SfxMachineDomainEffectHooks() {}

    public static int register(
            SfxMachineRuntimeEngine runtime,
            SfxGpsService gps,
            SfxAndroidService android,
            SfxAncientAltarService altar,
            SfxEnergyService energy,
            SfxCargoService cargo,
            SfxSpawnerService spawner,
            SfxInfusedHopperService infusedHopper,
            SfxHologramProjectorService hologram,
            SfxBlockPlacerService placer,
            SfxIndustrialMinerService miner,
            SfxDecorationService decoration
    ) {
        if (runtime == null) return 0;
        int before = runtime.effectHookCount();
        registerGps(runtime, gps);
        registerAndroid(runtime, android);
        registerAltar(runtime, altar);
        registerEnergy(runtime, energy);
        registerCargo(runtime, cargo);
        registerSpawner(runtime, spawner);
        registerInfusedHopper(runtime, infusedHopper);
        registerHologram(runtime, hologram);
        registerBlockPlacer(runtime, placer);
        registerIndustrialMiner(runtime, miner);
        registerDecoration(runtime, decoration);
        return Math.max(0, runtime.effectHookCount() - before);
    }

    private static void registerGps(SfxMachineRuntimeEngine runtime, SfxGpsService gps) {
        for (String name : List.of("gps:check-signal-and-scan", "gps:resolve-signal")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> gps == null ? domain(ctx, "gps", null) : gps.frameworkEffect(name, ctx));
        }
    }

    private static void registerAndroid(SfxMachineRuntimeEngine runtime, SfxAndroidService android) {
        for (String name : List.of("android-interface:sync-storage", "android:execute-script-step", "android:relocate-anchor")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> android == null ? domain(ctx, "android", null) : android.frameworkEffect(name, ctx));
        }
    }

    private static void registerAltar(SfxMachineRuntimeEngine runtime, SfxAncientAltarService altar) {
        for (String name : List.of("altar:validate-structure", "altar:play-ritual")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> altar == null ? domain(ctx, "altar", null) : altar.frameworkEffect(name, ctx));
        }
    }

    private static void registerEnergy(SfxMachineRuntimeEngine runtime, SfxEnergyService energy) {
        for (String name : List.of("energy:inspect-grid", "generator:check-world-condition", "generator:consume-fuel", "generator:emit-energy", "charge:write-item-energy")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> energy == null ? domain(ctx, "energy", null) : energy.frameworkEffect(name, ctx));
        }
    }

    private static void registerCargo(SfxMachineRuntimeEngine runtime, SfxCargoService cargo) {
        for (String name : List.of("cargo:resolve-endpoints", "cargo:commit-transfer")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> cargo == null ? domain(ctx, "cargo", null) : cargo.frameworkEffect(name, ctx));
        }
    }

    private static void registerSpawner(SfxMachineRuntimeEngine runtime, SfxSpawnerService spawner) {
        for (String name : List.of("spawner:restore-entity-type", "spawner:drop-fractured-item", "spawner:repair-to-reinforced")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> spawner == null ? domain(ctx, "spawner", null) : spawner.frameworkEffect(name, ctx));
        }
    }

    private static void registerInfusedHopper(SfxMachineRuntimeEngine runtime, SfxInfusedHopperService infusedHopper) {
        for (String name : List.of("hopper:scan-items", "hopper:teleport-item", "hopper:emit-particles")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> infusedHopper == null ? domain(ctx, "hopper", null) : infusedHopper.frameworkEffect(name, ctx));
        }
    }

    private static void registerHologram(SfxMachineRuntimeEngine runtime, SfxHologramProjectorService hologram) {
        for (String name : List.of("hologram:open-editor", "hologram:update-text", "hologram:sync-display")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> hologram == null ? domain(ctx, "hologram", null) : hologram.frameworkEffect(name, ctx));
        }
    }

    private static void registerBlockPlacer(SfxMachineRuntimeEngine runtime, SfxBlockPlacerService placer) {
        for (String name : List.of("placer:resolve-target", "placer:consume-input", "placer:place-block", "placer:rollback-on-fail")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> placer == null ? domain(ctx, "placer", null) : placer.frameworkEffect(name, ctx));
        }
    }

    private static void registerIndustrialMiner(SfxMachineRuntimeEngine runtime, SfxIndustrialMinerService miner) {
        for (String name : List.of("miner:validate-structure", "miner:consume-fuel", "miner:animate-piston", "miner:extract-ore", "miner:commit-output", "miner:stop-on-error")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> miner == null ? domain(ctx, "miner", null) : miner.frameworkEffect(name, ctx));
        }
    }

    private static void registerDecoration(SfxMachineRuntimeEngine runtime, SfxDecorationService decoration) {
        for (String name : List.of("decoration:animate-state", "decoration:sync-visual", "decoration:drop-plugin-block")) {
            runtime.registerEffectHookIfAbsent(name, ctx -> decoration == null ? domain(ctx, "decoration", null) : decoration.frameworkEffect(name, ctx));
        }
    }

    private static SfxMachinePhaseResult domain(SfxMachinePhaseContext context, String domain, Object service) {
        if (context != null) {
            context.put(domain + ".framework.effect.handled", Boolean.FALSE);
            context.put(domain + ".framework.effect.missing-service", Boolean.TRUE);
        }
        return SfxMachinePhaseResult.failed("domain service is not available for effect domain: " + domain);
    }
}
