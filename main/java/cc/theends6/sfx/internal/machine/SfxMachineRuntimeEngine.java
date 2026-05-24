package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import cc.theends6.sfx.internal.core.SfxResult;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Location;









public final class SfxMachineRuntimeEngine {
    private final Map<String, SfxMachineDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, SfxMachineProcessor> processors = new ConcurrentHashMap<>();
    private final Map<UUID, SfxMachineRuntimeSnapshot> snapshots = new ConcurrentHashMap<>();

    public synchronized void registerDefinition(SfxMachineDefinition definition) {
        if (definition != null && definition.id() != null && !definition.id().isBlank()) {
            definition = SfxMachineSpecialProfiles.apply(definition);
            definitions.put(definition.id(), definition);
            ensureDefaultProcessor(definition);
        }
    }

    public synchronized void registerDefinitionIfAbsent(SfxMachineDefinition definition) {
        if (definition != null && definition.id() != null && !definition.id().isBlank()) {
            definition = SfxMachineSpecialProfiles.apply(definition);
            definitions.putIfAbsent(definition.id(), definition);
            ensureDefaultProcessor(definitions.get(definition.id()));
        }
    }

    public synchronized void enrichDefinition(String machineId, java.util.function.UnaryOperator<SfxMachineDefinition> enricher) {
        if (machineId == null || enricher == null) return;
        SfxMachineDefinition existing = definitions.get(machineId);
        if (existing == null) return;
        SfxMachineDefinition enriched = enricher.apply(existing);
        if (enriched != null) {
            definitions.put(machineId, enriched);
            ensureDefaultProcessor(enriched);
        }
    }

    public synchronized void registerDefinitions(Collection<SfxMachineDefinition> definitions) {
        if (definitions != null) definitions.forEach(this::registerDefinition);
    }

    public synchronized void registerDefinitionsIfAbsent(Collection<SfxMachineDefinition> definitions) {
        if (definitions != null) definitions.forEach(this::registerDefinitionIfAbsent);
    }

    public void registerProcessor(SfxMachineProcessor processor) {
        if (processor != null && processor.machineId() != null && !processor.machineId().isBlank()) {
            processors.put(processor.machineId(), processor);
        }
    }

    private void ensureDefaultProcessor(SfxMachineDefinition definition) {
        if (definition != null && definition.id() != null && !definition.id().isBlank()) {
            processors.putIfAbsent(definition.id(), new SfxDefaultMachineProcessor(definition.id()));
        }
    }

    public synchronized void ensureDefaultProcessors() {
        for (SfxMachineDefinition definition : definitions.values()) {
            ensureDefaultProcessor(definition);
        }
    }

    public Optional<SfxMachineProcessor> processor(String machineId) {
        return Optional.ofNullable(processors.get(machineId));
    }

    public Collection<SfxMachineProcessor> processors() {
        return java.util.List.copyOf(processors.values());
    }

    public synchronized Optional<SfxMachineDefinition> definition(String id) { return Optional.ofNullable(definitions.get(id)); }
    public synchronized Collection<SfxMachineDefinition> definitions() { return java.util.List.copyOf(definitions.values()); }
    public synchronized int definitionCount() { return definitions.size(); }

    public synchronized long definitionCount(SfxMachineCategory category) {
        return definitions.values().stream().filter(definition -> definition.category() == category).count();
    }

    public synchronized long effectCount() {
        return definitions.values().stream().mapToLong(definition -> definition.effects().size()).sum();
    }

    public synchronized long policyRefCount() {
        return definitions.values().stream().mapToLong(definition -> definition.policyRefs().size()).sum();
    }

    public synchronized long capabilityDeclarationCount() {
        return definitions.values().stream().mapToLong(definition -> definition.capabilities().size()).sum();
    }

    public SfxMachineExecution beginTick(UUID instanceId, String machineId, Location location, SfxMachineTickContext context) {
        SfxMachineExecution execution = new SfxMachineExecution(this, instanceId, machineId, location, context);
        runPhase(machineId, SfxMachinePhase.BEFORE_TICK, instanceId, location, context, null, SfxMachineStatus.IDLE);
        return execution;
    }

    public SfxResult<SfxMachineStatus> executeProcessor(UUID instanceId, String machineId, Location location, SfxMachineTickContext tickContext, SfxMachineState state) {
        SfxMachineProcessor processor = processors.get(machineId);
        if (processor == null) {
            return SfxResult.fail(SfxErrorCode.UNSUPPORTED, "No machine processor registered for " + machineId);
        }
        SfxMachineRuntimeContext runtimeContext = new SfxMachineRuntimeContext(instanceId, location, tickContext == null ? 0L : tickContext.currentTick(), System.currentTimeMillis());
        return executeTick(instanceId, machineId, location, tickContext, () -> {
            SfxMachinePhaseResult before = runPhase(machineId, SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instanceId, location, tickContext, state, SfxMachineStatus.IDLE);
            if (before.stopsPipeline()) return before.status() == null ? SfxMachineStatus.BLOCKED : before.status();
            SfxResult<SfxMachineStatus> result = processor.tick(runtimeContext, state);
            SfxMachineStatus status = result == null || !result.success() ? SfxMachineStatus.ERROR : result.valueOrNull();
            runPhase(machineId, status == SfxMachineStatus.ERROR ? SfxMachinePhase.ON_ERROR : SfxMachinePhase.AFTER_OPERATION_RESOLVE, instanceId, location, tickContext, state, status);
            return status;
        });
    }

    public SfxResult<SfxMachineStatus> executeTick(UUID instanceId, String machineId, Location location, SfxMachineTickContext context, Supplier<SfxMachineStatus> action) {
        try (SfxMachineExecution execution = beginTick(instanceId, machineId, location, context)) {
            try {
                SfxMachineStatus status = action == null ? SfxMachineStatus.IDLE : action.get();
                status = status == null ? SfxMachineStatus.ERROR : status;
                execution.status(status);
                return SfxResult.ok(execution.status());
            } catch (RuntimeException exception) {
                execution.status(SfxMachineStatus.ERROR);
                runPhase(machineId, SfxMachinePhase.ON_ERROR, instanceId, location, context, null, SfxMachineStatus.ERROR);
                return SfxResult.fail(SfxErrorCode.INTERNAL_ERROR, "Machine tick failed for " + machineId, exception);
            }
        }
    }


    public SfxMachinePhaseResult runStatusPhase(String machineId, UUID instanceId, Location location, SfxMachineTickContext context, SfxMachineState state, SfxMachineStatus status) {
        SfxMachinePhase phase = switch (status == null ? SfxMachineStatus.ERROR : status) {
            case RUNNING -> SfxMachinePhase.AFTER_PROGRESS;
            case OUTPUT_FULL -> SfxMachinePhase.ON_OUTPUT_BLOCKED;
            case IDLE, NO_INPUT, NO_POWER, PAUSED, BLOCKED -> SfxMachinePhase.ON_IDLE;
            case ERROR -> SfxMachinePhase.ON_ERROR;
        };
        SfxMachinePhaseResult result = runPhase(machineId, phase, instanceId, location, context, state, status);
        if ((status == SfxMachineStatus.RUNNING || status == SfxMachineStatus.IDLE) && !result.stopsPipeline()) {
            runPhase(machineId, SfxMachinePhase.ON_COMPLETE, instanceId, location, context, state, status);
        }
        return result;
    }

    public SfxMachinePhaseResult runPhase(String machineId, SfxMachinePhase phase, UUID instanceId, Location location, SfxMachineTickContext context, SfxMachineState state, SfxMachineStatus currentStatus) {
        SfxMachineDefinition definition = definition(machineId).orElse(null);
        if (definition == null || definition.effects().isEmpty()) return SfxMachinePhaseResult.cont();
        SfxMachinePhaseContext phaseContext = new SfxMachinePhaseContext(definition, phase, instanceId, location == null ? null : location.clone(), context, state, currentStatus);
        for (SfxMachineEffect effect : definition.effects()) {
            if (effect.phase() != phase) continue;
            try {
                SfxMachinePhaseResult result = effect.hook().apply(phaseContext);
                if (result != null && result.stopsPipeline()) return result;
            } catch (RuntimeException ignored) {
                return SfxMachinePhaseResult.failed("machine effect failed: " + effect.name());
            }
        }
        return SfxMachinePhaseResult.cont();
    }

    public void recordState(UUID instanceId, String machineId, Location location, SfxMachineStatus status) {
        SfxMachineTickContext context = new SfxMachineTickContext(0L, 1L, false);
        runPhase(machineId, SfxMachinePhase.ON_PLACE, instanceId, location, context, null, status == null ? SfxMachineStatus.IDLE : status);
        finishTick(instanceId, machineId, location, context, status == null ? SfxMachineStatus.IDLE : status, 0L);
    }

    void finishTick(UUID instanceId, String machineId, Location location, SfxMachineTickContext context, SfxMachineStatus status, long durationNanos) {
        if (instanceId == null || machineId == null) return;
        runPhase(machineId, SfxMachinePhase.AFTER_TICK, instanceId, location, context, null, status == null ? SfxMachineStatus.ERROR : status);
        long tick = context == null ? 0L : context.currentTick();
        snapshots.put(instanceId, new SfxMachineRuntimeSnapshot(instanceId, machineId, status == null ? SfxMachineStatus.ERROR : status, tick, Math.max(0L, durationNanos), location == null ? null : location.clone()));
    }
    public Optional<SfxMachineRuntimeSnapshot> snapshot(UUID instanceId) { return Optional.ofNullable(snapshots.get(instanceId)); }
    public Collection<SfxMachineRuntimeSnapshot> snapshots() { return java.util.List.copyOf(snapshots.values()); }
    public void forget(UUID instanceId) { if (instanceId != null) snapshots.remove(instanceId); }
    public void clear() { snapshots.clear(); processors.clear(); synchronized (this) { definitions.clear(); } }
}
