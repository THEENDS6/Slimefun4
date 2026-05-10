package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemRegistry;

public final class LegacySfImportBootstrap {
    private LegacySfImportBootstrap() {
    }

    public static void register(SfxItemRegistry registry) {
        LegacySfCategoryBootstrap.register(registry);
        LegacySfItemBootstrapPart1.register(registry);
        LegacySfItemBootstrapPart2.register(registry);
        LegacySfItemBootstrapPart3.register(registry);
        LegacySfItemBootstrapPart4.register(registry);
    }
}
