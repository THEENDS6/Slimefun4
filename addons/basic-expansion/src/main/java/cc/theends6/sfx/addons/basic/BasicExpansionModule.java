package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

interface BasicExpansionModule {
    void register(SfxAddonContext context);
    default void disable() {}
}
