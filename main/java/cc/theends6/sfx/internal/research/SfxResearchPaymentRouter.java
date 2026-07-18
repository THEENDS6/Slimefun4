package cc.theends6.sfx.internal.research;

import cc.theends6.sfx.api.override.SfxComponentOverrideTargets;
import cc.theends6.sfx.api.research.SfxResearchPaymentComponent;
import cc.theends6.sfx.api.research.SfxResearchPaymentContext;
import cc.theends6.sfx.api.research.SfxResearchPaymentResult;
import cc.theends6.sfx.internal.addon.DefaultSfxComponentOverrideRegistry;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.Objects;
import org.bukkit.entity.Player;


public final class SfxResearchPaymentRouter {
    private final DefaultSfxComponentOverrideRegistry componentOverrides;
    private final SfxResearchPaymentComponent defaultPayment;

    public SfxResearchPaymentRouter(DefaultSfxComponentOverrideRegistry componentOverrides,
                                    SfxLocalization localization) {
        this.componentOverrides = Objects.requireNonNull(componentOverrides, "componentOverrides");
        this.defaultPayment = new DefaultSfxResearchPaymentComponent(localization);
    }

    public String displayCost(SfxResearchDefinition research) {
        return active().displayCost(context(research));
    }

    public SfxResearchPaymentResult charge(Player player, SfxResearchDefinition research) {
        return active().charge(player, context(research));
    }

    private SfxResearchPaymentComponent active() {
        return componentOverrides.implementation(SfxComponentOverrideTargets.RESEARCH_PAYMENT)
                .orElse(defaultPayment);
    }

    private static SfxResearchPaymentContext context(SfxResearchDefinition research) {
        return new SfxResearchPaymentContext(research.id(), research.cost());
    }
}
