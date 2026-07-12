package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxEnergyGeneratorProviderFactory {
    SfxEnergyGeneratorProvider create(SfxEnergyGeneratorProviderContext context);
}
