package cc.theends6.sfx.api.behavior;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.electric.SfxElectricRecipeProvider;
import org.bukkit.plugin.java.JavaPlugin;

@FunctionalInterface
public interface SfxElectricSpecialProviderFactory {
    SfxElectricRecipeProvider create(JavaPlugin plugin, SfxItems items, SfxBlockDataService blockData);
}
