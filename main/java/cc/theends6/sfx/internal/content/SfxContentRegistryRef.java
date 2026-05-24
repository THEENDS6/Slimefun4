package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;

public record SfxContentRegistryRef(SfxItemRegistry items, SfxManualMachineRegistry manualMachines) {
}
