package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.ui.SfxMenuLayout;
import cc.theends6.sfx.internal.ui.SfxSlotPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

final class SfxConfigurableMachineFrameworkBridge {
    private SfxConfigurableMachineFrameworkBridge() {}
    static Collection<SfxMachineDefinition> definitions(Map<String, SfxConfigurableMachineDefinition> definitions) {
        List<SfxMachineDefinition> result = new ArrayList<>();
        if (definitions != null) for (SfxConfigurableMachineDefinition definition : definitions.values()) result.add(toFrameworkDefinition(definition));
        return result;
    }
    static SfxMachineDefinition toFrameworkDefinition(SfxConfigurableMachineDefinition definition) {
        SfxConfigurableMachineUiPanel panel = compiledPanel(definition, defaultPanelType(definition.kind()));
        List<Integer> inputs = panel == null ? List.of(19, 28, 25, 34) : ints(panel.inputSlots());
        List<Integer> outputs = panel == null ? List.of() : ints(panel.outputSlots());
        int status = panel == null ? 22 : panel.firstRoleSlot("status");
        SfxMachineDefinition frameworkDefinition = cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles.apply(new SfxMachineDefinition(definition.id(), definition.id(), SfxMachineCategory.CONFIGURABLE, inputs, outputs, status, 1));
        return frameworkDefinition.toBuilder()
                .build();
    }
    static SfxMachineStatus statusFor(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        if (definition == null || state == null) return SfxMachineStatus.ERROR;
        if (!state.enabled()) return SfxMachineStatus.PAUSED;
        return state.isActive() ? SfxMachineStatus.RUNNING : SfxMachineStatus.IDLE;
    }
    static SfxMenuLayout layout(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineHolder.PanelType panelType, Predicate<ItemStack> inputPredicate) {
        SfxConfigurableMachineUiPanel panel = compiledPanel(definition, panelType);
        if (panel == null) {
            return SfxMenuLayout.builder(54)
                    .slot(13, SfxSlotPolicy.button())
                    .slot(31, SfxSlotPolicy.button())
                    .slot(22, SfxSlotPolicy.status())
                    .slots(new int[] {19, 28, 25, 34}, SfxSlotPolicy.input(inputPredicate))
                    .build();
        }
        SfxMenuLayout.Builder builder = SfxMenuLayout.builder(panel.inventorySize());
        for (SfxConfigurableMachineUiSlot slot : panel.slots().values()) {
            builder.slot(slot.slot(), policyFor(slot, inputPredicate));
        }
        return builder.build();
    }

    private static SfxConfigurableMachineHolder.PanelType defaultPanelType(SfxConfigurableMachineKind kind) {
        return switch (kind) {
            case REACTOR -> SfxConfigurableMachineHolder.PanelType.REACTOR;
            case ACCESS_PORT -> SfxConfigurableMachineHolder.PanelType.ACCESS_PORT;
            case ASSEMBLER -> SfxConfigurableMachineHolder.PanelType.ASSEMBLER;
        };
    }

    private static SfxConfigurableMachineUiPanel compiledPanel(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineHolder.PanelType panelType) {
        if (definition == null || definition.ui() == null || panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return null;
        }
        SfxConfigurableMachineUiPanel panel = definition.ui().panel(panelType);
        if (panel == null) {
            throw new IllegalStateException("Missing compiled configurable UI panel " + panelType + " for " + definition.id());
        }
        return panel;
    }

    private static List<Integer> ints(int[] values) {
        return values == null ? List.of() : Arrays.stream(values).boxed().toList();
    }

    private static SfxSlotPolicy policyFor(SfxConfigurableMachineUiSlot slot, Predicate<ItemStack> inputPredicate) {
        if (slot.isRole("input")) {
            return SfxSlotPolicy.input(inputPredicate);
        }
        if (slot.isRole("output")) {
            return SfxSlotPolicy.output();
        }
        if (slot.isRole("button")) {
            return SfxSlotPolicy.button();
        }
        if (slot.isRole("status")) {
            return SfxSlotPolicy.status();
        }
        if (slot.isRole("decoration") || slot.isRole("preview")) {
            return SfxSlotPolicy.decoration();
        }
        return SfxSlotPolicy.locked();
    }
}
