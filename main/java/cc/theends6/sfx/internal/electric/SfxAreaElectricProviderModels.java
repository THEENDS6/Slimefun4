package cc.theends6.sfx.internal.electric;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;

abstract class WorldActionProvider implements SfxElectricRecipeProvider {
    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasWorldAction() {
        return true;
    }
}

abstract class SpecialProvider implements SfxElectricRecipeProvider {
    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasSpecialTick() {
        return true;
    }

    @Override
    public int specialTickIntervalTicks() {
        return 1;
    }
}

record FluidPumpAction(SfxElectricMachineRenderStatus status, int inputSlot, SfxElectricStack output, Block source, boolean consumeSource) {
    static FluidPumpAction status(SfxElectricMachineRenderStatus status) {
        return new FluidPumpAction(status, -1, null, null, false);
    }
}

record FluidPoolCacheKey(UUID worldId, int x, int y, int z, Material fluid, int threshold) {
}

record FluidPoolCacheEntry(long checkedTick, boolean largeEnough) {
}

record FluidPumpSourceCacheKey(UUID worldId, int x, int y, int z, Material container) {
}

record FluidPumpSourceCacheEntry(long checkedTick, boolean found, Material fluid, int x, int y, int z) {
}

record AssemblerStart(SfxElectricMachineRenderStatus status, List<SfxElectricStack> reservedInputs, int primaryInputSlot) {
    static AssemblerStart status(SfxElectricMachineRenderStatus status) {
        return new AssemblerStart(status, List.of(), -1);
    }
}

record AssemblerConsume(int slot, int amount) {
}

enum ProduceStartStatus {
    NO_INPUT,
    NO_TARGET,
    OUTPUT_FULL,
    READY
}

enum ProduceAction {
    MILK("milk"),
    STEW("stew");

    private final String key;

    ProduceAction(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }

    static ProduceAction fromKey(String key) {
        if (key == null) {
            return null;
        }
        String prefix = "sf:produce_collector:";
        if (!key.startsWith(prefix)) {
            return null;
        }
        String actionKey = key.substring(prefix.length()).toLowerCase(Locale.ROOT);
        for (ProduceAction action : values()) {
            if (action.key.equals(actionKey)) {
                return action;
            }
        }
        return null;
    }
}

record ProduceStart(ProduceStartStatus status, int inputSlot, ProduceAction action, SfxElectricStack primaryOutput) {
    static ProduceStart noInput() {
        return new ProduceStart(ProduceStartStatus.NO_INPUT, -1, null, null);
    }

    static ProduceStart noTarget() {
        return new ProduceStart(ProduceStartStatus.NO_TARGET, -1, null, null);
    }

    static ProduceStart outputFull() {
        return new ProduceStart(ProduceStartStatus.OUTPUT_FULL, -1, null, null);
    }

    static ProduceStart ready(int inputSlot, ProduceAction action, SfxElectricStack primaryOutput) {
        return new ProduceStart(ProduceStartStatus.READY, inputSlot, action, primaryOutput);
    }
}

record ProduceTarget(List<SfxElectricStack> outputs, Runnable apply) {
}

record ProduceCompletion(SfxElectricMachineRenderStatus status) {
    static ProduceCompletion status(SfxElectricMachineRenderStatus status) {
        return new ProduceCompletion(status);
    }
}

record FlushResult(boolean changed, int consumedEnergy) {
}
