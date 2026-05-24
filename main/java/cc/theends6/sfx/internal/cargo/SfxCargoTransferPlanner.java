package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.internal.inventory.SfxReservationLedger;
import cc.theends6.sfx.internal.inventory.SfxStorageKey;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;




final class SfxCargoTransferPlanner {
    private SfxCargoTransferPlanner() {
    }

    static List<SfxCargoOutputMove> planOutputMoves(SfxCargoService service, SfxCargoNodeRef input, ItemStack stack, List<SfxCargoNodeRef> outputs, SfxCargoDistributionMode mode, SfxCargoEndpoint source) {
        if (service.isEmpty(stack)) {
            return List.of();
        }
        List<SfxCargoNodeRef> candidates = new ArrayList<>();
        SfxReservationLedger reservations = new SfxReservationLedger();
        for (SfxCargoNodeRef output : outputs) {
            if (output.state().channel != input.state().channel) {
                continue;
            }
            if (!service.acceptsOutputFilter(output.state(), output.definition(), stack)) {
                continue;
            }
            SfxCargoEndpoint endpoint = output.endpoint();
            if (endpoint == null || endpoint.sameStorage(source)) {
                continue;
            }
            if (availableCapacityFor(service, output.withEndpoint(endpoint), input, stack, reservations) <= 0) {
                continue;
            }
            candidates.add(output.withEndpoint(endpoint));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator.comparingInt((SfxCargoNodeRef ref) -> ref.priority()).reversed().thenComparing(ref -> ref.instance().anchorKey(), (left, right) -> service.compareAnchorKeys(left, right)));
        List<SfxCargoOutputMove> moves = new ArrayList<>();
        int remaining = stack.getAmount();
        int index = 0;
        while (index < candidates.size() && remaining > 0) {
            int priority = candidates.get(index).priority();
            List<SfxCargoNodeRef> group = new ArrayList<>();
            while (index < candidates.size() && candidates.get(index).priority() == priority) {
                group.add(candidates.get(index++));
            }
            int before = remaining;
            if (mode == SfxCargoDistributionMode.EVEN) {
                remaining = planEvenMoves(service, input, priority, group, stack, remaining, moves, reservations);
            } else if (mode == SfxCargoDistributionMode.ROUND_ROBIN) {
                remaining = planRoundRobinMoves(service, input, group, stack, remaining, moves, reservations);
            } else {
                remaining = planSequentialMoves(service, input, group, stack, remaining, moves, reservations);
            }
            if (input.definition().type() == SfxCargoComponentType.INPUT_NODE && !moves.isEmpty()) {
                break;
            }
            if (remaining == before && !group.isEmpty()) {
                break;
            }
        }
        return moves;
    }

    private static int availableCapacityFor(SfxCargoService service, SfxCargoNodeRef output, SfxCargoNodeRef input, ItemStack stack, SfxReservationLedger reservations) {
        if (output == null || output.endpoint() == null) {
            return 0;
        }
        int capacity = input.definition().type() == SfxCargoComponentType.INPUT_NODE
                ? output.endpoint().capacityForSingleSlot(stack, input.state().smartFill)
                : output.endpoint().capacityFor(stack, input.state().smartFill);
        SfxStorageKey key = output.endpoint().storageKey();
        return reservations.available(key == null ? null : key.value(), capacity);
    }

    private static void reserveCapacity(SfxCargoService service, SfxCargoEndpoint endpoint, int amount, SfxReservationLedger reservations) {
        if (endpoint == null || amount <= 0) {
            return;
        }
        SfxStorageKey key = endpoint.storageKey();
        if (key != null) {
            reservations.reserve(key.value(), amount);
        }
    }

    private static int planSequentialMoves(SfxCargoService service, SfxCargoNodeRef input, List<SfxCargoNodeRef> group, ItemStack stack, int remaining, List<SfxCargoOutputMove> moves, SfxReservationLedger reservations) {
        for (SfxCargoNodeRef output : group) {
            if (remaining <= 0) {
                break;
            }
            int amount = Math.min(remaining, availableCapacityFor(service, output, input, stack, reservations));
            if (amount <= 0) {
                continue;
            }
            moves.add(new SfxCargoOutputMove(output.endpoint(), amount));
            reserveCapacity(service, output.endpoint(), amount, reservations);
            remaining -= amount;
            if (input.definition().type() == SfxCargoComponentType.INPUT_NODE) {
                break;
            }
        }
        return remaining;
    }

    private static int planRoundRobinMoves(SfxCargoService service, SfxCargoNodeRef input, List<SfxCargoNodeRef> group, ItemStack stack, int remaining, List<SfxCargoOutputMove> moves, SfxReservationLedger reservations) {
        if (group.isEmpty() || remaining <= 0) {
            return remaining;
        }
        int start = input.state().roundRobinCursor % group.size();
        boolean movedAny = false;
        for (int i = 0; i < group.size() && remaining > 0; i++) {
            SfxCargoNodeRef output = group.get((start + i) % group.size());
            int amount = Math.min(remaining, availableCapacityFor(service, output, input, stack, reservations));
            if (amount <= 0) {
                continue;
            }
            moves.add(new SfxCargoOutputMove(output.endpoint(), amount));
            reserveCapacity(service, output.endpoint(), amount, reservations);
            remaining -= amount;
            movedAny = true;
            input.state().roundRobinCursor = (start + i + 1) % group.size();
            if (input.definition().type() == SfxCargoComponentType.INPUT_NODE) {
                break;
            }
        }
        if (movedAny) {
            service.persistState(input.instance().instanceId(), input.state());
        }
        return remaining;
    }

    private static int planEvenMoves(SfxCargoService service, SfxCargoNodeRef input, int priority, List<SfxCargoNodeRef> group, ItemStack stack, int remaining, List<SfxCargoOutputMove> moves, SfxReservationLedger reservations) {
        if (group.isEmpty() || remaining <= 0) {
            return remaining;
        }
        String key = input.instance().instanceId() + ":" + input.state().channel + ":" + priority + ":" + SfxCargoItemKey.of(service.items, stack).key();
        Map<UUID, Integer> debts = service.distributionDebt.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        group.sort(Comparator.comparingInt((SfxCargoNodeRef ref) -> debts.getOrDefault(ref.instance().instanceId(), 0)).reversed()
                .thenComparing(ref -> ref.instance().anchorKey(), (left, right) -> service.compareAnchorKeys(left, right)));
        int originalRemaining = remaining;
        Map<UUID, Integer> movedByNode = new HashMap<>();
        while (remaining > 0) {
            List<SfxCargoNodeRef> eligible = group.stream()
                    .filter(ref -> availableCapacityFor(service, ref, input, stack, reservations) > 0)
                    .toList();
            if (eligible.isEmpty()) {
                break;
            }
            int base = Math.max(1, (int) Math.ceil(remaining / (double) eligible.size()));
            boolean any = false;
            for (SfxCargoNodeRef output : eligible) {
                if (remaining <= 0) {
                    break;
                }
                int capacity = availableCapacityFor(service, output, input, stack, reservations);
                int amount = Math.min(Math.min(base, remaining), capacity);
                if (amount <= 0) {
                    continue;
                }
                moves.add(new SfxCargoOutputMove(output.endpoint(), amount));
                reserveCapacity(service, output.endpoint(), amount, reservations);
                movedByNode.merge(output.instance().instanceId(), amount, Integer::sum);
                remaining -= amount;
                any = true;
            }
            if (!any) {
                break;
            }
        }
        int totalMoved = originalRemaining - remaining;
        if (totalMoved > 0) {
            int eligibleCount = Math.max(1, group.size());
            int expected = totalMoved / eligibleCount;
            for (SfxCargoNodeRef node : group) {
                UUID id = node.instance().instanceId();
                int debt = debts.getOrDefault(id, 0);
                debt += expected - movedByNode.getOrDefault(id, 0);
                debts.put(id, Math.max(-4096, Math.min(4096, debt)));
            }
        }
        return remaining;
    }


}
