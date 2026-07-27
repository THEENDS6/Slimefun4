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
