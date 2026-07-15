package cc.theends6.sfx.addons.content.runtime;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxBufferedResource;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import cc.theends6.sfx.api.energy.runtime.SfxDynamicEnergyGeneratorProvider;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyComponentDefinition;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyGeneratorAccess;
import cc.theends6.sfx.api.energy.runtime.SfxEnergyNodeState;
import cc.theends6.sfx.api.machine.runtime.SfxMachineStatusKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

public final class SfxOxidizingGeneratorProvider implements SfxDynamicEnergyGeneratorProvider {
    private static final SfxBufferedResource SALT = new SfxBufferedResource("salt", 600, 6000, false, true);
    private static final int WATER_CAPACITY = 2000;
    private static final int WATER_PER_TICK = 5;
    private static final int WATER_BUCKET_AMOUNT = 1000;
    private static final int WATER_BOTTLE_AMOUNT = 100;
    private static final int PROGRESS_SCALE = 4;
    private static final int NORMAL_PROGRESS_PER_TICK = 4;
    private static final int WATER_PROGRESS_PER_TICK = 5;
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
    private static final int MODE_DISPLAY_SLOT = 4;
    private static final int SALT_DISPLAY_SLOT = 7;
    private static final int POWER_MODE = 0;
    private static final int PRODUCTION_MODE = 1;

    @Override
    public int potentialGeneration(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location) {
        if (state.hasActiveFuel()) {
            return generationFor(parseActive(state.activeFuelKey()), state, hasUsableWater(state));
        }
        Plan plan = plan(items, state, hasUsableWater(state));
        return plan == null ? 0 : generationFor(plan, state, hasUsableWater(state));
    }

    @Override
    public int generate(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access) {
        boolean changed = refillSalt(state);

        if (!state.hasActiveFuel()) {
            WaterFill fill = waterFill(items, state);
            boolean waterAtStart = hasUsableWater(state) || fill != null;
            Plan plan = plan(items, state, waterAtStart);
            if (plan == null) {
                if (fill != null && access.hasOutputSpace(definition, state, fill.emptyContainer())) {
                    absorbWater(state, fill);
                    access.pushOutput(definition, state, fill.emptyContainer());
                    changed = true;
                }
                if (changed) access.markDirty();
                return 0;
            }
            List<SfxElectricStack> startOutputs = outputs(plan.output(), fill == null ? null : fill.emptyContainer());
            if (!access.hasOutputSpace(definition, state, startOutputs)) {
                if (changed) access.markDirty();
                return 0;
            }
            if (fill != null) {
                absorbWater(state, fill);
                access.pushOutput(definition, state, fill.emptyContainer());
            }
            consumeOne(state, plan.mainInput());
            state.activeFuelKey(plan.encode());
            state.fuelProgressTenths(0);
            state.fuelTotalTenths(plan.baseTicks() * PROGRESS_SCALE);
            access.markDirty();
        }

        Plan active = parseActive(state.activeFuelKey());
        if (active.legacy()) {
            active = migrateLegacyOperation(state, active);
            access.markDirty();
        }
        WaterFill fill = waterFill(items, state);
        if (fill != null) {
            List<SfxElectricStack> protectedOutputs = outputs(active.output(), fill.emptyContainer());
            if (access.hasOutputSpace(definition, state, protectedOutputs)) {
                absorbWater(state, fill);
                access.pushOutput(definition, state, fill.emptyContainer());
            }
        }
        boolean waterActive = hasUsableWater(state);
        int generation = generationFor(active, state, waterActive);
        if (generation <= 0 || active.baseTicks() <= 0) {
            state.clearFuelOperation();
            access.markDirty();
            return 0;
        }
        if (state.specialData3() != PRODUCTION_MODE
                && definition.capacity() > 0
                && state.storedEnergy() + generation > definition.capacity()) {
            return 0;
        }
        int nextProgress = state.fuelProgressTenths() + (waterActive ? WATER_PROGRESS_PER_TICK : NORMAL_PROGRESS_PER_TICK);
        if (nextProgress >= state.fuelTotalTenths()
                && active.output() != null
                && !access.hasOutputSpace(definition, state, active.output())) {
            return 0;
        }
        if (!active.freeSaltBonus() && state.specialData() > 0) {
            state.specialData(state.specialData() - 1);
        }
        if (waterActive) {
            state.specialData2(state.specialData2() - WATER_PER_TICK);
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
    public SfxMachineStatusKey status(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, Location location, SfxEnergyGeneratorAccess access) {
        WaterFill fill = waterFill(items, state);
        if (state.hasActiveFuel()) {
            Plan active = parseActive(state.activeFuelKey());
            if (active.output() != null && !access.hasOutputSpace(definition, state, active.output())) {
                return SfxMachineStatusKey.BLOCKED_OUTPUT;
            }
            return SfxMachineStatusKey.WORKING;
        }
        Plan plan = plan(items, state, hasUsableWater(state) || fill != null);
        if (plan == null) {
            if (fill != null && !access.hasOutputSpace(definition, state, fill.emptyContainer())) {
                return SfxMachineStatusKey.OUTPUT_FULL;
            }
            return SfxMachineStatusKey.IDLE;
        }
        if (!access.hasOutputSpace(definition, state, outputs(plan.output(), fill == null ? null : fill.emptyContainer()))) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        return SfxMachineStatusKey.IDLE;
    }

    @Override
    public String workingStatusLoreKey() {
        return "content-expansion.ui.oxidizing.active";
    }

    @Override
    public int remainingTicks(SfxEnergyNodeState state) {
        int remaining = Math.max(0, state.fuelTotalTenths() - state.fuelProgressTenths());
        int progressPerTick = hasUsableWater(state) ? WATER_PROGRESS_PER_TICK : NORMAL_PROGRESS_PER_TICK;
        return (int) Math.ceil(remaining / (double) progressPerTick);
    }

    @Override
    public boolean excludeFromAutoPause(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        return state.specialData3() == PRODUCTION_MODE;
    }

    @Override
    public int[] shiftInputSlots(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, ItemStack stack) {
        int[] slots = definition.inputSlots();
        SfxElectricStack electric = SfxElectricStack.fromItemStack(items, stack);
        if (isWaterContainer(items, electric)) {
            return new int[] {slots[WATER_INPUT]};
        }
        if (electric != null && electric.isSfxItem() && SALT_ID.equals(electric.itemId())) {
            return new int[] {slots[SALT_INPUT]};
        }
        if (isValidMain(electric)) {
            return new int[] {slots[MAIN_INPUT_FIRST], slots[MAIN_INPUT_SECOND]};
        }
        return slots;
    }

    @Override
    public boolean acceptsInput(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, int logicalSlot, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        SfxElectricStack electric = SfxElectricStack.fromItemStack(items, stack);
        return switch (logicalSlot) {
            case MAIN_INPUT_FIRST, MAIN_INPUT_SECOND -> isValidMain(electric);
            case WATER_INPUT -> isWaterContainer(items, electric);
            case SALT_INPUT -> electric != null && electric.isSfxItem() && SALT_ID.equals(electric.itemId());
            default -> false;
        };
    }

    @Override
    public boolean handleMenuClick(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, int rawSlot, ClickType clickType) {
        if (rawSlot != MODE_DISPLAY_SLOT) {
            return false;
        }
        state.specialData3(state.specialData3() == PRODUCTION_MODE ? POWER_MODE : PRODUCTION_MODE);
        return true;
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

    private WaterFill waterFill(SfxItems items, SfxEnergyNodeState state) {
        SfxElectricStack input = state.input(WATER_INPUT);
        if (isWaterBucket(input) && state.specialData2() + WATER_BUCKET_AMOUNT <= WATER_CAPACITY) {
            return new WaterFill(WATER_BUCKET_AMOUNT, SfxElectricStack.vanilla(Material.BUCKET, 1));
        }
        if (isWaterBottle(items, input) && state.specialData2() + WATER_BOTTLE_AMOUNT <= WATER_CAPACITY) {
            return new WaterFill(WATER_BOTTLE_AMOUNT, SfxElectricStack.vanilla(Material.GLASS_BOTTLE, 1));
        }
        return null;
    }

    private void absorbWater(SfxEnergyNodeState state, WaterFill fill) {
        state.specialData2(Math.min(WATER_CAPACITY, state.specialData2() + fill.amount()));
        consumeOne(state, WATER_INPUT);
    }

    private Plan plan(SfxItems items, SfxEnergyNodeState state, boolean waterAtStart) {
        int mainInput = state.hasInput(MAIN_INPUT_FIRST) ? MAIN_INPUT_FIRST : MAIN_INPUT_SECOND;
        SfxElectricStack main = state.input(mainInput);
        if (main == null || main.amount() <= 0) {
            return null;
        }
        SfxElectricStack one = main.copyWithAmount(1);
        double value = SfxCopperVariants.copperValue(one);
        double generation = 24.0D;
        double ticks;
        boolean saltBonus = false;
        SfxElectricStack output = null;

        if (value > 0.0D) {
            ticks = 120.0D * value;
            output = SfxCopperVariants.oxidizedProduct(items, one, waterAtStart);
            if (output == null) return null;
        } else if (isZinc(one)) {
            ticks = 80.0D;
            generation *= 1.5D;
        } else if (isMagnesium(one)) {
            ticks = 60.0D;
            generation *= 3.0D;
            saltBonus = MAGNESIUM_SALT.equals(one.itemId());
        } else {
            return null;
        }
        return new Plan(Math.max(1, (int) Math.ceil(generation)), Math.max(1, (int) Math.ceil(ticks)), output, waterAtStart, saltBonus, mainInput, false);
    }

    private Plan parseActive(String key) {
        if (key == null || (!key.startsWith("oxidizing|") && !key.startsWith("oxidizing2|"))) {
            return new Plan(0, 0, null, false, false, -1, false);
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
        boolean waterOutput = parts.length >= 8 && Boolean.parseBoolean(parts[7]);
        boolean legacy = key.startsWith("oxidizing|");
        if (legacy && waterOutput) {
            generation = Math.max(1, (int) Math.ceil(generation / 4.0D));
            ticks = Math.max(1, (int) Math.ceil(ticks / 0.8D));
        }
        return new Plan(generation, ticks, output, waterOutput, freeSaltBonus, -1, legacy);
    }

    private Plan migrateLegacyOperation(SfxEnergyNodeState state, Plan legacy) {
        int oldTotal = Math.max(1, state.fuelTotalTenths());
        double completed = Math.min(1.0D, state.fuelProgressTenths() / (double) oldTotal);
        int newTotal = Math.max(PROGRESS_SCALE, legacy.baseTicks() * PROGRESS_SCALE);
        state.fuelTotalTenths(newTotal);
        state.fuelProgressTenths((int) Math.round(completed * newTotal));
        Plan migrated = new Plan(legacy.baseGeneration(), legacy.baseTicks(), legacy.output(), legacy.waterOutput(), legacy.freeSaltBonus(), -1, false);
        state.activeFuelKey(migrated.encode());
        return migrated;
    }

    private int generationFor(Plan plan, SfxEnergyNodeState state, boolean waterActive) {
        if (plan == null) return 0;
        double generation = plan.baseGeneration();
        if (waterActive) generation *= 4.0D;
        if (plan.freeSaltBonus() || state.specialData() > 0) generation *= 1.5D;
        return Math.max(0, (int) Math.ceil(generation));
    }

    private boolean hasUsableWater(SfxEnergyNodeState state) {
        return state.specialData2() >= WATER_PER_TICK;
    }

    private void consumeOne(SfxEnergyNodeState state, int slot) {
        SfxElectricStack input = state.input(slot);
        if (input == null) return;
        state.input(slot, input.amount() <= 1 ? null : input.copyWithAmount(input.amount() - 1));
    }

    private boolean isWaterContainer(SfxItems items, SfxElectricStack stack) {
        return isWaterBucket(stack) || isWaterBottle(items, stack);
    }

    private boolean isWaterBucket(SfxElectricStack stack) {
        return stack != null && !stack.isSfxItem() && !stack.hasSnapshot() && stack.material() == Material.WATER_BUCKET;
    }

    private boolean isWaterBottle(SfxItems items, SfxElectricStack stack) {
        if (stack == null || stack.isSfxItem() || stack.material() != Material.POTION) return false;
        ItemStack item = stack.toItemStack(items);
        return item.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() == PotionType.WATER;
    }

    private boolean isValidMain(SfxElectricStack stack) {
        return stack != null && (SfxCopperVariants.copperValue(stack.copyWithAmount(1)) > 0.0D || isZinc(stack) || isMagnesium(stack));
    }

    private boolean isZinc(SfxElectricStack stack) {
        return stack != null && stack.isSfxItem() && (ZINC_INGOT.equals(stack.itemId()) || ZINC_DUST.equals(stack.itemId()));
    }

    private boolean isMagnesium(SfxElectricStack stack) {
        return stack != null && stack.isSfxItem() && (MAGNESIUM_INGOT.equals(stack.itemId()) || MAGNESIUM_DUST.equals(stack.itemId()) || MAGNESIUM_SALT.equals(stack.itemId()));
    }

    private int integer(String[] parts, int index) {
        if (parts.length <= index || parts[index].isBlank()) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private List<SfxElectricStack> outputs(SfxElectricStack... candidates) {
        List<SfxElectricStack> outputs = new ArrayList<>();
        for (SfxElectricStack candidate : candidates) {
            if (candidate != null) outputs.add(candidate);
        }
        return List.copyOf(outputs);
    }

    @Override
    public Map<Integer, SfxMachineDisplayItem> displayItems(JavaPlugin plugin, SfxItems items, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        boolean saltStored = state.specialData() > 0;
        boolean waterStored = state.specialData2() > 0;
        boolean production = state.specialData3() == PRODUCTION_MODE;
        return Map.of(
                WATER_DISPLAY_SLOT, new SfxMachineDisplayItem(
                        waterStored ? Material.WATER_BUCKET : Material.GLASS_BOTTLE,
                        "content-expansion.ui.water.name",
                        List.of("content-expansion.ui.water.amount", "content-expansion.ui.water.insert", "content-expansion.ui.water.consumption", "content-expansion.ui.water.bonus", "content-expansion.ui.water.output"),
                        Map.of("stored", state.specialData2(), "capacity", WATER_CAPACITY, "consumption", WATER_PER_TICK),
                        waterStored, state.specialData2(), WATER_CAPACITY),
                MODE_DISPLAY_SLOT, new SfxMachineDisplayItem(
                        production ? Material.OXIDIZED_COPPER : Material.REDSTONE,
                        production ? "content-expansion.ui.mode.production.name" : "content-expansion.ui.mode.power.name",
                        List.of(production ? "content-expansion.ui.mode.production.lore" : "content-expansion.ui.mode.power.lore", "content-expansion.ui.mode.toggle"),
                        Map.of(), production, 0, 0),
                SALT_DISPLAY_SLOT, new SfxMachineDisplayItem(
                        saltStored ? Material.GLOWSTONE_DUST : Material.SUGAR,
                        "content-expansion.ui.salt.name",
                        List.of("content-expansion.ui.salt.amount", "content-expansion.ui.salt.items", "content-expansion.ui.salt.effect", "content-expansion.ui.salt.insert"),
                        Map.of("stored", state.specialData(), "capacity", SALT.maxAmount(), "items", SALT.refundableItemsFloor(state.specialData()), "capacity_items", SALT.refundableItemsFloor(SALT.maxAmount())),
                        saltStored, state.specialData(), SALT.maxAmount()));
    }

    private record WaterFill(int amount, SfxElectricStack emptyContainer) {
    }

    private record Plan(int baseGeneration, int baseTicks, SfxElectricStack output, boolean waterOutput, boolean freeSaltBonus, int mainInput, boolean legacy) {
        String encode() {
            String outputKind = output == null ? "" : output.isSfxItem() ? "sfx" : "material";
            String outputId = output == null ? "" : output.isSfxItem() ? output.itemId() : output.material().name();
            return "oxidizing2|" + baseGeneration + "|" + baseTicks + "|" + outputKind + "|" + outputId + "||" + freeSaltBonus + "|" + waterOutput;
        }
    }
}
