package cc.theends6.sfx.internal.behavior;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxUtilityRuleContext;
import cc.theends6.sfx.api.behavior.SfxUtilityRuleProvider;
import cc.theends6.sfx.api.behavior.SfxUtilityRules;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxUtilityRulesResolver {
    private SfxUtilityRulesResolver() {
    }

    public static SfxUtilityRules rules(JavaPlugin plugin) {
        SfxUtilityRules rules = SfxUtilityRules.classicDefaults();
        SfxApi api = plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
        if (api == null) {
            return rules;
        }
        SfxUtilityRuleContext context = new SfxUtilityRuleContext();
        for (SfxUtilityRuleProvider provider : api.behaviors().utilityRuleProviders()) {
            SfxUtilityRules provided = provider.apply(context, rules);
            if (provided != null) {
                rules = provided;
            }
        }
        return rules;
    }
}
