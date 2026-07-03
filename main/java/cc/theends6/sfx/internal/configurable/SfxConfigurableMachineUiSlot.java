package cc.theends6.sfx.internal.configurable;

import java.util.Locale;

record SfxConfigurableMachineUiSlot(
        int slot,
        String role,
        String behavior,
        String accepts,
        String action,
        String itemSource,
        Integer stateIndex,
        SfxConfigurableMachineUiItem item
) {
    SfxConfigurableMachineUiSlot {
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

    boolean accepts(String expected) {
        return expected != null && accepts != null && accepts.equals(normalizeOptional(expected));
    }

    boolean actionIs(String expected) {
        return expected != null && action != null && action.equals(normalizeOptional(expected));
    }

    boolean itemSourceIs(String expected) {
        return expected != null && itemSource != null && itemSource.equals(normalizeOptional(expected));
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
