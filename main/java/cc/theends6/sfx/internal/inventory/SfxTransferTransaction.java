package cc.theends6.sfx.internal.inventory;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Small two-phase transfer helper.
 *
 * <p>Older callers inserted target-by-target and then tried to refund on partial failure. This
 * implementation first simulates all declared targets and only mutates inventories when the full
 * planned amount can be accepted. That gives machine output and cargo commits a hard transaction
 * boundary without changing their public call sites.</p>
 */
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
        if (targets == null || targets.isEmpty()) {
            return SfxTransferResult.success(planned, 0, planned);
        }
        int readyCapacity = simulateCapacity(template, planned, targets, smartFill);
        if (readyCapacity < planned) {
            return SfxTransferResult.failed(SfxErrorCode.NOT_READY, planned, 0, planned, Math.max(0, readyCapacity));
        }
        ItemStack unit = template.clone();
        unit.setAmount(1);
        int inserted = 0;
        for (Target target : targets) {
            if (target == null || target.endpoint() == null || target.amount() <= 0 || !target.endpoint().ready()) {
                continue;
            }
            ItemStack part = unit.clone();
            part.setAmount(target.amount());
            ItemStack remainder = target.singleSlot()
                    ? target.endpoint().insertSingleSlot(part, smartFill)
                    : target.endpoint().insert(part, smartFill);
            inserted += target.amount() - remainderAmount(remainder);
        }
        if (inserted != planned) {
            return SfxTransferResult.failed(SfxErrorCode.TRANSACTION_FAILED, planned, inserted, planned - inserted, readyCapacity);
        }
        return SfxTransferResult.success(planned, inserted, 0);
    }

    private int simulateCapacity(ItemStack template, int planned, List<Target> targets, boolean smartFill) {
        ItemStack probe = template.clone();
        probe.setAmount(Math.max(1, Math.min(template.getMaxStackSize(), planned)));
        int capacity = 0;
        for (Target target : targets) {
            if (target == null || target.endpoint() == null || target.amount() <= 0 || !target.endpoint().ready()) {
                continue;
            }
            int simulated = target.singleSlot()
                    ? target.endpoint().simulateInsertSingleSlot(probe, smartFill)
                    : target.endpoint().simulateInsert(probe, smartFill);
            capacity += Math.min(target.amount(), Math.max(0, simulated));
            if (capacity >= planned) {
                return capacity;
            }
        }
        return capacity;
    }

    private int remainderAmount(ItemStack remainder) {
        return remainder == null || remainder.getType() == Material.AIR ? 0 : Math.max(0, remainder.getAmount());
    }
}
