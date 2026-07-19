package cc.theends6.sfx.addons.research;

import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.override.SfxComponentOverrideTargets;

public final class SfxResearchExpansionAddon implements SfxAddon {
    @Override
    public String id() {
        return "sfx:research_expansion";
    }

    @Override
    public String name() {
        return "SFX Research Expansion";
    }

    @Override
    public void onRegister(SfxAddonContext context) {
        context.overrides().replace(
                SfxComponentOverrideTargets.RESEARCH_PAYMENT,
                new SfxResearchExpansionPayment(context.config(), context.api().localization())
        );
    }
}
