package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.behavior.SfxEnergyGeneratorProvider;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public interface SfxDynamicEnergyGeneratorProvider extends SfxEnergyGeneratorProvider {
    int potentialGeneration(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location);

    int generate(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access);

    default List<SfxElectricStack> dropsOnDestroy(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        return List.of();
    }

    default boolean excludeFromAutoPause(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        return false;
    }

    default Map<Integer, SfxMachineDisplayItem> displayItems(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        return Map.of();
    }
}
