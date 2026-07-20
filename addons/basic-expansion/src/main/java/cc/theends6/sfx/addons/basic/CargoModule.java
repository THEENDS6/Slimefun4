package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class CargoModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerCargoInputTransferPolicy(
                (value, current) -> SfxBasicExpansionAddon.advancedInputTransfer(context, value, current));
        context.behaviors().registerCargoFilterRuleProvider(
                (value, current) -> SfxBasicExpansionAddon.cargoFilterRules(context, value, current));
        context.behaviors().registerGpsTransmitterInteractionPolicy(
                (value, current) -> SfxBasicExpansionAddon.gpsTransmitterInteraction(context, current));
        context.behaviors().registerGpsTransmitterStatusViewProvider(
                (value, current) -> SfxBasicExpansionAddon.gpsTransmitterStatusView(context, value, current));
    }
}
