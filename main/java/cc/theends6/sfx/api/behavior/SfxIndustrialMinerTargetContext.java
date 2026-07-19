package cc.theends6.sfx.api.behavior;

import java.util.List;
import java.util.Objects;
import org.bukkit.block.Block;


public record SfxIndustrialMinerTargetContext(boolean advanced, Block ore,
                                              List<Block> adjacentCandidates) {
    public SfxIndustrialMinerTargetContext {
        Objects.requireNonNull(ore, "ore");
        adjacentCandidates = List.copyOf(adjacentCandidates);
    }
}
