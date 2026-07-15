package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import cc.theends6.sfx.internal.technical.SfxRechargeableItemService;
import cc.theends6.sfx.internal.technical.SfxTechnicalGadgetBalance;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.api.machine.runtime.SfxMachineStatusKey;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import java.util.Map;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
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
    private final SfxEnergyService energy;

    SfxEnergyGeneratorMenuRenderer(SfxEnergyService energy, JavaPlugin plugin, SfxItems items, SfxLocalization localization, SfxRechargeableItemService rechargeableItems) {
        this.energy = energy;
        this.plugin = plugin;
        this.items = items;
        this.localization = localization;
        this.rechargeableItems = rechargeableItems;
        this.statusIcons = new SfxMachineStatusIconRenderer(localization);
    }

    void render(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state, SfxMachineStatusKey status, SfxDynamicEnergyGeneratorProvider provider, Location location) {
        if (provider != null && provider.customMenuLayout()) {
            inventory.clear();
            renderProviderItems(definition, inventory, state, provider);
            return;
        }
        fillInventoryFrame(definition, inventory);
        inventory.setItem(definition.ui().statusSlot(), progressIcon(definition, state, status, provider, location));
        int[] inputSlots = definition.ui().inputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            inventory.setItem(inputSlots[i], state.input(i) == null ? null : state.input(i).toItemStack(items));
        }
        int[] outputSlots = definition.ui().outputSlots();
        for (int i = 0; i < outputSlots.length; i++) {
            inventory.setItem(outputSlots[i], state.output(i) == null ? null : state.output(i).toItemStack(items));
        }
        renderProviderItems(definition, inventory, state, provider);
    }

    void renderStatusOnly(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state, SfxMachineStatusKey status, SfxDynamicEnergyGeneratorProvider provider, Location location) {
        if (provider != null && provider.customMenuLayout()) {
            inventory.clear();
            renderProviderItems(definition, inventory, state, provider);
            return;
        }
        fillInventoryFrame(definition, inventory);
        inventory.setItem(definition.ui().statusSlot(), progressIcon(definition, state, status, provider, location));
        renderProviderItems(definition, inventory, state, provider);
    }

    private void renderProviderItems(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state, SfxDynamicEnergyGeneratorProvider provider) {
        if (provider == null) {
            return;
        }
        for (Map.Entry<Integer, SfxMachineDisplayItem> entry : provider.displayItems(plugin, items, definition, state).entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < inventory.getSize()) {
                inventory.setItem(entry.getKey(), displayItem(entry.getValue()));
            }
        }
    }

    private ItemStack displayItem(SfxMachineDisplayItem display) {
        ItemStack stack = new ItemStack(display.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(localization.component(display.nameKey(), display.placeholders()));
            meta.lore(display.loreKeys().stream().map(key -> localization.component(key, display.placeholders())).toList());
            meta.setEnchantmentGlintOverride(display.glint());
            stack.setItemMeta(meta);
        }
        if (display.capacity() > 0) {
            cc.theends6.sfx.internal.ui.SfxItemProgressBar.apply(stack, display.progress(), display.capacity());
        }
        return stack;
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

    private ItemStack progressIcon(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxMachineStatusKey status, SfxDynamicEnergyGeneratorProvider provider, Location location) {
        if (definition.isCharger()) {
            return chargingBenchIcon(definition, state);
        }
        if (definition.isSolarGenerator()) {
            return solarIcon(definition, status, location);
        }
        int currentGeneration = provider == null || location == null
                ? definition.energyPerTick()
                : Math.max(0, provider.potentialGeneration(plugin, items, definition, state, location));
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
        if (status == SfxMachineStatusKey.OUTPUT_FULL || status == SfxMachineStatusKey.BLOCKED_OUTPUT) {
            return statusIcons.render(SfxMachineStatusView.builder(status)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .build());
        }
        if (status == SfxMachineStatusKey.IDLE && !definition.isSolarGenerator()) {
            return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.IDLE)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .generation(currentGeneration)
                    .includeDefaultStatusLore(false)
                .statusLore(localization.component("energy.generator.idle.lore"))
                    .build());
        }
        if (status != SfxMachineStatusKey.WORKING) {
            return statusIcons.render(SfxMachineStatusView.builder(status)
                    .energy(displayedEnergy(state, definition), definition.capacity())
                    .generation(currentGeneration)
                    .build());
        }
        int total = Math.max(1, state.fuelTotalTenths());
        int progress = Math.min(total, state.fuelProgressTenths());
        int remainingTicks = provider == null
                ? (int) Math.ceil(Math.max(0, total - progress) / (double) Math.max(1, definition.fuelBurnRateTenths()))
                : provider.remainingTicks(state);
        return statusIcons.render(SfxMachineStatusView.builder(SfxMachineStatusKey.WORKING)
                .material(definition.progressMaterial())
                .name(localization.component("energy.generator.active.name"))
                .progress(progress, total, remainingTicks, true)
                .energy(displayedEnergy(state, definition), definition.capacity())
                .generation(currentGeneration)
                .includeDefaultStatusLore(false)
                .statusLore(localization.component(provider == null
                        ? "energy.generator.active.lore"
                        : provider.workingStatusLoreKey()))
                .build());
    }

    private ItemStack solarIcon(SfxEnergyComponentDefinition definition, SfxMachineStatusKey status, Location location) {
        World world = location == null ? null : location.getWorld();
        long ticks = world == null ? 0L : world.getTime();
        SfxEnergyService.SolarGenerationState solar = energy.solarGenerationState(definition, location);
        SfxMachineStatusView.Builder builder;
        if (!solar.dimensionAllowed()) {
            builder = SfxMachineStatusView.builder(Material.BARRIER, localization.component("energy.generator.solar.invalid-dimension.name"))
                    .statusLore(localization.component("energy.generator.solar.invalid-dimension.lore"));
        } else if (!solar.exposed()) {
            builder = SfxMachineStatusView.builder(Material.RED_STAINED_GLASS_PANE, localization.component("energy.generator.solar.obstructed.name"))
                    .statusLore(localization.component("energy.generator.solar.obstructed.lore"));
        } else if (status == SfxMachineStatusKey.NO_NETWORK || status == SfxMachineStatusKey.NETWORK_CONFLICT) {
            builder = SfxMachineStatusView.builder(status);
        } else {
            builder = SfxMachineStatusView.builder(definition.progressMaterial(), localization.component("energy.generator.solar.name"))
                    .statusLore(localization.component(solar.daytime()
                            ? "energy.generator.solar.day-active"
                            : "energy.generator.solar.night-active"));
        }
        return statusIcons.render(builder
                .includeDefaultStatusLore(status == SfxMachineStatusKey.NO_NETWORK || status == SfxMachineStatusKey.NETWORK_CONFLICT)
                .extraLore(localization.component("energy.generator.solar.current", Map.of("energy", solar.generation())))
                .extraLore(localization.component("energy.generator.solar.day", Map.of("energy", definition.energyPerTick())))
                .extraLore(localization.component("energy.generator.solar.night", Map.of("energy", definition.nightEnergyPerTick())))
                .extraLore(localization.component("energy.generator.solar.time", Map.of("time", formatGameTime(ticks), "ticks", ticks)))
                .extraLore(localization.component("energy.generator.solar.day-range"))
                .extraLore(localization.component("energy.generator.solar.night-range"))
                .build());
    }

    private String formatGameTime(long ticks) {
        long normalized = Math.floorMod(ticks, 24000L);
        long totalMinutes = Math.floorMod(normalized + 6000L, 24000L) * 1440L / 24000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalMinutes / 60L, totalMinutes % 60L);
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
        return (int) Math.round(SfxTechnicalGadgetBalance.rules(plugin).chargingBenchEnergyLoss() * 100.0D);
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
