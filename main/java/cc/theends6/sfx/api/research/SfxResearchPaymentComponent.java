package cc.theends6.sfx.api.research;

import org.bukkit.entity.Player;





public interface SfxResearchPaymentComponent {
    String displayCost(Player player, SfxResearchPaymentContext context);

    SfxResearchPaymentResult charge(Player player, SfxResearchPaymentContext context);
}
