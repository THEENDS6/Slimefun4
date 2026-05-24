package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineEffect;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
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
        int statusSlot = definition.menuStyle() == SfxElectricMachineMenuStyle.NONE ? -1 : 22;
        SfxMachineDefinition frameworkDefinition = new SfxMachineDefinition(definition.id(), definition.title(), SfxMachineCategory.ELECTRIC, ints(definition.inputSlots()), ints(definition.outputSlots()), statusSlot, 1);
        SfxMachineDefinition.Builder builder = cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles.apply(frameworkDefinition).toBuilder();
        if (definition.recipeProvider().hasWorldAction() || definition.recipeProvider().hasSpecialTick()) {
            builder.effect(SfxMachineEffect.marker("electric:legacy-special-operation", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
        } else {
            builder.effect(SfxMachineEffect.marker("electric:legacy-recipe-pipeline", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
            builder.effect(SfxMachineEffect.marker("electric:legacy-complete-recipe", SfxMachinePhase.ON_COMPLETE));
        }
        return builder.build();
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
        SfxMenuLayout.Builder builder = SfxMenuLayout.builder(definition.menuStyle().inventorySize()).slots(definition.inputSlots(), SfxSlotPolicy.input(inputPredicate)).slots(definition.outputSlots(), SfxSlotPolicy.output());
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER) builder.slot(22, SfxSlotPolicy.button());
        return builder.build();
    }
    private static List<Integer> ints(int[] values) {
        List<Integer> result = new ArrayList<>();
        if (values != null) for (int value : values) result.add(value);
        return result;
    }
}
