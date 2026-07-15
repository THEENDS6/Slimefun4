package cc.theends6.sfx.addons.content;

import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.addons.content.runtime.SfxContentExpansionElectricProviders;
import cc.theends6.sfx.addons.content.runtime.SfxOxidizingGeneratorProvider;

public final class SfxContentExpansionAddon implements SfxAddon {
    public static final String ID = "sfx:content_expansion";
    public static final String FEATURE = "sfx:content_expansion";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "SFX Content Expansion";
    }

    @Override
    public void onLoad(SfxAddonContext context) {
        context.features().registerBoolean(FEATURE, "addons.content-expansion.enabled", true);
        SfxContentExpansionElectricProviders.register(context);
        context.behaviors().registerEnergyGeneratorProvider("sfx:oxidizing_generator",
                hook -> new SfxOxidizingGeneratorProvider());
    }
}
