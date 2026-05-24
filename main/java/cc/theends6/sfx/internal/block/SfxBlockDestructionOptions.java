package cc.theends6.sfx.internal.block;

/** Destruction flags passed from Bukkit events into the framework lifecycle router. */
public record SfxBlockDestructionOptions(boolean containmentPickup) {
    public static final SfxBlockDestructionOptions NONE = new SfxBlockDestructionOptions(false);
}
