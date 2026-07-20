package cc.theends6.sfx.api.testkit;

import cc.theends6.sfx.api.block.SfxBlockStateSchema;
import cc.theends6.sfx.api.container.SfxFluidStack;
import cc.theends6.sfx.api.container.SfxTransactionMode;
import cc.theends6.sfx.api.container.SfxTransactionCoordinator;
import cc.theends6.sfx.api.container.SfxTransactionReservation;
import cc.theends6.sfx.api.container.SfxVirtualFluidContainer;
import cc.theends6.sfx.api.power.SfxInventoryPowerRouter;
import cc.theends6.sfx.api.power.SfxPowerPort;
import cc.theends6.sfx.api.power.SfxPowerRoute;
import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;
import cc.theends6.sfx.api.time.SfxServerActiveClock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;





public final class SfxAddonTestKit {
    private SfxAddonTestKit() {
    }

    public static <T> void assertDuplicateIdRejected(SfxDefinitionRegistry<T> registry, String id,
                                                      T first, T duplicate) {
        Objects.requireNonNull(registry, "registry");
        registry.register(id, first);
        try {
            registry.register(id, duplicate);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Registry accepted duplicate id: " + id);
    }

    public static <S> void assertStateMigration(SfxBlockStateSchema<S> schema, int storedVersion,
                                                 byte[] payload, S expected) {
        Objects.requireNonNull(schema, "schema");
        S actual = schema.decode(storedVersion, payload == null ? new byte[0] : payload.clone());
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("State migration mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    public static void assertFluidSimulationMatchesCommit(SfxVirtualFluidContainer container,
                                                           SfxFluidStack offered) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(offered, "offered");
        long simulated = container.insert(offered, SfxTransactionMode.SIMULATE);
        long committed = container.insert(offered, SfxTransactionMode.COMMIT);
        if (simulated != committed) {
            throw new AssertionError("Fluid insert simulation mismatch: simulated=" + simulated
                    + ", committed=" + committed);
        }
    }

    public static void assertPowerRoutes(SfxInventoryPowerRouter router,
                                         Collection<? extends SfxPowerPort> sources,
                                         Collection<? extends SfxPowerPort> consumers,
                                         double transferLimit, List<SfxPowerRoute> expected) {
        Objects.requireNonNull(router, "router");
        List<SfxPowerRoute> actual = router.route(sources, consumers, transferLimit);
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Power routes mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    public static long elapsedActiveTicks(SfxServerActiveClock clock, long lastSettledActiveTick) {
        Objects.requireNonNull(clock, "clock");
        return Math.max(0L, clock.activeTicks() - Math.max(0L, lastSettledActiveTick));
    }

    
    public static <T> void assertPreparedRollback(
            Collection<? extends Supplier<Optional<SfxTransactionReservation>>> participants,
            Supplier<T> snapshot, T expectedAfterRollback) {
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            SfxTransactionCoordinator.commit(participants);
        } catch (RuntimeException expectedFailure) {
            T actual = snapshot.get();
            if (!Objects.equals(expectedAfterRollback, actual)) {
                throw new AssertionError("Prepared rollback mismatch: expected=" + expectedAfterRollback
                        + ", actual=" + actual, expectedFailure);
            }
            return;
        }
        throw new AssertionError("Prepared transaction unexpectedly committed; include a rejecting participant");
    }
}
