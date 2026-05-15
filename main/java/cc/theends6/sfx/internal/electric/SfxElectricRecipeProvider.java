package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public interface SfxElectricRecipeProvider {
    List<SfxElectricRecipe> recipes();

    default SfxElectricRecipeMatch findDynamicMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        return null;
    }

    default SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return null;
    }

    default SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return null;
    }

    default boolean hasSpecialTick() {
        return false;
    }

    default boolean hasWorldAction() {
        return false;
    }

    default int specialTickIntervalTicks() {
        return 1;
    }

    default int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return 0;
    }

    default List<SfxElectricStack> dropsOnDestroy(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return List.of();
    }
}

