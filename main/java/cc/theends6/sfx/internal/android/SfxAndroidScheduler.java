package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;




final class SfxAndroidScheduler {
    private SfxAndroidScheduler() {
    }

    static void tickAndroids(SfxAndroidService service, long tickId) {
        List<SfxBlockInstanceRecord> active = new ArrayList<>();
        for (UUID instanceId : service.androidSchedule.poll(tickId)) {
            if (!service.activeAndroids.contains(instanceId)) {
                continue;
            }
            SfxBlockInstanceRecord instance = service.blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                service.forgetAndroid(instanceId);
                service.states.remove(instanceId);
                continue;
            }
            SfxAndroidState state = service.stateFor(instance.instanceId(), instance.typeId(), service.toLocation(instance.anchorKey()));
            if (state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED) {
                service.forgetAndroid(instanceId);
                continue;
            }
            if (service.freezeUnloadedMachines() && !service.isInstanceChunkLoaded(instance)) {
                service.rescheduleAndroid(instanceId, tickId);
                continue;
            }
            if (service.shouldSkipForBackoff(state, tickId)) {
                service.rescheduleAndroid(instanceId, tickId);
                continue;
            }
            active.add(instance);
        }
        Map<String, List<SfxBlockInstanceRecord>> groups = new HashMap<>();
        for (SfxBlockInstanceRecord instance : active) {
            String key = instance.anchorKey().worldId() + ":" + (instance.anchorKey().x() >> 4) + ":" + (instance.anchorKey().z() >> 4);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(instance);
        }
        for (List<SfxBlockInstanceRecord> group : groups.values()) {
            if (group.isEmpty()) {
                continue;
            }
            group.sort(Comparator.comparing(SfxBlockInstanceRecord::instanceId));
            SfxBlockInstanceRecord first = group.get(0);
            Location location = service.toLocation(first.anchorKey());
            if (location == null) {
                for (SfxBlockInstanceRecord instance : group) {
                    service.rescheduleAndroid(instance.instanceId(), tickId);
                }
                continue;
            }
            List<SfxBlockInstanceRecord> snapshot = group.size() > service.maxActivePerRegion
                    ? List.copyOf(group.subList(0, service.maxActivePerRegion))
                    : List.copyOf(group);
            for (SfxBlockInstanceRecord instance : group) {
                if (!snapshot.contains(instance)) {
                    service.rescheduleAndroid(instance.instanceId(), tickId);
                }
            }
            service.runtime.executeAt(location, () -> {
                boolean hookEntered = false;
                try {
                    SfxMachineLegacyHookBridge.beforeNetworkTick(service.machineRuntime, "sf:android", first.instanceId(), location, "android", "SfxAndroidService.tickAndroids");
                    hookEntered = true;
                    service.tickRegionBatch(snapshot, tickId);
                } finally {
                    for (SfxBlockInstanceRecord instance : snapshot) {
                        service.rescheduleAndroid(instance.instanceId(), tickId);
                    }
                    if (hookEntered) {
                        SfxMachineLegacyHookBridge.afterNetworkTick(service.machineRuntime, "sf:android", first.instanceId(), location, "android", "SfxAndroidService.tickAndroids");
                    }
                }
            });
        }
    
    }
}
