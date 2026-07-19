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
        remaining = accepted;
        long committed = 0L;
        java.util.ArrayList<Move> moves = new java.util.ArrayList<>();
        for (SfxVirtualFluidContainer child : children) {
            if (remaining <= 0L) break;
            long moved = child.insert(new SfxFluidStack(fluid.fluidType(), remaining), SfxTransactionMode.COMMIT);
            committed = Math.addExact(committed, moved);
            remaining -= moved;
            if (moved > 0L) moves.add(new Move(child, moved));
        }
        if (committed != accepted) {
            rollbackInsert(fluid.fluidType(), moves);
            throw new IllegalStateException("Fluid container changed after simulation; insert was rolled back");
        }
        return committed;
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
        remaining = available;
        long committed = 0L;
        java.util.ArrayList<Move> moves = new java.util.ArrayList<>();
        for (SfxVirtualFluidContainer child : children) {
            if (remaining <= 0L) break;
            long moved = child.extract(fluidType, remaining, SfxTransactionMode.COMMIT);
            committed = Math.addExact(committed, moved);
            remaining -= moved;
            if (moved > 0L) moves.add(new Move(child, moved));
        }
        if (committed != available) {
            rollbackExtract(fluidType, moves);
            throw new IllegalStateException("Fluid container changed after simulation; extraction was rolled back");
        }
        return committed;
    }

    private static void rollbackInsert(String fluidType, List<Move> moves) {
        for (int index = moves.size() - 1; index >= 0; index--) {
            Move move = moves.get(index);
            long restored = move.container().extract(fluidType, move.amount(), SfxTransactionMode.COMMIT);
            if (restored != move.amount()) {
                throw new IllegalStateException("Failed to roll back composite fluid insert");
            }
        }
    }

    private static void rollbackExtract(String fluidType, List<Move> moves) {
        for (int index = moves.size() - 1; index >= 0; index--) {
            Move move = moves.get(index);
            long restored = move.container().insert(new SfxFluidStack(fluidType, move.amount()), SfxTransactionMode.COMMIT);
            if (restored != move.amount()) {
                throw new IllegalStateException("Failed to roll back composite fluid extraction");
            }
        }
    }

    private record Move(SfxVirtualFluidContainer container, long amount) {}
}
