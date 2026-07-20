package cc.theends6.sfx.api.container;

public interface SfxVirtualFluidContainer {
    long capacity();
    SfxFluidStack contents();
    long insert(SfxFluidStack fluid, SfxTransactionMode mode);
    long extract(String fluidType, long amount, SfxTransactionMode mode);
    default java.util.Optional<SfxTransactionReservation> prepareInsert(SfxFluidStack fluid) {
        return java.util.Optional.empty();
    }
    default java.util.Optional<SfxTransactionReservation> prepareExtract(String fluidType, long amount) {
        return java.util.Optional.empty();
    }
}
