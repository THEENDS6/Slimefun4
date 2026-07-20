package cc.theends6.sfx.api.container;

import java.util.List;
import java.util.Objects;


public final class SfxCompositeFluidContainer implements SfxVirtualFluidContainer {
    private final List<SfxVirtualFluidContainer> children;

    public SfxCompositeFluidContainer(List<? extends SfxVirtualFluidContainer> children) {
        this.children = List.copyOf(Objects.requireNonNull(children, "children"));
        if (this.children.isEmpty()) throw new IllegalArgumentException("Composite fluid container must have children");
    }

    @Override public long capacity() {
        long total = 0L;
        for (SfxVirtualFluidContainer child : children) total = Math.addExact(total, child.capacity());
        return total;
    }

    @Override public SfxFluidStack contents() {
        String type = null;
        long amount = 0L;
        for (SfxVirtualFluidContainer child : children) {
            SfxFluidStack value = child.contents();
            if (value == null || value.amount() == 0L) continue;
            if (type != null && !type.equals(value.fluidType())) {
                throw new IllegalStateException("Composite contains incompatible fluid types");
            }
            type = value.fluidType();
            amount = Math.addExact(amount, value.amount());
        }
        return type == null ? null : new SfxFluidStack(type, amount);
    }

    @Override public synchronized long insert(SfxFluidStack fluid, SfxTransactionMode mode) {
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(mode, "mode");
        long remaining = fluid.amount();
        long accepted = 0L;
        for (SfxVirtualFluidContainer child : children) {
            if (remaining <= 0L) break;
            long moved = child.insert(new SfxFluidStack(fluid.fluidType(), remaining), SfxTransactionMode.SIMULATE);
            accepted = Math.addExact(accepted, moved);
            remaining -= moved;
        }
        if (mode == SfxTransactionMode.SIMULATE) return accepted;
        SfxTransactionReservation reservation = prepareInsert(fluid).orElseThrow(
                () -> new IllegalStateException("Fluid child does not support prepared rollback"));
        commit(reservation);
        return accepted;
    }

    @Override public synchronized long extract(String fluidType, long amount, SfxTransactionMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (fluidType == null || fluidType.isBlank() || amount <= 0L) return 0L;
        long remaining = amount;
        long available = 0L;
        for (SfxVirtualFluidContainer child : children) {
            if (remaining <= 0L) break;
            long moved = child.extract(fluidType, remaining, SfxTransactionMode.SIMULATE);
            available = Math.addExact(available, moved);
            remaining -= moved;
        }
        if (mode == SfxTransactionMode.SIMULATE) return available;
        SfxTransactionReservation reservation = prepareExtract(fluidType, amount).orElseThrow(
                () -> new IllegalStateException("Fluid child does not support prepared rollback"));
        commit(reservation);
        return available;
    }

    @Override public synchronized java.util.Optional<SfxTransactionReservation> prepareInsert(SfxFluidStack fluid) {
        Objects.requireNonNull(fluid, "fluid");
        long remaining = fluid.amount();
        List<java.util.function.Supplier<java.util.Optional<SfxTransactionReservation>>> preparations = new java.util.ArrayList<>();
        for (SfxVirtualFluidContainer child : children) {
            if (remaining <= 0L) break;
            long moved = child.insert(new SfxFluidStack(fluid.fluidType(), remaining), SfxTransactionMode.SIMULATE);
            if (moved > 0L) {
                long reserved = moved;
                preparations.add(() -> child.prepareInsert(new SfxFluidStack(fluid.fluidType(), reserved)));
                remaining -= moved;
            }
        }
        return prepare(preparations);
    }

    @Override public synchronized java.util.Optional<SfxTransactionReservation> prepareExtract(String fluidType, long amount) {
        if (fluidType == null || fluidType.isBlank() || amount <= 0L) return java.util.Optional.of(new CompositeReservation(List.of()));
        long remaining = amount;
        List<java.util.function.Supplier<java.util.Optional<SfxTransactionReservation>>> preparations = new java.util.ArrayList<>();
        for (SfxVirtualFluidContainer child : children) {
            if (remaining <= 0L) break;
            long moved = child.extract(fluidType, remaining, SfxTransactionMode.SIMULATE);
            if (moved > 0L) {
                long reserved = moved;
                preparations.add(() -> child.prepareExtract(fluidType, reserved));
                remaining -= moved;
            }
        }
        return prepare(preparations);
    }

    private static java.util.Optional<SfxTransactionReservation> prepare(
            List<java.util.function.Supplier<java.util.Optional<SfxTransactionReservation>>> preparations) {
        List<SfxTransactionReservation> reservations = new java.util.ArrayList<>();
        for (java.util.function.Supplier<java.util.Optional<SfxTransactionReservation>> preparation : preparations) {
            java.util.Optional<SfxTransactionReservation> reservation = preparation.get();
            if (reservation.isEmpty()) {
                for (int index = reservations.size() - 1; index >= 0; index--) reservations.get(index).rollback();
                return java.util.Optional.empty();
            }
            reservations.add(reservation.get());
        }
        return java.util.Optional.of(new CompositeReservation(reservations));
    }

    private static void commit(SfxTransactionReservation reservation) {
        try { reservation.commit(); }
        catch (RuntimeException failure) { reservation.rollback(); throw failure; }
    }

    private static final class CompositeReservation implements SfxTransactionReservation {
        private final List<SfxTransactionReservation> children;
        private CompositeReservation(List<SfxTransactionReservation> children) { this.children = List.copyOf(children); }
        @Override public void commit() { for (SfxTransactionReservation child : children) child.commit(); }
        @Override public void rollback() {
            for (int index = children.size() - 1; index >= 0; index--) children.get(index).rollback();
        }
    }
}
