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
            definitions.put(definition.id(), definition);
            ensureDefaultProcessor(definition);
        }
    }

    public synchronized void registerDefinitionIfAbsent(SfxMachineDefinition definition) {
        if (definition != null && definition.id() != null && !definition.id().isBlank()) {
            definitions.putIfAbsent(definition.id(), definition);
            ensureDefaultProcessor(definition);
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

    public SfxMachineExecution beginTick(UUID instanceId, String machineId, Location location, SfxMachineTickContext context) {
        return new SfxMachineExecution(this, instanceId, machineId, location, context);
    }

    public SfxResult<SfxMachineStatus> executeProcessor(UUID instanceId, String machineId, Location location, SfxMachineTickContext tickContext, SfxMachineState state) {
        SfxMachineProcessor processor = processors.get(machineId);
        if (processor == null) {
            return SfxResult.fail(SfxErrorCode.UNSUPPORTED, "No machine processor registered for " + machineId);
        }
        SfxMachineRuntimeContext runtimeContext = new SfxMachineRuntimeContext(instanceId, location, tickContext == null ? 0L : tickContext.currentTick(), System.currentTimeMillis());
        return executeTick(instanceId, machineId, location, tickContext, () -> {
            SfxResult<SfxMachineStatus> result = processor.tick(runtimeContext, state);
            return result == null || !result.success() ? SfxMachineStatus.ERROR : result.valueOrNull();
        });
    }

    public SfxResult<SfxMachineStatus> executeTick(UUID instanceId, String machineId, Location location, SfxMachineTickContext context, Supplier<SfxMachineStatus> action) {
        try (SfxMachineExecution execution = beginTick(instanceId, machineId, location, context)) {
            try {
                SfxMachineStatus status = action == null ? SfxMachineStatus.IDLE : action.get();
                execution.status(status == null ? SfxMachineStatus.ERROR : status);
                return SfxResult.ok(execution.status());
            } catch (RuntimeException exception) {
                execution.status(SfxMachineStatus.ERROR);
                return SfxResult.fail(SfxErrorCode.INTERNAL_ERROR, "Machine tick failed for " + machineId, exception);
            }
        }
    }

    public void recordState(UUID instanceId, String machineId, Location location, SfxMachineStatus status) {
        finishTick(instanceId, machineId, location, new SfxMachineTickContext(0L, 1L, false), status == null ? SfxMachineStatus.IDLE : status, 0L);
    }

    void finishTick(UUID instanceId, String machineId, Location location, SfxMachineTickContext context, SfxMachineStatus status, long durationNanos) {
        if (instanceId == null || machineId == null) return;
        long tick = context == null ? 0L : context.currentTick();
        snapshots.put(instanceId, new SfxMachineRuntimeSnapshot(instanceId, machineId, status == null ? SfxMachineStatus.ERROR : status, tick, Math.max(0L, durationNanos), location == null ? null : location.clone()));
    }
    public Optional<SfxMachineRuntimeSnapshot> snapshot(UUID instanceId) { return Optional.ofNullable(snapshots.get(instanceId)); }
    public Collection<SfxMachineRuntimeSnapshot> snapshots() { return java.util.List.copyOf(snapshots.values()); }
    public void forget(UUID instanceId) { if (instanceId != null) snapshots.remove(instanceId); }
    public void clear() { snapshots.clear(); processors.clear(); synchronized (this) { definitions.clear(); } }
}
