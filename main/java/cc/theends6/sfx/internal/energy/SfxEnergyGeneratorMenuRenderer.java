package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxEnergyGeneratorMenuRenderer {
    private static final int DISPLAY_SLOT = 22;
    private static final int[] INPUT_SLOTS = {19, 20};
    private static final int[] OUTPUT_SLOTS = {24, 25};
    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] BORDER_IN = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] BORDER_OUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxMachineStatusIconRenderer statusIcons;

    SfxEnergyGeneratorMenuRenderer(SfxItems items, SfxLocalization localization) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxMachineStatusIconRenderer(localization);
    }

    void render(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state, SfxEnergyGeneratorRenderStatus status) {
        fillInventoryFrame(inventory);
        inventory.setItem(DISPLAY_SLOT, progressIcon(definition, state, status));
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            inventory.setItem(INPUT_SLOTS[i], state.input(i) == null ? null : state.input(i).toItemStack(items));
        }
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            inventory.setItem(OUTPUT_SLOTS[i], state.output(i) == null ? null : state.output(i).toItemStack(items));
        }
    }

    private void fillInventoryFrame(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack inputBorder = namedItem(
                Material.CYAN_STAINED_GLASS_PANE,
                localization.component("electric-ui.input.name", "<aqua>Input</aqua>"),
                List.of(localization.component("electric-ui.input.lore", "<gray>Place items here.</gray>")));
        ItemStack outputBorder = namedItem(
                Material.ORANGE_STAINED_GLASS_PANE,
                localization.component("electric-ui.output.name", "<gold>Output</gold>"),
                List.of(localization.component("electric-ui.output.lore", "<gray>Take finished items here.</gray>")));
        for (int slot : BORDER) {
            inventory.setItem(slot, filler);
        }
        for (int slot : BORDER_IN) {
            inventory.setItem(slot, inputBorder);
        }
        for (int slot : BORDER_OUT) {
            inventory.setItem(slot, outputBorder);
        }
    }

    private ItemStack progressIcon(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxEnergyGeneratorRenderStatus status) {
        if (definition.isCharger()) {
            return statusIcons.render(SfxMachineStatusView.builder(
                            definition.progressMaterial(),
                            localization.component("energy.charging-bench.name", "<yellow>Charging Bench</yellow>"))
                    .energy(state.storedEnergy(), definition.capacity())
                    .statusLore(localization.component("energy.charging-bench.lore", "<gray>Charges one rechargeable item at a time. Consumes twice the transferred charge.</gray>"))
                    .build());
        }
        if (status == SfxEnergyGeneratorRenderStatus.NO_NETWORK) {
            return statusIcons.render(SfxMachineStatusView.builder(
                            Material.RED_STAINED_GLASS_PANE,
                            localization.component("energy.generator.no-network.name", "<red>Not Connected</red>"))
                    .energy(state.storedEnergy(), definition.capacity())
                    .statusLore(localization.component("energy.generator.no-network.lore", "<gray>This generator is not connected to an energy network.</gray>"))
                    .build());
        }
        if (status == SfxEnergyGeneratorRenderStatus.CONFLICT) {
            return statusIcons.render(SfxMachineStatusView.builder(
                            Material.RED_STAINED_GLASS_PANE,
                            localization.component("energy.generator.conflict.name", "<red>Network Conflict</red>"))
                    .energy(state.storedEnergy(), definition.capacity())
                    .statusLore(localization.component("energy.generator.conflict.lore", "<gray>Resolve regulator or shared-node conflicts first.</gray>"))
                    .build());
        }
        if (status == SfxEnergyGeneratorRenderStatus.OUTPUT_FULL) {
            return statusIcons.render(SfxMachineStatusView.builder(
                            Material.ORANGE_STAINED_GLASS_PANE,
                            localization.component("energy.generator.output-full.name", "<red>Output Full</red>"))
                    .energy(state.storedEnergy(), definition.capacity())
                    .statusLore(localization.component("energy.generator.output-full.lore", "<gray>Clear the output slots to continue.</gray>"))
                    .build());
        }
        if (status == SfxEnergyGeneratorRenderStatus.IDLE && !definition.isSolarGenerator()) {
            return statusIcons.render(SfxMachineStatusView.builder(
                            Material.BLACK_STAINED_GLASS_PANE,
                            localization.component("energy.generator.idle.name", "<gray>Idle</gray>"))
                    .energy(state.storedEnergy(), definition.capacity())
                    .generation(definition.energyPerTick())
                    .statusLore(localization.component("energy.generator.idle.lore", "<gray>Insert fuel to start generating power.</gray>"))
                    .build());
        }
        if (definition.isSolarGenerator()) {
            return statusIcons.render(SfxMachineStatusView.builder(
                            definition.progressMaterial(),
                            localization.component("energy.generator.solar.name", "<yellow>Solar Generator</yellow>"))
                    .energy(state.storedEnergy(), definition.capacity())
                    .generation(definition.energyPerTick())
                    .build());
        }

        int total = Math.max(1, state.fuelTotalTenths());
        int progress = Math.min(total, state.fuelProgressTenths());
        int remainingTicks = (int) Math.ceil(Math.max(0, total - progress) / (double) Math.max(1, definition.fuelBurnRateTenths()));
        return statusIcons.render(SfxMachineStatusView.builder(
                        definition.progressMaterial(),
                        localization.component("energy.generator.active.name", "<green>Generating</green>"))
                .progress(progress, total, remainingTicks, true)
                .energy(state.storedEnergy(), definition.capacity())
                .generation(definition.energyPerTick())
                .statusLore(localization.component("energy.generator.active.lore", "<gray>Fuel is currently being converted into energy.</gray>"))
                .build());
    }

    private Component progressBarLine(int current, int total) {
        float percentage = Math.round(((current * 100.0F) / total) * 100.0F) / 100.0F;
        int filled = Math.min(20, Math.max(0, (int) (percentage / 5.0F)));
        StringBuilder builder = new StringBuilder();
        builder.append(percentage < 50.0F ? "&6" : "&a");
        for (int i = 0; i < filled; i++) {
            builder.append(':');
        }
        builder.append("&7");
        for (int i = filled; i < 20; i++) {
            builder.append(':');
        }
        builder.append(" - ").append(percentage).append('%');
        return Text.legacy(builder.toString());
    }

    private String formatGeneratorSeconds(int remainingTenths, int burnRateTenths) {
        int remainingTicks = (int) Math.ceil(Math.max(0, remainingTenths) / (double) Math.max(1, burnRateTenths));
        double seconds = remainingTicks / 20.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return String.valueOf((int) Math.rint(seconds));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
