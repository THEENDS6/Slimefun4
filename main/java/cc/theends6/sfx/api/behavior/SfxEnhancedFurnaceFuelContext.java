package cc.theends6.sfx.api.behavior;

import org.bukkit.inventory.ItemStack;

public record SfxEnhancedFurnaceFuelContext(
        ItemStack fuel,
        int baseBurnTicks,
        double fuelEfficiency,
        int processingSpeed
) {
}
