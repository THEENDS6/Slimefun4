package cc.theends6.sfx.internal.power;

import cc.theends6.sfx.api.container.SfxTransactionMode;
import cc.theends6.sfx.api.container.SfxTransactionCoordinator;
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
                if (source == consumer || source.id().equals(consumer.id())) continue;
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
        List<PortAmount> extracts = new ArrayList<>();
        List<PortAmount> inserts = new ArrayList<>();
        for (Plan plan : plans) {
            add(extracts, plan.source(), plan.amount());
            add(inserts, plan.consumer(), plan.amount());
        }
        List<java.util.function.Supplier<java.util.Optional<cc.theends6.sfx.api.container.SfxTransactionReservation>>> participants
                = new ArrayList<>();
        extracts.forEach(move -> participants.add(() -> move.port().prepareExtract(move.amount())));
        inserts.forEach(move -> participants.add(() -> move.port().prepareInsert(move.amount())));
        SfxTransactionCoordinator.commit(participants);
        return plans.stream().map(plan -> new SfxPowerRoute(
                plan.source().id(), plan.consumer().id(), plan.amount())).toList();
    }

    private static void add(List<PortAmount> amounts, SfxPowerPort port, double amount) {
        for (int index = 0; index < amounts.size(); index++) {
            PortAmount existing = amounts.get(index);
            if (existing.port() == port) {
                amounts.set(index, new PortAmount(port, existing.amount() + amount));
                return;
            }
        }
        amounts.add(new PortAmount(port, amount));
    }

    private record Plan(SfxPowerPort source, SfxPowerPort consumer, double amount) {}
    private record PortAmount(SfxPowerPort port, double amount) {}
}
