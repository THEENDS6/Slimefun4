package cc.theends6.sfx.api.behavior;

public record SfxEnergyGeneratorProviderRegistration(
        String key,
        SfxEnergyGeneratorProviderFactory factory
) {
    public SfxEnergyGeneratorProviderRegistration {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Energy generator provider key must not be blank.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Energy generator provider factory must not be null.");
        }
        key = key.trim();
    }
}
