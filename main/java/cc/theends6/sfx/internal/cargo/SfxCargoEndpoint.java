package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.inventory.SfxInventoryAccessState;
import cc.theends6.sfx.internal.inventory.SfxStorageEndpoint;
import cc.theends6.sfx.internal.inventory.SfxStorageKey;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainer;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService.PlannedStack;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

final class SfxCargoEndpoint implements SfxStorageEndpoint {
    private final SfxVirtualContainerService virtualContainers;
    private final SfxElectricMachineService electricMachines;
    private final SfxVirtualContainer container;
    private final UUID electricMachineId;
    private final boolean electricInputTarget;
    private final boolean trash;

    private SfxCargoEndpoint(
            SfxVirtualContainerService virtualContainers,
            SfxElectricMachineService electricMachines,
            SfxVirtualContainer container,
            UUID electricMachineId,
            boolean electricInputTarget,
            boolean trash
    ) {
        this.virtualContainers = virtualContainers;
        this.electricMachines = electricMachines;
        this.container = container;
        this.electricMachineId = electricMachineId;
        this.electricInputTarget = electricInputTarget;
        this.trash = trash;
    }

    static SfxCargoEndpoint container(SfxVirtualContainerService virtualContainers, SfxElectricMachineService electricMachines, SfxVirtualContainer container) {
        return new SfxCargoEndpoint(virtualContainers, electricMachines, container, null, false, false);
    }

    static SfxCargoEndpoint trash(SfxVirtualContainerService virtualContainers, SfxElectricMachineService electricMachines) {
        return new SfxCargoEndpoint(virtualContainers, electricMachines, null, null, false, true);
    }

    static SfxCargoEndpoint electric(SfxVirtualContainerService virtualContainers, SfxElectricMachineService electricMachines, UUID instanceId, boolean insertIntoInputs) {
        return new SfxCargoEndpoint(virtualContainers, electricMachines, null, instanceId, insertIntoInputs, false);
    }

    boolean canExtract() {
        return container != null || (electricMachineId != null && !electricInputTarget);
    }

    boolean sameStorage(SfxCargoEndpoint other) {
        if (other == null) {
            return false;
        }
        if (container != null && other.container != null) {
            return container == other.container;
        }
        return electricMachineId != null && electricMachineId.equals(other.electricMachineId);
    }

    @Override
    public SfxStorageKey storageKey() {
        if (trash) {
            return new SfxStorageKey("trash");
        }
        if (container != null) {
            return new SfxStorageKey("container:" + container.key());
        }
        if (electricMachineId != null) {
            return new SfxStorageKey("electric:" + electricMachineId + ":" + electricInputTarget);
        }
        return new SfxStorageKey("unknown");
    }

    @Override
    public SfxInventoryAccessState accessState() {
        return trash || container != null || electricMachineId != null
                ? SfxInventoryAccessState.READY
                : SfxInventoryAccessState.UNAVAILABLE;
    }

    PlannedStack planFirst(Predicate<ItemStack> filter, int maxAmount) {
        if (container != null) {
            return virtualContainers.planFirst(container, filter, maxAmount);
        }
        if (electricMachineId != null && !electricInputTarget) {
            return electricMachines.planFirstCargoOutput(electricMachineId, filter, maxAmount);
        }
        return new PlannedStack(null, List.of());
    }

    List<PlannedStack> planBatch(Predicate<ItemStack> filter, int maxItems, int maxDistinctTypes, boolean allowMultipleSlots) {
        if (container != null) {
            return virtualContainers.planBatch(container, filter, maxItems, maxDistinctTypes, allowMultipleSlots);
        }
        if (electricMachineId != null && !electricInputTarget) {
            return electricMachines.planCargoOutputBatch(electricMachineId, filter, maxItems, maxDistinctTypes, allowMultipleSlots);
        }
        return List.of();
    }

    boolean canRemovePlanned(List<SfxVirtualContainerService.SlotTake> takes) {
        if (container != null) {
            return virtualContainers.canRemovePlanned(container, takes);
        }
        if (electricMachineId != null && !electricInputTarget) {
            return electricMachines.canRemoveCargoOutput(electricMachineId, takes);
        }
        return false;
    }

    boolean removePlanned(List<SfxVirtualContainerService.SlotTake> takes) {
        if (container != null) {
            return virtualContainers.removePlanned(container, takes);
        }
        if (electricMachineId != null && !electricInputTarget) {
            return electricMachines.removeCargoOutput(electricMachineId, takes);
        }
        return false;
    }

    @Override
    public int simulateInsert(ItemStack stack, boolean smartFill) {
        return capacityFor(stack, smartFill);
    }

    @Override
    public int simulateInsertSingleSlot(ItemStack stack, boolean smartFill) {
        return capacityForSingleSlot(stack, smartFill);
    }

    int capacityFor(ItemStack stack, boolean smartFill) {
        if (trash) {
            return stack == null ? 0 : stack.getAmount();
        }
        if (container != null) {
            return virtualContainers.capacityFor(container, stack, smartFill);
        }
        if (electricMachineId != null) {
            return electricInputTarget
                    ? electricMachines.cargoInputCapacity(electricMachineId, stack, smartFill)
                    : electricMachines.cargoOutputCapacity(electricMachineId, stack, smartFill);
        }
        return 0;
    }

    int capacityForSingleSlot(ItemStack stack, boolean smartFill) {
        if (trash) {
            return stack == null ? 0 : stack.getAmount();
        }
        if (container != null) {
            return virtualContainers.capacityForSingleSlot(container, stack, smartFill);
        }
        if (electricMachineId != null) {
            return electricInputTarget
                    ? electricMachines.cargoInputCapacitySingleSlot(electricMachineId, stack, smartFill)
                    : electricMachines.cargoOutputCapacitySingleSlot(electricMachineId, stack, smartFill);
        }
        return 0;
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean smartFill) {
        if (trash || isEmpty(stack)) {
            return null;
        }
        if (container != null) {
            return virtualContainers.insert(container, stack, smartFill);
        }
        if (electricMachineId != null) {
            return electricInputTarget
                    ? electricMachines.insertCargoInput(electricMachineId, stack, smartFill)
                    : electricMachines.insertCargoOutput(electricMachineId, stack, smartFill);
        }
        return stack;
    }

    @Override
    public ItemStack insertSingleSlot(ItemStack stack, boolean smartFill) {
        if (trash || isEmpty(stack)) {
            return null;
        }
        if (container != null) {
            return virtualContainers.insertSingleSlot(container, stack, smartFill);
        }
        if (electricMachineId != null) {
            return electricInputTarget
                    ? electricMachines.insertCargoInputSingleSlot(electricMachineId, stack, smartFill)
                    : electricMachines.insertCargoOutputSingleSlot(electricMachineId, stack, smartFill);
        }
        return stack;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
