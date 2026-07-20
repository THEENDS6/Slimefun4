package cc.theends6.sfx.api.addon;

import cc.theends6.sfx.api.container.SfxVirtualFluidContainer;
import cc.theends6.sfx.api.block.SfxBlockStateService;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.container.SfxVirtualItemContainer;
import cc.theends6.sfx.api.display.SfxDisplayCategory;
import cc.theends6.sfx.api.display.SfxDisplayType;
import cc.theends6.sfx.api.display.SfxDisplaySessionService;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousMachineRuntime;
import cc.theends6.sfx.api.power.SfxPoweredItemRuntime;
import cc.theends6.sfx.api.power.SfxPoweredInventoryRuntime;
import java.util.Optional;
import org.bukkit.Location;


public interface SfxAddonRuntime {
    Optional<SfxBlockType<?>> blockType(String id);
    SfxBlockStateService blockStates();
    Optional<SfxDisplayCategory> displayCategory(String id);
    Optional<SfxDisplayType> displayType(String id);
    SfxDisplaySessionService displays();
    Optional<SfxVirtualItemContainer> itemContainer(String typeId, Location location);
    Optional<SfxVirtualFluidContainer> fluidContainer(String typeId, Location location);
    SfxContinuousMachineRuntime continuousMachines();
    SfxPoweredItemRuntime poweredItems();
    SfxPoweredInventoryRuntime inventoryPower();
}
