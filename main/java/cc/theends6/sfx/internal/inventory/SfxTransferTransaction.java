package cc.theends6.sfx.internal.inventory;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SfxTransferTransaction {
    public record Target(SfxStorageEndpoint endpoint, int amount, boolean singleSlot) {
        public Target(SfxStorageEndpoint endpoint, int amount) {
            this(endpoint, amount, false);
        }
    }

    public SfxTransferResult commit(ItemStack template, int planned, List<Target> targets, boolean smartFill) {
        if (template == null || template.getType() == Material.AIR || planned <= 0) {
            return SfxTransferResult.failed(SfxErrorCode.INVALID_INPUT, planned, 0, 0, 0);
        }
        ItemStack unit = template.clone();
        unit.setAmount(1);
        int inserted = 0;
        if (targets == null || targets.isEmpty()) {
            return SfxTransferResult.success(planned, 0, planned);
        }
        for (Target target : targets) {
            if (target == null || target.endpoint() == null || target.amount() <= 0) {
                continue;
            }
            if (!target.endpoint().ready()) {
                continue;
            }
            ItemStack part = unit.clone();
            part.setAmount(target.amount());
            ItemStack remainder = target.singleSlot()
                    ? target.endpoint().insertSingleSlot(part, smartFill)
                    : target.endpoint().insert(part, smartFill);
            inserted += target.amount() - (remainder == null || remainder.getType() == Material.AIR ? 0 : remainder.getAmount());
        }
        return SfxTransferResult.success(planned, inserted, planned - inserted);
    }
}
