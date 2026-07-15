package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.UUID;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import cc.theends6.sfx.internal.ui.SfxItemProgressBar;

final class SfxElectricMachineMenuRenderer {
    private final SfxItems items;
    private final JavaPlugin plugin;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxElectricMachineMenuRenderer(JavaPlugin plugin, SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.plugin = plugin;
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        fillInventoryFrame(definition, inventory);
        if (definition.ui().statusSlot() >= 0) {
            inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, recipe, status));
        }
        int[] inputSlots = definition.inputSlots();
        for (int slot = 0; slot < inputSlots.length; slot++) {
            inventory.setItem(inputSlots[slot], state.input(slot) == null ? null : state.input(slot).toItemStack(items));
        }
        int[] outputSlots = definition.outputSlots();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            inventory.setItem(outputSlots[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
        }
        for (Map.Entry<Integer, SfxMachineDisplayItem> entry : definition.recipeProvider().displayItems(plugin, items, definition, state).entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < inventory.getSize()) {
                inventory.setItem(entry.getKey(), displayItem(entry.getValue()));
            }
        }
    }

    private ItemStack displayItem(SfxMachineDisplayItem display) {
        ItemStack stack = new ItemStack(display.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(localization.component(display.nameKey(), display.placeholders()));
            meta.lore(display.loreKeys().stream().map(key -> localization.component(key, display.placeholders())).toList());
            meta.setEnchantmentGlintOverride(display.glint());
            if (display.headTextureHash() != null) {
                HeadTextures.apply(meta, display.headTextureHash());
            }
            stack.setItemMeta(meta);
        }
        if (display.capacity() > 0) {
            SfxItemProgressBar.apply(stack, display.progress(), display.capacity());
        }
        return stack;
    }

    private void fillInventoryFrame(SfxElectricMachineDefinition definition, Inventory inventory) {
        SfxElectricMachineUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }
}
