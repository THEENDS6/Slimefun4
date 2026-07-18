package cc.theends6.sfx.api.override;

import cc.theends6.sfx.api.research.SfxResearchPaymentComponent;

public final class SfxComponentOverrideTargets {
    public static final SfxComponentOverrideTarget<SfxResearchPaymentComponent> RESEARCH_PAYMENT =
            new SfxComponentOverrideTarget<>("sfx:research-payment", 1, SfxResearchPaymentComponent.class);

    private SfxComponentOverrideTargets() {
    }
}
