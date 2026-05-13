package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxMultimeterListener implements Listener {
    private final JavaPlugin plugin;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxElectricMachineService electricMachines;
    private final SfxConfigurableMachineService configurableMachines;
    private final SfxEnergyService energyService;

    public SfxMultimeterListener(
            JavaPlugin plugin,
            SfxItems items,
            SfxLocalization localization,
            SfxBlockDataService blockData,
            SfxElectricMachineService electricMachines,
            SfxConfigurableMachineService configurableMachines,
            SfxEnergyService energyService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.energyService = Objects.requireNonNull(energyService, "energyService");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction().isLeftClick()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(clicked.getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        Player player = event.getPlayer();
        if (isMultimeter(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            inspectWithMultimeter(player, instance);
            return;
        }
        if (energyService.isConnectionStatusNode(instance.typeId())) {
            event.setCancelled(true);
            sendConnectionStatus(player, instance.instanceId());
        }
    }

    private boolean isMultimeter(ItemStack stack) {
        return items.readMarker(stack)
                .map(marker -> "sf:multimeter".equals(marker.itemId()))
                .orElse(false);
    }

    private void inspectWithMultimeter(Player player, SfxBlockInstanceRecord instance) {
        boolean enhanced = plugin.getConfig().getBoolean("tools.multimeter.use-sfx-enhanced", true);
        if (!enhanced) {
            inspectClassic(player, instance);
            return;
        }
        inspectEnhanced(player, instance);
    }

    private void inspectClassic(Player player, SfxBlockInstanceRecord instance) {
        EnergyNumbers numbers = energyNumbers(instance);
        if (numbers == null || numbers.capacity() <= 0) {
            send(player, "tools.multimeter.unsupported", "<red>This block has no readable energy storage.</red>", Map.of());
            return;
        }
        send(player, "tools.multimeter.classic", "<gray>Stored Energy: <yellow>{stored}</yellow><gray>/<yellow>{capacity}</yellow> J</gray>", Map.of(
                "stored", numbers.stored(),
                "capacity", numbers.capacity()));
    }

    private void inspectEnhanced(Player player, SfxBlockInstanceRecord instance) {
        if (energyService.supportsType(instance.typeId())) {
            inspectEnergyComponent(player, instance);
            return;
        }
        if (electricMachines.supportsType(instance.typeId())) {
            EnergyNumbers numbers = energyNumbers(instance);
            sendMachineHeader(player, instance, "consumer");
            sendEnergyLine(player, numbers);
            send(player, "tools.multimeter.enhanced.consumption", "<gray>Consumption: <yellow>{value}</yellow> J/t</gray>", Map.of(
                    "value", electricMachines.requestedEnergyConsumption(List.of(instance.instanceId()))));
            sendGridSummary(player, instance.instanceId());
            return;
        }
        if (configurableMachines.supportsType(instance.typeId())) {
            EnergyNumbers numbers = energyNumbers(instance);
            sendMachineHeader(player, instance, configurableMachines.isProducer(instance.typeId()) ? "producer" : "consumer");
            sendEnergyLine(player, numbers);
            if (configurableMachines.isProducer(instance.typeId())) {
                send(player, "tools.multimeter.enhanced.generation", "<gray>Generation: <green>{value}</green> J/t</gray>", Map.of(
                        "value", configurableMachines.producerPotentialGeneration(instance.instanceId())));
            } else if (configurableMachines.isConsumer(instance.typeId())) {
                send(player, "tools.multimeter.enhanced.consumption", "<gray>Consumption: <yellow>{value}</yellow> J/t</gray>", Map.of(
                        "value", configurableMachines.requestedEnergyConsumption(List.of(instance.instanceId()))));
            }
            sendGridSummary(player, instance.instanceId());
            return;
        }
        send(player, "tools.multimeter.unsupported", "<red>This block has no readable energy storage.</red>", Map.of());
    }

    private void inspectEnergyComponent(Player player, SfxBlockInstanceRecord instance) {
        SfxEnergyService.SfxEnergyInspection inspection = energyService.inspectEnergyComponent(instance.instanceId());
        if (inspection == null) {
            send(player, "tools.multimeter.unsupported", "<red>This block has no readable energy storage.</red>", Map.of());
            return;
        }
        sendMachineHeader(player, instance, inspection.componentType().name().toLowerCase(Locale.ROOT));
        if (inspection.capacity() > 0) {
            sendEnergyLine(player, new EnergyNumbers(inspection.storedEnergy(), inspection.capacity()));
        }
        if (inspection.generationPerTick() > 0) {
            send(player, "tools.multimeter.enhanced.generation", "<gray>Generation: <green>{value}</green> J/t</gray>", Map.of("value", inspection.generationPerTick()));
        }
        send(player, inspection.connected() ? "tools.multimeter.enhanced.connected" : "tools.multimeter.enhanced.disconnected",
                inspection.connected() ? "<gray>Connection: <green>Connected</green></gray>" : "<gray>Connection: <red>Disconnected</red></gray>", Map.of());
        if (inspection.autoPaused()) {
            send(player, "tools.multimeter.enhanced.auto-paused", "<gray>Auto pause: <yellow>enabled</yellow></gray>", Map.of());
        }
        sendGridSummary(player, instance.instanceId());
    }

    private void sendMachineHeader(Player player, SfxBlockInstanceRecord instance, String typeKey) {
        send(player, "tools.multimeter.enhanced.title", "<yellow>Multimeter</yellow>", Map.of());
        send(player, "tools.multimeter.enhanced.machine", "<gray>Machine: <white>{name}</white></gray>", Map.of(
                "name", itemName(instance.typeId())));
        send(player, "tools.multimeter.enhanced.type", "<gray>Type: <white>{type}</white></gray>", Map.of(
                "type", localizedType(typeKey)));
        send(player, "tools.multimeter.enhanced.location", "<gray>Location: <white>{location}</white></gray>", Map.of(
                "location", locationString(instance.anchorKey())));
    }

    private void sendEnergyLine(Player player, EnergyNumbers numbers) {
        if (numbers == null || numbers.capacity() <= 0) {
            return;
        }
        int percent = (int) Math.floor(numbers.stored() * 100.0D / Math.max(1, numbers.capacity()));
        send(player, "tools.multimeter.enhanced.energy", "<gray>Energy: {bar} <yellow>{stored}</yellow><gray>/<yellow>{capacity}</yellow> J</gray>", Map.of(
                "bar", bar(percent),
                "stored", numbers.stored(),
                "capacity", numbers.capacity()));
    }

    private void sendGridSummary(Player player, UUID instanceId) {
        SfxEnergyService.SfxEnergyGridInspection grid = energyService.inspectGridForMember(instanceId);
        if (grid == null) {
            send(player, "tools.multimeter.enhanced.scheduler-missing", "<gray>Scheduler: <red>not connected</red></gray>", Map.of());
            return;
        }
        send(player, "tools.multimeter.enhanced.scheduler", "<gray>Scheduler: <white>{location}</white></gray>", Map.of(
                "location", locationString(grid.regulatorKey())));
        int percent = grid.capacity() <= 0 ? 0 : (int) Math.floor(grid.storedEnergy() * 100.0D / Math.max(1, grid.capacity()));
        send(player, "tools.multimeter.enhanced.grid-energy", "<gray>Grid energy: {bar} <yellow>{stored}</yellow><gray>/<yellow>{capacity}</yellow> J</gray>", Map.of(
                "bar", bar(percent),
                "stored", grid.storedEnergy(),
                "capacity", grid.capacity()));
        send(player, "tools.multimeter.enhanced.grid-rates", "<gray>Generation: <green>{generation}</green> J/t <dark_gray>|</dark_gray> Consumption: <yellow>{consumption}</yellow> J/t</gray>", Map.of(
                "generation", grid.generationPerTick(),
                "consumption", grid.consumptionPerTick()));
        send(player, "tools.multimeter.enhanced.grid-members", "<gray>Connected: <white>{members}</white> <dark_gray>(G:{generators}, R:{reactors}, C:{capacitors}, N:{connectors}, M:{consumers})</dark_gray></gray>", Map.of(
                "members", grid.members(),
                "generators", grid.generators(),
                "reactors", grid.reactors(),
                "capacitors", grid.capacitors(),
                "connectors", grid.connectors(),
                "consumers", grid.consumers()));
        if (grid.autoPaused() > 0) {
            send(player, "tools.multimeter.enhanced.grid-auto-paused", "<gray>Auto-paused generators: <yellow>{amount}</yellow></gray>", Map.of(
                    "amount", grid.autoPaused()));
        }
    }

    private void sendConnectionStatus(Player player, UUID instanceId) {
        boolean connected = energyService.isConnectedToOnlineGrid(instanceId);
        send(player,
                connected ? "energy.messages.connected" : "energy.messages.disconnected",
                connected ? "<green>Connected</green>" : "<red>Not connected</red>",
                Map.of());
    }

    private EnergyNumbers energyNumbers(SfxBlockInstanceRecord instance) {
        if (energyService.supportsType(instance.typeId())) {
            SfxEnergyService.SfxEnergyInspection inspection = energyService.inspectEnergyComponent(instance.instanceId());
            return inspection == null ? null : new EnergyNumbers(inspection.storedEnergy(), inspection.capacity());
        }
        if (electricMachines.supportsType(instance.typeId())) {
            return new EnergyNumbers(electricMachines.consumerStoredEnergy(instance.instanceId()), electricMachines.consumerCapacity(instance.typeId()));
        }
        if (configurableMachines.isConsumer(instance.typeId())) {
            return new EnergyNumbers(configurableMachines.consumerStoredEnergy(instance.instanceId()), configurableMachines.consumerCapacity(instance.typeId()));
        }
        if (configurableMachines.isProducer(instance.typeId())) {
            return new EnergyNumbers(configurableMachines.producerStoredEnergy(instance.instanceId()), configurableMachines.producerCapacity(instance.typeId()));
        }
        return null;
    }

    private String itemName(String typeId) {
        Component name = localization.itemName(typeId, Component.text(typeId));
        return Text.toLegacy(name);
    }

    private String localizedType(String typeKey) {
        return localization.text("tools.multimeter.types." + typeKey, typeKey);
    }

    private String locationString(SfxBlockAnchorKey key) {
        World world = plugin.getServer().getWorld(key.worldId());
        String worldName = world == null ? key.worldId().toString() : world.getName();
        return worldName + " " + key.x() + " " + key.y() + " " + key.z();
    }

    private String bar(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        int filled = Math.max(0, Math.min(10, (int) Math.round(clamped / 10.0D)));
        return "<green>" + "|".repeat(filled) + "</green><dark_gray>" + "|".repeat(10 - filled) + "</dark_gray> <gray>" + clamped + "%</gray>";
    }

    private void send(Player player, String path, String fallback, Map<String, ?> placeholders) {
        player.sendMessage(Text.prefixed(plugin, localization.text(path, fallback, placeholders)));
    }

    private record EnergyNumbers(int stored, int capacity) {
    }
}
