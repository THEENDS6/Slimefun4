package cc.theends6.sfx.internal.electric;

import java.util.Locale;

record SfxElectricMachineUiSlot(
        int slot,
        String role,
        String behavior,
        String accepts,
        String action,
        String itemSource,
        Integer stateIndex,
        SfxElectricMachineUiItem item
) {
    SfxElectricMachineUiSlot {
        if (slot < 0) {
            throw new IllegalArgumentException("UI slot index must be non-negative.");
        }
        role = normalizeRequired(role, "role");
        behavior = normalizeRequired(behavior, "behavior");
        accepts = normalizeOptional(accepts);
        action = normalizeOptional(action);
        itemSource = normalizeOptional(itemSource);
        if (stateIndex != null && stateIndex < 0) {
            throw new IllegalArgumentException("UI slot state-index must be non-negative.");
        }
    }

    boolean isRole(String expected) {
        return expected != null && role.equals(normalizeOptional(expected));
    }

    boolean isBehavior(String expected) {
        return expected != null && behavior.equals(normalizeOptional(expected));
    }

    private static String normalizeRequired(String raw, String field) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("UI slot requires " + field + ".");
        }
        return normalized;
    }

    private static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
