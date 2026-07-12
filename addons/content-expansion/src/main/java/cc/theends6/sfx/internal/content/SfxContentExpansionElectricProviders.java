package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.api.addon.SfxAddonContext;

public final class SfxContentExpansionElectricProviders {
    private SfxContentExpansionElectricProviders() {
    }

    public static void register(SfxAddonContext context) {
        context.behaviors().registerElectricMachineProvider("sfx:waxing_machine",
                hook -> new SfxWaxingMachineProvider());
        context.behaviors().registerElectricMachineProvider("sfx:cutting_machine",
                hook -> new SfxCuttingMachineProvider(hook.plugin()));
    }
}
