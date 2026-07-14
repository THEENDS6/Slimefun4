package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineEffect;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SfxBasicMachineFrameworkBridge {
    private SfxBasicMachineFrameworkBridge() {}
    static List<SfxMachineDefinition> definitions(Collection<String> basicHandInputIds, Collection<String> enhancedFurnaceIds) {
        List<SfxMachineDefinition> definitions = new ArrayList<>();
        for (String id : basicHandInputIds) {
            definitions.add(simple(id));
        }
        for (String id : enhancedFurnaceIds) {
            definitions.add(furnace(id));
        }
        return List.copyOf(definitions);
    }
    static SfxMachineStatus furnaceStatus(boolean initialized, boolean canContinue, boolean outputBlocked, boolean hasFuel) {
        if (!initialized) return SfxMachineStatus.ERROR;
        if (outputBlocked) return SfxMachineStatus.OUTPUT_FULL;
        if (canContinue) return SfxMachineStatus.RUNNING;
        return hasFuel ? SfxMachineStatus.IDLE : SfxMachineStatus.NO_INPUT;
    }
    private static SfxMachineDefinition furnace(String id) {
        return cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles.apply(
                        new SfxMachineDefinition(id, id, SfxMachineCategory.BASIC, List.of(0, 1), List.of(2), -1, 1),
                        "vanilla_furnace")
                .toBuilder()
                .build();
    }
    private static SfxMachineDefinition simple(String id) {
        return cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles.apply(new SfxMachineDefinition(id, id, SfxMachineCategory.BASIC, List.of(), List.of(), -1, 1))
                .toBuilder()
                .effect(SfxMachineEffect.marker("basic:hand-input", SfxMachinePhase.ON_COMPLETE))
                .build();
    }
}
