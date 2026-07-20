package cc.theends6.sfx.api.block;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;


public record SfxBlockStateView<S>(UUID instanceId, String blockTypeId, Location location,
                                   int schemaVersion, SfxBlockLifecycleState lifecycleState, S state) {
    public SfxBlockStateView {
        Objects.requireNonNull(instanceId, "instanceId");
        if (blockTypeId == null || blockTypeId.isBlank()) throw new IllegalArgumentException("blockTypeId must not be blank");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        location = location.clone();
    }

    @Override public Location location() { return location.clone(); }
}
