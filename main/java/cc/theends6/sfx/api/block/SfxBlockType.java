package cc.theends6.sfx.api.block;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Material;

public record SfxBlockType<S>(String id, Set<Material> anchorMaterials, SfxBlockStateSchema<S> stateSchema,
                              Supplier<S> initialState, SfxBlockLifecycle<S> lifecycle) {
    public SfxBlockType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Block type id must not be blank");
        anchorMaterials = Set.copyOf(Objects.requireNonNull(anchorMaterials, "anchorMaterials"));
        if (anchorMaterials.isEmpty() || anchorMaterials.stream().anyMatch(material -> material == null || !material.isBlock())) {
            throw new IllegalArgumentException("Block type must declare at least one block anchor material");
        }
        Objects.requireNonNull(stateSchema, "stateSchema");
        Objects.requireNonNull(initialState, "initialState");
        Objects.requireNonNull(lifecycle, "lifecycle");
    }
}
