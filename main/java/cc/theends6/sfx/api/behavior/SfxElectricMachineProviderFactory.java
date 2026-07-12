package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxElectricMachineProviderFactory {
    SfxElectricMachineProvider create(SfxElectricMachineProviderContext context);
}
