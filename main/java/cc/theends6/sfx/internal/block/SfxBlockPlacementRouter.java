package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.android.SfxAndroidType;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.core.SfxResult;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.machine.SfxWorldMutationBridge;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;








public final class SfxBlockPlacementRouter {
    private static final Set<String> GENERIC_ANCHORED_BLOCKS = Set.of(
            "sf:hardened_glass",
            "sf:wither_proof_obsidian",
            "sf:wither_proof_glass"
    );

    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final SfxBasicMachineBlockListener basicMachines;
    private final SfxElectricMachineService electricMachines;
    private final SfxConfigurableMachineService configurableMachines;
    private final SfxEnergyService energyService;
    private final SfxCargoService cargoService;
    private final SfxDecorationService decorationService;
    private final SfxGpsService gpsService;
    private final SfxAncientAltarService ancientAltarService;
    private final SfxAndroidService androidService;
    private final SfxSpawnerService spawnerService;
    private final SfxBlockPlacerService blockPlacerService;
    private final SfxInfusedHopperService infusedHopperService;
    private final SfxHologramProjectorService hologramProjectorService;
    private final SfxRuntime runtime;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final Logger logger;

    public SfxBlockPlacementRouter(
            SfxItems items,
            SfxBlockDataService blockData,
            SfxBasicMachineBlockListener basicMachines,
            SfxElectricMachineService electricMachines,
            SfxConfigurableMachineService configurableMachines,
            SfxEnergyService energyService,
            SfxCargoService cargoService,
            SfxDecorationService decorationService,
            SfxGpsService gpsService,
            SfxAncientAltarService ancientAltarService,
            SfxAndroidService androidService,
            SfxSpawnerService spawnerService,
            SfxBlockPlacerService blockPlacerService,
            SfxInfusedHopperService infusedHopperService,
            SfxHologramProjectorService hologramProjectorService,
            SfxRuntime runtime,
            SfxMachineRuntimeEngine machineRuntime,
            Logger logger
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.basicMachines = Objects.requireNonNull(basicMachines, "basicMachines");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.energyService = Objects.requireNonNull(energyService, "energyService");
        this.cargoService = Objects.requireNonNull(cargoService, "cargoService");
        this.decorationService = Objects.requireNonNull(decorationService, "decorationService");
        this.gpsService = Objects.requireNonNull(gpsService, "gpsService");
        this.ancientAltarService = Objects.requireNonNull(ancientAltarService, "ancientAltarService");
        this.androidService = Objects.requireNonNull(androidService, "androidService");
        this.spawnerService = Objects.requireNonNull(spawnerService, "spawnerService");
        this.blockPlacerService = Objects.requireNonNull(blockPlacerService, "blockPlacerService");
        this.infusedHopperService = Objects.requireNonNull(infusedHopperService, "infusedHopperService");
        this.hologramProjectorService = Objects.requireNonNull(hologramProjectorService, "hologramProjectorService");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.machineRuntime = Objects.requireNonNull(machineRuntime, "machineRuntime");
        this.logger = logger == null ? Logger.getLogger("SlimeFunX") : logger;
    }

    public void handlePlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!isPlaceableMarker(marker.itemId(), marker.flags())) {
                if (marker.flags().contains("placeable-block")) {
                    event.setCancelled(true);
                }
                return;
            }
            if (SfxAndroidType.isAndroidItem(marker.itemId()) && shouldRedirectAndroidPlacement(event)) {
                redirectAndroidPlacement(event, marker.itemId());
                return;
            }
            if (cargoService.supportsType(marker.itemId()) && !cargoService.canPlace(marker.itemId(), event)) {
                event.setCancelled(true);
                return;
            }
            if (blockData.findAnchor(event.getBlockPlaced().getLocation()).isPresent()) {
                return;
            }
            SfxBlockPlacementContext context = new SfxBlockPlacementContext(
                    marker.itemId(),
                    event.getBlockPlaced().getLocation(),
                    event.getBlockPlaced().getType(),
                    event.getPlayer().getUniqueId(),
                    event.getPlayer(),
                    event.getItemInHand());
            SfxBlockPlacementTransaction transaction = new SfxBlockPlacementTransaction(
                    blockData,
                    new SfxDelegatingBlockBehavior(marker.itemId(), this::initializePlacedDomain),
                    logger);
            SfxResult<UUID> result = transaction.commit(context);
            if (!result.success()) {
                event.setCancelled(true);
                result.cause().ifPresentOrElse(
                        cause -> logger.log(Level.WARNING, "Failed to initialize SFX block placement for " + marker.itemId()
                                + " at " + event.getBlockPlaced().getLocation(), cause),
                        () -> logger.warning("Failed to initialize SFX block placement for " + marker.itemId()
                                + " at " + event.getBlockPlaced().getLocation() + ": " + result.message()));
            }
        });
    }

    public boolean placeFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId, BlockFace facing) {
        if (itemId == null || stack == null || target == null || !target.getType().isAir()) {
            return false;
        }
        var marker = items.readMarker(stack).orElse(null);
        var definition = items.definition(itemId).orElse(null);
        if (marker == null || definition == null || !isPlaceableMarker(itemId, marker.flags())) {
            return false;
        }
        SfxBlockPlacementContext context = new SfxBlockPlacementContext(
                itemId, target.getLocation(), definition.material(), ownerId, null, stack);
        SfxBlockPlacementTransaction transaction = new SfxBlockPlacementTransaction(
                blockData,
                new SfxDelegatingBlockBehavior(itemId, (placedContext, instanceId) -> {
                    applyPlacementFacing(target, facing, itemId);
                    initializePlacedDomain(placedContext, instanceId, facing);
                }),
                logger);
        return transaction.commit(context).success();
    }

    static void applyPlacementFacing(Block block, BlockFace facing, String typeId) {
        if (block == null || facing == null) {
            return;
        }
        BlockData data = block.getBlockData();
        boolean changed = false;
        if (data instanceof Directional directional && directional.getFaces().contains(facing)) {
            directional.setFacing(facing);
            changed = true;
        } else if (data instanceof Rotatable rotatable && facing.getModY() == 0) {
            rotatable.setRotation(facing);
            changed = true;
        } else if (data instanceof Orientable orientable) {
            org.bukkit.Axis axis = facing.getModY() != 0 ? org.bukkit.Axis.Y
                    : facing.getModX() != 0 ? org.bukkit.Axis.X : org.bukkit.Axis.Z;
            if (orientable.getAxes().contains(axis)) {
                orientable.setAxis(axis);
                changed = true;
            }
        }
        if (changed) {
            SfxWorldMutationBridge.setBlockData(null, typeId, block, data, false,
                    "block-placement", "apply-placer-facing");
        }
    }

    public boolean isPlaceableMarker(String itemId, List<String> flags) {
        if (basicMachines.supportsType(itemId)
                || electricMachines.supportsType(itemId)
                || configurableMachines.supportsType(itemId)
                || energyService.supportsType(itemId)
                || cargoService.supportsType(itemId)
                || gpsService.supportsType(itemId)
                || decorationService.supportsType(itemId)
                || ancientAltarService.supportsType(itemId)
                || androidService.supportsType(itemId)
                || spawnerService.supportsType(itemId)
                || blockPlacerService.supportsType(itemId)
                || infusedHopperService.supportsType(itemId)
                || hologramProjectorService.supportsType(itemId)) {
            return true;
        }
        return flags != null && flags.contains("placeable-block") && GENERIC_ANCHORED_BLOCKS.contains(itemId);
    }

    private void initializePlacedDomain(SfxBlockPlacementContext context, UUID instanceId) {
        initializePlacedDomain(context, instanceId, null);
    }

    private void initializePlacedDomain(SfxBlockPlacementContext context, UUID instanceId, BlockFace programmaticFacing) {
        String typeId = context.typeId();
        if (decorationService.supportsType(typeId)) {
            decorationService.handlePlaced(instanceId, typeId);
        }
        if (gpsService.supportsType(typeId)) {
            gpsService.handlePlaced(instanceId, typeId);
        }
        if (ancientAltarService.supportsType(typeId)) {
            ancientAltarService.handlePlaced(instanceId, typeId);
        }
        if (androidService.supportsType(typeId)) {
            if (programmaticFacing == null) {
                androidService.handlePlaced(instanceId, typeId, context.player(), context.location().getBlock());
            } else {
                androidService.handlePlaced(instanceId, typeId, programmaticFacing, context.location().getBlock());
            }
        }
        if (spawnerService.supportsType(typeId)) {
            spawnerService.handlePlaced(instanceId, typeId, context.itemInHand());
        }
        if (blockPlacerService.supportsType(typeId)) {
            blockPlacerService.handlePlaced(instanceId, typeId);
        }
        if (infusedHopperService.supportsType(typeId)) {
            infusedHopperService.handlePlaced(instanceId, typeId);
        }
        if (hologramProjectorService.supportsType(typeId)) {
            hologramProjectorService.handlePlaced(instanceId, typeId);
        }
        machineRuntime.recordState(instanceId, typeId, context.location(), SfxMachineStatus.IDLE);
    }

    private boolean shouldRedirectAndroidPlacement(BlockPlaceEvent event) {
        return event.getBlockPlaced() != null && event.getBlockPlaced().getType() == Material.PLAYER_WALL_HEAD;
    }

    private void redirectAndroidPlacement(BlockPlaceEvent event, String itemId) {
        Block target = event.getBlockPlaced();
        event.setCancelled(true);
        if (target == null || blockData.findAnchor(target.getLocation()).isPresent()) {
            return;
        }
        ItemStack refund = singleRefundItem(event.getItemInHand());
        boolean consumed = consumeManualPlacementItem(event);
        runtime.executeAtLater(target.getLocation(), 1L, () -> {
            if (!target.getType().isAir() || blockData.findAnchor(target.getLocation()).isPresent()) {
                refundManualPlacementItem(event.getPlayer(), refund, consumed);
                return;
            }
            boolean placed = SfxProgrammaticPlacementTransactions.place(
                    blockData,
                    itemId,
                    target,
                    Material.PLAYER_HEAD,
                    event.getPlayer().getUniqueId(),
                    event.getItemInHand(),
                    (context, instanceId) -> {
                        androidService.handlePlaced(instanceId, itemId, event.getPlayer(), target);
                        machineRuntime.recordState(instanceId, itemId, target.getLocation(), SfxMachineStatus.IDLE);
                    },
                    logger
            ).isPresent();
            if (!placed) {
                refundManualPlacementItem(event.getPlayer(), refund, consumed);
            }
        });
    }

    private boolean consumeManualPlacementItem(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        ItemStack stack = event.getItemInHand();
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemStack remaining = stack.clone();
        remaining.setAmount(remaining.getAmount() - 1);
        if (remaining.getAmount() <= 0) {
            remaining = null;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.getPlayer().getInventory().setItemInOffHand(remaining);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(remaining);
        }
        return true;
    }

    private ItemStack singleRefundItem(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return null;
        }
        ItemStack refund = source.clone();
        refund.setAmount(1);
        return refund;
    }

    private void refundManualPlacementItem(org.bukkit.entity.Player player, ItemStack refund, boolean consumed) {
        if (!consumed || player == null || refund == null || refund.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(refund.clone());
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null && !leftover.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }
}
