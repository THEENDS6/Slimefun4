package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxBufferedResource;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.internal.energy.SfxDynamicEnergyGeneratorProvider;
import cc.theends6.sfx.internal.energy.SfxEnergyComponentDefinition;
import cc.theends6.sfx.internal.energy.SfxEnergyGeneratorAccess;
import cc.theends6.sfx.internal.energy.SfxEnergyNodeState;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;
import java.util.Map;
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
    private static final int MAIN_INPUT_FIRST = 0;
    private static final int MAIN_INPUT_SECOND = 1;
    private static final int WATER_INPUT = 2;
    private static final int SALT_INPUT = 3;
    private static final int WATER_DISPLAY_SLOT = 1;
    private static final int SALT_DISPLAY_SLOT = 7;

    @Override
    public int potentialGeneration(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        if (state.hasActiveFuel()) {
            return generationFor(parseActive(state.activeFuelKey()), state);
        }
        Plan plan = plan(items, definition, state);
        return plan == null ? 0 : generationFor(plan, state);
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
            consumeOne(state, plan.mainInput());
            if (plan.waterBucket()) {
                consumeOne(state, WATER_INPUT);
                access.pushOutput(definition, state, SfxElectricStack.vanilla(Material.BUCKET, 1));
            }
            state.activeFuelKey(plan.encode());
            state.fuelProgressTenths(0);
            state.fuelTotalTenths(plan.ticks());
            access.markDirty();
        }

        Plan active = parseActive(state.activeFuelKey());
        int generation = generationFor(active, state);
        if (generation <= 0 || active.ticks() <= 0) {
            state.clearFuelOperation();
            access.markDirty();
            return 0;
        }
        if (definition.capacity() > 0 && state.storedEnergy() + generation > definition.capacity()) {
            return 0;
        }
        if (!active.freeSaltBonus() && state.specialData() > 0) {
            state.specialData(state.specialData() - 1);
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
        return generation;
    }

    @Override
    public List<SfxElectricStack> dropsOnDestroy(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        int salts = SALT.refundableItemsFloor(state.specialData());
        return salts <= 0 ? List.of() : List.of(SfxElectricStack.sfx(SALT_ID, salts));
    }

    private boolean refillSalt(SfxEnergyNodeState state) {
        SfxElectricStack input = state.input(SALT_INPUT);
        if (input == null || !input.isSfxItem() || !SALT_ID.equals(input.itemId())) {
            return false;
        }
        int accepted = SALT.acceptedUnits(state.specialData(), input.amount());
        if (accepted <= 0) {
            return false;
        }
        state.specialData(SALT.addUnits(state.specialData(), accepted));
        int remaining = input.amount() - accepted;
        state.input(SALT_INPUT, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return true;
    }

    private Plan plan(SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        int mainInput = state.hasInput(MAIN_INPUT_FIRST) ? MAIN_INPUT_FIRST : MAIN_INPUT_SECOND;
        SfxElectricStack main = state.input(mainInput);
        if (main == null || main.amount() <= 0) {
            return null;
        }
        SfxElectricStack one = main.copyWithAmount(1);
        boolean water = isWaterBucket(state.input(WATER_INPUT));
        boolean powder = isPowder(one);
        double value = SfxCopperVariants.copperValue(one);
        double generation = 24.0D;
        double ticks = 80.0D * Math.max(1.0D, value);
        boolean saltBonus = false;
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
        return new Plan(Math.max(1, (int) Math.ceil(generation)), plannedTicks, output, water, saltBonus, mainInput);
    }

    private Plan parseActive(String key) {
        if (key == null || !key.startsWith("oxidizing|")) {
            return new Plan(0, 0, null, false, false, -1);
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
        boolean freeSaltBonus = parts.length >= 7 && Boolean.parseBoolean(parts[6]);
        return new Plan(generation, ticks, output, false, freeSaltBonus, -1);
    }

    private int generationFor(Plan plan, SfxEnergyNodeState state) {
        if (plan == null) {
            return 0;
        }
        double generation = plan.generation();
        if (plan.freeSaltBonus() || state.specialData() > 0) {
            generation *= 1.5D;
        }
        return Math.max(0, (int) Math.ceil(generation));
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

    @Override
    public Map<Integer, SfxMachineDisplayItem> displayItems(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        boolean stored = state.specialData() > 0;
        return Map.of(
                WATER_DISPLAY_SLOT, new SfxMachineDisplayItem(
                        Material.POTION,
                        "content-expansion.ui.water.name",
                        List.of("content-expansion.ui.water.insert", "content-expansion.ui.water.effect", "content-expansion.ui.water.output"),
                        Map.of(),
                        false,
                        0,
                        0),
                SALT_DISPLAY_SLOT, new SfxMachineDisplayItem(
                        stored ? Material.GLOWSTONE_DUST : Material.SUGAR,
                        "content-expansion.ui.salt.name",
                        List.of("content-expansion.ui.salt.amount", "content-expansion.ui.salt.items", "content-expansion.ui.salt.effect", "content-expansion.ui.salt.insert"),
                        Map.of(
                                "stored", state.specialData(),
                                "capacity", SALT.maxAmount(),
                                "items", SALT.refundableItemsFloor(state.specialData()),
                                "capacity_items", SALT.refundableItemsFloor(SALT.maxAmount())),
                        stored,
                        state.specialData(),
                        SALT.maxAmount()));
    }

    private record Plan(int generation, int ticks, SfxElectricStack output, boolean waterBucket, boolean freeSaltBonus, int mainInput) {
        String encode() {
            String outputKind = "";
            String outputId = "";
            if (output != null) {
                outputKind = output.isSfxItem() ? "sfx" : "material";
                outputId = output.isSfxItem() ? output.itemId() : output.material().name();
            }
            return "oxidizing|" + generation + "|" + ticks + "|" + outputKind + "|" + outputId + "||" + freeSaltBonus;
        }
    }
}
