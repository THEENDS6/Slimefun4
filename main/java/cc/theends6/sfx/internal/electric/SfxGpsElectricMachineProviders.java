package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.gps.SfxGpsElectricBridge;
import cc.theends6.sfx.internal.gps.SfxGpsExtractionResult;
import cc.theends6.sfx.api.machine.runtime.SfxMachineTickContext;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxGpsElectricMachineProviders {
    private SfxGpsElectricMachineProviders() {
    }

    static SfxElectricRecipeProvider transmitter() {
        return new SfxElectricRecipeProvider() {
            @Override
            public List<SfxElectricRecipe> recipes() {
                return List.of();
            }

            @Override
            public boolean hasSpecialTick() {
                return true;
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!state.enabled()) {
                    return 0;
                }
                return Math.max(0, definition.energyConsumptionPerTick());
            }

            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
                int perTick = Math.max(0, definition.energyConsumptionPerTick());
                if (!state.enabled()) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.PAUSED, true);
                }
                if (perTick <= 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.WORKING, true);
                }
                int elapsed = Math.max(1, context.elapsedTicksInt());
                int required = Math.max(1, perTick * elapsed);
                int consumed = Math.min(required, state.storedEnergy());
                if (consumed <= 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
                }
                state.storedEnergy(state.storedEnergy() - consumed);
                return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, consumed, true);
            }
        };
    }

    static SfxElectricRecipeProvider geoExtractor(boolean oilOnly) {
        return new SfxElectricRecipeProvider() {
            private static final int GEO_WORK_TICKS = 40;
            private static final int OIL_WORK_TICKS = 520;

            @Override
            public List<SfxElectricRecipe> recipes() {
                return List.of();
            }

            @Override
            public boolean hasSpecialTick() {
                return true;
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!state.enabled()) {
                    return 0;
                }
                if (oilOnly && findBucketInput(state) < 0) {
                    return 0;
                }
                SfxGpsExtractionResult result = SfxGpsElectricBridge.peekExtraction(location, oilOnly);
                if (!result.scanned() || !result.hasResource() || result.output() == null) {
                    return 0;
                }
                if (findOutputSlot(definition, state, result.output(), items) < 0) {
                    return 0;
                }
                return definition.energyConsumptionPerTick();
            }

            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
                if (!state.enabled()) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.PAUSED, true);
                }
                int bucketSlot = oilOnly ? findBucketInput(state) : -2;
                int workTicks = oilOnly ? OIL_WORK_TICKS : GEO_WORK_TICKS;
                if (oilOnly && bucketSlot < 0) {
                    clearWork(state);
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                SfxGpsExtractionResult peek = SfxGpsElectricBridge.peekExtraction(location, oilOnly);
                if (!peek.scanned()) {
                    clearWork(state);
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.CHUNK_NOT_SCANNED, false);
                }
                if (!peek.hasResource() || peek.output() == null) {
                    clearWork(state);
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_GEO_RESOURCE, false);
                }
                int outputSlot = findOutputSlot(definition, state, peek.output(), items);
                if (outputSlot < 0) {
                    state.activeBaseTicks(workTicks);
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
                }
                state.activeBaseTicks(workTicks);
                int energyPerTick = Math.max(1, definition.energyConsumptionPerTick());
                int elapsed = Math.max(1, context.elapsedTicksInt());
                int affordableTicks = state.storedEnergy() / energyPerTick;
                int progressTicks = Math.min(elapsed, affordableTicks);
                if (progressTicks <= 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
                }
                int nextProgress = Math.min(workTicks, state.progressWork() + progressTicks);
                int consumed = progressTicks * energyPerTick;
                state.storedEnergy(state.storedEnergy() - consumed);
                state.progressWork(nextProgress);
                if (nextProgress < workTicks) {
                    return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, consumed, true);
                }
                if (oilOnly) {
                    bucketSlot = findBucketInput(state);
                    if (bucketSlot < 0) {
                        clearWork(state);
                        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.NO_INPUT, consumed, false);
                    }
                }
                outputSlot = findOutputSlot(definition, state, peek.output(), items);
                if (outputSlot < 0) {
                    state.progressWork(workTicks);
                    state.activeBaseTicks(workTicks);
                    return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.OUTPUT_FULL, consumed, true);
                }
                SfxGpsExtractionResult result = SfxGpsElectricBridge.consumeExtraction(location, oilOnly);
                if (!result.scanned()) {
                    clearWork(state);
                    return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.CHUNK_NOT_SCANNED, consumed, false);
                }
                if (!result.hasResource() || result.output() == null) {
                    clearWork(state);
                    return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.NO_GEO_RESOURCE, consumed, false);
                }
                outputSlot = findOutputSlot(definition, state, result.output(), items);
                if (outputSlot < 0) {
                    state.progressWork(workTicks);
                    return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.OUTPUT_FULL, consumed, true);
                }
                if (oilOnly) {
                    consumeOneBucket(state, bucketSlot);
                }
                pushOutput(state, outputSlot, result.output());
                clearWork(state);
                return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, consumed, true);
            }

            private void clearWork(SfxElectricMachineState state) {
                state.progressWork(0);
                state.activeBaseTicks(0);
            }

            private int findBucketInput(SfxElectricMachineState state) {
                for (int slot = 0; slot < state.inputCapacity(); slot++) {
                    SfxElectricStack input = state.input(slot);
                    if (input != null && !input.isSfxItem() && input.material() == Material.BUCKET && !input.hasSnapshot() && input.amount() > 0) {
                        return slot;
                    }
                }
                return -1;
            }

            private void consumeOneBucket(SfxElectricMachineState state, int slot) {
                SfxElectricStack input = state.input(slot);
                if (input == null) {
                    return;
                }
                int remaining = input.amount() - 1;
                state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
            }

            private int findOutputSlot(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack output, SfxItems items) {
                int[] slots = definition.outputSlots();
                for (int index = 0; index < slots.length; index++) {
                    SfxElectricStack current = state.output(index);
                    if (current != null && output.canMerge(current, items)) {
                        return index;
                    }
                }
                for (int index = 0; index < slots.length; index++) {
                    if (state.output(index) == null) {
                        return index;
                    }
                }
                return -1;
            }

            private void pushOutput(SfxElectricMachineState state, int slot, SfxElectricStack output) {
                SfxElectricStack current = state.output(slot);
                if (current == null) {
                    state.output(slot, output);
                } else {
                    state.output(slot, current.copyWithAmount(current.amount() + output.amount()));
                }
            }
        };
    }
}
