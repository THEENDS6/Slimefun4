package cc.theends6.sfx.api.display;

import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;

public interface SfxDisplayRegistrar {
    SfxDefinitionRegistry<SfxDisplayCategory> categories();
    SfxDefinitionRegistry<SfxDisplayType> types();
}
