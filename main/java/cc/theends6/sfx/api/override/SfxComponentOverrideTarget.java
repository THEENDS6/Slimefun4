package cc.theends6.sfx.api.override;

import java.util.Objects;


public record SfxComponentOverrideTarget<T>(String id, int contractVersion, Class<T> contract) {
    public SfxComponentOverrideTarget {
        id = normalizeId(id);
        if (contractVersion < 1) {
            throw new IllegalArgumentException("Override contract version must be at least 1");
        }
        contract = Objects.requireNonNull(contract, "contract");
    }

    private static String normalizeId(String id) {
        if (id == null || !id.trim().matches("[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid component override target id: " + id);
        }
        return id.trim();
    }
}
