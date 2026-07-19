package cc.theends6.sfx.api.container;

public interface SfxVirtualFluidContainer {
    long capacity();
    SfxFluidStack contents();
    long insert(SfxFluidStack fluid, SfxTransactionMode mode);
    long extract(String fluidType, long amount, SfxTransactionMode mode);
}
