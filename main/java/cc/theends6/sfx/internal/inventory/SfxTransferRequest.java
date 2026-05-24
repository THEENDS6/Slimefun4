package cc.theends6.sfx.internal.inventory;

import org.bukkit.inventory.ItemStack;

public record SfxTransferRequest(ItemStack template, int amount, boolean smartFill) {
    public SfxTransferRequest {
        amount = Math.max(0, amount);
        template = template == null ? null : template.clone();
    }
}
