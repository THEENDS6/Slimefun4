package cc.theends6.sfx.api.behavior;

public record SfxUtilityRules(
        boolean autoBrewerEnabled,
        boolean enhancedMultimeterEnabled
) {
    public static SfxUtilityRules classicDefaults() {
        return new SfxUtilityRules(false, false);
    }
}
