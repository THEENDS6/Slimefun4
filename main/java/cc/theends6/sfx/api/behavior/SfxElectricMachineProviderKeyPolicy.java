package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxElectricMachineProviderKeyPolicy {
    String resolve(SfxElectricMachineProviderKeyContext context, String currentProviderKey);
}
