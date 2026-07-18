package cc.theends6.sfx.example.cargo;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.cargo.SfxCargoManagerAccess;
import cc.theends6.sfx.api.cargo.SfxCargoManagerProvider;
import cc.theends6.sfx.api.cargo.SfxCargoManagerState;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;


public final class DebugTransportDispatcherProvider implements SfxCargoManagerProvider {
    private final double defaultWorkIntervalTicks;
    private final double maxWorkIntervalTicks;
    private final int instantWorkPassLimit;
    private final SfxAddonContext context;

    public DebugTransportDispatcherProvider(SfxAddonContext context) {
        this.context = context;
        maxWorkIntervalTicks = clamp(
                context.configDouble("debug-transport-dispatcher.max-work-interval-ticks", 1200.0D),
                0.1D, 86400.0D);
        defaultWorkIntervalTicks = clamp(
                context.configDouble("debug-transport-dispatcher.default-work-interval-ticks", 10.0D),
                0.0D, maxWorkIntervalTicks);
        instantWorkPassLimit = Math.max(1, Math.min(100_000,
                context.configInt("debug-transport-dispatcher.instant-max-passes-per-tick", 4096)));
    }

    @Override
    public double defaultWorkIntervalTicks() {
        return defaultWorkIntervalTicks;
    }

    @Override
    public int instantWorkPassLimit() {
        return instantWorkPassLimit;
    }

    @Override
    public boolean ignoresGlobalVisualizerSetting() {
        return true;
    }

    @Override
    public String titleKey() {
        return "example-cargo.title";
    }

    @Override
    public Map<Integer, SfxMachineDisplayItem> displayItems(
            JavaPlugin plugin, SfxItems items, SfxCargoManagerState state) {
        Map<String, Object> values = Map.of(
                "state", context.api().localization().requiredText(state.enabled()
                        ? "example-cargo.state.enabled"
                        : "example-cargo.state.disabled"),
                "interval", format(state.workIntervalTicks()),
                "max_interval", format(maxWorkIntervalTicks));
        return Map.of(
                4, item(state.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "example-cargo.state.name", "example-cargo.state.lore", values, state.enabled()),
                10, item(Material.LIME_CONCRETE, "example-cargo.enable.name", "example-cargo.enable.lore", values, false),
                12, item(Material.RED_DYE, "example-cargo.interval-down.name", "example-cargo.interval.lore", values, false),
                13, item(Material.CLOCK, "example-cargo.interval.name", "example-cargo.interval.lore", values, false),
                14, item(Material.LIME_DYE, "example-cargo.interval-up.name", "example-cargo.interval.lore", values, false),
                16, item(Material.RED_CONCRETE, "example-cargo.disable.name", "example-cargo.disable.lore", values, false),
                22, item(Material.END_ROD, "example-cargo.visualizer.name", "example-cargo.visualizer.lore", values, false));
    }

    @Override
    public boolean handleMenuClick(
            JavaPlugin plugin,
            SfxItems items,
            SfxCargoManagerState state,
            Player player,
            int rawSlot,
            ClickType clickType,
            SfxCargoManagerAccess access) {
        switch (rawSlot) {
            case 10 -> access.setEnabled(true);
            case 12 -> access.setWorkIntervalTicks(clamp(
                    state.workIntervalTicks() - adjustmentStep(clickType), 0.0D, maxWorkIntervalTicks));
            case 13 -> {
                player.closeInventory();
                player.sendMessage(localized("example-cargo.message.interval-prompt", Map.of(
                        "max", format(maxWorkIntervalTicks))));
                context.api().chatInput().await(
                        player,
                        "sfx-example:debug-cargo-interval",
                        Duration.ofSeconds(30),
                        input -> updateExactInterval(input, player, access),
                        () -> player.sendMessage(localized("example-cargo.message.timeout", Map.of())));
            }
            case 14 -> access.setWorkIntervalTicks(clamp(
                    state.workIntervalTicks() + adjustmentStep(clickType), 0.0D, maxWorkIntervalTicks));
            case 16 -> access.setEnabled(false);
            case 22 -> access.toggleVisualizer(player);
            default -> {
                return false;
            }
        }
        access.markDirty();
        return true;
    }

    private SfxMachineDisplayItem item(
            Material material, String name, String lore, Map<String, Object> values, boolean glint) {
        return new SfxMachineDisplayItem(material, name, List.of(lore), values, glint, 0, 0);
    }

    private void updateExactInterval(String input, Player player, SfxCargoManagerAccess access) {
        if (input.equalsIgnoreCase("cancel")) {
            return;
        }
        try {
            double parsed = Double.parseDouble(input.trim());
            if (!Double.isFinite(parsed) || parsed < 0.0D || parsed > maxWorkIntervalTicks) {
                throw new NumberFormatException("outside supported interval range");
            }
            access.setWorkIntervalTicks(parsed);
            access.markDirty();
            player.sendMessage(localized("example-cargo.message.updated", Map.of(
                    "interval", format(parsed))));
        } catch (NumberFormatException exception) {
            player.sendMessage(localized("example-cargo.message.invalid", Map.of(
                    "max", format(maxWorkIntervalTicks))));
        }
    }

    private double adjustmentStep(ClickType clickType) {
        if (clickType == ClickType.SHIFT_RIGHT) return 100.0D;
        if (clickType == ClickType.SHIFT_LEFT) return 10.0D;
        if (clickType.isRightClick()) return 1.0D;
        return 0.1D;
    }

    private String localized(String key, Map<String, ?> placeholders) {
        String value = context.api().localization().requiredText(key);
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return value.replace('&', '§');
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
