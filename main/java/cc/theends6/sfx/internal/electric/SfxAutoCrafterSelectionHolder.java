package cc.theends6.sfx.internal.electric;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

record SfxAutoCrafterSelectionHolder(UUID instanceId, List<SfxAutoCrafterRecipeChoice> choices, int index) implements InventoryHolder {
    SfxAutoCrafterSelectionHolder {
        choices = List.copyOf(choices == null ? List.of() : choices);
        index = choices.isEmpty() ? 0 : Math.max(0, Math.min(index, choices.size() - 1));
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
