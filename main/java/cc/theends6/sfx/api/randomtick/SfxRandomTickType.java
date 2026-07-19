package cc.theends6.sfx.api.randomtick;

import java.util.Objects;
import java.util.function.Consumer;

public record SfxRandomTickType<S>(String id, String blockTypeId, boolean affectedByGameRule,
                                   double weight, int perTickBudget, Consumer<SfxRandomTickContext<S>> handler) {
    public SfxRandomTickType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Random tick id must not be blank");
        if (blockTypeId == null || blockTypeId.isBlank()) throw new IllegalArgumentException("blockTypeId must not be blank");
        if (!Double.isFinite(weight) || weight <= 0.0D) throw new IllegalArgumentException("weight must be positive");
        if (perTickBudget < 1) throw new IllegalArgumentException("perTickBudget must be positive");
        Objects.requireNonNull(handler, "handler");
    }
}
