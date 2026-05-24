package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;




final class SfxAndroidBatchTickController {
    private SfxAndroidBatchTickController() {
    }

    static void tickRegionBatch(SfxAndroidService service, List<SfxBlockInstanceRecord> instances, long tickId) {
        Map<UUID, MoveIntent> moveIntents = new HashMap<>();
        Set<UUID> runnable = new HashSet<>();
        Set<LocationKey> occupiedBefore = new HashSet<>();
        Map<UUID, SfxAndroidState> batchStates = new HashMap<>();
        for (SfxBlockInstanceRecord instance : instances) {
            Location from = service.toLocation(instance.anchorKey());
            if (from == null) {
                continue;
            }
            occupiedBefore.add(LocationKey.of(from));
            SfxAndroidState state = service.stateFor(instance.instanceId(), instance.typeId(), from);
            batchStates.put(instance.instanceId(), state);
            SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
            if (type == null || state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED) {
                continue;
            }
            SfxAndroidInstruction instruction = state.currentInstruction();
            if (!instruction.validFor(type)) {
                state.runtimeState(SfxAndroidRuntimeState.DORMANT_SCRIPT_INVALID);
                service.activeAndroids.remove(instance.instanceId());
                service.persist(instance.instanceId(), state);
                continue;
            }
            boolean fuelInterfaceBootstrap = instruction == SfxAndroidInstruction.INTERFACE_FUEL
                    && state.fuelTicks() <= 0
                    && service.fuelValue(state.fuelSlot(), type) <= 0;
            if (!fuelInterfaceBootstrap && !service.ensureFuel(instance, state, type, from.getBlock())) {
                continue;
            }
            if (fuelInterfaceBootstrap) {
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            }
            runnable.add(instance.instanceId());
            BlockFace face = service.actionFacing(state);
            Block target = service.targetBlock(from.getBlock(), face, instruction);
            if (service.isMoveInstruction(instruction)) {
                boolean clearsTarget = service.isMoveAndDigInstruction(instruction) && service.canClearTargetForMoveAndDig(state, target);
                moveIntents.put(instance.instanceId(), new MoveIntent(instance, from, target.getLocation(), instruction, clearsTarget));
            }
        }
        Map<LocationKey, List<MoveIntent>> byTarget = new HashMap<>();
        Set<LocationKey> leaving = new HashSet<>();
        for (MoveIntent intent : moveIntents.values()) {
            byTarget.computeIfAbsent(LocationKey.of(intent.to), ignored -> new ArrayList<>()).add(intent);
            leaving.add(LocationKey.of(intent.from));
        }
        Set<UUID> acceptedMoves = new HashSet<>();
        for (List<MoveIntent> contenders : byTarget.values()) {
            contenders.sort(Comparator.comparing(intent -> intent.instance.instanceId()));
            MoveIntent winner = contenders.get(0);
            Block target = winner.to.getBlock();
            LocationKey targetKey = LocationKey.of(winner.to);
            boolean targetFree = target.getType().isAir()
                    || (occupiedBefore.contains(targetKey) && leaving.contains(targetKey))
                    || winner.clearsTargetBeforeMove();
            if (targetFree) {
                acceptedMoves.add(winner.instance.instanceId());
            }
        }
        for (SfxBlockInstanceRecord instance : instances) {
            Location location = service.toLocation(instance.anchorKey());
            if (location == null) {
                continue;
            }
            SfxAndroidState state = batchStates.getOrDefault(instance.instanceId(), service.stateFor(instance.instanceId(), instance.typeId(), location));
            SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
            if (type == null || !runnable.contains(instance.instanceId()) || state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED || state.runtimeState() == SfxAndroidRuntimeState.DORMANT_SCRIPT_INVALID) {
                continue;
            }
            SfxAndroidInstruction instruction = state.currentInstruction();
            Map<String, Object> frameworkAttributes = service.androidFrameworkAttributes(instance, type, state, location.getBlock(), instruction, acceptedMoves.contains(instance.instanceId()), tickId);
            frameworkAttributes.put("android.instruction.name", instruction.name());
            SfxMachineTickContext frameworkTick = new SfxMachineTickContext(tickId, 1L, false);
            service.machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instance.instanceId(), location, frameworkTick, null, SfxMachineStatus.IDLE, frameworkAttributes);
            service.machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_PROGRESS, instance.instanceId(), location, frameworkTick, null, SfxMachineStatus.IDLE, frameworkAttributes);
            boolean success = service.executeInstruction(instance, type, state, instruction, location.getBlock(), acceptedMoves.contains(instance.instanceId()), tickId);
            frameworkAttributes.put("android.execution.success", success);
            service.machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.AFTER_PROGRESS, instance.instanceId(), location, frameworkTick, null, success ? SfxMachineStatus.RUNNING : SfxMachineStatus.IDLE, frameworkAttributes);
            if (success) {
                service.machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_COMPLETE, instance.instanceId(), location, frameworkTick, null, SfxMachineStatus.RUNNING, frameworkAttributes);
                state.advance();
            }
            service.machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.AFTER_TICK, instance.instanceId(), location, frameworkTick, null, success ? SfxMachineStatus.RUNNING : SfxMachineStatus.IDLE, frameworkAttributes);
            if (state.runtimeState() == SfxAndroidRuntimeState.ACTIVE) {
                state.resetNoEffectTicks();
            } else if (!state.paused() && state.runtimeState() != SfxAndroidRuntimeState.PAUSED && state.runtimeState() != SfxAndroidRuntimeState.DORMANT_SCRIPT_INVALID) {
                state.incrementNoEffectTicks();
            }
            service.persist(service.currentInstanceId(instance, location), state);
        }
    
    }
}
