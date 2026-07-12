package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxBufferedResource;
import cc.theends6.sfx.internal.energy.SfxDynamicEnergyGeneratorProvider;
import cc.theends6.sfx.internal.energy.SfxEnergyComponentDefinition;
import cc.theends6.sfx.internal.energy.SfxEnergyGeneratorAccess;
import cc.theends6.sfx.internal.energy.SfxEnergyNodeState;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxOxidizingGeneratorProvider implements SfxDynamicEnergyGeneratorProvider {
    private static final SfxBufferedResource SALT = new SfxBufferedResource("salt", 600, 6000, false, true);
    private static final String SALT_ID = "sf:salt";
    private static final String ZINC_INGOT = "sf:zinc_ingot";
    private static final String ZINC_DUST = "sf:zinc_dust";
    private static final String MAGNESIUM_INGOT = "sf:magnesium_ingot";
    private static final String MAGNESIUM_DUST = "sf:magnesium_dust";
    private static final String MAGNESIUM_SALT = "sf:magnesium_salt";

    @Override
    public int potentialGeneration(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        if (state.hasActiveFuel()) {
            return parseActive(state.activeFuelKey()).generation();
        }
        Plan plan = plan(items, definition, state);
        return plan == null ? 0 : plan.generation();
    }

    @Override
    public int generate(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access) {
        boolean changed = refillSalt(state);
        if (!state.hasActiveFuel()) {
            Plan plan = plan(items, definition, state);
            if (plan == null) {
                if (changed) access.markDirty();
                return 0;
            }
            if (plan.output() != null && !access.hasOutputSpace(definition, state, plan.output())) {
                if (changed) access.markDirty();
                return 0;
            }
            if (plan.waterBucket() && !access.hasOutputSpace(definition, state, SfxElectricStack.vanilla(Material.BUCKET, 1))) {
                if (changed) access.markDirty();
                return 0;
            }
            consumeOne(state, 0);
            if (plan.waterBucket()) {
                consumeOne(state, 1);
                access.pushOutput(definition, state, SfxElectricStack.vanilla(Material.BUCKET, 1));
            }
            if (plan.consumeSaltTicks() > 0) {
                state.specialData(Math.max(0, state.specialData() - plan.consumeSaltTicks()));
            }
            state.activeFuelKey(plan.encode());
            state.fuelProgressTenths(0);
            state.fuelTotalTenths(plan.ticks());
            access.markDirty();
        }

        Plan active = parseActive(state.activeFuelKey());
        if (active.generation() <= 0 || active.ticks() <= 0) {
            state.clearFuelOperation();
            access.markDirty();
            return 0;
        }
        if (definition.capacity() > 0 && state.storedEnergy() + active.generation() > definition.capacity()) {
            return 0;
        }
        int nextProgress = state.fuelProgressTenths() + 1;
        if (nextProgress >= state.fuelTotalTenths() && active.output() != null && !access.hasOutputSpace(definition, state, active.output())) {
            return 0;
        }
        state.fuelProgressTenths(nextProgress);
        if (nextProgress >= state.fuelTotalTenths()) {
            if (active.output() != null) {
                access.pushOutput(definition, state, active.output());
            }
            state.clearFuelOperation();
        }
        access.markDirty();
        return active.generation();
    }

    @Override
    public List<SfxElectricStack> dropsOnDestroy(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        int salts = SALT.refundableItemsFloor(state.specialData());
        return salts <= 0 ? List.of() : List.of(SfxElectricStack.sfx(SALT_ID, salts));
    }

    private boolean refillSalt(SfxEnergyNodeState state) {
        SfxElectricStack input = state.input(1);
        if (input == null || !input.isSfxItem() || !SALT_ID.equals(input.itemId())) {
            return false;
        }
        int accepted = SALT.acceptedUnits(state.specialData(), input.amount());
        if (accepted <= 0) {
            return false;
        }
        state.specialData(SALT.addUnits(state.specialData(), accepted));
        int remaining = input.amount() - accepted;
        state.input(1, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return true;
    }

    private Plan plan(SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        SfxElectricStack main = state.input(0);
        if (main == null || main.amount() <= 0) {
            return null;
        }
        SfxElectricStack one = main.copyWithAmount(1);
        boolean water = isWaterBucket(state.input(1));
        boolean powder = isPowder(one);
        double value = SfxCopperVariants.copperValue(one);
        double generation = 24.0D;
        double ticks = 80.0D * Math.max(1.0D, value);
        boolean saltBonus = false;
        int saltConsume = 0;
        SfxElectricStack output = null;

        if (value > 0.0D) {
            output = SfxCopperVariants.oxidizedProduct(items, one, water);
            if (output == null) {
                return null;
            }
        } else if (isZinc(one)) {
            generation *= 1.5D;
        } else if (isMagnesium(one)) {
            generation *= 3.0D;
            ticks *= 0.8D;
            saltBonus = MAGNESIUM_SALT.equals(one.itemId());
        } else {
            return null;
        }

        if (powder) {
            generation *= 2.0D;
            ticks /= 3.0D;
        }
        if (water) {
            generation *= 4.0D;
            ticks *= 0.8D;
        }
        int plannedTicks = Math.max(1, (int) Math.ceil(ticks));
        if (saltBonus || state.specialData() >= plannedTicks) {
            generation *= 1.5D;
            if (!saltBonus) {
                saltConsume = plannedTicks;
            }
        }
        return new Plan(Math.max(1, (int) Math.ceil(generation)), Math.max(1, (int) Math.ceil(ticks)), output, water, saltConsume);
    }

    private Plan parseActive(String key) {
        if (key == null || !key.startsWith("oxidizing|")) {
            return new Plan(0, 0, null, false, 0);
        }
        String[] parts = key.split("\\|", -1);
        int generation = integer(parts, 1);
        int ticks = integer(parts, 2);
        SfxElectricStack output = null;
        if (parts.length >= 6 && !parts[3].isBlank()) {
            output = "sfx".equals(parts[3])
                    ? SfxElectricStack.sfx(parts[4], 1)
                    : SfxElectricStack.vanilla(Material.valueOf(parts[4]), 1);
        }
        return new Plan(generation, ticks, output, false, 0);
    }

    private void consumeOne(SfxEnergyNodeState state, int slot) {
        SfxElectricStack input = state.input(slot);
        if (input == null) return;
        state.input(slot, input.amount() <= 1 ? null : input.copyWithAmount(input.amount() - 1));
    }

    private boolean isWaterBucket(SfxElectricStack stack) {
        return stack != null && !stack.isSfxItem() && !stack.hasSnapshot() && stack.material() == Material.WATER_BUCKET;
    }

    private boolean isPowder(SfxElectricStack stack) {
        if (stack == null) return false;
        return stack.isSfxItem() && (stack.itemId().endsWith("_dust") || stack.itemId().contains("copper_dust"));
    }

    private boolean isZinc(SfxElectricStack stack) {
        return stack.isSfxItem() && (ZINC_INGOT.equals(stack.itemId()) || ZINC_DUST.equals(stack.itemId()));
    }

    private boolean isMagnesium(SfxElectricStack stack) {
        return stack.isSfxItem() && (MAGNESIUM_INGOT.equals(stack.itemId()) || MAGNESIUM_DUST.equals(stack.itemId()) || MAGNESIUM_SALT.equals(stack.itemId()));
    }

    private int integer(String[] parts, int index) {
        if (parts.length <= index || parts[index].isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private record Plan(int generation, int ticks, SfxElectricStack output, boolean waterBucket, int consumeSaltTicks) {
        String encode() {
            String outputKind = "";
            String outputId = "";
            if (output != null) {
                outputKind = output.isSfxItem() ? "sfx" : "material";
                outputId = output.isSfxItem() ? output.itemId() : output.material().name();
            }
            return "oxidizing|" + generation + "|" + ticks + "|" + outputKind + "|" + outputId + "|";
        }
    }
}
