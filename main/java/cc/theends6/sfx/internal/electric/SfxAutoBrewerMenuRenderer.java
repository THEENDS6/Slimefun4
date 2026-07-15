package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.behavior.SfxAutoBrewerBehaviorProvider;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxItemProgressBar;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxAutoBrewerMenuRenderer {
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxAutoBrewerBehaviorProvider behavior;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxAutoBrewerMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles, SfxAutoBrewerBehaviorProvider behavior) {
        this.items = items;
        this.localization = localization;
        this.behavior = behavior;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillFrame(definition, inventory);
        if (behavior == null) {
            return;
        }
        inventory.setItem(behavior.blazeSlot(), state.input(0) == null ? null : state.input(0).toItemStack(items));
        inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, null, status));
        inventory.setItem(behavior.ingredientSlot(), state.input(1) == null ? null : state.input(1).toItemStack(items));
        inventory.setItem(behavior.fuelDisplaySlot(), fuelDisplay(definition, state));
        int[] potionSlots = behavior.potionSlots();
        for (int index = 0; index < potionSlots.length; index++) {
            inventory.setItem(potionSlots[index], state.input(2 + index) == null ? null : state.input(2 + index).toItemStack(items));
        }
    }

    private void fillFrame(SfxElectricMachineDefinition definition, Inventory inventory) {
        inventory.clear();
        SfxElectricMachineUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }

    private ItemStack fuelDisplay(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        int stored = Math.max(0, Math.min(behavior.maxBlazeFuelTicks(), state.specialData()));
        int recipeFuel = state.hasProgress() && state.activeBaseTicks() > 0 ? Math.max(1, state.activeBaseTicks()) : 0;
        int powders = recipeFuel <= 0 ? 0 : (recipeFuel + behavior.blazeFuelTicks() - 1) / behavior.blazeFuelTicks();
        ItemStack stack = definition.ui().requiredItem(stored <= 0 ? "auto-brewer.fuel.empty" : "auto-brewer.fuel.stored")
                .toItemStack(localization, Map.of(
                "fuel", stored,
                "capacity", behavior.maxBlazeFuelTicks(),
                "recipe_fuel", recipeFuel,
                "powders", powders));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (stored > 0 && stored >= behavior.maxBlazeFuelTicks()) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            stack.setItemMeta(meta);
            if (stored > 0) {
                SfxItemProgressBar.apply(stack, stored, behavior.maxBlazeFuelTicks());
            }
        }
        return stack;
    }


}
