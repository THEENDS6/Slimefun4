package cc.theends6.sfx.api.power;

import java.util.List;
import java.util.Objects;
import java.util.UUID;


public record SfxPowerInventorySnapshot(UUID playerId, long serverTick,
                                        List<SfxPowerPort> sources, List<SfxPowerPort> consumers) {
    public SfxPowerInventorySnapshot {
        Objects.requireNonNull(playerId, "playerId");
        serverTick = Math.max(0L, serverTick);
        sources = sources == null ? List.of() : List.copyOf(sources);
        consumers = consumers == null ? List.of() : List.copyOf(consumers);
    }
}
