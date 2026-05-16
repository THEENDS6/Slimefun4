package cc.theends6.sfx.internal.cargo;

public enum SfxCargoFilterMode {
    WHITELIST,
    BLACKLIST;

    public SfxCargoFilterMode toggle() {
        return this == WHITELIST ? BLACKLIST : WHITELIST;
    }
}
