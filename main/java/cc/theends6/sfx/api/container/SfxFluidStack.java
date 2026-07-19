package cc.theends6.sfx.api.container;

public record SfxFluidStack(String fluidType, long amount) {
    public SfxFluidStack {
        if (fluidType == null || fluidType.isBlank()) throw new IllegalArgumentException("fluidType must not be blank");
        if (amount < 0L) throw new IllegalArgumentException("amount must not be negative");
    }
}
