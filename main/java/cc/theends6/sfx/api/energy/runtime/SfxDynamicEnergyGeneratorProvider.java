package cc.theends6.sfx.api.energy.runtime;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.behavior.SfxEnergyGeneratorProvider;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import cc.theends6.sfx.api.machine.runtime.SfxMachineStatusKey;

public interface SfxDynamicEnergyGeneratorProvider extends SfxEnergyGeneratorProvider {
    int potentialGeneration(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location);

    int generate(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access);

    



    default int requestedConsumption(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        return 0;
    }

    
    default int acceptEnergy(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state,
                             Location location, int offered, SfxEnergyGeneratorAccess access) {
        return 0;
    }

    
    default boolean managesStoredEnergy() {
        return false;
    }

    default List<SfxElectricStack> dropsOnDestroy(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        return List.of();
    }

    default boolean excludeFromAutoPause(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        return false;
    }

    default Map<Integer, SfxMachineDisplayItem> displayItems(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        return Map.of();
    }

    default int[] shiftInputSlots(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, ItemStack stack) {
        return definition.ui().inputSlots();
    }

    default boolean acceptsInput(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, int logicalSlot, ItemStack stack) {
        return true;
    }

    default boolean handleMenuClick(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, int rawSlot, ClickType clickType) {
        return false;
    }

    
    default boolean handleMenuClick(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition,
                                    SfxEnergyNodeState state, Location location, Player player, int rawSlot,
                                    ClickType clickType, SfxEnergyGeneratorAccess access) {
        return handleMenuClick(plugin, items, definition, state, rawSlot, clickType);
    }

    default SfxMachineStatusKey status(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access) {
        return null;
    }

    default String workingStatusLoreKey() {
        return "energy.generator.active.lore";
    }

    default int remainingTicks(SfxEnergyNodeState state) {
        return Math.max(0, state.fuelTotalTenths() - state.fuelProgressTenths());
    }
}
