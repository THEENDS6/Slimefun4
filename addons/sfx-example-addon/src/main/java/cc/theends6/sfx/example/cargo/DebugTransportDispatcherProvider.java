package cc.theends6.sfx.example.cargo;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.cargo.SfxCargoManagerAccess;
import cc.theends6.sfx.api.cargo.SfxCargoManagerProvider;
import cc.theends6.sfx.api.cargo.SfxCargoManagerState;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;


public final class DebugTransportDispatcherProvider implements SfxCargoManagerProvider {
    private final int maxSpeedMultiplier;

    public DebugTransportDispatcherProvider(SfxAddonContext context) {
        maxSpeedMultiplier = Math.max(1, Math.min(64,
                context.configInt("debug-transport-dispatcher.max-speed-multiplier", 16)));
    }

    @Override
    public String titleKey() {
        return "example-cargo.title";
    }

    @Override
    public Map<Integer, SfxMachineDisplayItem> displayItems(
            JavaPlugin plugin, SfxItems items, SfxCargoManagerState state) {
        Map<String, Object> values = Map.of(
                "state", state.enabled() ? "ON" : "OFF",
                "speed", state.speedMultiplier(),
                "max_speed", maxSpeedMultiplier);
        return Map.of(
                4, item(state.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "example-cargo.state.name", "example-cargo.state.lore", values, state.enabled()),
                10, item(Material.LIME_CONCRETE, "example-cargo.enable.name", "example-cargo.enable.lore", values, false),
                12, item(Material.RED_DYE, "example-cargo.speed-down.name", "example-cargo.speed.lore", values, false),
                13, item(Material.CLOCK, "example-cargo.speed.name", "example-cargo.speed.lore", values, false),
                14, item(Material.LIME_DYE, "example-cargo.speed-up.name", "example-cargo.speed.lore", values, false),
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
            case 12 -> access.setSpeedMultiplier(Math.max(1, state.speedMultiplier() - speedStep(clickType)));
            case 14 -> access.setSpeedMultiplier(Math.min(maxSpeedMultiplier,
                    state.speedMultiplier() + speedStep(clickType)));
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

    private int speedStep(ClickType clickType) {
        return clickType.isShiftClick() ? 10 : 1;
    }
}
