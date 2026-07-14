package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.ui.SfxMenuLayout;
import cc.theends6.sfx.internal.ui.SfxSlotPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

final class SfxElectricMachineFrameworkBridge {
    private SfxElectricMachineFrameworkBridge() {}
    static Collection<SfxMachineDefinition> definitions(SfxElectricMachineRegistry registry) {
        List<SfxMachineDefinition> result = new ArrayList<>();
        if (registry != null) for (SfxElectricMachineDefinition definition : registry.definitions()) result.add(toFrameworkDefinition(definition));
        return result;
    }
    static SfxMachineDefinition toFrameworkDefinition(SfxElectricMachineDefinition definition) {
        int statusSlot = definition.ui().statusSlot();
        SfxMachineDefinition frameworkDefinition = new SfxMachineDefinition(definition.id(), definition.id(), SfxMachineCategory.ELECTRIC, ints(definition.inputSlots()), ints(definition.outputSlots()), statusSlot, 1);
        String profile = definition.recipeProvider().hasWorldAction() ? "electric_world_action" : null;
        return cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles.apply(frameworkDefinition, profile);
    }
    static SfxMachineStatus status(SfxElectricMachineRenderStatus status) {
        if (status == null) return SfxMachineStatus.ERROR;
        return switch (status) {
            case WORKING -> SfxMachineStatus.RUNNING;
            case NO_POWER -> SfxMachineStatus.NO_POWER;
            case NO_RECIPE, NO_INPUT, NO_BLAZE_FUEL, NO_BREWING_INGREDIENT, NO_POTION, NO_TARGET, CHUNK_NOT_SCANNED, NO_GEO_RESOURCE -> SfxMachineStatus.NO_INPUT;
            case OUTPUT_FULL, BLOCKED_OUTPUT -> SfxMachineStatus.OUTPUT_FULL;
            case PAUSED -> SfxMachineStatus.PAUSED;
            case OVERLAPPING_AREA -> SfxMachineStatus.BLOCKED;
            case IDLE -> SfxMachineStatus.IDLE;
        };
    }
    static SfxMenuLayout layout(SfxElectricMachineDefinition definition, Predicate<ItemStack> inputPredicate) {
        SfxMenuLayout.Builder builder = SfxMenuLayout.builder(definition.ui().inventorySize());
        for (SfxElectricMachineUiSlot slot : definition.ui().slots().values()) {
            builder.slot(slot.slot(), policy(slot, inputPredicate));
        }
        return builder.build();
    }

    private static SfxSlotPolicy policy(SfxElectricMachineUiSlot slot, Predicate<ItemStack> inputPredicate) {
        if (slot == null) {
            return SfxSlotPolicy.locked();
        }
        return switch (slot.role()) {
            case "input" -> SfxSlotPolicy.input(inputPredicate);
            case "output" -> SfxSlotPolicy.output();
            case "button" -> SfxSlotPolicy.button();
            case "status", "fuel" -> SfxSlotPolicy.status();
            case "decoration", "preview", "empty", "locked" -> SfxSlotPolicy.locked();
            case "filter", "ghost" -> SfxSlotPolicy.filterGhost();
            default -> SfxSlotPolicy.locked();
        };
    }
    private static List<Integer> ints(int[] values) {
        List<Integer> result = new ArrayList<>();
        if (values != null) for (int value : values) result.add(value);
        return result;
    }
}
