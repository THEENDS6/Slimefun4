package cc.theends6.sfx.internal.power;

import cc.theends6.sfx.api.container.SfxTransactionMode;
import cc.theends6.sfx.api.power.SfxInventoryPowerRouter;
import cc.theends6.sfx.api.power.SfxPowerPort;
import cc.theends6.sfx.api.power.SfxPowerRoute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;


public final class DefaultSfxInventoryPowerRouter implements SfxInventoryPowerRouter {
    @Override
    public synchronized List<SfxPowerRoute> route(Collection<? extends SfxPowerPort> sources,
                                                   Collection<? extends SfxPowerPort> consumers,
                                                   double transferLimit) {
        double remainingLimit = Math.max(0.0D, transferLimit);
        List<SfxPowerPort> orderedSources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
        orderedSources.sort(Comparator.comparingInt(SfxPowerPort::priority).thenComparing(SfxPowerPort::id));
        List<SfxPowerPort> orderedConsumers = consumers == null ? new ArrayList<>() : new ArrayList<>(consumers);
        orderedConsumers.sort(Comparator.comparingInt(SfxPowerPort::priority).thenComparing(SfxPowerPort::id));
        Map<SfxPowerPort, Double> sourceRemaining = new IdentityHashMap<>();
        for (SfxPowerPort source : orderedSources) {
            sourceRemaining.put(source, Math.max(0.0D, source.available()));
        }
        List<Plan> plans = new ArrayList<>();
        for (SfxPowerPort consumer : orderedConsumers) {
            double demand = Math.min(Math.max(0.0D, consumer.demand()), remainingLimit);
            for (SfxPowerPort source : orderedSources) {
                if (demand <= 0.0D || remainingLimit <= 0.0D) break;
                double candidate = Math.min(Math.min(demand, remainingLimit), sourceRemaining.getOrDefault(source, 0.0D));
                double extractable = source.extract(candidate, SfxTransactionMode.SIMULATE);
                double insertable = consumer.insert(extractable, SfxTransactionMode.SIMULATE);
                double amount = Math.min(extractable, insertable);
                if (amount <= 0.0D) continue;
                plans.add(new Plan(source, consumer, amount));
                sourceRemaining.computeIfPresent(source, (ignored, available) -> Math.max(0.0D, available - amount));
                demand -= amount;
                remainingLimit -= amount;
            }
        }
        List<SfxPowerRoute> committed = new ArrayList<>();
        List<Applied> applied = new ArrayList<>();
        for (Plan plan : plans) {
            double extracted = plan.source().extract(plan.amount(), SfxTransactionMode.COMMIT);
            if (extracted + 1.0E-9D < plan.amount()) {
                Applied partial = new Applied(plan, extracted, 0.0D);
                if (extracted > 0.0D) applied.add(partial);
                throw rollback(applied, new IllegalStateException(
                        "Power source changed after simulation: " + plan.source().id()));
            }
            double inserted = plan.consumer().insert(plan.amount(), SfxTransactionMode.COMMIT);
            applied.add(new Applied(plan, extracted, inserted));
            if (inserted + 1.0E-9D < plan.amount()) {
                throw rollback(applied, new IllegalStateException(
                        "Power consumer changed after simulation: " + plan.consumer().id()));
            }
            committed.add(new SfxPowerRoute(plan.source().id(), plan.consumer().id(), plan.amount()));
        }
        return List.copyOf(committed);
    }

    private static IllegalStateException rollback(List<Applied> applied, IllegalStateException failure) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            Applied move = applied.get(index);
            try {
                double removed = move.inserted() <= 0.0D ? 0.0D
                        : move.plan().consumer().extract(move.inserted(), SfxTransactionMode.COMMIT);
                double restored = move.extracted() <= 0.0D ? 0.0D
                        : move.plan().source().insert(move.extracted(), SfxTransactionMode.COMMIT);
                if (removed + 1.0E-9D < move.inserted() || restored + 1.0E-9D < move.extracted()) {
                    failure.addSuppressed(new IllegalStateException("Power rollback was incomplete for "
                            + move.plan().source().id() + " -> " + move.plan().consumer().id()));
                }
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        return failure;
    }

    private record Plan(SfxPowerPort source, SfxPowerPort consumer, double amount) {}
    private record Applied(Plan plan, double extracted, double inserted) {}
}
