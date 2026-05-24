package cc.theends6.sfx.internal.inventory;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Small two-phase transfer helper.
 *
 * <p>The transaction now prepares per-target capacity, snapshots endpoints that expose rollback
 * state, then restores those snapshots if commit diverges from the prepared plan. Endpoints that do
 * not expose snapshots still benefit from the prepare phase, but Bukkit/electric endpoints can now
 * be restored after partial mutations.</p>
 */
public final class SfxTransferTransaction {
    public record Target(SfxStorageEndpoint endpoint, int amount, boolean singleSlot) {
        public Target(SfxStorageEndpoint endpoint, int amount) {
            this(endpoint, amount, false);
        }
    }

    private record EndpointSnapshot(SfxStorageEndpoint endpoint, Object snapshot) {
    }

    public SfxTransferResult commit(ItemStack template, int planned, List<Target> targets, boolean smartFill) {
        if (template == null || template.getType() == Material.AIR || planned <= 0) {
            return SfxTransferResult.failed(SfxErrorCode.INVALID_INPUT, planned, 0, 0, 0);
        }
        if (targets == null || targets.isEmpty()) {
            return SfxTransferResult.success(planned, 0, planned);
        }
        List<Target> preparedTargets = prepareTargets(template, planned, targets, smartFill);
        int prepared = preparedTargets.stream().mapToInt(Target::amount).sum();
        if (prepared < planned) {
            return SfxTransferResult.failed(SfxErrorCode.NOT_READY, planned, 0, planned, Math.max(0, prepared));
        }
        Map<SfxStorageKey, EndpointSnapshot> snapshots = snapshotTargets(preparedTargets);
        ItemStack unit = template.clone();
        unit.setAmount(1);
        int inserted = 0;
        for (Target target : preparedTargets) {
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
            restoreSnapshots(snapshots);
            return SfxTransferResult.failed(SfxErrorCode.TRANSACTION_FAILED, planned, 0, planned, prepared);
        }
        return SfxTransferResult.success(planned, inserted, 0);
    }

    private List<Target> prepareTargets(ItemStack template, int planned, List<Target> targets, boolean smartFill) {
        java.util.ArrayList<Target> prepared = new java.util.ArrayList<>();
        int remaining = planned;
        for (Target target : targets) {
            if (remaining <= 0) {
                break;
            }
            if (target == null || target.endpoint() == null || target.amount() <= 0 || !target.endpoint().ready()) {
                continue;
            }
            int requested = Math.min(remaining, target.amount());
            ItemStack probe = template.clone();
            probe.setAmount(Math.max(1, requested));
            int capacity = target.singleSlot()
                    ? target.endpoint().simulateInsertSingleSlot(probe, smartFill)
                    : target.endpoint().simulateInsert(probe, smartFill);
            int accepted = Math.min(requested, Math.max(0, capacity));
            if (accepted <= 0) {
                continue;
            }
            prepared.add(new Target(target.endpoint(), accepted, target.singleSlot()));
            remaining -= accepted;
        }
        return List.copyOf(prepared);
    }

    private Map<SfxStorageKey, EndpointSnapshot> snapshotTargets(List<Target> targets) {
        Map<SfxStorageKey, EndpointSnapshot> snapshots = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target == null || target.endpoint() == null) {
                continue;
            }
            SfxStorageKey key = target.endpoint().storageKey();
            if (snapshots.containsKey(key)) {
                continue;
            }
            Object snapshot = target.endpoint().snapshot();
            if (snapshot != null) {
                snapshots.put(key, new EndpointSnapshot(target.endpoint(), snapshot));
            }
        }
        return snapshots;
    }

    private void restoreSnapshots(Map<SfxStorageKey, EndpointSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        java.util.ArrayList<EndpointSnapshot> ordered = new java.util.ArrayList<>(snapshots.values());
        java.util.Collections.reverse(ordered);
        for (EndpointSnapshot snapshot : ordered) {
            try {
                snapshot.endpoint().restoreSnapshot(snapshot.snapshot());
            } catch (RuntimeException ignored) {
                // The caller receives TRANSACTION_FAILED; rollback best-effort must not throw again.
            }
        }
    }

    private int remainderAmount(ItemStack remainder) {
        return remainder == null || remainder.getType() == Material.AIR ? 0 : Math.max(0, remainder.getAmount());
    }
}
