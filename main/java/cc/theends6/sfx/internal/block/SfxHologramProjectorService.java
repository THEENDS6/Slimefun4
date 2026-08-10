package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.api.block.SfxBlockLifecycleState;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextProjection;
import cc.theends6.sfx.internal.machine.*;
import cc.theends6.sfx.api.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxHologramProjectorService implements Listener, SfxProgrammaticBlockPlacement {
    public static final String HOLOGRAM_PROJECTOR = "sf:hologram_projector";
    private static final HologramState DEFAULT_STATE = new HologramState("Edit me via the Projector", 0.5D);

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxFloatingTextDisplayService floatingText;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final Map<UUID, UUID> pendingEdits = new ConcurrentHashMap<>();

    public SfxHologramProjectorService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData, SfxFloatingTextDisplayService floatingText, SfxMachineRuntimeEngine machineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.floatingText = Objects.requireNonNull(floatingText, "floatingText");
        this.machineRuntime = machineRuntime == null ? new SfxMachineRuntimeEngine() : machineRuntime;
        registerFrameworkDefinitions();
    }

    private void registerFrameworkDefinitions() {
        machineRuntime.registerDefinitionIfAbsent(SfxMachineDefinition.builder(HOLOGRAM_PROJECTOR)
                .displayName(HOLOGRAM_PROJECTOR)
                .category(SfxMachineCategory.SPECIAL)
                .effect(SfxMachineEffect.marker("hologram:open-editor", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("hologram:update-text", SfxMachinePhase.ON_COMPLETE))
                .effect(SfxMachineEffect.marker("hologram:sync-display", SfxMachinePhase.AFTER_TICK))
                .build());
    }

    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) return SfxMachinePhaseResult.cont();
        context.put("hologram.framework.effect", effectName);
        SfxBlockInstanceRecord instance = context.attachment("hologram.instance", SfxBlockInstanceRecord.class).orElse(null);
        HologramState state = context.attachment("hologram.state", HologramState.class).orElse(instance == null ? DEFAULT_STATE : HologramState.decode(instance.stateBlob()));
        if ("hologram:open-editor".equals(effectName)) {
            Player player = context.attachment("hologram.player", Player.class).orElse(null);
            if (player == null || instance == null) {
                return SfxMachinePhaseResult.blocked(SfxMachineStatus.BLOCKED, "hologram editor context missing");
            }
            if (!canEdit(player, instance)) {
                send(player, "machines.hologram-projector.not-owner");
                return SfxMachinePhaseResult.blocked(SfxMachineStatus.BLOCKED, "player cannot edit this hologram");
            }
            openEditor(player, instance.instanceId());
            context.put("hologram.editor.opened", Boolean.TRUE);
            return SfxMachinePhaseResult.cont();
        }
        if (("hologram:update-text".equals(effectName) || "hologram:sync-display".equals(effectName)) && instance != null) {
            updateProjection(instance, state);
            context.put("hologram.display.synced", Boolean.TRUE);
            return SfxMachinePhaseResult.cont();
        }
        context.put("hologram.framework.effect.handled", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private Map<String, Object> frameworkAttributes(SfxBlockInstanceRecord instance, HologramState state) {
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("hologram.instance", instance);
        attributes.put("hologram.state", state);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    public boolean supportsType(String typeId) {
        return HOLOGRAM_PROJECTOR.equals(typeId);
    }

    public void handlePlaced(UUID instanceId, String typeId) {
        if (!supportsType(typeId)) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        HologramState state = HologramState.decode(instance.stateBlob());
        Map<String, Object> framework = frameworkAttributes(instance, state);
        World world = Bukkit.getWorld(instance.anchorKey().worldId());
        Location location = world == null ? null : new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
        machineRuntime.runPhase(typeId, SfxMachinePhase.ON_PLACE, instanceId, location, null, null, SfxMachineStatus.IDLE, framework);
        if (!instance.hasState()) {
            blockData.updateInstanceState(instanceId, state.encode(), SfxBlockLifecycleState.IDLE);
        }
        updateProjection(instance, state);
        machineRuntime.runPhase(typeId, SfxMachinePhase.AFTER_TICK, instanceId, location, null, null, SfxMachineStatus.IDLE, framework);
    }

    public void rebuildIndex() {
        for (SfxAnchorRecord anchor : blockData.allAnchorsSnapshot()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && supportsType(instance.typeId())) {
                handlePlaced(instance.instanceId(), instance.typeId());
            }
        }
    }

    public void shutdown() {
        pendingEdits.clear();
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance != null) {
            World world = Bukkit.getWorld(instance.anchorKey().worldId());
            Location location = world == null ? block.getLocation() : new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
            machineRuntime.runPhase(typeId, SfxMachinePhase.ON_BREAK, instanceId, location, null, null, SfxMachineStatus.IDLE, frameworkAttributes(instance, HologramState.decode(instance.stateBlob())));
            floatingText.remove(key(instance.anchorKey()));
        }
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    @Override
    public boolean canPlaceFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId) {
        return supportsType(itemId) && target != null && target.getType().isAir();
    }

    @Override
    public boolean placeFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId) {
        if (!canPlaceFromBlockPlacer(itemId, stack, target, ownerId)) {
            return false;
        }
        return SfxProgrammaticPlacementTransactions.place(
                blockData,
                itemId,
                target,
                Material.QUARTZ_SLAB,
                ownerId,
                stack,
                (context, instanceId) -> {
                    handlePlaced(instanceId, itemId);
                    machineRuntime.recordState(instanceId, itemId, target.getLocation(), SfxMachineStatus.IDLE);
                },
                plugin.getLogger()
        ).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(event.getClickedBlock().getLocation()).orElse(null);
        SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        event.setCancelled(true);
        Map<String, Object> framework = frameworkAttributes(instance, HologramState.decode(instance.stateBlob()));
        framework.put("hologram.player", event.getPlayer());
        SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instance.instanceId(), event.getClickedBlock().getLocation(), null, null, SfxMachineStatus.IDLE, framework), framework, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HologramEditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null || !supportsType(instance.typeId()) || !canEdit(player, instance)) {
            player.closeInventory();
            return;
        }
        HologramState state = HologramState.decode(instance.stateBlob());
        if (event.getRawSlot() == 0) {
            player.closeInventory();
            pendingEdits.put(player.getUniqueId(), instance.instanceId());
            send(player, "machines.hologram-projector.enter-text");
            return;
        }
        if (event.getRawSlot() == 1) {
            double delta = event.isRightClick() ? -0.1D : 0.1D;
            double next = Math.max(0.1D, Math.min(8.0D, round1(state.offset() + delta)));
            persist(instance, new HologramState(state.text(), next));
            openEditor(player, instance.instanceId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID instanceId = pendingEdits.remove(event.getPlayer().getUniqueId());
        if (instanceId == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        if (message == null || message.equalsIgnoreCase("cancel")) {
            runtime.executeForPlayer(event.getPlayer(), () -> send(event.getPlayer(), "machines.hologram-projector.edit-cancelled"));
            return;
        }
        runtime.executeForPlayer(event.getPlayer(), () -> {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !supportsType(instance.typeId())) {
                return;
            }
            HologramState old = HologramState.decode(instance.stateBlob());
            persist(instance, new HologramState(message, old.offset()));
            send(event.getPlayer(), "machines.hologram-projector.updated");
            openEditor(event.getPlayer(), instance.instanceId());
        });
    }

    private void openEditor(Player player, UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        HologramState state = HologramState.decode(instance.stateBlob());
        Component title = localization.component("machines.hologram-projector.inventory-title");
        Inventory inventory = plugin.getServer().createInventory(new HologramEditorHolder(instanceId), 9, title);
        inventory.setItem(0, SfxUiItems.named(Material.NAME_TAG,
                Text.renderFlexible(localization.text("machines.hologram-projector.text-item-name")),
                List.of(Component.empty(), Text.renderFlexible("&f" + state.text()))));
        String offsetLabel = String.format(Locale.ROOT, "%.1f", state.offset() + 1.0D);
        inventory.setItem(1, SfxUiItems.named(Material.CLOCK,
                Text.renderFlexible(localization.text("machines.hologram-projector.offset-item-name", Map.of("offset", offsetLabel))),
                List.of(Component.empty(),
                        Text.renderFlexible(localization.text("machines.hologram-projector.offset-left")),
                        Text.renderFlexible(localization.text("machines.hologram-projector.offset-right")))));
        player.openInventory(inventory);
    }

    private boolean canEdit(Player player, SfxBlockInstanceRecord instance) {
        return instance.ownerId() == null || instance.ownerId().equals(player.getUniqueId()) || player.hasPermission("slimefunx.admin");
    }

    private void persist(SfxBlockInstanceRecord instance, HologramState state) {
        blockData.updateInstanceState(instance.instanceId(), state.encode(), SfxBlockLifecycleState.IDLE);
        SfxBlockInstanceRecord updated = blockData.findInstance(instance.instanceId()).orElse(instance);
        World world = Bukkit.getWorld(updated.anchorKey().worldId());
        Location location = world == null ? null : new Location(world, updated.anchorKey().x(), updated.anchorKey().y(), updated.anchorKey().z());
        Map<String, Object> framework = frameworkAttributes(updated, state);
        if (SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(updated.typeId(), SfxMachinePhase.ON_COMPLETE, updated.instanceId(), location, null, null, SfxMachineStatus.RUNNING, framework), framework, SfxMachinePhase.ON_COMPLETE.name())) {
            machineRuntime.runPhase(updated.typeId(), SfxMachinePhase.AFTER_TICK, updated.instanceId(), location, null, null, SfxMachineStatus.RUNNING, framework);
        }
    }

    private void updateProjection(SfxBlockInstanceRecord instance, HologramState state) {
        World world = Bukkit.getWorld(instance.anchorKey().worldId());
        if (world == null) {
            return;
        }
        double x = instance.anchorKey().x() + 0.5D;
        double y = instance.anchorKey().y() + state.offset();
        double z = instance.anchorKey().z() + 0.5D;
        floatingText.update(new SfxFloatingTextProjection(
                key(instance.anchorKey()),
                x,
                y,
                z,
                Text.renderFlexible(state.text()),
                plugin.getConfig().getInt("legacy.hologram-projector.view-distance-squared", 48 * 48)
        ));
    }

    private SfxFloatingTextKey key(SfxBlockAnchorKey anchorKey) {
        return new SfxFloatingTextKey("hologram_projector", anchorKey.worldId(), anchorKey.x(), anchorKey.y(), anchorKey.z());
    }

    private void send(Player player, String key) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key)));
    }

    private double round1(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private record HologramState(String text, double offset) {
        private static HologramState decode(byte[] blob) {
            if (blob == null || blob.length == 0) {
                return DEFAULT_STATE;
            }
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
                int version = in.readInt();
                if (version != 1) {
                    return DEFAULT_STATE;
                }
                return new HologramState(in.readUTF(), Math.max(0.1D, Math.min(8.0D, in.readDouble())));
            } catch (IOException ignored) {
                return DEFAULT_STATE;
            }
        }

        private byte[] encode() {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                out.writeUTF(text == null || text.isBlank() ? DEFAULT_STATE.text() : text);
                out.writeDouble(offset <= 0.0D ? DEFAULT_STATE.offset() : offset);
                return bytes.toByteArray();
            } catch (IOException exception) {
                return new byte[0];
            }
        }
    }

    private record HologramEditorHolder(UUID instanceId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
