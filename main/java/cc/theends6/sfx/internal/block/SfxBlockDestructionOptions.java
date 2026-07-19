package cc.theends6.sfx.internal.block;


public record SfxBlockDestructionOptions(boolean containmentPickup, SfxBlockDestructionCause cause,
                                         org.bukkit.entity.Player actor) {
    public static final SfxBlockDestructionOptions NONE = new SfxBlockDestructionOptions(false,
            SfxBlockDestructionCause.UNKNOWN, null);

    public SfxBlockDestructionOptions(boolean containmentPickup) {
        this(containmentPickup, SfxBlockDestructionCause.UNKNOWN, null);
    }
}
