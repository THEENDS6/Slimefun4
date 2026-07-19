package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.override.SfxComponentOverrideTargets;
import cc.theends6.sfx.api.research.SfxResearchPaymentComponent;
import cc.theends6.sfx.api.research.SfxResearchPaymentContext;
import cc.theends6.sfx.api.research.SfxResearchPaymentResult;
import org.bukkit.entity.Player;

public final class SfxComponentOverrideRegistrySmokeCli {
    private SfxComponentOverrideRegistrySmokeCli() {
    }

    public static void main(String[] args) {
        DefaultSfxComponentOverrideRegistry registry = new DefaultSfxComponentOverrideRegistry();
        registry.claim("example:first", SfxComponentOverrideTargets.RESEARCH_PAYMENT.id(), 2);
        expectFailure(() -> registry.claim("example:second", SfxComponentOverrideTargets.RESEARCH_PAYMENT.id(), 2),
                "target conflict");
        expectFailure(registry::validateImplementations, "missing implementation");

        SfxResearchPaymentComponent implementation = new SfxResearchPaymentComponent() {
            @Override
            public String displayCost(Player player, SfxResearchPaymentContext context) {
                return Integer.toString(context.configuredLevelCost());
            }

            @Override
            public SfxResearchPaymentResult charge(Player player, SfxResearchPaymentContext context) {
                return SfxResearchPaymentResult.success();
            }
        };
        registry.registrarFor("example:first").replace(SfxComponentOverrideTargets.RESEARCH_PAYMENT, implementation);
        registry.validateImplementations();
        if (registry.implementation(SfxComponentOverrideTargets.RESEARCH_PAYMENT).orElseThrow() != implementation) {
            throw new IllegalStateException("Installed component override was not resolved");
        }
        registry.removeOwner("example:first");
        if (registry.implementation(SfxComponentOverrideTargets.RESEARCH_PAYMENT).isPresent()) {
            throw new IllegalStateException("Component override survived owner removal");
        }
        System.out.println("Validated exclusive component override claims, installation and cleanup.");
    }

    private static void expectFailure(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Expected " + label + " failure");
    }
}
