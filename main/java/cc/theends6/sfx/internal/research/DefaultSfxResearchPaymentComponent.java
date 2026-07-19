package cc.theends6.sfx.internal.research;

import cc.theends6.sfx.api.localization.SfxLocalizationView;
import cc.theends6.sfx.api.research.SfxResearchPaymentComponent;
import cc.theends6.sfx.api.research.SfxResearchPaymentContext;
import cc.theends6.sfx.api.research.SfxResearchPaymentResult;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class DefaultSfxResearchPaymentComponent implements SfxResearchPaymentComponent {
    private final SfxLocalizationView localization;

    public DefaultSfxResearchPaymentComponent(SfxLocalizationView localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    @Override
    public String displayCost(Player player, SfxResearchPaymentContext context) {
        return localization.requiredText("guide.research.cost")
                .replace("{cost}", Integer.toString(context.configuredLevelCost()));
    }

    @Override
    public SfxResearchPaymentResult charge(Player player, SfxResearchPaymentContext context) {
        Objects.requireNonNull(player, "player");
        if (player.getGameMode() == GameMode.CREATIVE) {
            return SfxResearchPaymentResult.success();
        }
        int cost = context.configuredLevelCost();
        if (player.getLevel() < cost) {
            return SfxResearchPaymentResult.rejected(localization.requiredText("messages.not-enough-xp"));
        }
        player.setLevel(player.getLevel() - cost);
        return SfxResearchPaymentResult.success();
    }
}
