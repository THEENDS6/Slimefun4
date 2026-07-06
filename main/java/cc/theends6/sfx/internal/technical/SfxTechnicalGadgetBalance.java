package cc.theends6.sfx.internal.technical;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRuleContext;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRuleProvider;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRules;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxTechnicalGadgetBalance {
    private static final int DEFAULT_CLASSIC_JETPACK_INTERVAL_TICKS = 3;
    private static final int DEFAULT_CLASSIC_JETBOOTS_INTERVAL_TICKS = 10;

    private SfxTechnicalGadgetBalance() {
    }

    public static SfxTechnicalGadgetRules rules(JavaPlugin plugin) {
        SfxTechnicalGadgetRuleContext context = new SfxTechnicalGadgetRuleContext(
                positiveConfigInt(plugin, "technical-gadgets.classic.jetpack-interval-ticks", DEFAULT_CLASSIC_JETPACK_INTERVAL_TICKS),
                positiveConfigInt(plugin, "technical-gadgets.classic.jetboots-interval-ticks", DEFAULT_CLASSIC_JETBOOTS_INTERVAL_TICKS),
                doubleConfig(plugin, "technical-gadgets.rechargeable.base-multiplier", 20.0D),
                doubleConfig(plugin, "technical-gadgets.classic.charging-bench.energy-loss", 0.50D)
        );
        SfxTechnicalGadgetRules rules = new SfxTechnicalGadgetRules(
                false,
                Math.max(1.0D, context.configuredRechargeableBaseMultiplier()),
                clamp01(context.configuredClassicChargingBenchEnergyLoss())
        );
        SfxApi api = plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
        if (api == null) {
            return rules;
        }
        for (SfxTechnicalGadgetRuleProvider provider : api.behaviors().technicalGadgetRuleProviders()) {
            SfxTechnicalGadgetRules provided = provider.apply(context, rules);
            if (provided != null) {
                rules = provided;
            }
        }
        return rules;
    }

    public static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static int positiveConfigInt(JavaPlugin plugin, String path, int fallback) {
        return plugin == null ? Math.max(1, fallback) : Math.max(1, plugin.getConfig().getInt(path, fallback));
    }

    private static double doubleConfig(JavaPlugin plugin, String path, double fallback) {
        return plugin == null ? fallback : plugin.getConfig().getDouble(path, fallback);
    }
}
