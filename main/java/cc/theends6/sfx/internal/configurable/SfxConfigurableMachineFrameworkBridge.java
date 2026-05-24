package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.ui.SfxMenuLayout;
import cc.theends6.sfx.internal.ui.SfxSlotPolicy;
import java.util.ArrayList;
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
        List<Integer> inputs = switch (definition.kind()) { case ASSEMBLER -> List.of(19, 28, 25, 34); case REACTOR -> List.of(19, 28, 37, 25, 34, 43); case ACCESS_PORT -> List.of(); };
        List<Integer> outputs = switch (definition.kind()) { case ASSEMBLER -> List.of(); case REACTOR -> List.of(40); case ACCESS_PORT -> List.of(); };
        int status = switch (definition.kind()) { case ASSEMBLER -> 22; case REACTOR -> 49; case ACCESS_PORT -> -1; };
        return new SfxMachineDefinition(definition.id(), definition.id(), SfxMachineCategory.CONFIGURABLE, inputs, outputs, status, 1);
    }
    static SfxMachineStatus statusFor(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        if (definition == null || state == null) return SfxMachineStatus.ERROR;
        if (!state.enabled()) return SfxMachineStatus.PAUSED;
        return state.isActive() ? SfxMachineStatus.RUNNING : SfxMachineStatus.IDLE;
    }
    static SfxMenuLayout layout(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineHolder.PanelType panelType, Predicate<ItemStack> inputPredicate) {
        SfxMenuLayout.Builder builder = SfxMenuLayout.builder(54);
        switch (panelType) {
            case ASSEMBLER -> builder.slot(13, SfxSlotPolicy.button()).slot(31, SfxSlotPolicy.button()).slot(22, SfxSlotPolicy.status()).slots(new int[]{19, 28, 25, 34}, SfxSlotPolicy.input(inputPredicate));
            case REACTOR -> builder.slot(4, SfxSlotPolicy.button()).slot(22, SfxSlotPolicy.status()).slot(49, SfxSlotPolicy.status()).slots(new int[]{19, 28, 37, 25, 34, 43}, SfxSlotPolicy.input(inputPredicate)).slots(new int[]{40}, SfxSlotPolicy.output());
            case ACCESS_PORT -> {}
        }
        return builder.build();
    }
}
