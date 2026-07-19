package cc.theends6.sfx.api.behavior;

import org.bukkit.block.Block;


@FunctionalInterface
public interface SfxIndustrialMinerTargetPolicy {
    Block selectTarget(SfxIndustrialMinerTargetContext context, Block currentTarget);
}
