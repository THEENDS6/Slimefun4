package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import java.util.List;









public final class SfxMachineDomainEffectHooks {
    private SfxMachineDomainEffectHooks() {}

    public static int register(
            SfxMachineRuntimeEngine runtime,
            SfxGpsService gps,
            SfxAndroidService android,
            SfxAncientAltarService altar,
            SfxEnergyService energy,
            SfxCargoService cargo
    ) {
        if (runtime == null) return 0;
        int before = runtime.effectHookCount();
        registerGps(runtime, gps);
        registerAndroid(runtime, android);
        registerAltar(runtime, altar);
        registerEnergy(runtime, energy);
        registerCargo(runtime, cargo);
        return Math.max(0, runtime.effectHookCount() - before);
    }

    private static void registerGps(SfxMachineRuntimeEngine runtime, SfxGpsService gps) {
        for (String name : List.of("gps:check-signal-and-scan", "gps:resolve-signal")) {
            runtime.registerEffectHook(name, ctx -> gps == null ? domain(ctx, "gps", null) : gps.frameworkEffect(name, ctx));
        }
    }

    private static void registerAndroid(SfxMachineRuntimeEngine runtime, SfxAndroidService android) {
        for (String name : List.of("android-interface:sync-storage", "android:execute-script-step", "android:relocate-anchor")) {
            runtime.registerEffectHook(name, ctx -> android == null ? domain(ctx, "android", null) : android.frameworkEffect(name, ctx));
        }
    }

    private static void registerAltar(SfxMachineRuntimeEngine runtime, SfxAncientAltarService altar) {
        for (String name : List.of("altar:validate-structure", "altar:play-ritual")) {
            runtime.registerEffectHook(name, ctx -> altar == null ? domain(ctx, "altar", null) : altar.frameworkEffect(name, ctx));
        }
    }

    private static void registerEnergy(SfxMachineRuntimeEngine runtime, SfxEnergyService energy) {
        for (String name : List.of("energy:inspect-grid", "generator:check-world-condition", "generator:consume-fuel", "generator:emit-energy", "charge:write-item-energy")) {
            runtime.registerEffectHook(name, ctx -> energy == null ? domain(ctx, "energy", null) : energy.frameworkEffect(name, ctx));
        }
    }

    private static void registerCargo(SfxMachineRuntimeEngine runtime, SfxCargoService cargo) {
        for (String name : List.of("cargo:resolve-endpoints", "cargo:commit-transfer")) {
            runtime.registerEffectHook(name, ctx -> cargo == null ? domain(ctx, "cargo", null) : cargo.frameworkEffect(name, ctx));
        }
    }

    private static SfxMachinePhaseResult domain(SfxMachinePhaseContext context, String domain, Object service) {
        if (context == null) return SfxMachinePhaseResult.cont();
        context.put(domain + ".framework.effect.handled", Boolean.TRUE);
        if (service != null) {
            context.put(domain + ".framework.service", service.getClass().getName());
        }
        return SfxMachinePhaseResult.cont();
    }
}
