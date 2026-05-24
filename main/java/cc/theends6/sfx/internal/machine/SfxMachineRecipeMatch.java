package cc.theends6.sfx.internal.machine;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public record SfxMachineRecipeMatch(String recipeId, List<ItemStack> inputs, List<ItemStack> outputs, int ticks) {
    public SfxMachineRecipeMatch {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        ticks = Math.max(1, ticks);
    }
}
