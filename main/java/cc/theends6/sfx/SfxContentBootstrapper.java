package cc.theends6.sfx;

import cc.theends6.sfx.internal.SfxApiImpl;
import cc.theends6.sfx.internal.bootstrap.BaseContentBootstrap;
import cc.theends6.sfx.internal.bootstrap.SfxYamlContentLoader;
import cc.theends6.sfx.internal.core.SfxAuditReport;
import cc.theends6.sfx.internal.core.SfxAuditSink;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.recipe.DefaultSfxRecipeRegistry;
import cc.theends6.sfx.internal.recipe.SfxRecipeYamlLoader;
import cc.theends6.sfx.internal.research.SfxResearchRegistry;
import cc.theends6.sfx.internal.research.SfxResearchYamlLoader;

/** Owns content import/reload so the plugin entry no longer carries recipe/item bootstrapping. */
final class SfxContentBootstrapper {
    private SfxContentBootstrapper() {
    }

    static void bootstrap(SlimeFunXPlugin plugin, SfxApiImpl api, SfxResearchRegistry researchRegistry) {
        DefaultSfxItemRegistry itemRegistry = (DefaultSfxItemRegistry) api.itemRegistry();
        itemRegistry.clear();
        api.internalManualMachines().clear();

        BaseContentBootstrap.register(itemRegistry, api.internalManualMachines());
        SfxYamlContentLoader yamlContentLoader = new SfxYamlContentLoader(plugin, itemRegistry);
        yamlContentLoader.ensureDefaultFiles(plugin.syncBundledItemFiles());
        yamlContentLoader.registerAll();

        SfxRecipeYamlLoader recipeYamlLoader = new SfxRecipeYamlLoader(plugin);
        recipeYamlLoader.ensureDefaultFiles(plugin.syncBundledRecipeFiles());
        DefaultSfxRecipeRegistry recipeRegistry = new DefaultSfxRecipeRegistry();
        recipeYamlLoader.loadInto(recipeRegistry);
        DefaultSfxRecipeRegistry.AuditResult recipeAudit = recipeRegistry.apply(itemRegistry, api.internalManualMachines());
        logRecipeAudit(plugin, recipeAudit);
        BaseContentBootstrap.syncManualMachineGuideContent(itemRegistry, api.internalManualMachines());

        SfxResearchYamlLoader researchYamlLoader = new SfxResearchYamlLoader(plugin);
        researchYamlLoader.ensureDefaultFiles(plugin.syncBundledResearchFiles());
        researchYamlLoader.loadInto(researchRegistry);
    }

    private static void logRecipeAudit(SlimeFunXPlugin plugin, DefaultSfxRecipeRegistry.AuditResult audit) {
        if (audit == null) {
            return;
        }
        SfxAuditReport.Builder report = SfxAuditReport.builder("recipe-import").info(audit.summary());
        int shown = Math.min(20, audit.warnings().size());
        for (int i = 0; i < shown; i++) {
            report.warning(audit.warnings().get(i));
        }
        if (audit.warnings().size() > shown) {
            report.warning((audit.warnings().size() - shown) + " additional recipe import warnings suppressed");
        }
        SfxAuditSink.toLogger(plugin.getLogger()).publish(report.build());
    }
}
