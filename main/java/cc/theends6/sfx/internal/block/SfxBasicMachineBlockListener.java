package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.energy.SfxFuelBurnTimeBridge;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.machine.SfxMachineTickSettings;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBasicMachineBlockListener implements Listener {
    private static final Set<String> SUPPORTED_BLOCKS = Set.of(
            "sf:composter",
            "sf:crucible",
            "sf:output_chest",
            "sf:ignition_chamber",
            "sf:enhanced_furnace",
            "sf:enhanced_furnace_2",
            "sf:enhanced_furnace_3",
            "sf:enhanced_furnace_4",
            "sf:enhanced_furnace_5",
            "sf:enhanced_furnace_6",
            "sf:enhanced_furnace_7",
            "sf:enhanced_furnace_8",
            "sf:enhanced_furnace_9",
            "sf:enhanced_furnace_10",
            "sf:enhanced_furnace_11",
            "sf:reinforced_furnace",
            "sf:carbonado_edged_furnace"
    );

    private static final BlockFace[] OUTPUT_CHEST_SEARCH = {
            BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };
    private static final BlockFace[] IGNITION_SEARCH = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final Map<SfxBlockAnchorKey, ActiveCrucibleProcess> activeCrucibles = new ConcurrentHashMap<>();
    private final Set<SfxBlockAnchorKey> enhancedFurnaces = ConcurrentHashMap.newKeySet();
    private final Map<SfxBlockAnchorKey, VirtualFurnaceState> virtualFurnaces = new ConcurrentHashMap<>();
    private final Set<SfxBlockAnchorKey> viewedFurnaces = ConcurrentHashMap.newKeySet();
    private final Map<Material, Optional<VirtualFurnaceRecipe>> furnaceRecipeCache = new ConcurrentHashMap<>();
    private volatile SfxFuelBurnTimeBridge fuelBurnTimeBridge;
    private final SfxMachineTickSettings tickSettings;
    private volatile boolean furnaceTickerRunning;
    private volatile long furnaceTickCounter;

    public SfxBasicMachineBlockListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.tickSettings = SfxMachineTickSettings.from(plugin.getConfig());
        bootstrapEnhancedFurnaces();
        startEnhancedFurnaceTicker();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!SUPPORTED_BLOCKS.contains(marker.itemId())) {
                return;
            }
            if (blockData.findAnchor(event.getBlockPlaced().getLocation()).isPresent()) {
                return;
            }
            blockData.registerSingleBlock(marker.itemId(), event.getBlockPlaced().getLocation(), event.getBlockPlaced().getType(), event.getPlayer().getUniqueId());
            if (furnaceStats(marker.itemId()) != null) {
                enhancedFurnaces.add(SfxBlockAnchorKey.fromLocation(event.getBlockPlaced().getLocation()));
            }
        });
    }

    public boolean supportsType(String typeId) {
        return SUPPORTED_BLOCKS.contains(typeId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<SfxAnchorRecord> anchor = blockData.findAnchor(event.getBlock().getLocation());
        if (anchor.isEmpty()) {
            return;
        }
        String typeId = instanceType(anchor.get().instanceId());
        if (!SUPPORTED_BLOCKS.contains(typeId)) {
            return;
        }
        event.setDropItems(false);
        destroyAnchoredBlock(event.getBlock(), typeId);
    }

    public void destroyAnchoredBlock(Block block, String typeId) {
        if (block == null || typeId == null || !SUPPORTED_BLOCKS.contains(typeId)) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(block.getLocation());
        clearActiveCrucible(key, true);
        enhancedFurnaces.remove(key);
        virtualFurnaces.remove(key);
        viewedFurnaces.remove(key);
        dropStoredContents(block);
        dropPluginBlock(block, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick()) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null) {
            return;
        }
        Block clicked = interaction.block();
        String typeId = interaction.instance().typeId();
        if (typeId != null && SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        if (furnaceStats(typeId) != null) {
            SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(clicked.getLocation());
            enhancedFurnaces.add(key);
            virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState()).sleeping(false);
            return;
        }
        if ("sf:composter".equals(typeId)) {
            handleComposter(event, clicked);
            return;
        }
        if ("sf:crucible".equals(typeId)) {
            handleCrucible(event, clicked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Optional<SfxAnchorRecord> anchor = blockData.findAnchor(event.getBlock().getLocation());
        if (anchor.isEmpty()) {
            return;
        }
        String typeId = instanceType(anchor.get().instanceId());
        if (typeId == null) {
            return;
        }
        event.setCancelled(true);
        SfxPickBlockSupport.selectOrCreate(event.getPlayer(), items, typeId, event.getTargetSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuelBurn(FurnaceBurnEvent event) {
        if (event.getBlock().getType() != Material.FURNACE) {
            return;
        }
        FurnaceStats stats = furnaceStats(event.getBlock().getLocation());
        if (stats == null) {
            return;
        }
        event.setCancelled(true);
        enhancedFurnaces.add(SfxBlockAnchorKey.fromLocation(event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSmelt(FurnaceSmeltEvent event) {
        if (event.getBlock().getType() != Material.FURNACE) {
            return;
        }
        FurnaceStats stats = furnaceStats(event.getBlock().getLocation());
        if (stats == null) {
            return;
        }
        event.setCancelled(true);
        enhancedFurnaces.add(SfxBlockAnchorKey.fromLocation(event.getBlock().getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Furnace furnace)) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        if (furnaceStats(furnace.getLocation()) == null) {
            return;
        }
        enhancedFurnaces.add(key);
        viewedFurnaces.add(key);
        virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState()).sleeping(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Furnace furnace)) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        runtime.executeAtLater(furnace.getLocation(), 1L, () -> {
            if (event.getInventory().getViewers().isEmpty()) {
                viewedFurnaces.remove(key);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryMove(InventoryMoveItemEvent event) {
        wakeFurnaceInventory(event.getSource());
        wakeFurnaceInventory(event.getDestination());
    }

    private void wakeFurnaceInventory(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Furnace furnace)) {
            return;
        }
        if (furnaceStats(furnace.getLocation()) == null) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        enhancedFurnaces.add(key);
        virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState()).sleeping(false);
    }

    public Optional<Inventory> findOutputChestFor(Block machineBlock, ItemStack output) {
        for (BlockFace face : OUTPUT_CHEST_SEARCH) {
            Block target = machineBlock.getRelative(face);
            if (target.getType() != Material.CHEST) {
                continue;
            }
            if (!"sf:output_chest".equals(instanceTypeAt(target.getLocation()))) {
                continue;
            }
            BlockState state = target.getState();
            if (state instanceof Chest chest) {
                Inventory inventory = chest.getInventory();
                if (fits(inventory, output)) {
                    return Optional.of(inventory);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Inventory> findAnyOutputChestFor(Block machineBlock) {
        for (BlockFace face : OUTPUT_CHEST_SEARCH) {
            Block target = machineBlock.getRelative(face);
            if (target.getType() != Material.CHEST) {
                continue;
            }
            if (!"sf:output_chest".equals(instanceTypeAt(target.getLocation()))) {
                continue;
            }
            BlockState state = target.getState();
            if (state instanceof Chest chest) {
                return Optional.of(chest.getInventory());
            }
        }
        return Optional.empty();
    }

    public boolean useIgnitionChamber(Player player, Block smelteryDispenserBlock) {
        for (BlockFace face : IGNITION_SEARCH) {
            Block target = smelteryDispenserBlock.getRelative(face);
            if (target.getType() != Material.DROPPER) {
                continue;
            }
            if (!"sf:ignition_chamber".equals(instanceTypeAt(target.getLocation()))) {
                continue;
            }
            BlockState state = target.getState();
            if (!(state instanceof Dropper dropper)) {
                continue;
            }
            Inventory inventory = dropper.getInventory();
            int slot = inventory.first(Material.FLINT_AND_STEEL);
            if (slot < 0) {
                if (player != null) {
                    player.sendMessage(Text.prefixed(plugin, localization.text("machines.ignition-chamber-no-flint", "<red>自动点火室中没有打火石。</red>")));
                }
                return false;
            }
            ItemStack tool = inventory.getItem(slot);
            if (tool == null) {
                return false;
            }
            if (!tool.getItemMeta().isUnbreakable()) {
                short durability = (short) (tool.getDurability() + 1);
                if (durability >= tool.getType().getMaxDurability()) {
                    inventory.setItem(slot, null);
                } else {
                    tool.setDurability(durability);
                    inventory.setItem(slot, tool);
                }
            }
            playSound(smelteryDispenserBlock.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    private void handleComposter(PlayerInteractEvent event, Block block) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack input = itemInHand(event);
        if (input == null || input.getType().isAir()) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.empty", "<gray>手中没有可处理的物品。</gray>")));
            return;
        }
        ItemStack output = composterOutput(input);
        if (output == null) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.wrong-item", "<red>这个物品不能被这台机器处理。</red>")));
            return;
        }
        consumeFromHand(player, event, input, requiredComposterAmount(input.getType()));
        Location origin = block.getLocation();
        playComposterEffects(origin, input.getType());
        runtime.executeAtLater(origin, 20L, () -> completeComposter(block, output));
    }

    private void handleCrucible(PlayerInteractEvent event, Block block) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        SfxBlockAnchorKey anchorKey = SfxBlockAnchorKey.fromLocation(block.getLocation());
        if (activeCrucibles.containsKey(anchorKey)) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy", "<red>This machine is already working.</red>")));
            return;
        }
        ItemStack input = itemInHand(event);
        if (input == null || input.getType().isAir()) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.empty", "<gray>手中没有可处理的物品。</gray>")));
            return;
        }
        CruciblePlan plan = cruciblePlan(input.getType());
        if (plan == null) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.wrong-item", "<red>这个物品不能被这台机器处理。</red>")));
            return;
        }
        Block outputBlock = block.getRelative(BlockFace.UP);
        if (!canStartCrucible(outputBlock, plan.water())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.crucible-blocked", "<red>The space above the Crucible is blocked.</red>")));
            return;
        }
        consumeFromHand(player, event, input, plan.inputAmount());
        UUID token = UUID.randomUUID();
        activeCrucibles.put(anchorKey, new ActiveCrucibleProcess(token, outputBlock.getLocation(), plan.water()));
        setInstanceState(block, SfxBlockLifecycleState.ACTIVE, "crucible:active:" + plan.water());
        generateLiquid(anchorKey, token, outputBlock, plan.water());
    }

    private void completeComposter(Block block, ItemStack output) {
        Optional<Inventory> outputChest = findOutputChestFor(block, output);
        if (outputChest.isPresent()) {
            outputChest.get().addItem(output.clone());
        } else {
            World world = block.getWorld();
            world.dropItemNaturally(block.getRelative(BlockFace.UP).getLocation().add(0.5, 0.5, 0.5), output.clone());
        }
        playSound(block.getLocation(), Sound.BLOCK_COMPOSTER_READY, 1.0f, 1.0f);
    }

    private void generateLiquid(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        if (water && block.getWorld().getEnvironment() == World.Environment.NETHER
                && !plugin.getConfig().getBoolean("plugin-blocks.crucible.allow-water-in-nether", false)) {
            block.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 4, 0.1, 0.1, 0.1, 0.01);
            playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
            return;
        }

        if (block.getType() == (water ? Material.WATER : Material.LAVA)) {
            addLiquidLevel(anchorKey, token, block, water);
        } else if (block.getType() == (water ? Material.LAVA : Material.WATER)) {
            Levelled levelled = (Levelled) block.getBlockData();
            block.setType(levelled.getLevel() == 0 || levelled.getLevel() == 8 ? Material.OBSIDIAN : Material.STONE, false);
            playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
        } else {
            runtime.executeAtLater(block.getLocation(), 50L, () -> placeLiquid(anchorKey, token, block, water));
        }
    }

    private void addLiquidLevel(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        Levelled levelled = (Levelled) block.getBlockData();
        int level = levelled.getLevel();
        if (level > 7) {
            level -= 8;
        }
        if (level == 0) {
            runCruciblePostTask(anchorKey, token, block, water, 1);
        } else {
            int next = 7 - level;
            runtime.executeAtLater(block.getLocation(), 50L, () -> runCruciblePostTask(anchorKey, token, block, water, next));
        }
    }

    private void placeLiquid(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        if (block.getType().isAir()) {
            block.setType(water ? Material.WATER : Material.LAVA, false);
        } else if (water && block.getBlockData() instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(true);
            block.setBlockData(waterlogged, false);
            playSound(block.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
            return;
        }
        runCruciblePostTask(anchorKey, token, block, water, 1);
    }

    private void runCruciblePostTask(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water, int times) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
            return;
        }
        playSound(block.getLocation(), water ? Sound.ITEM_BUCKET_EMPTY : Sound.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);
        levelled.setLevel(8 - times);
        block.setBlockData(levelled, false);
        if (times < 8) {
            runtime.executeAtLater(block.getLocation(), 50L, () -> runCruciblePostTask(anchorKey, token, block, water, times + 1));
        } else {
            playSound(block.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
        }
    }

    private void playComposterEffects(Location origin, Material source) {
        Location center = origin.clone().add(0.5, 0.5, 0.5);
        runtime.executeAt(origin, () -> origin.getWorld().playEffect(center, org.bukkit.Effect.STEP_SOUND, source.isBlock() ? source : Material.HAY_BLOCK));
        runtime.executeAtLater(origin, 10L, () -> origin.getWorld().playEffect(center, org.bukkit.Effect.STEP_SOUND, source.isBlock() ? source : Material.HAY_BLOCK));
        runtime.executeAtLater(origin, 20L, () -> origin.getWorld().playEffect(center, org.bukkit.Effect.STEP_SOUND, source.isBlock() ? source : Material.HAY_BLOCK));
    }

    private FurnaceStats furnaceStats(Location location) {
        String typeId = instanceTypeAt(location);
        if (typeId == null) {
            return null;
        }
        return switch (typeId) {
            case "sf:enhanced_furnace" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_2" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_3" -> new FurnaceStats(3, 2, 1);
            case "sf:enhanced_furnace_4" -> new FurnaceStats(3, 3, 1);
            case "sf:enhanced_furnace_5" -> new FurnaceStats(4, 3, 1);
            case "sf:enhanced_furnace_6" -> new FurnaceStats(4, 3, 2);
            case "sf:enhanced_furnace_7" -> new FurnaceStats(5, 3, 2);
            case "sf:enhanced_furnace_8" -> new FurnaceStats(5, 4, 2);
            case "sf:enhanced_furnace_9" -> new FurnaceStats(6, 4, 2);
            case "sf:enhanced_furnace_10" -> new FurnaceStats(7, 4, 2);
            case "sf:enhanced_furnace_11" -> new FurnaceStats(8, 4, 2);
            case "sf:reinforced_furnace" -> new FurnaceStats(10, 5, 3);
            case "sf:carbonado_edged_furnace" -> new FurnaceStats(20, 10, 3);
            default -> null;
        };
    }

    private String instanceTypeAt(Location location) {
        return blockData.findAnchor(location)
                .map(anchor -> instanceType(anchor.instanceId()))
                .orElse(null);
    }

    private String instanceType(UUID instanceId) {
        return blockData.findInstance(instanceId).map(SfxBlockInstanceRecord::typeId).orElse(null);
    }

    private boolean fits(Inventory inventory, ItemStack stack) {
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = stack.getAmount();
        for (ItemStack current : contents) {
            if (current == null || current.getType().isAir()) {
                remaining -= stack.getMaxStackSize();
            } else if (current.isSimilar(stack)) {
                remaining -= Math.max(0, current.getMaxStackSize() - current.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack itemInHand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null) {
            return item;
        }
        return switch (event.getHand()) {
            case HAND -> event.getPlayer().getInventory().getItemInMainHand();
            case OFF_HAND -> event.getPlayer().getInventory().getItemInOffHand();
            default -> null;
        };
    }

    private void consumeFromHand(Player player, PlayerInteractEvent event, ItemStack stack, int amount) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        int next = stack.getAmount() - amount;
        if (next <= 0) {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(next);
        }
    }

    private ItemStack composterOutput(ItemStack input) {
        Material type = input.getType();
        if (Tag.LEAVES.isTagged(type) || Tag.SAPLINGS.isTagged(type)) {
            return input.getAmount() >= 8 ? new ItemStack(Material.DIRT) : null;
        }
        if (type == Material.STONE && input.getAmount() >= 4) {
            return new ItemStack(Material.NETHERRACK);
        }
        if (type == Material.SAND && input.getAmount() >= 2) {
            return new ItemStack(Material.SOUL_SAND);
        }
        if (type == Material.WHEAT && input.getAmount() >= 4) {
            return new ItemStack(Material.NETHER_WART);
        }
        return null;
    }

    private int requiredComposterAmount(Material type) {
        if (Tag.LEAVES.isTagged(type) || Tag.SAPLINGS.isTagged(type)) {
            return 8;
        }
        return switch (type) {
            case STONE -> 4;
            case SAND -> 2;
            case WHEAT -> 4;
            default -> 1;
        };
    }

    private CruciblePlan cruciblePlan(Material type) {
        if (type == Material.COBBLESTONE || type == Material.NETHERRACK) {
            return new CruciblePlan(16, false);
        }
        if (type == Material.STONE || type == Material.TERRACOTTA) {
            return new CruciblePlan(12, false);
        }
        if (type == Material.OBSIDIAN) {
            return new CruciblePlan(1, false);
        }
        if (type == Material.BLACKSTONE) {
            return new CruciblePlan(8, false);
        }
        if (type == Material.BASALT) {
            return new CruciblePlan(12, false);
        }
        if (type == Material.COBBLED_DEEPSLATE) {
            return new CruciblePlan(12, false);
        }
        if (type == Material.DEEPSLATE) {
            return new CruciblePlan(10, false);
        }
        if (type == Material.TUFF) {
            return new CruciblePlan(8, false);
        }
        if (Tag.LEAVES.isTagged(type)) {
            return new CruciblePlan(16, true);
        }
        if (isTerracottaVariant(type)) {
            return new CruciblePlan(12, false);
        }
        return null;
    }

    private boolean isEnhancedFurnaceLuckMaterial(Material type) {
        return type.name().endsWith("_ORE")
                || type == Material.RAW_IRON
                || type == Material.RAW_GOLD
                || type == Material.RAW_COPPER
                || type == Material.NETHER_GOLD_ORE
                || type == Material.DEEPSLATE_IRON_ORE
                || type == Material.DEEPSLATE_GOLD_ORE
                || type == Material.DEEPSLATE_COPPER_ORE
                || type == Material.DEEPSLATE_DIAMOND_ORE
                || type == Material.DEEPSLATE_EMERALD_ORE
                || type == Material.DEEPSLATE_REDSTONE_ORE
                || type == Material.DEEPSLATE_LAPIS_ORE
                || type == Material.DEEPSLATE_COAL_ORE;
    }

    private boolean isTerracottaVariant(Material type) {
        String name = type.name();
        return name.endsWith("TERRACOTTA") && !name.endsWith("GLAZED_TERRACOTTA");
    }

    private void bootstrapEnhancedFurnaces() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            if (furnaceStatsAt(anchor) != null) {
                enhancedFurnaces.add(anchor.key());
            }
        }
    }

    private void startEnhancedFurnaceTicker() {
        furnaceTickerRunning = true;
        scheduleEnhancedFurnaceTick();
    }

    public void shutdown() {
        furnaceTickerRunning = false;
        furnaceTickCounter = 0L;
        activeCrucibles.clear();
        enhancedFurnaces.clear();
        virtualFurnaces.clear();
        viewedFurnaces.clear();
        furnaceRecipeCache.clear();
    }

    private void scheduleEnhancedFurnaceTick() {
        runtime.executeGlobalLater(1L, () -> {
            if (!furnaceTickerRunning) {
                return;
            }
            long currentTick = ++furnaceTickCounter;
            for (SfxBlockAnchorKey key : Set.copyOf(enhancedFurnaces)) {
                SfxAnchorRecord anchor = blockData.findAnchorFast(key).orElse(null);
                if (anchor == null) {
                    enhancedFurnaces.remove(key);
                    virtualFurnaces.remove(key);
                    viewedFurnaces.remove(key);
                    continue;
                }
                FurnaceStats stats = furnaceStatsAt(anchor);
                if (stats == null) {
                    enhancedFurnaces.remove(key);
                    virtualFurnaces.remove(key);
                    viewedFurnaces.remove(key);
                    continue;
                }
                VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
                boolean hasViewers = viewedFurnaces.contains(key);
                int interval = state.sleeping() && !hasViewers
                        ? tickSettings.sleepingProbeIntervalTicks()
                        : tickSettings.intervalFor(hasViewers);
                long lastTick = state.lastLogicTick();
                if (lastTick > 0L && currentTick - lastTick < interval) {
                    continue;
                }
                long elapsedTicks = lastTick <= 0L ? 1L : Math.max(1L, currentTick - lastTick);
                state.lastLogicTick(currentTick);
                World world = plugin.getServer().getWorld(key.worldId());
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, key.x(), key.y(), key.z());
                SfxMachineTickContext context = new SfxMachineTickContext(currentTick, elapsedTicks, hasViewers);
                runtime.executeAt(location, () -> tickEnhancedFurnace(location.getBlock(), key, stats, context));
            }
            scheduleEnhancedFurnaceTick();
        });
    }

    private void tickEnhancedFurnace(Block block, SfxBlockAnchorKey key, FurnaceStats stats, SfxMachineTickContext context) {
        if (block.getType() != Material.FURNACE) {
            enhancedFurnaces.remove(key);
            virtualFurnaces.remove(key);
            viewedFurnaces.remove(key);
            return;
        }
        BlockState blockState = block.getState();
        if (!(blockState instanceof Furnace furnace)) {
            return;
        }
        FurnaceInventory inventory = furnace.getInventory();
        VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
        state.sleeping(false);

        int elapsed = Math.max(1, context.elapsedTicksInt());
        int cookTime = currentCookTime(inventory, state);
        boolean forceVisual = false;

        for (int tick = 0; tick < elapsed; tick++) {
            ItemStack input = inventory.getSmelting();
            VirtualFurnaceRecipe recipe = resolveFurnaceRecipe(input).orElse(null);
            if (recipe == null || input == null || input.getType().isAir()) {
                if (state.cookProgress() != 0 || state.inputKey() != null) {
                    forceVisual = true;
                }
                state.cookProgress(0);
                state.inputKey(null);
                // Already-lit fuel must keep burning even when the recipe/input disappears.
                burnOneVirtualFuelTick(state);
                continue;
            }

            cookTime = recipe.cookingTime();
            String inputKey = inputKey(input);
            if (!inputKey.equals(state.inputKey())) {
                state.inputKey(inputKey);
                state.cookProgress(0);
                forceVisual = true;
            }

            ItemStack result = applyEnhancedFurnaceFortune(recipe.result(), input.getType(), stats);
            boolean canSmelt = canFitResult(inventory, result);
            if (!canSmelt) {
                if (state.cookProgress() != 0) {
                    state.cookProgress(0);
                    forceVisual = true;
                }
                // Fuel already accepted by the furnace is consumed by time, not by work done.
                burnOneVirtualFuelTick(state);
                continue;
            }

            if (state.burnTimeRemaining() <= 0) {
                ItemStack fuel = inventory.getFuel();
                int burnTicks = enhancedFuelTicks(fuel, stats);
                if (burnTicks <= 0) {
                    if (state.cookProgress() != 0) {
                        state.cookProgress(0);
                        forceVisual = true;
                    }
                    break;
                }
                consumeFuel(inventory, fuel);
                state.burnTimeRemaining(burnTicks);
                state.burnTimeTotal(burnTicks);
                forceVisual = true;
            }

            if (state.burnTimeRemaining() > 0) {
                burnOneVirtualFuelTick(state);
                state.cookProgress(state.cookProgress() + Math.max(1, stats.processingSpeed()));
                if (state.cookProgress() >= cookTime) {
                    consumeSmeltingInput(inventory, input);
                    pushFurnaceResult(inventory, result);
                    state.cookProgress(0);
                    ItemStack next = inventory.getSmelting();
                    state.inputKey(next == null || next.getType().isAir() ? null : inputKey(next));
                    forceVisual = true;
                }
            }
        }

        if (!context.hasViewers() && state.burnTimeRemaining() <= 0 && !canStartOrContinueVirtualSmelting(inventory, stats)) {
            state.sleeping(true);
        }
        syncVirtualFurnaceVisualAndRestoreInventory(furnace, inventory, state, cookTime, forceVisual, context.hasViewers());
    }

    private void burnOneVirtualFuelTick(VirtualFurnaceState state) {
        if (state.burnTimeRemaining() > 0) {
            state.burnTimeRemaining(state.burnTimeRemaining() - 1);
        }
    }

    private int currentCookTime(FurnaceInventory inventory, VirtualFurnaceState state) {
        ItemStack input = inventory.getSmelting();
        VirtualFurnaceRecipe recipe = resolveFurnaceRecipe(input).orElse(null);
        if (recipe != null) {
            return Math.max(1, recipe.cookingTime());
        }
        return Math.max(1, state.cookTimeTotal());
    }

    private boolean canStartOrContinueVirtualSmelting(FurnaceInventory inventory, FurnaceStats stats) {
        ItemStack input = inventory.getSmelting();
        VirtualFurnaceRecipe recipe = resolveFurnaceRecipe(input).orElse(null);
        if (recipe == null || input == null || input.getType().isAir()) {
            return false;
        }
        ItemStack result = recipe.result();
        if (!canFitResult(inventory, result)) {
            return false;
        }
        return enhancedFuelTicks(inventory.getFuel(), stats) > 0;
    }

    private Optional<VirtualFurnaceRecipe> resolveFurnaceRecipe(ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return Optional.empty();
        }
        ItemStack probe = input.clone();
        probe.setAmount(1);
        return furnaceRecipeCache.computeIfAbsent(input.getType(), material -> {
            Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
            while (iterator.hasNext()) {
                Recipe recipe = iterator.next();
                if (!(recipe instanceof FurnaceRecipe furnaceRecipe)) {
                    continue;
                }
                if (!matchesFurnaceInput(furnaceRecipe, probe)) {
                    continue;
                }
                ItemStack result = furnaceRecipe.getResult();
                if (result == null || result.getType().isAir()) {
                    continue;
                }
                int cookingTime = Math.max(1, furnaceRecipe.getCookingTime());
                return Optional.of(new VirtualFurnaceRecipe(result.clone(), cookingTime));
            }
            return Optional.empty();
        });
    }

    private boolean matchesFurnaceInput(FurnaceRecipe furnaceRecipe, ItemStack probe) {
        try {
            RecipeChoice choice = furnaceRecipe.getInputChoice();
            if (choice != null) {
                return choice.test(probe);
            }
        } catch (Throwable ignored) {
            // Some forks or exotic RecipeChoice implementations can throw; fall back below.
        }
        ItemStack legacy = furnaceRecipe.getInput();
        return legacy != null && !legacy.getType().isAir() && legacy.getType() == probe.getType();
    }

    private ItemStack applyEnhancedFurnaceFortune(ItemStack baseResult, Material inputType, FurnaceStats stats) {
        ItemStack result = baseResult.clone();
        if (stats.fortuneLevel() > 0 && isEnhancedFurnaceLuckMaterial(inputType)) {
            int bonus = ThreadLocalRandom.current().nextInt(stats.fortuneLevel() + 1);
            result.setAmount(Math.min(result.getMaxStackSize(), result.getAmount() + bonus));
        }
        return result;
    }

    private int enhancedFuelTicks(ItemStack fuel, FurnaceStats stats) {
        if (fuel == null || fuel.getType().isAir()) {
            return 0;
        }
        SfxFuelBurnTimeBridge bridge = fuelBurnTimeBridge;
        int burnTicks = 0;
        if (bridge == null) {
            try {
                bridge = SfxFuelBurnTimeBridge.create();
                fuelBurnTimeBridge = bridge;
            } catch (RuntimeException ignored) {
                // Fall back to the static vanilla-equivalent table below.
            }
        }
        if (bridge != null) {
            burnTicks = bridge.burnTicks(fuel);
        }
        if (burnTicks <= 0) {
            burnTicks = fallbackFuelTicks(fuel.getType());
        }
        if (burnTicks <= 0) {
            return 0;
        }
        double burnMultiplier = stats.fuelEfficiency();
        if (plugin.getConfig().getBoolean("plugin-blocks.enhanced-furnace.speed-affects-fuel-consumption", false)
                && stats.processingSpeed() > 0) {
            burnMultiplier /= stats.processingSpeed();
        }
        return Math.max(1, Math.min(Short.MAX_VALUE - 1, (int) Math.ceil(burnTicks * burnMultiplier)));
    }

    private int fallbackFuelTicks(Material type) {
        if (type == null || type.isAir()) {
            return 0;
        }
        return switch (type) {
            case LAVA_BUCKET -> 20_000;
            case COAL_BLOCK -> 16_000;
            case DRIED_KELP_BLOCK -> 4_000;
            case BLAZE_ROD -> 2_400;
            case COAL, CHARCOAL -> 1_600;
            case BAMBOO -> 50;
            case STICK, BOWL, OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING, ACACIA_SAPLING,
                    DARK_OAK_SAPLING, MANGROVE_PROPAGULE, CHERRY_SAPLING, AZALEA, FLOWERING_AZALEA -> 100;
            case WOODEN_SWORD, WOODEN_SHOVEL, WOODEN_PICKAXE, WOODEN_AXE, WOODEN_HOE -> 200;
            default -> fallbackWoodFuelTicks(type);
        };
    }

    private int fallbackWoodFuelTicks(Material type) {
        String name = type.name();
        if (!isOverworldWoodFamily(name) && !name.contains("BAMBOO")) {
            return 0;
        }
        if (name.endsWith("_SLAB")) {
            return 150;
        }
        if (name.endsWith("_PLANKS")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STAIRS")
                || name.endsWith("_FENCE")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_SIGN")
                || name.endsWith("_HANGING_SIGN")
                || name.endsWith("_BOAT")
                || name.endsWith("_CHEST_BOAT")) {
            return 300;
        }
        if (name.endsWith("_BUTTON")) {
            return 100;
        }
        return 0;
    }

    private boolean isOverworldWoodFamily(String name) {
        return name.contains("OAK")
                || name.contains("SPRUCE")
                || name.contains("BIRCH")
                || name.contains("JUNGLE")
                || name.contains("ACACIA")
                || name.contains("DARK_OAK")
                || name.contains("MANGROVE")
                || name.contains("CHERRY")
                || name.contains("PALE_OAK");
    }

    private void consumeFuel(FurnaceInventory inventory, ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir()) {
            return;
        }
        if (fuel.getType() == Material.LAVA_BUCKET) {
            inventory.setFuel(new ItemStack(Material.BUCKET, 1));
            return;
        }
        int next = fuel.getAmount() - 1;
        if (next <= 0) {
            inventory.setFuel(null);
        } else {
            fuel.setAmount(next);
            inventory.setFuel(fuel);
        }
    }

    private void consumeSmeltingInput(FurnaceInventory inventory, ItemStack input) {
        int next = input.getAmount() - 1;
        if (next <= 0) {
            inventory.setSmelting(null);
        } else {
            input.setAmount(next);
            inventory.setSmelting(input);
        }
    }

    private boolean canFitResult(FurnaceInventory inventory, ItemStack result) {
        ItemStack existing = inventory.getResult();
        return existing == null || existing.getType().isAir()
                || (existing.isSimilar(result) && existing.getAmount() + result.getAmount() <= existing.getMaxStackSize());
    }

    private void pushFurnaceResult(FurnaceInventory inventory, ItemStack result) {
        ItemStack existing = inventory.getResult();
        if (existing == null || existing.getType().isAir()) {
            inventory.setResult(result.clone());
            return;
        }
        if (existing.isSimilar(result)) {
            existing.setAmount(Math.min(existing.getMaxStackSize(), existing.getAmount() + result.getAmount()));
            inventory.setResult(existing);
        }
    }

    private void syncVirtualFurnaceVisualAndRestoreInventory(Furnace furnace, FurnaceInventory inventory, VirtualFurnaceState state, int cookTimeTotal, boolean force, boolean hasViewers) {
        ItemStack smelting = cloneSlot(inventory.getSmelting());
        ItemStack fuel = cloneSlot(inventory.getFuel());
        ItemStack result = cloneSlot(inventory.getResult());
        syncVirtualFurnaceVisual(furnace, inventory, state, cookTimeTotal, force, hasViewers);
        // BlockState#update is required for the vanilla furnace progress bars, but on some
        // Paper/Bukkit versions the snapshot update can overwrite live FurnaceInventory slots.
        // Restore the slots after the visual update so SFX's virtual smelting remains authoritative.
        restoreFurnaceInventory(inventory, smelting, fuel, result);
    }

    private ItemStack cloneSlot(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    private void restoreFurnaceInventory(FurnaceInventory inventory, ItemStack smelting, ItemStack fuel, ItemStack result) {
        inventory.setSmelting(cloneSlot(smelting));
        inventory.setFuel(cloneSlot(fuel));
        inventory.setResult(cloneSlot(result));
    }

    private void syncVirtualFurnaceVisual(Furnace furnace, FurnaceInventory inventory, VirtualFurnaceState state, int cookTimeTotal, boolean force, boolean hasViewers) {
        boolean burning = state.burnTimeRemaining() > 0;

        // A vanilla Furnace block keeps its lit model by ticking the tile entity burnTime.
        // If SFX only toggles BlockData#lit while leaving the tile burnTime at 0, the
        // vanilla furnace tick can immediately set lit=false again. That is what caused
        // unloaded/unviewed enhanced furnaces to blink. Keep the real tile burnTime in
        // step with the virtual burn while still letting SFX own all input/output logic.
        int totalCookTime = Math.max(1, cookTimeTotal);
        state.cookTimeTotal(totalCookTime);
        furnace.setCookTimeTotal(totalCookTime);
        if (hasViewers) {
            furnace.setCookTime((short) Math.min(Short.MAX_VALUE, Math.max(0, Math.min(state.cookProgress(), totalCookTime - 1))));
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, Math.max(0, state.burnTimeRemaining())));
        } else {
            // No one is looking at the vanilla UI. Keep cook progress visually idle, but
            // keep burnTime positive while the virtual furnace is burning so vanilla
            // will not extinguish the block between SFX lazy ticks.
            furnace.setCookTime((short) 0);
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, Math.max(0, state.burnTimeRemaining())));
        }
        furnace.update(true, false);

        // BlockState#update may apply the snapshot BlockData, so force the lit flag after
        // the tile update. This makes the visible block state follow the virtual burn.
        syncFurnaceLitAppearance(furnace, burning);
    }

    private void syncFurnaceLitAppearance(Furnace furnace, boolean lit) {
        Block block = furnace.getBlock();
        BlockData data = block.getBlockData();
        if (!(data instanceof Lightable lightable) || lightable.isLit() == lit) {
            return;
        }
        lightable.setLit(lit);
        block.setBlockData(lightable, false);
    }

    private String inputKey(ItemStack input) {
        return input.getType().key().toString();
    }

    private boolean canStartCrucible(Block block, boolean water) {
        Material type = block.getType();
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return true;
        }
        return water && block.getBlockData() instanceof Waterlogged;
    }

    private boolean isCrucibleProcessActive(SfxBlockAnchorKey anchorKey, UUID token) {
        ActiveCrucibleProcess process = activeCrucibles.get(anchorKey);
        return process != null && process.token().equals(token);
    }

    private void clearActiveCrucible(SfxBlockAnchorKey anchorKey, boolean clearOutput) {
        ActiveCrucibleProcess process = activeCrucibles.remove(anchorKey);
        if (process == null || !clearOutput) {
            return;
        }
        Block block = process.outputLocation().getBlock();
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            block.setType(Material.AIR, false);
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            waterlogged.setWaterlogged(false);
            block.setBlockData(waterlogged, false);
        }
    }

    private void setInstanceState(Block anchorBlock, SfxBlockLifecycleState state, String payload) {
        blockData.findAnchor(anchorBlock.getLocation())
                .ifPresent(anchor -> blockData.updateInstanceState(anchor.instanceId(), payload.getBytes(StandardCharsets.UTF_8), state));
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStoredContents(Block block) {
        if (!(block.getState() instanceof InventoryHolder holder)) {
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

    private FurnaceStats furnaceStatsAt(SfxAnchorRecord anchor) {
        String typeId = instanceType(anchor.instanceId());
        if (typeId == null) {
            return null;
        }
        return furnaceStats(typeId);
    }

    private FurnaceStats furnaceStats(String typeId) {
        return switch (typeId) {
            case "sf:enhanced_furnace" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_2" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_3" -> new FurnaceStats(3, 2, 1);
            case "sf:enhanced_furnace_4" -> new FurnaceStats(3, 3, 1);
            case "sf:enhanced_furnace_5" -> new FurnaceStats(4, 3, 1);
            case "sf:enhanced_furnace_6" -> new FurnaceStats(4, 3, 2);
            case "sf:enhanced_furnace_7" -> new FurnaceStats(5, 3, 2);
            case "sf:enhanced_furnace_8" -> new FurnaceStats(5, 4, 2);
            case "sf:enhanced_furnace_9" -> new FurnaceStats(6, 4, 2);
            case "sf:enhanced_furnace_10" -> new FurnaceStats(7, 4, 2);
            case "sf:enhanced_furnace_11" -> new FurnaceStats(8, 4, 2);
            case "sf:reinforced_furnace" -> new FurnaceStats(10, 5, 3);
            case "sf:carbonado_edged_furnace" -> new FurnaceStats(20, 10, 3);
            default -> null;
        };
    }

    private void playSound(Location location, Sound sound, float volume, float pitch) {
        World world = location.getWorld();
        if (world != null) {
            world.playSound(location.clone().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }


    private record ActiveCrucibleProcess(UUID token, Location outputLocation, boolean water) {
    }

    private record CruciblePlan(int inputAmount, boolean water) {
    }

    private record FurnaceStats(int processingSpeed, int fuelEfficiency, int fortuneLevel) {
    }

    private record VirtualFurnaceRecipe(ItemStack result, int cookingTime) {
    }

    private static final class VirtualFurnaceState {
        private int burnTimeRemaining;
        private int burnTimeTotal;
        private int cookProgress;
        private int visualTick;
        private int cookTimeTotal = 200;
        private long lastLogicTick;
        private boolean sleeping;
        private String inputKey;

        int burnTimeRemaining() {
            return burnTimeRemaining;
        }

        void burnTimeRemaining(int burnTimeRemaining) {
            this.burnTimeRemaining = burnTimeRemaining;
        }

        void burnTimeTotal(int burnTimeTotal) {
            this.burnTimeTotal = burnTimeTotal;
        }

        int cookTimeTotal() {
            return Math.max(1, cookTimeTotal);
        }

        void cookTimeTotal(int cookTimeTotal) {
            this.cookTimeTotal = Math.max(1, cookTimeTotal);
        }

        int cookProgress() {
            return cookProgress;
        }

        void cookProgress(int cookProgress) {
            this.cookProgress = cookProgress;
        }

        int visualTick() {
            return visualTick;
        }

        void visualTick(int visualTick) {
            this.visualTick = visualTick;
        }


        long lastLogicTick() {
            return lastLogicTick;
        }

        void lastLogicTick(long lastLogicTick) {
            this.lastLogicTick = lastLogicTick;
        }

        boolean sleeping() {
            return sleeping;
        }

        void sleeping(boolean sleeping) {
            this.sleeping = sleeping;
        }

        String inputKey() {
            return inputKey;
        }

        void inputKey(String inputKey) {
            this.inputKey = inputKey;
        }
    }

}
