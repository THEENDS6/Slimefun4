package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxElectricSpecialProviderKeyPolicy {
    String resolve(SfxElectricSpecialProviderKeyContext context, String currentProviderKey);
}
