package cc.theends6.sfx.api.behavior;

public record SfxCargoFilterRules(boolean ghostFilterInterfaceEnabled) {
    public static SfxCargoFilterRules classicDefaults() {
        return new SfxCargoFilterRules(false);
    }
}
