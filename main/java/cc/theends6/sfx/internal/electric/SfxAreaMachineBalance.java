package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuleContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuleProvider;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxAreaMachineBalance {
    private SfxAreaMachineBalance() {
    }

    public static SfxAreaMachineRules rules(JavaPlugin plugin) {
        SfxAreaMachineRules defaults = SfxAreaMachineRules.classicDefaults();
        SfxApi api = plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
        if (api == null) {
            return defaults;
        }
        SfxAreaMachineRuleContext context = new SfxAreaMachineRuleContext();
        SfxAreaMachineRules rules = defaults;
        for (SfxAreaMachineRuleProvider provider : api.behaviors().areaMachineRuleProviders()) {
            SfxAreaMachineRules provided = provider.apply(context, rules);
            if (provided != null) {
                rules = provided;
            }
        }
        return rules;
    }

}
