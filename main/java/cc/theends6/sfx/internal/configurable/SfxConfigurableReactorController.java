package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;




final class SfxConfigurableReactorController {
    private SfxConfigurableReactorController() {
    }

    static ReactorTickResult tickReactor(SfxConfigurableMachineService service, UUID instanceId, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location, Map<String, Object> frameworkAttributes) {
        if (location == null || location.getWorld() == null) {
            return new ReactorTickResult(0, false);
        }
        boolean changed = false;
        if (service.isReactorOutputBlocked(definition, state)) {
            service.updateReactorHologram(instance.anchorKey(), state);
            return new ReactorTickResult(0, false);
        }
        if (!state.hasActiveFuel()) {
            SfxConfigurableMachineDefinition.ReactorFuel fuel = service.findFuel(definition, state);
            if (fuel == null || (fuel.output() != null && !service.canFitOutput(service.items, state, fuel.output(), 0, 1))) {
                service.removeReactorHologram(instance.anchorKey());
                return new ReactorTickResult(0, false);
            }
            if (!service.hasWaterCooling(location) || !service.consumeCoolantIfNeeded(state, definition)) {
                service.meltDownReactor(instance, location);
                if (frameworkAttributes != null) frameworkAttributes.put("configurable.reactor.meltdown", Boolean.TRUE);
                return new ReactorTickResult(0, true);
            }
            service.consumeInput(state, service.fuelSlotIndex(state, fuel), fuel.amount());
            state.activeFuelKey(fuel.key());
            state.fuelProgressTicks(0);
            state.fuelTotalTicks(fuel.seconds() * 20);
            changed = true;
        }
        if (!service.hasWaterCooling(location)) {
            service.meltDownReactor(instance, location);
            if (frameworkAttributes != null) frameworkAttributes.put("configurable.reactor.meltdown", Boolean.TRUE);
            return new ReactorTickResult(0, true);
        }
        if (!service.consumeCoolantIfNeeded(state, definition)) {
            service.meltDownReactor(instance, location);
            if (frameworkAttributes != null) frameworkAttributes.put("configurable.reactor.meltdown", Boolean.TRUE);
            return new ReactorTickResult(0, true);
        }
        boolean electricityFocus = state.mode() == 0;
        if (electricityFocus && state.storedEnergy() + definition.energyPerTick() > definition.capacity()) {
            service.updateReactorHologram(instance.anchorKey(), state);
            return new ReactorTickResult(0, changed);
        }
        state.fuelProgressTicks(state.fuelProgressTicks() + 1);
        if (state.coolantTotalTicks() > 0) {
            state.coolantProgressTicks(state.coolantProgressTicks() + 1);
        }
        int generated = 0;
        if (state.storedEnergy() + definition.energyPerTick() <= definition.capacity()) {
            generated = definition.energyPerTick();
            state.storedEnergy(state.storedEnergy() + generated);
        }
        if (definition.witherAura() && service.tickCounter % 20L == 0L) {
            service.applyWitherAura(location);
        }
        if (state.fuelProgressTicks() >= state.fuelTotalTicks()) {
            SfxConfigurableMachineDefinition.ReactorFuel completed = service.fuelByKey(definition, state.activeFuelKey());
            if (completed != null && completed.output() != null) {
                if (!service.canFitOutput(service.items, state, completed.output(), 0, 1)) {
                    state.fuelProgressTicks(state.fuelTotalTicks());
                    service.updateReactorHologram(instance.anchorKey(), state);
                    return new ReactorTickResult(generated, true);
                }
                service.pushOutput(service.items, state, completed.output(), 0, 1);
            }
            state.clearFuel();
        }
        service.updateReactorHologram(instance.anchorKey(), state);
        if (frameworkAttributes != null) frameworkAttributes.put("configurable.reactor.generated", generated);
        return new ReactorTickResult(generated, true);
    
    }
}
