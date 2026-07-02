package cc.theends6.sfx.internal.energy;

record SfxEnergyComponentUiSlot(
        int slot,
        String role,
        String behavior,
        String itemSource,
        Integer stateIndex,
        SfxEnergyComponentUiItem item
) {
    SfxEnergyComponentUiSlot {
        role = normalize(role);
        behavior = normalize(behavior);
        itemSource = blankToNull(itemSource);
    }

    boolean roleIs(String expected) {
        return role.equals(normalize(expected));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
