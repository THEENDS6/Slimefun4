package cc.theends6.sfx.api.energy.runtime;

public record SfxEnergyComponentUiSlot(
        int slot,
        String role,
        String behavior,
        String itemSource,
        Integer stateIndex,
        SfxEnergyComponentUiItem item
) {
    public SfxEnergyComponentUiSlot {
        role = normalize(role);
        behavior = normalize(behavior);
        itemSource = blankToNull(itemSource);
    }

    public boolean roleIs(String expected) {
        return role.equals(normalize(expected));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
