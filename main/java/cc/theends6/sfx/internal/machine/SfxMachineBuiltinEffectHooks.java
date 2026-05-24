package cc.theends6.sfx.internal.machine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;









public final class SfxMachineBuiltinEffectHooks {
    private SfxMachineBuiltinEffectHooks() {}

    private static final Set<String> KNOWN_EFFECTS = Set.of("framework:audit-tick");

    
    public static int registerDefaults(SfxMachineRuntimeEngine runtime) {
        if (runtime == null) return 0;
        int before = runtime.effectHookCount();
        for (String effectName : KNOWN_EFFECTS) {
            runtime.registerEffectHookIfAbsent(effectName, context -> apply(effectName, context));
        }
        return Math.max(0, runtime.effectHookCount() - before);
    }

    private static SfxMachinePhaseResult apply(String effectName, SfxMachinePhaseContext context) {
        if (context == null) return SfxMachinePhaseResult.cont();
        mark(context, effectName);
        SfxMachinePhaseResult dispatched = dispatchAttached(effectName, context);
        if (dispatched != null) {
            context.put("framework.effect." + effectName + ".handled", Boolean.TRUE);
            return dispatched;
        }
        if ("framework:audit-tick".equals(effectName)) {
            return auditTick(context);
        }
        context.put("framework.effect." + effectName + ".handled", Boolean.FALSE);
        return SfxMachinePhaseResult.failed("no built-in implementation for effect: " + effectName);
    }

    private static SfxMachinePhaseResult recipeResolve(SfxMachinePhaseContext context, String source) {
        context.put("framework.operation.kind", "recipe");
        context.put("framework.operation.resolved.by", source);
        if (context.definition().inputProvider() != null) {
            context.put("framework.input.provider", context.definition().inputProvider().description());
        }
        if (context.definition().outputProvider() != null) {
            context.put("framework.output.provider", context.definition().outputProvider().description());
        }
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult inventoryReserveOutput(SfxMachinePhaseContext context) {
        context.put("framework.inventory.output.reserved", Boolean.TRUE);
        context.put("framework.inventory.output.provider", context.definition().outputProvider().description());
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult inventoryCommitOutput(SfxMachinePhaseContext context, String source) {
        context.put("framework.inventory.output.committed", Boolean.TRUE);
        context.put("framework.inventory.output.committed.by", source);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult furnaceIntercept(SfxMachinePhaseContext context) {
        context.put("framework.furnace.events.intercepted", Boolean.TRUE);
        context.put("framework.inventory.provider", "vanilla-furnace");
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult furnaceSync(SfxMachinePhaseContext context, String source) {
        context.put("framework.furnace.virtual-state.synced", Boolean.TRUE);
        context.put("framework.furnace.synced.by", source);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult handInput(SfxMachinePhaseContext context, String source) {
        context.put("framework.hand-input.active", Boolean.TRUE);
        context.put("framework.hand-input.by", source);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult worldDrop(SfxMachinePhaseContext context) {
        context.put("framework.world.drop-result.requested", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult brewValidate(SfxMachinePhaseContext context) {
        context.put("framework.brew.snapshot.required", Boolean.TRUE);
        context.put("framework.brew.validation.phase", context.phase().name());
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult brewRefund(SfxMachinePhaseContext context) {
        context.put("framework.brew.refund-on-interrupt", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult brewCommit(SfxMachinePhaseContext context) {
        context.put("framework.brew.multi-bottle-output", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult craftingSimulate(SfxMachinePhaseContext context) {
        context.put("framework.crafting.simulated", Boolean.TRUE);
        context.put("framework.inventory.provider", context.definition().inputProvider().description());
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult craftingCommit(SfxMachinePhaseContext context) {
        context.put("framework.crafting.transaction.commit", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult gpsResolve(SfxMachinePhaseContext context, String source) {
        context.put("framework.gps.resolve", Boolean.TRUE);
        context.put("framework.gps.resolved.by", source);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult geoExtract(SfxMachinePhaseContext context) {
        context.put("framework.geo.extract-resource", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult visualUpdate(SfxMachinePhaseContext context) {
        context.put("framework.visual.update", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult fluidLocate(SfxMachinePhaseContext context) {
        context.put("framework.fluid.locate-source", Boolean.TRUE);
        if (context.location() == null) {
            return SfxMachinePhaseResult.blocked(SfxMachineStatus.BLOCKED, "fluid operation missing location");
        }
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult fluidCommit(SfxMachinePhaseContext context) {
        context.put("framework.fluid.remove-source-and-update", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult metaValidate(SfxMachinePhaseContext context) {
        context.put("framework.meta.validate-input", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult metaApply(SfxMachinePhaseContext context) {
        context.put("framework.meta.apply-transform", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult reactorCoolant(SfxMachinePhaseContext context) {
        context.put("framework.reactor.consume-coolant", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult energyEmit(SfxMachinePhaseContext context, String source) {
        context.put("framework.energy.emit", Boolean.TRUE);
        context.put("framework.energy.emit.by", source);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult reactorMeltdown(SfxMachinePhaseContext context) {
        context.put("framework.reactor.meltdown-transaction", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult assemblerValidate(SfxMachinePhaseContext context) {
        context.put("framework.assembler.validate-offset", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult assemblerSpawn(SfxMachinePhaseContext context) {
        context.put("framework.assembler.spawn-entity", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult proxyResolve(SfxMachinePhaseContext context) {
        context.put("framework.proxy.resolve-host", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult androidInterfaceSync(SfxMachinePhaseContext context) {
        context.put("framework.android-interface.sync-storage", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult androidScriptStep(SfxMachinePhaseContext context) {
        context.put("framework.android.execute-script-step", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult androidRelocate(SfxMachinePhaseContext context) {
        context.put("framework.android.relocate-anchor", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult altarValidate(SfxMachinePhaseContext context) {
        context.put("framework.altar.validate-structure", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult altarRitual(SfxMachinePhaseContext context) {
        context.put("framework.altar.play-ritual", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult cargoResolve(SfxMachinePhaseContext context) {
        context.put("framework.cargo.resolve-endpoints", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult cargoCommit(SfxMachinePhaseContext context) {
        context.put("framework.cargo.commit-transfer", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult energyInspect(SfxMachinePhaseContext context) {
        context.put("framework.energy.inspect-grid", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult generatorWorldCondition(SfxMachinePhaseContext context) {
        context.put("framework.generator.check-world-condition", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult generatorConsumeFuel(SfxMachinePhaseContext context) {
        context.put("framework.generator.consume-fuel", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult chargeItem(SfxMachinePhaseContext context) {
        context.put("framework.charge.write-item-energy", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }


    private static SfxMachinePhaseResult domainMark(SfxMachinePhaseContext context, String domain, String effectName) {
        context.put("framework.domain", domain);
        context.put("framework.domain.effect", effectName);
        context.put(domain + ".framework.default-hook", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult auditTick(SfxMachinePhaseContext context) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("id", context.definition().id());
        audit.put("category", context.definition().category().name());
        audit.put("phase", context.phase().name());
        audit.put("capabilities", context.definition().capabilities().size());
        audit.put("policies", context.definition().policyRefs().size());
        audit.put("effects", context.definition().effects().size());
        context.put("framework.audit.last", audit);
        return SfxMachinePhaseResult.cont();
    }

    private static SfxMachinePhaseResult dispatchAttached(String effectName, SfxMachinePhaseContext context) {
        SfxMachineEffectDispatcher dispatcher = context.attachment("framework.effect.dispatcher", SfxMachineEffectDispatcher.class).orElse(null);
        if (dispatcher == null) {
            dispatcher = context.attachment("framework.effect.dispatcher." + effectName, SfxMachineEffectDispatcher.class).orElse(null);
        }
        if (dispatcher == null) {
            SfxMachineHook hook = context.attachment("framework.effect.hook." + effectName, SfxMachineHook.class).orElse(null);
            if (hook != null) {
                return hook.apply(context);
            }
            return null;
        }
        return dispatcher.apply(effectName, context);
    }

    private static void mark(SfxMachinePhaseContext context, String effectName) {
        context.put("framework.effect." + effectName, Boolean.TRUE);
        context.put("framework.last-effect", effectName);
        Object countObject = context.attachment("framework.effect-count");
        int count = countObject instanceof Number number ? number.intValue() : 0;
        context.put("framework.effect-count", count + 1);
    }
}
