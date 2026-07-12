package cc.theends6.sfx.api.behavior;

public record SfxElectricMachineProviderRegistration(
        String key,
        SfxElectricMachineProviderFactory factory
) {
    public SfxElectricMachineProviderRegistration {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Electric machine provider key must not be blank.");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Electric machine provider factory must not be null.");
        }
        key = key.trim();
    }
}
