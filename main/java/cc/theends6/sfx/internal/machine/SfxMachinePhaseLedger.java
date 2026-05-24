package cc.theends6.sfx.internal.machine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;

/** Central phase ledger used to prove that old service paths are entering the framework surface. */
public final class SfxMachinePhaseLedger implements SfxMachinePhaseObserver {
    private final AtomicLong total = new AtomicLong();
    private final Map<String, AtomicLong> byMachine = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> byPhase = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> byDomain = new ConcurrentHashMap<>();

    @Override
    public void observe(String machineId, SfxMachinePhase phase, UUID instanceId, Location location, SfxMachineStatus status, Map<String, Object> attributes) {
        total.incrementAndGet();
        byMachine.computeIfAbsent(machineId == null ? "<unknown>" : machineId, ignored -> new AtomicLong()).incrementAndGet();
        byPhase.computeIfAbsent(phase == null ? "<unknown>" : phase.name(), ignored -> new AtomicLong()).incrementAndGet();
        Object domain = attributes == null ? null : attributes.get("framework.domain");
        if (domain == null) domain = attributes == null ? null : attributes.get("framework.pipeline.category");
        if (domain != null) {
            byDomain.computeIfAbsent(String.valueOf(domain), ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    public long total() {
        return total.get();
    }

    public Map<String, Long> byMachine() {
        return snapshot(byMachine);
    }

    public Map<String, Long> byPhase() {
        return snapshot(byPhase);
    }

    public Map<String, Long> byDomain() {
        return snapshot(byDomain);
    }

    private Map<String, Long> snapshot(Map<String, AtomicLong> source) {
        Map<String, Long> out = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.put(entry.getKey(), entry.getValue().get()));
        return Collections.unmodifiableMap(out);
    }
}
