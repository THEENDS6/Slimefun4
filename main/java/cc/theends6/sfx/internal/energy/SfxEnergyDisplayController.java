package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
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

    SfxEnergyDisplayController(JavaPlugin plugin, SfxLocalization localization, SfxFloatingTextDisplayService floatingText) {
        this.plugin = plugin;
        this.localization = localization;
        this.floatingText = floatingText;
    }

    void displayStatus(SfxBlockAnchorKey regulatorKey, SfxEnergyGridStatus status, int supply, int consumption, int net, int totalStored, int totalCapacity) {
        switch (status) {
            case NO_NETWORK -> update(regulatorKey, new DisplayText(
                    "energy.regulator.no-network",
                    Map.of(),
                    "<red>Not connected to any electric machines</red>"));
            case MULTIPLE_REGULATORS -> update(regulatorKey, new DisplayText(
                    "energy.regulator.multi-regulator",
                    Map.of(),
                    "<red>Multiple energy regulators in this connection</red>"));
            case SHARED_NODE_CONFLICT -> update(regulatorKey, new DisplayText(
                    "energy.regulator.shared-node-conflict",
                    Map.of(),
                    "<red>Energy network conflict</red>"));
            case ONLINE -> {
                boolean classic = "classic".equalsIgnoreCase(plugin.getConfig().getString("energy.regulator-display.mode", "sfx"));
                String key;
                String fallback;
                if (classic) {
                    key = net >= 0 ? "energy.regulator.classic-positive" : "energy.regulator.classic-negative";
                    fallback = net >= 0
                            ? "<green>+{net} J ⚡</green>"
                            : "<red>{net} J ⚡</red>";
                    update(regulatorKey, new DisplayText(
                            key,
                            Map.of("net", net, "supply", supply, "consumption", consumption, "stored", totalStored, "capacity", totalCapacity),
                            fallback));
                } else {
                    key = net >= 0 ? "energy.regulator.sfx-positive" : "energy.regulator.sfx-negative";
                    EnergyDisplayParts parts = energyDisplayParts(supply, consumption, net, totalStored, totalCapacity);
                    fallback = (net >= 0 ? "<green>+{net_text} J/t</green>" : "<red>-{net_text} J/t</red>")
                            + "<newline>{flow_line}<newline>{storage_line}";
                    update(regulatorKey, new DisplayText(
                            key,
                            Map.of(
                                    "net", net,
                                    "supply", supply,
                                    "consumption", consumption,
                                    "stored", totalStored,
                                    "capacity", totalCapacity,
                                    "net_text", parts.netText(),
                                    "flow_line", parts.flowLine(),
                                    "storage_line", parts.storageLine()),
                            fallback));
                }
            }
        }
    }

    void remove(SfxBlockAnchorKey anchorKey) {
        SfxFloatingTextKey key = key(anchorKey);
        ownedKeys.remove(key);
        floatingText.remove(key);
    }

    void shutdown() {
        for (SfxFloatingTextKey key : Set.copyOf(ownedKeys)) {
            floatingText.remove(key);
            ownedKeys.remove(key);
        }
    }

    private void update(SfxBlockAnchorKey anchorKey, DisplayText displayText) {
        Component text = localization.component(displayText.key(), displayText.fallback(), displayText.placeholders());
        SfxFloatingTextKey key = key(anchorKey);
        ownedKeys.add(key);
        floatingText.update(new SfxFloatingTextProjection(
                key,
                anchorKey.x() + 0.5D,
                anchorKey.y() + 1.15D,
                anchorKey.z() + 0.5D,
                text,
                VIEW_DISTANCE_SQUARED));
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

    private record DisplayText(String key, Map<String, ?> placeholders, String fallback) {
    }

    private record EnergyDisplayParts(String netText, String flowLine, String storageLine) {
    }
}
