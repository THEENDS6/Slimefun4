package cc.theends6.sfx.api.power;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface SfxPoweredItemRuntime {
    Optional<SfxPoweredItem> definition(String id);
    Optional<SfxPoweredItemState> charge(String id, SfxPoweredItemState state, double offeredEnergy);
    Optional<SfxPoweredItemUseResult> use(String id, SfxPoweredItemState state, boolean actionSucceeded);
    Optional<SfxPoweredItemState> readState(ItemStack item);
    boolean writeState(ItemStack item, SfxPoweredItemState state);
}
