package cc.theends6.sfx.internal.block;


public record SfxBlockDestructionOptions(boolean containmentPickup) {
    public static final SfxBlockDestructionOptions NONE = new SfxBlockDestructionOptions(false);
}
