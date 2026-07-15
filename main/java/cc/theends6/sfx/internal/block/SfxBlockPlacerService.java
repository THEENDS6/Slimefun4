package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.machine.*;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SfxBlockPlacerService implements Listener, SfxProgrammaticBlockPlacement {
    public static final String BLOCK_PLACER = "sf:block_placer";

    private static final Set<Material> UNSAFE_VANILLA_PLACEMENTS = new HashSet<>(Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.LIGHT
    ));

    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final SfxSpawnerService spawners;
    private final SfxHologramProjectorService holograms;
    private final SfxInfusedHopperService infusedHoppers;
    private final SfxMachineRuntimeEngine machineRuntime;
    private volatile SfxBlockPlacementRouter placementRouter;

    public SfxBlockPlacerService(SfxRuntime runtime, SfxItems items, SfxBlockDataService blockData, SfxSpawnerService spawners, SfxHologramProjectorService holograms, SfxInfusedHopperService infusedHoppers, SfxMachineRuntimeEngine machineRuntime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.spawners = Objects.requireNonNull(spawners, "spawners");
        this.holograms = Objects.requireNonNull(holograms, "holograms");
        this.infusedHoppers = Objects.requireNonNull(infusedHoppers, "infusedHoppers");
        this.machineRuntime = machineRuntime == null ? new SfxMachineRuntimeEngine() : machineRuntime;
        registerFrameworkDefinitions();
    }

    private void registerFrameworkDefinitions() {
        machineRuntime.registerDefinitionIfAbsent(SfxMachineDefinition.builder(BLOCK_PLACER)
                .displayName(BLOCK_PLACER)
                .category(SfxMachineCategory.SPECIAL)
                .effect(SfxMachineEffect.marker("placer:resolve-target", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("placer:consume-input", SfxMachinePhase.BEFORE_INPUT))
                .effect(SfxMachineEffect.marker("placer:place-block", SfxMachinePhase.ON_COMPLETE))
                .effect(SfxMachineEffect.marker("placer:rollback-on-fail", SfxMachinePhase.ON_ERROR))
                .build());
    }

    public void bindPlacementRouter(SfxBlockPlacementRouter placementRouter) {
        this.placementRouter = Objects.requireNonNull(placementRouter, "placementRouter");
    }

    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) return SfxMachinePhaseResult.cont();
        context.put("placer.framework.effect", effectName);
        Block dispenser = context.attachment("placer.dispenser", Block.class).orElse(null);
        Block target = context.attachment("placer.target", Block.class).orElse(null);
        ItemStack one = context.attachment("placer.item", ItemStack.class).orElse(null);
        if ("placer:resolve-target".equals(effectName)) {
            boolean valid = dispenser != null && dispenser.getType() == Material.DISPENSER && target != null && canReplace(target) && one != null && !one.getType().isAir();
            context.put("placer.target.valid", valid);
            return valid ? SfxMachinePhaseResult.cont() : SfxMachinePhaseResult.blocked(SfxMachineStatus.BLOCKED, "block placer target is invalid");
        }
        if ("placer:consume-input".equals(effectName)) {
            ItemStack source = context.attachment("placer.sourceItem", ItemStack.class).orElse(one);
            boolean consumed = dispenser != null && source != null && consumeOne(dispenser, source);
            context.put("placer.input.consumed", consumed);
            return consumed ? SfxMachinePhaseResult.cont() : SfxMachinePhaseResult.blocked(SfxMachineStatus.NO_INPUT, "block placer input could not be consumed");
        }
        if ("placer:place-block".equals(effectName)) {
            UUID ownerId = context.attachment("placer.ownerId", UUID.class).orElse(null);
            boolean placed = false;
            if (target != null && one != null && canReplace(target)) {
                target.getWorld().playEffect(target.getLocation(), Effect.STEP_SOUND, one.getType());
                BlockFace facing = context.attachment("placer.facing", BlockFace.class).orElse(BlockFace.NORTH);
                placed = placeOne(one, target, ownerId, facing);
            }
            context.put("placer.placed", placed);
            return placed ? SfxMachinePhaseResult.cont() : SfxMachinePhaseResult.failed("block placer placement failed");
        }
        if ("placer:rollback-on-fail".equals(effectName)) {
            Boolean placed = context.attachment("placer.placed", Boolean.class).orElse(Boolean.FALSE);
            Boolean consumed = context.attachment("placer.input.consumed", Boolean.class).orElse(Boolean.FALSE);
            if (!Boolean.TRUE.equals(placed) && Boolean.TRUE.equals(consumed) && dispenser != null && one != null) {
                returnOne(dispenser, one);
                context.put("placer.rollback.returned", Boolean.TRUE);
            }
            return SfxMachinePhaseResult.cont();
        }
        context.put("placer.framework.effect.handled", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private java.util.Map<String, Object> frameworkAttributes(SfxBlockInstanceRecord instance, Block dispenserBlock, Block target, ItemStack item) {
        java.util.Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("placer.instance", instance);
        attributes.put("placer.dispenser", dispenserBlock);
        attributes.put("placer.target", target);
        attributes.put("placer.item", item);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    public boolean supportsType(String typeId) {
        return BLOCK_PLACER.equals(typeId);
    }

    public void handlePlaced(UUID instanceId, String typeId) {
        
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        machineRuntime.runPhase(typeId, SfxMachinePhase.ON_BREAK, instanceId, block.getLocation(), null, null, SfxMachineStatus.IDLE, frameworkAttributes(blockData.findInstance(instanceId).orElse(null), block, null, null));
        dropStoredContents(block);
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
                Material.DISPENSER,
                ownerId,
                stack,
                (context, instanceId) -> {
                    handlePlaced(instanceId, itemId);
                    machineRuntime.recordState(instanceId, itemId, target.getLocation(), SfxMachineStatus.IDLE);
                },
                java.util.logging.Logger.getLogger("SlimeFunX")
        ).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenserBlock = event.getBlock();
        if (dispenserBlock.getType() != Material.DISPENSER) {
            return;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(dispenserBlock.getLocation()).orElse(null);
        SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        if (!(dispenserBlock.getBlockData() instanceof Directional directional)) {
            return;
        }
        Block target = dispenserBlock.getRelative(directional.getFacing());
        if (!canReplace(target)) {
            event.setCancelled(true);
            return;
        }
        ItemStack dispensed = event.getItem();
        if (dispensed == null || dispensed.getType().isAir()) {
            return;
        }
        if (isShulkerBox(dispensed.getType())) {
            return;
        }
        event.setCancelled(true);
        ItemStack one = dispensed.clone();
        one.setAmount(1);
        UUID ownerId = instance.ownerId();
        runtime.executeAtLater(target.getLocation(), 2L, () -> {
            java.util.Map<String, Object> framework = frameworkAttributes(instance, dispenserBlock, target, one);
            framework.put("placer.sourceItem", dispensed);
            framework.put("placer.ownerId", ownerId);
            framework.put("placer.facing", directional.getFacing());
            SfxMachineTickContext tickContext = new SfxMachineTickContext(0L, 1L, false);
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.IDLE, framework), framework, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
                machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_ERROR, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.ERROR, framework);
                return;
            }
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_INPUT, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.IDLE, framework), framework, SfxMachinePhase.BEFORE_INPUT.name())) {
                machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_ERROR, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.ERROR, framework);
                return;
            }
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_COMPLETE, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.RUNNING, framework), framework, SfxMachinePhase.ON_COMPLETE.name())) {
                machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_ERROR, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.ERROR, framework);
                return;
            }
            machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.AFTER_TICK, instance.instanceId(), dispenserBlock.getLocation(), tickContext, null, SfxMachineStatus.RUNNING, framework);
        });
    }

    private boolean placeOne(ItemStack stack, Block target, UUID ownerId, BlockFace facing) {
        String itemId = items.readMarker(stack).map(SfxItemMarker::itemId).orElse(null);
        if (itemId != null) {
            SfxBlockPlacementRouter router = placementRouter;
            return router != null && router.placeFromBlockPlacer(itemId, stack, target, ownerId, facing);
        }
        Material material = stack.getType();
        if (!material.isBlock() || UNSAFE_VANILLA_PLACEMENTS.contains(material) || material.name().endsWith("_SPAWN_EGG")) {
            return false;
        }
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, BLOCK_PLACER, target, material, true, "placer", "place-block");
        SfxBlockPlacementRouter.applyPlacementFacing(target, facing, BLOCK_PLACER);
        applyCustomName(stack, target);
        return true;
    }

    private boolean canReplace(Block target) {
        if (target == null || blockData.findAnchor(target.getLocation()).isPresent()) {
            return false;
        }
        return target.getType().isAir();
    }

    private boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    private void applyCustomName(ItemStack stack, Block target) {
        if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) {
            return;
        }
        BlockState state = target.getState();
        if (state instanceof Nameable nameable) {
            nameable.customName(stack.getItemMeta().displayName());
            state.update(true, false);
        }
    }

    private boolean consumeOne(Block dispenserBlock, ItemStack dispensed) {
        if (!(dispenserBlock.getState() instanceof Dispenser dispenser)) {
            return false;
        }
        Inventory inventory = dispenser.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir() || !stack.isSimilar(dispensed)) {
                continue;
            }
            int next = stack.getAmount() - 1;
            if (next <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(next);
                inventory.setItem(slot, stack);
            }
            return true;
        }
        return false;
    }

    private void returnOne(Block dispenserBlock, ItemStack item) {
        if (!(dispenserBlock.getState() instanceof Dispenser dispenser)) {
            return;
        }
        cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAll(dispenser.getInventory(), item.clone(), false, "block-placer:return-one");
    }

    private void dropStoredContents(Block block) {
        if (!(block.getState() instanceof org.bukkit.inventory.InventoryHolder holder)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        for (ItemStack content : inventory.getContents()) {
            if (content == null || content.getType().isAir()) {
                continue;
            }
            SfxBlockDrops.dropItem(block, content.clone());
        }
        inventory.clear();
    }
}
