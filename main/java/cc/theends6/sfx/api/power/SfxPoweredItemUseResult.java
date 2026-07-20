package cc.theends6.sfx.api.power;

public record SfxPoweredItemUseResult(boolean executed, double effectMultiplier,
                                      double consumedEnergy, SfxPoweredItemState state) {}
