package cc.theends6.sfx.api.research;

import org.bukkit.entity.Player;





public interface SfxResearchPaymentComponent {
    String displayCost(SfxResearchPaymentContext context);

    SfxResearchPaymentResult charge(Player player, SfxResearchPaymentContext context);
}
