package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineEffect;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import java.util.List;

final class SfxBasicMachineFrameworkBridge {
    private SfxBasicMachineFrameworkBridge() {}
    static List<SfxMachineDefinition> definitions() {
        return List.of(simple("sf:composter"), simple("sf:crucible"), simple("sf:output_chest"), simple("sf:ignition_chamber"), furnace("sf:enhanced_furnace"), furnace("sf:enhanced_furnace_2"), furnace("sf:enhanced_furnace_3"), furnace("sf:enhanced_furnace_4"), furnace("sf:enhanced_furnace_5"), furnace("sf:enhanced_furnace_6"), furnace("sf:enhanced_furnace_7"), furnace("sf:enhanced_furnace_8"), furnace("sf:enhanced_furnace_9"), furnace("sf:enhanced_furnace_10"), furnace("sf:enhanced_furnace_11"), furnace("sf:reinforced_furnace"), furnace("sf:carbonado_edged_furnace"));
    }
    static SfxMachineStatus furnaceStatus(boolean initialized, boolean canContinue, boolean outputBlocked, boolean hasFuel) {
        if (!initialized) return SfxMachineStatus.ERROR;
        if (outputBlocked) return SfxMachineStatus.OUTPUT_FULL;
        if (canContinue) return SfxMachineStatus.RUNNING;
        return hasFuel ? SfxMachineStatus.IDLE : SfxMachineStatus.NO_INPUT;
    }
    private static SfxMachineDefinition furnace(String id) {
        return cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles.apply(new SfxMachineDefinition(id, id, SfxMachineCategory.BASIC, List.of(0, 1), List.of(2), -1, 1))
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
