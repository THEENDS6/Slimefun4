package cc.theends6.sfx.api.behavior;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public interface SfxTechnicalGadgetBehaviorProvider {
    Vector jetpackVelocity(Player player, Vector currentVelocity, Vector inputDirection, SfxTechnicalGadgetItem jetpack, boolean aboveHeightLimit);

    double hoverCost(SfxTechnicalGadgetItem jetpack, boolean verticalInput);

    double hoverYVelocity(double currentY, SfxTechnicalGadgetItem jetpack, boolean jumpDown, boolean shiftDown, boolean aboveHeightLimit);

    double hoverHorizontalAcceleration(SfxTechnicalGadgetItem jetpack);

    double maxJetpackHorizontalSpeed(SfxTechnicalGadgetItem jetpack);

    Vector jetBootsThrustVelocity(Player player, Vector currentVelocity, SfxTechnicalGadgetItem jetBoots);

    double jetBootsThrustHorizontalAcceleration(SfxTechnicalGadgetItem jetBoots);

    double jetBootsAssistAcceleration(Player player, SfxTechnicalGadgetItem jetBoots);

    double jetBootsUseCost(SfxTechnicalGadgetItem jetBoots, SfxJetBootsDriveMode mode);

    double maxJetBootsHorizontalSpeed(SfxTechnicalGadgetItem jetBoots);

    int maxAirJumps(SfxTechnicalGadgetItem jetBoots);

    double airJumpVelocity(SfxTechnicalGadgetItem jetBoots);

    double safeFallBonus(SfxTechnicalGadgetItem jetBoots);

    double fallDamageMultiplier(SfxTechnicalGadgetItem jetBoots);

    void playJetpackEffects(Player player, SfxTechnicalGadgetItem jetpack);

    void playJetBootsEffects(Player player, double extraY);

    void playJetBootsAirJumpSound(Player player);
}
