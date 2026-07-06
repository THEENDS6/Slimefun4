package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRuleContext;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRuleProvider;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRules;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxEnergyBalance {
    private SfxEnergyBalance() {
    }

    public static SfxEnergyBalanceRules rules(JavaPlugin plugin) {
        SfxEnergyBalanceRules rules = SfxEnergyBalanceRules.classicDefaults();
        SfxApi api = plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
        if (api == null) {
            return rules;
        }
        SfxEnergyBalanceRuleContext context = new SfxEnergyBalanceRuleContext();
        for (SfxEnergyBalanceRuleProvider provider : api.behaviors().energyBalanceRuleProviders()) {
            SfxEnergyBalanceRules provided = provider.apply(context, rules);
            if (provided != null) {
                rules = provided;
            }
        }
        return rules;
    }

}
