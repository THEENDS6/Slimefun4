package cc.theends6.sfx.internal.behavior;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxCargoFilterRuleContext;
import cc.theends6.sfx.api.behavior.SfxCargoFilterRuleProvider;
import cc.theends6.sfx.api.behavior.SfxCargoFilterRules;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCargoFilterRulesResolver {
    private SfxCargoFilterRulesResolver() {
    }

    public static SfxCargoFilterRules rules(JavaPlugin plugin) {
        SfxCargoFilterRules rules = SfxCargoFilterRules.classicDefaults();
        SfxApi api = plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
        if (api == null) {
            return rules;
        }
        SfxCargoFilterRuleContext context = new SfxCargoFilterRuleContext();
        for (SfxCargoFilterRuleProvider provider : api.behaviors().cargoFilterRuleProviders()) {
            SfxCargoFilterRules provided = provider.apply(context, rules);
            if (provided != null) {
                rules = provided;
            }
        }
        return rules;
    }
}
