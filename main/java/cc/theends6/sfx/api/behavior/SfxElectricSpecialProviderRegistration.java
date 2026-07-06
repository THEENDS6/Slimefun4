package cc.theends6.sfx.api.behavior;

public record SfxElectricSpecialProviderRegistration(
        String key,
        SfxElectricSpecialProviderFactory factory
) {
    public SfxElectricSpecialProviderRegistration {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Electric special provider key must not be blank.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Electric special provider factory must not be null.");
        }
        key = key.trim();
    }
}
