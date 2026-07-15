package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayMode;
import cc.theends6.sfx.internal.display.SfxFloatingTextKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextProjection;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxEnergyDisplayController {
    private static final int VIEW_DISTANCE_SQUARED = 32 * 32;

    private final JavaPlugin plugin;
    private final SfxLocalization localization;
    private final SfxFloatingTextDisplayService floatingText;
    private final Set<SfxFloatingTextKey> ownedKeys = ConcurrentHashMap.newKeySet();
    private final Map<SfxFloatingTextKey, CachedDisplay> lastDisplays = new ConcurrentHashMap<>();

    SfxEnergyDisplayController(JavaPlugin plugin, SfxLocalization localization, SfxFloatingTextDisplayService floatingText) {
        this.plugin = plugin;
        this.localization = localization;
        this.floatingText = floatingText;
    }

    void displayStatus(SfxBlockAnchorKey regulatorKey, SfxEnergyGridStatus status, int supply, int consumption, int net, int totalStored, int totalCapacity) {
        SfxFloatingTextKey cacheKey = key(regulatorKey);
        CachedDisplay previous = lastDisplays.get(cacheKey);
        long now = System.currentTimeMillis() / 50L;
        int interval = Math.max(1, plugin.getConfig().getInt("energy.display-update-interval-ticks", 1));
        boolean updateOnlyOnChange = plugin.getConfig().getBoolean("energy.update-only-on-change", true);
        boolean classic = "classic".equalsIgnoreCase(plugin.getConfig().getString("energy.regulator-display.mode", "sfx"));
        DisplayValues values = new DisplayValues(status, supply, consumption, net, totalStored, totalCapacity, classic);
        if (previous != null) {
            if (updateOnlyOnChange && previous.values().equals(values)) {
                long resendInterval = Math.max(1L, plugin.getConfig().getLong("floating-text.resync-interval-ticks", 20L));
                if (previous.updatedTick() + resendInterval > now) {
                    return;
                }
                lastDisplays.put(cacheKey, new CachedDisplay(previous.values(), previous.displayText(), previous.renderedText(), now));
                update(regulatorKey, previous.renderedText());
                return;
            }
            if (!updateOnlyOnChange && previous.updatedTick() + interval > now) {
                return;
            }
        }
        DisplayText displayText = renderDisplayText(values);
        Component renderedText = localization.component(displayText.key(), displayText.placeholders());
        lastDisplays.put(cacheKey, new CachedDisplay(values, displayText, renderedText, now));
        update(regulatorKey, renderedText);
    }

    private DisplayText renderDisplayText(DisplayValues values) {
        SfxEnergyGridStatus status = values.status();
        int supply = values.supply();
        int consumption = values.consumption();
        int net = values.net();
        int totalStored = values.totalStored();
        int totalCapacity = values.totalCapacity();
        return switch (status) {
            case NO_NETWORK -> new DisplayText(
                    "energy.regulator.no-network",
                    Map.of());
            case MULTIPLE_REGULATORS -> new DisplayText(
                    "energy.regulator.multi-regulator",
                    Map.of());
            case SHARED_NODE_CONFLICT -> new DisplayText(
                    "energy.regulator.shared-node-conflict",
                    Map.of());
            case ONLINE -> {
                if (values.classic()) {
                    String key = net >= 0 ? "energy.regulator.classic-positive" : "energy.regulator.classic-negative";
                    yield new DisplayText(
                            key,
                            Map.of("net", net, "supply", supply, "consumption", consumption, "stored", totalStored, "capacity", totalCapacity));
                }
                String key = net >= 0 ? "energy.regulator.sfx-positive" : "energy.regulator.sfx-negative";
                EnergyDisplayParts parts = energyDisplayParts(supply, consumption, net, totalStored, totalCapacity);
                yield new DisplayText(
                        key,
                        Map.of(
                                "net", net,
                                "supply", supply,
                                "consumption", consumption,
                                "stored", totalStored,
                                "capacity", totalCapacity,
                                "net_text", parts.netText(),
                                "flow_line", parts.flowLine(),
                                "storage_line", parts.storageLine()));
            }
        };
    }

    void remove(SfxBlockAnchorKey anchorKey) {
        SfxFloatingTextKey key = key(anchorKey);
        ownedKeys.remove(key);
        lastDisplays.remove(key);
        floatingText.remove(key);
    }

    void shutdown() {
        for (SfxFloatingTextKey key : Set.copyOf(ownedKeys)) {
            floatingText.remove(key);
            ownedKeys.remove(key);
            lastDisplays.remove(key);
        }
    }

    private void update(SfxBlockAnchorKey anchorKey, Component text) {
        SfxFloatingTextKey key = key(anchorKey);
        ownedKeys.add(key);
        boolean seeThrough = plugin.getConfig().getBoolean("energy.regulator-display.see-through", false);
        floatingText.update(new SfxFloatingTextProjection(
                key,
                anchorKey.x() + 0.5D,
                anchorKey.y() + 1.15D,
                anchorKey.z() + 0.5D,
                text,
                VIEW_DISTANCE_SQUARED,
                seeThrough,
                SfxFloatingTextDisplayMode.TEXT_DISPLAY));
    }

    private SfxFloatingTextKey key(SfxBlockAnchorKey anchorKey) {
        return new SfxFloatingTextKey("energy-regulator", anchorKey.worldId(), anchorKey.x(), anchorKey.y(), anchorKey.z());
    }

    private EnergyDisplayParts energyDisplayParts(int supply, int consumption, int net, int totalStored, int totalCapacity) {
        String netText = formatEnergyShort(Math.abs((long) net));
        String supplyText = formatEnergyShort(supply) + " J/t";
        String consumptionText = formatEnergyShort(consumption) + " J/t";
        String storedText = formatEnergyShort(totalStored) + " J";
        String capacityText = formatEnergyShort(totalCapacity) + " J";
        String[] flow = centeredPair(supplyText, consumptionText);
        String[] storage = centeredPair(storedText, capacityText);
        String flowLine = "<green>" + flow[0] + "</green><gray> | </gray><red>" + flow[1] + "</red>";
        String storageLine = "<gray>" + storage[0] + " / " + storage[1] + "</gray>";
        return new EnergyDisplayParts(netText, flowLine, storageLine);
    }

    private String[] centeredPair(String left, String right) {
        int side = Math.max(visibleLength(left), visibleLength(right));
        return new String[] {
                " ".repeat(Math.max(0, side - visibleLength(left))) + left,
                right + " ".repeat(Math.max(0, side - visibleLength(right)))
        };
    }

    private int visibleLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String formatEnergyShort(long value) {
        long abs = Math.abs(value);
        if (abs < 1000) {
            return Long.toString(value);
        }
        String[] units = {"k", "m", "b", "t", "p", "e"};
        double scaled = abs;
        int unitIndex = -1;
        while (scaled >= 1000.0 && unitIndex + 1 < units.length) {
            scaled /= 1000.0;
            unitIndex++;
        }
        String number = scaled < 10.0 ? String.format(java.util.Locale.ROOT, "%.1f", scaled) : String.format(java.util.Locale.ROOT, "%.0f", scaled);
        if (number.endsWith(".0") && scaled >= 10.0) {
            number = number.substring(0, number.length() - 2);
        }
        return (value < 0 ? "-" : "") + number + units[unitIndex];
    }

    private record CachedDisplay(DisplayValues values, DisplayText displayText, Component renderedText, long updatedTick) {
    }

    private record DisplayValues(SfxEnergyGridStatus status, int supply, int consumption, int net, int totalStored, int totalCapacity, boolean classic) {
    }

    private record DisplayText(String key, Map<String, ?> placeholders) {
    }

    private record EnergyDisplayParts(String netText, String flowLine, String storageLine) {
    }
}
