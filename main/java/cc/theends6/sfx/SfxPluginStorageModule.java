package cc.theends6.sfx;

import cc.theends6.sfx.internal.SfxApiImpl;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SqliteSfxBlockDataRepository;
import cc.theends6.sfx.internal.config.SfxLegacyItemBehaviorConfig;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SqliteSfxPlayerDataRepository;
import cc.theends6.sfx.internal.research.SfxResearchRegistry;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.runtime.PaperSfxRuntime;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.feature.DefaultSfxFeatureRegistry;




final class SfxPluginStorageModule {
    private SfxPluginStorageModule() {
    }

    static void initialize(SlimeFunXPlugin plugin) {
        var runtime = new PaperSfxRuntime(plugin);
        try {
            plugin.playerDataService = new SfxPlayerDataService(plugin, runtime, new SqliteSfxPlayerDataRepository(plugin, plugin.playerDataFile()));
            plugin.playerDataService.initialize();
            plugin.playerDataService.preloadOnlinePlayers();
            plugin.blockDataService = new SfxBlockDataService(plugin, runtime, new SqliteSfxBlockDataRepository(plugin, plugin.blockDataFile()));
            plugin.blockDataService.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SFX persistent storage", exception);
        }

        plugin.researchRegistry = new SfxResearchRegistry();
        plugin.researchService = new SfxResearchService(plugin.researchRegistry, plugin.playerDataService);
        plugin.localization = new SfxLocalization(plugin);
        plugin.legacyItemBehaviorConfig = new SfxLegacyItemBehaviorConfig(plugin);
        plugin.legacyItemBehaviorConfig.ensureDefaultFile();
        plugin.legacyItemBehaviorConfig.reload();
        plugin.featureRegistry = new DefaultSfxFeatureRegistry(plugin);
        plugin.behaviorRegistry = new DefaultSfxBehaviorRegistry();
        plugin.api = SfxApiImpl.bootstrap(plugin, plugin.localization, plugin.playerDataService, plugin.researchService, plugin.featureRegistry, plugin.behaviorRegistry);
    }
}
