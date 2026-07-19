package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.addons.basic.electric.SfxBasicExpansionElectricProviders;
import cc.theends6.sfx.api.addon.SfxAddonContext;

final class BasicExpansionElectricModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) { SfxBasicExpansionElectricProviders.register(context); }
    @Override public void disable() { SfxBasicExpansionElectricProviders.disable(); }
}
