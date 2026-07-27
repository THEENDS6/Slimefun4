package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.machine.SfxMachineExecution;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachineState;
import cc.theends6.sfx.api.machine.runtime.SfxMachineTickContext;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;




final class SfxElectricMachineTickController {
    private SfxElectricMachineTickController() {
    }

    static void tickMachine(SfxElectricMachineService service, UUID instanceId, SfxMachineTickContext context) {
        SfxBlockInstanceRecord instance = service.blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            service.activeInstances.remove(instanceId);
            return;
        }
        SfxElectricMachineDefinition definition = service.registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
            service.activeInstances.remove(instanceId);
            return;
        }
        SfxElectricMachineState state = service.currentState(instanceId, instance);
        SfxElectricMachineSession session = service.sessionsByInstance.get(instanceId);
        if (session != null && session.inventoryMutationPending()) {
            service.activeInstances.add(instanceId);
            return;
        }
        if (session != null) {
            service.syncInventoryToState(session.inventory(), state);
        }
        Location frameworkLocation = service.locationFor(instance);
        Map<String, Object> frameworkAttributes = service.electricFrameworkAttributes(definition, state, session);
        SfxMachineState frameworkState = new SfxMachineState();
        try (SfxMachineExecution machineExecution = service.machineRuntime.beginTick(instanceId, definition.id(), frameworkLocation, context, frameworkState, frameworkAttributes)) {
        if (!machineExecution.canProceed()) {
            service.activeInstances.add(instanceId);
            return;
        }
        if (!state.enabled()) {
            SfxElectricMachineRenderStatus paused = SfxElectricMachineRenderStatus.PAUSED;
            if (session != null && service.shouldRenderSession(session, paused)) {
                service.render(session, definition, session.inventory(), state, service.recipeProcessor.activeRecipe(definition, state), paused);
            }
            if (session != null || state.hasProgress() || state.hasAnyInput()) {
                service.activeInstances.add(instanceId);
            } else {
                service.activeInstances.remove(instanceId);
            }
            machineExecution.status(cc.theends6.sfx.internal.machine.SfxMachineStatus.PAUSED);
            return;
        }

        Location location = frameworkLocation;
        if (location == null || !service.isInstanceChunkLoaded(instance)) {
            service.activeInstances.add(instanceId);
            return;
        }
        SfxElectricMachineTickResult customResult = location == null ? null : service.runFrameworkSpecialOperation(instanceId, definition, state, session, location, context, frameworkAttributes);
        if (customResult != null) {
            frameworkAttributes.put("electric.renderStatus", customResult.status());
            if (customResult.consumedEnergy() > 0) {
                service.recentEnergyConsumption.merge(instanceId, customResult.consumedEnergy(), Integer::sum);
            }
            if (customResult.supplementalEnergy() > 0) {
                service.supplementalEnergyThisSecond.merge(instanceId, customResult.supplementalEnergy(), Integer::sum);
            }
            if (customResult.changed()) {
                service.dirtyInstances.add(instanceId);
            }
            if (customResult.changed() || customResult.status() == SfxElectricMachineRenderStatus.WORKING) {
                SfxMachinePhaseResult complete = service.machineRuntime.runPhase(definition.id(), SfxMachinePhase.ON_COMPLETE, instanceId, location, context, null, SfxElectricMachineFrameworkBridge.status(customResult.status()), frameworkAttributes);
                cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard.proceed(complete, frameworkAttributes, SfxMachinePhase.ON_COMPLETE.name());
            }
            if (session != null && service.shouldRenderSession(session, customResult.status())) {
                SfxElectricRecipe renderRecipe = definition.hasFunction("simple-io")
                        || definition.hasFunction("geo-miner")
                        || definition.hasFunction("assembler")
                        || definition.hasFunction("auto-brewer")
                        || definition.hasFunction("auto-crafter")
                        ? null
                        : service.recipeProcessor.activeRecipe(definition, state);
                service.render(session, definition, session.inventory(), state, renderRecipe, customResult.status());
            }
            if (customResult.keepActive() || state.hasAnyInput()) {
                service.activeInstances.add(instanceId);
            } else if (session == null && !state.hasProgress()) {
                service.activeInstances.remove(instanceId);
            }
            machineExecution.status(SfxElectricMachineFrameworkBridge.status(customResult.status()));
            return;
        }

        SfxMachinePhaseResult beforeOperation = service.machineRuntime.runPhase(definition.id(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instanceId, location, context, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, frameworkAttributes);
        if (!cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard.proceed(beforeOperation, frameworkAttributes, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
            machineExecution.status(beforeOperation.status() == null ? cc.theends6.sfx.internal.machine.SfxMachineStatus.BLOCKED : beforeOperation.status());
            service.activeInstances.add(instanceId);
            return;
        }
        SfxElectricRecipe activeRecipe = frameworkAttributes.get("electric.activeRecipe") instanceof SfxElectricRecipe frameworkActiveRecipe
                ? frameworkActiveRecipe
                : service.recipeProcessor.activeRecipe(definition, state);
        SfxElectricMachineRenderStatus status = SfxElectricMachineRenderStatus.IDLE;

        if (state.hasPendingOutput()) {
            SfxElectricStack pendingOutput = state.pendingOutput();
            Integer outputSlot = pendingOutput == null ? null : service.recipeProcessor.findOutputSlot(definition, state, pendingOutput);
            if (pendingOutput != null && outputSlot != null) {
                service.recipeProcessor.pushOutput(state, outputSlot, pendingOutput);
                state.resetProgress();
                service.dirtyInstances.add(instanceId);
                service.runFrameworkOutputCommit(instanceId, definition, location, context, frameworkAttributes, SfxElectricMachineRenderStatus.WORKING);
                service.playCompleteSound(session);
                SfxElectricRecipeStart nextStart = service.recipeProcessor.tryStartNextRecipe(definition, state);
                if (nextStart != null) {
                    activeRecipe = nextStart.recipe();
                    status = SfxElectricMachineRenderStatus.WORKING;
                    service.activeInstances.add(instanceId);
                } else {
                    activeRecipe = null;
                    status = state.hasAnyInput() ? service.recipeProcessor.deriveStatus(definition, state) : SfxElectricMachineRenderStatus.IDLE;
                }
            } else {
                status = SfxElectricMachineRenderStatus.BLOCKED_OUTPUT;
                service.activeInstances.add(instanceId);
            }
        } else if (activeRecipe == null) {
            SfxElectricRecipeMatch match = frameworkAttributes.get("electric.recipeMatch") instanceof SfxElectricRecipeMatch frameworkMatch
                    ? frameworkMatch
                    : service.recipeProcessor.findRecipeMatch(definition, state);
            if (match == null) {
                if (state.hasProgress()) {
                    state.resetProgress();
                    service.dirtyInstances.add(instanceId);
                }
                status = state.hasAnyInput() ? SfxElectricMachineRenderStatus.NO_RECIPE : SfxElectricMachineRenderStatus.IDLE;
            } else {
                frameworkAttributes.put("electric.recipeMatch", match);
                SfxMachinePhaseResult outputReserve = service.machineRuntime.runPhase(definition.id(), SfxMachinePhase.BEFORE_OUTPUT, instanceId, location, context, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, frameworkAttributes);
                boolean outputFits = frameworkAttributes.get("electric.outputFit") instanceof Boolean frameworkOutputFit
                        ? frameworkOutputFit
                        : service.recipeProcessor.canFitOutputForRecipe(definition, state, match.recipe());
                if (outputReserve.stopsPipeline() || !outputFits) {
                    status = SfxElectricMachineRenderStatus.OUTPUT_FULL;
                } else {
                    SfxElectricRecipeStart start = service.recipeProcessor.tryStartNextRecipe(definition, state);
                    if (start != null) {
                        activeRecipe = start.recipe();
                        status = SfxElectricMachineRenderStatus.WORKING;
                        service.activeInstances.add(instanceId);
                    } else {
                        activeRecipe = null;
                        status = service.recipeProcessor.deriveStatus(definition, state);
                    }
                    service.dirtyInstances.add(instanceId);
                }
            }
        } else {
            service.activeInstances.add(instanceId);
            if (!state.hasReservedInput()) {
                state.resetProgress();
                service.dirtyInstances.add(instanceId);
                status = state.hasAnyInput() ? service.recipeProcessor.deriveStatus(definition, state) : SfxElectricMachineRenderStatus.IDLE;
            } else {
                int totalWork = service.recipeProcessor.requiredWork(activeRecipe);
                if (state.progressWork() >= totalWork) {
                    status = service.completeActiveRecipe(instanceId, state, activeRecipe, definition, session, location, context, frameworkAttributes);
                } else {
                    int elapsed = Math.max(1, context.elapsedTicksInt());
                    int speed = Math.max(1, definition.speed());
                    int remainingWork = Math.max(0, totalWork - state.progressWork());
                    int ticksNeeded = Math.max(1, (remainingWork + speed - 1) / speed);
                    int progressTicks = Math.min(elapsed, ticksNeeded);
                    int energyPerTick = Math.max(0, definition.energyConsumptionPerTick());
                    if (energyPerTick > 0) {
                        progressTicks = Math.min(progressTicks, state.storedEnergy() / energyPerTick);
                    }
                    if (progressTicks <= 0) {
                        status = SfxElectricMachineRenderStatus.NO_POWER;
                    } else {
                        if (energyPerTick > 0) {
                            int consumed = energyPerTick * progressTicks;
                            state.storedEnergy(state.storedEnergy() - consumed);
                            service.recentEnergyConsumption.merge(instanceId, consumed, Integer::sum);
                        }
                        int progressed = Math.min(totalWork, state.progressWork() + speed * progressTicks);
                        state.progressWork(progressed);
                        status = progressed >= totalWork
                                ? service.completeActiveRecipe(instanceId, state, activeRecipe, definition, session, location, context, frameworkAttributes)
                                : SfxElectricMachineRenderStatus.WORKING;
                    }
                }
                service.dirtyInstances.add(instanceId);
            }
        }

        machineExecution.status(SfxElectricMachineFrameworkBridge.status(status));
        if (session != null && service.shouldRenderSession(session, status)) {
            service.render(session, definition, session.inventory(), state, service.recipeProcessor.activeRecipe(definition, state), status);
        }
        if (session == null && !state.hasAnyInput() && !state.hasProgress()) {
            service.activeInstances.remove(instanceId);
        }
        }
    
    }
}
