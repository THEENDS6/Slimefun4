package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.block.SfxBlockStateCodec;
import cc.theends6.sfx.api.block.SfxBlockStateSchema;
import cc.theends6.sfx.api.container.SfxTransactionMode;
import cc.theends6.sfx.api.container.SfxCompositeFluidContainer;
import cc.theends6.sfx.api.container.SfxFluidStack;
import cc.theends6.sfx.api.container.SfxVirtualFluidContainer;
import cc.theends6.sfx.api.power.SfxPowerPort;
import cc.theends6.sfx.api.testkit.SfxAddonTestKit;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.core.SfxOwnedEntries;
import cc.theends6.sfx.internal.power.DefaultSfxInventoryPowerRouter;
import java.nio.ByteBuffer;
import java.util.List;


public final class SfxAddonLifecycleSmokeCli {
    private SfxAddonLifecycleSmokeCli() {}

    public static void main(String[] args) {
        ownerRegistryRollback();
        stagedRegistrationAtomicity();
        behaviorOwnerCleanup();
        schemaMigration();
        fluidTransactionRollback();
        powerSettlement();
        powerSelfRouteGuard();
        powerTransactionRollback();
        System.out.println("SFX addon lifecycle smoke checks passed.");
    }

    private static void ownerRegistryRollback() {
        DefaultSfxDefinitionRegistry<String> registry = new DefaultSfxDefinitionRegistry<>();
        registry.view("test:first").register("test:value", "first");
        expectFailure(() -> registry.view("test:second").register("test:value", "second"));
        registry.removeOwner("test:first");
        require(registry.definitions().isEmpty(), "owner cleanup left a definition behind");

        SfxOwnedEntries<String> entries = new SfxOwnedEntries<>();
        entries.add("test:first", "a");
        entries.add("test:second", "b");
        entries.removeOwner("test:first");
        require(entries.values().equals(List.of("b")), "ordered owner entries cleanup failed");
    }

    private static void behaviorOwnerCleanup() {
        DefaultSfxBehaviorRegistry registry = new DefaultSfxBehaviorRegistry();
        registry.registrarFor("test:first").registerUtilityRuleProvider((context, current) -> current);
        require(registry.utilityRuleProviders().size() == 1, "owned behavior was not registered");
        registry.removeOwner("test:first");
        require(registry.utilityRuleProviders().isEmpty(), "owned behavior survived cleanup");
    }

    private static void stagedRegistrationAtomicity() {
        DefaultSfxDefinitionRegistry<String> backing = new DefaultSfxDefinitionRegistry<>();
        SfxStagedAddonRegistration transaction = new SfxStagedAddonRegistration("test:staged");
        var staged = transaction.definitions(backing.view("test:staged"));
        staged.register("test:value", "pending");
        require(backing.definitions().isEmpty(), "staged definition leaked before commit");
        require(staged.find("test:value").orElseThrow().equals("pending"), "staged definition was not visible inside session");
        transaction.commit();
        require(backing.find("test:value").orElseThrow().equals("pending"), "staged definition did not publish on commit");

        SfxStagedAddonRegistration rolledBack = new SfxStagedAddonRegistration("test:rollback");
        var discarded = rolledBack.definitions(backing.view("test:rollback"));
        discarded.register("test:discarded", "discarded");
        rolledBack.rollbackPending();
        require(backing.find("test:discarded").isEmpty(), "rolled back definition reached backing registry");
    }

    private static void schemaMigration() {
        SfxBlockStateSchema<Integer> schema = new SfxBlockStateSchema<>(2, new SfxBlockStateCodec<>() {
            @Override public byte[] encode(Integer state) { return ByteBuffer.allocate(4).putInt(state).array(); }
            @Override public Integer decode(byte[] payload) { return ByteBuffer.wrap(payload).getInt(); }
        }, (oldVersion, payload) -> ByteBuffer.allocate(4).putInt(payload[0]).array());
        SfxAddonTestKit.assertStateMigration(schema, 1, new byte[] {7}, 7);
    }

    private static void powerSettlement() {
        Port source = new Port("source", 0, 100.0D, 0.0D);
        Port first = new Port("first", 0, 0.0D, 60.0D);
        Port second = new Port("second", 1, 0.0D, 60.0D);
        var routes = new DefaultSfxInventoryPowerRouter().route(List.of(source), List.of(first, second), 100.0D);
        require(routes.size() == 2 && first.stored == 60.0D && second.stored == 40.0D && source.stored == 0.0D,
                "power routing priority or settlement was incorrect");
    }

    private static void fluidTransactionRollback() {
        FluidPort first = new FluidPort(5L, false);
        FluidPort changed = new FluidPort(5L, true);
        var composite = new SfxCompositeFluidContainer(List.of(first, changed));
        expectFailure(() -> composite.insert(new SfxFluidStack("minecraft:water", 10L), SfxTransactionMode.COMMIT));
        require(first.amount == 0L && changed.amount == 0L,
                "composite fluid insert left a partial commit behind");
    }

    private static void powerSelfRouteGuard() {
        Port battery = new Port("battery", 0, 50.0D, 50.0D);
        Port device = new Port("device", 1, 0.0D, 50.0D);
        var routes = new DefaultSfxInventoryPowerRouter().route(
                List.of(battery), List.of(battery, device), 50.0D);
        require(routes.size() == 1 && "device".equals(routes.getFirst().consumerId())
                        && battery.stored == 0.0D && device.stored == 50.0D,
                "power router allowed a port to charge itself");
    }

    private static void powerTransactionRollback() {
        Port source = new Port("source", 0, 100.0D, 0.0D);
        Port first = new Port("first", 0, 0.0D, 60.0D);
        Port changed = new Port("changed", 1, 0.0D, 40.0D, true);
        expectFailure(() -> new DefaultSfxInventoryPowerRouter().route(
                List.of(source), List.of(first, changed), 100.0D));
        require(source.stored == 100.0D && first.stored == 0.0D && changed.stored == 0.0D,
                "power route left a partial commit behind");
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new IllegalStateException("Expected operation to fail");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Port implements SfxPowerPort {
        private final String id;
        private final int priority;
        private final double capacity;
        private final boolean rejectCommitInsert;
        private double stored;
        private Port(String id, int priority, double stored, double demand) {
            this(id, priority, stored, demand, false);
        }
        private Port(String id, int priority, double stored, double demand, boolean rejectCommitInsert) {
            this.id = id; this.priority = priority; this.stored = stored; this.capacity = stored + demand;
            this.rejectCommitInsert = rejectCommitInsert;
        }
        @Override public String id() { return id; }
        @Override public int priority() { return priority; }
        @Override public double available() { return stored; }
        @Override public double demand() { return Math.max(0.0D, capacity - stored); }
        @Override public double extract(double amount, SfxTransactionMode mode) {
            double moved = Math.min(amount, stored);
            if (mode == SfxTransactionMode.COMMIT) stored -= moved;
            return moved;
        }
        @Override public double insert(double amount, SfxTransactionMode mode) {
            double moved = Math.min(amount, demand());
            if (mode == SfxTransactionMode.COMMIT && rejectCommitInsert) return 0.0D;
            if (mode == SfxTransactionMode.COMMIT) stored += moved;
            return moved;
        }
    }

    private static final class FluidPort implements SfxVirtualFluidContainer {
        private final long capacity;
        private final boolean rejectCommitInsert;
        private long amount;

        private FluidPort(long capacity, boolean rejectCommitInsert) {
            this.capacity = capacity;
            this.rejectCommitInsert = rejectCommitInsert;
        }

        @Override public long capacity() { return capacity; }
        @Override public SfxFluidStack contents() {
            return amount == 0L ? null : new SfxFluidStack("minecraft:water", amount);
        }
        @Override public long insert(SfxFluidStack fluid, SfxTransactionMode mode) {
            long moved = Math.min(fluid.amount(), capacity - amount);
            if (mode == SfxTransactionMode.COMMIT && rejectCommitInsert) return 0L;
            if (mode == SfxTransactionMode.COMMIT) amount += moved;
            return moved;
        }
        @Override public long extract(String fluidType, long requested, SfxTransactionMode mode) {
            long moved = "minecraft:water".equals(fluidType) ? Math.min(requested, amount) : 0L;
            if (mode == SfxTransactionMode.COMMIT) amount -= moved;
            return moved;
        }
    }
}
