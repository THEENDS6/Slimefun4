package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.technical.SfxRechargeableItemService;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxEnergyGeneratorMenuRenderer {
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxMachineStatusIconRenderer statusIcons;
    private final SfxRechargeableItemService rechargeableItems;
    private final JavaPlugin plugin;

    SfxEnergyGeneratorMenuRenderer(JavaPlugin plugin, SfxItems items, SfxLocalization localization, SfxRechargeableItemService rechargeableItems) {
        this.plugin = plugin;
        this.items = items;
        this.localization = localization;
        this.rechargeableItems = rechargeableItems;
        this.statusIcons = new SfxMachineStatusIconRenderer(localization);
    }

    void render(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state, SfxMachineStatusKey status) {
        fillInventoryFrame(definition, inventory);
        inventory.setItem(definition.ui().statusSlot(), progressIcon(definition, state, status));
        int[] inputSlots = definition.ui().inputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            inventory.setItem(inputSlots[i], state.input(i) == null ? null : state.input(i).toItemStack(items));
        }
        int[] outputSlots = definition.ui().outputSlots();
        for (int i = 0; i < outputSlots.length; i++) {
            inventory.setItem(outputSlots[i], state.output(i) == null ? null : state.output(i).toItemStack(items));
        }
    }

    void renderStatusOnly(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state, SfxMachineStatusKey status) {
        fillInventoryFrame(definition, inventory);
        inventory.setItem(definition.ui().statusSlot(), progressIcon(definition, state, status));
    }

    void renderStorageSlots(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        int[] inputSlots = definition.ui().inputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            inventory.setItem(inputSlots[i], state.input(i) == null ? null : state.input(i).toItemStack(items));
        }
        int[] outputSlots = definition.ui().outputSlots();
        for (int i = 0; i < outputSlots.length; i++) {
            inventory.setItem(outputSlots[i], state.output(i) == null ? null : state.output(i).toItemStack(items));
        }
    }

    private void fillInventoryFrame(SfxEnergyComponentDefinition definition, Inventory inventory) {
        SfxEnergyComponentUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }

    private int displayedEnergy(SfxEnergyNodeState state, SfxEnergyComponentDefinition definition) {
        return Math.max(0, Math.min(state.storedEnergy(), Math.max(0, definition.capacity())));
    }

    private ItemStack progressIcon(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxMachineStatusKey status) {
        if (definition.isCharger()) {
            return chargingBenchIcon(definition, state);
        }
        if (status == SfxMachineStatusKey.NO_NETWORK) {
            return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.NO_NETWORK)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .build());
        }
        if (status == SfxMachineStatusKey.NETWORK_CONFLICT) {
            return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.NETWORK_CONFLICT)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .build());
        }
        if (status == SfxMachineStatusKey.OUTPUT_FULL) {
            return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.OUTPUT_FULL)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .build());
        }
        if (status == SfxMachineStatusKey.IDLE && !definition.isSolarGenerator()) {
            return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.IDLE)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .generation(definition.energyPerTick())
                    .includeDefaultStatusLore(false)
                .statusLore(localization.component("energy.generator.idle.lore"))
                    .build());
        }
        if (definition.isSolarGenerator()) {
            return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.WORKING)
                    .material(definition.progressMaterial())
                    .name(localization.component("energy.generator.solar.name"))
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .generation(definition.energyPerTick())
                    .includeDefaultStatusLore(false)
                    .build());
        }

        int total = Math.max(1, state.fuelTotalTenths());
        int progress = Math.min(total, state.fuelProgressTenths());
        int remainingTicks = (int) Math.ceil(Math.max(0, total - progress) / (double) Math.max(1, definition.fuelBurnRateTenths()));
        return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.WORKING)
                .material(definition.progressMaterial())
                .name(localization.component("energy.generator.active.name"))
                .progress(progress, total, remainingTicks, true)
                .energy(displayedEnergy(state, definition), definition.capacity())
                .generation(definition.energyPerTick())
                .includeDefaultStatusLore(false)
                .statusLore(localization.component("energy.generator.active.lore"))
                .build());
    }

    private ItemStack chargingBenchIcon(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        ChargingBenchDisplay display = chargingBenchDisplay(definition, state);
        SfxMachineStatusView.Builder builder = SfxMachineStatusView.builder(display.status())
                .name(localization.component("energy.charging-bench.name"));
        if (display.charging()) {
            builder.icon(capacitorHead(display.current(), display.total()));
        }
        builder.energy(displayedEnergy(state, definition), definition.capacity())
                .consumption(chargingBenchEnergyPerTick(definition))
                .includeDefaultStatusLore(false)
                .statusLore(localization.component("energy.charging-bench.description"))
                .includeDefaultStatusLore(false)
                .statusLore(display.statusLore())
                .extraLore(localization.component("energy.charging-bench.energy-loss", Map.of("loss", chargingBenchEnergyLossPercent())));
        if (display.charging() && display.total() > 0) {
            builder.progress(display.current(), display.total(), -1, true);
            builder.extraLore(localization.component(
                    "energy.charging-bench.charge-line",
                    Map.of("current", display.current(), "capacity", display.total())));
        }
        return statusIcons.render(builder.build());
    }

    private ChargingBenchDisplay chargingBenchDisplay(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        for (int slot = 0; slot < definition.ui().inputSlots().length; slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null) {
                continue;
            }
            if (input.amount() != 1) {
                return new ChargingBenchDisplay(0, 0, SfxMachineStatusKey.INVALID_INPUT, false, localization.component(
                        "energy.charging-bench.status.single-item"));
            }
            ItemStack item = input.toItemStack(items);
            if (!rechargeableItems.isRechargeable(item)) {
                return new ChargingBenchDisplay(0, 0, SfxMachineStatusKey.INVALID_INPUT, false, localization.component(
                        "energy.charging-bench.status.invalid-item"));
            }
            int current = (int) Math.floor(rechargeableItems.charge(item));
            int total = Math.max(1, (int) Math.ceil(rechargeableItems.capacity(item)));
            if (current >= total) {
                return new ChargingBenchDisplay(total, total, SfxMachineStatusKey.FULL, false, localization.component(
                        "energy.charging-bench.status.full"));
            }
            if (state.storedEnergy() <= 0) {
                return new ChargingBenchDisplay(current, total, SfxMachineStatusKey.NO_POWER, false, localization.component(
                        "energy.charging-bench.status.no-power"));
            }
            return new ChargingBenchDisplay(current, total, SfxMachineStatusKey.WORKING, true, localization.component(
                    "energy.charging-bench.status.charging"));
        }
        return new ChargingBenchDisplay(0, 0, SfxMachineStatusKey.IDLE, false, localization.component(
                "energy.charging-bench.status.idle"));
    }

    private int chargingBenchEnergyPerTick(SfxEnergyComponentDefinition definition) {
        return Math.max(0, definition.energyPerTick());
    }

    private int chargingBenchEnergyLossPercent() {
        double loss = plugin.getConfig().getBoolean("technical-gadgets.sfx-balance.enabled", true)
                ? plugin.getConfig().getDouble("technical-gadgets.sfx-balance.charging-bench.energy-loss", 0.80D)
                : plugin.getConfig().getDouble("technical-gadgets.classic.charging-bench.energy-loss", 0.50D);
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, loss)) * 100.0D);
    }

    private ItemStack capacitorHead(int current, int total) {
        int safeTotal = Math.max(1, total);
        int safeCurrent = Math.max(0, Math.min(safeTotal, current));
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            HeadTextures.apply(meta, SfxCapacitorAppearanceProjector.capacitorTexture(safeCurrent, safeTotal));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private record ChargingBenchDisplay(int current, int total, SfxMachineStatusKey status, boolean charging, Component statusLore) {
    }
}
