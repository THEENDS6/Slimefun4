package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxIndustrialMinerService implements Listener {
    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS
    );
    private static final BlockFace[] AXES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final Map<String, MiningTask> active = new ConcurrentHashMap<>();

    public SfxIndustrialMinerService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
    }

    public void shutdown() {
        active.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.BLAST_FURNACE) {
            return;
        }
        MinerStructure structure = findStructure(clicked);
        if (structure == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String key = locationKey(structure.blastFurnace().getLocation());
        if (active.containsKey(key)) {
            send(player, "machines.industrial-miner.already-running", "<yellow>This Industrial Miner is already running.</yellow>");
            return;
        }
        MiningTask task = new MiningTask(key, structure, player.getUniqueId());
        active.put(key, task);
        send(player, structure.profile().advanced() ? "machines.industrial-miner.started-advanced" : "machines.industrial-miner.started",
                structure.profile().advanced() ? "<green>Advanced Industrial Miner started.</green>" : "<green>Industrial Miner started.</green>");
        warmUp(task);
    }

    private MinerStructure findStructure(Block blastFurnace) {
        if (blastFurnace.getType() != Material.BLAST_FURNACE) {
            return null;
        }
        Block chest = blastFurnace.getRelative(BlockFace.UP);
        if (chest.getType() != Material.CHEST || !(chest.getState() instanceof Chest)) {
            return null;
        }
        for (BlockFace side : AXES) {
            BlockFace other = side.getOppositeFace();
            if (chest.getRelative(side).getType() != Material.PISTON || chest.getRelative(other).getType() != Material.PISTON) {
                continue;
            }
            Block leftBase = blastFurnace.getRelative(side);
            Block rightBase = blastFurnace.getRelative(other);
            if (leftBase.getType() == Material.IRON_BLOCK && rightBase.getType() == Material.IRON_BLOCK) {
                return new MinerStructure(blastFurnace, chest, new Block[] { chest.getRelative(side), chest.getRelative(other) }, MinerProfile.normal());
            }
            if (leftBase.getType() == Material.DIAMOND_BLOCK && rightBase.getType() == Material.DIAMOND_BLOCK) {
                return new MinerStructure(blastFurnace, chest, new Block[] { chest.getRelative(side), chest.getRelative(other) }, MinerProfile.advancedProfile());
            }
        }
        return null;
    }

    private void warmUp(MiningTask task) {
        int delay = 0;
        delay = schedulePiston(task, delay + 4, 0, true);
        delay = schedulePiston(task, delay + 10, 0, false);
        delay = schedulePiston(task, delay + 8, 1, true);
        delay = schedulePiston(task, delay + 10, 1, false);
        schedule(task, delay + 1, () -> {
            if (!isActive(task)) {
                return;
            }
            Inventory inventory = chestInventory(task);
            if (inventory == null) {
                stop(task, "machines.industrial-miner.structure-changed", "<red>Industrial Miner stopped: structure changed.</red>");
                return;
            }
            task.fuelRemaining(consumeFuel(inventory, task.structure().profile()));
            if (task.fuelRemaining() <= 0) {
                stop(task, "machines.industrial-miner.no-fuel", "<red>Industrial Miner stopped: no fuel.</red>");
            }
        });
        delay += 1;
        delay = schedulePiston(task, delay + 6, 0, true);
        delay = schedulePiston(task, delay + 9, 0, false);
        delay = schedulePiston(task, delay + 4, 1, true);
        delay = schedulePiston(task, delay + 7, 1, false);
        delay = schedulePiston(task, delay + 3, 0, true);
        delay = schedulePiston(task, delay + 5, 0, false);
        delay = schedulePiston(task, delay + 2, 1, true);
        delay = schedulePiston(task, delay + 4, 1, false);
        delay = schedulePiston(task, delay + 1, 0, true);
        delay = schedulePiston(task, delay + 3, 0, false);
        delay = schedulePiston(task, delay + 1, 1, true);
        delay = schedulePiston(task, delay + 2, 1, false);
        schedule(task, delay + 1, () -> runMiningCycle(task));
    }

    private int schedulePiston(MiningTask task, int delay, int pistonIndex, boolean extended) {
        schedule(task, delay, () -> setPistonState(task, task.structure().pistons()[pistonIndex], extended));
        return delay;
    }

    private void schedule(MiningTask task, long delay, Runnable action) {
        runtime.executeAtLater(task.structure().blastFurnace().getLocation(), delay, () -> {
            if (!isActive(task)) {
                return;
            }
            action.run();
        });
    }

    private boolean isActive(MiningTask task) {
        return active.get(task.key()) == task;
    }

    private void runMiningCycle(MiningTask task) {
        if (!isActive(task)) {
            return;
        }
        int delay = 0;
        delay = schedulePiston(task, delay + 1, 0, true);
        delay = schedulePiston(task, delay + 3, 0, false);
        delay = schedulePiston(task, delay + 1, 1, true);
        delay = schedulePiston(task, delay + 3, 1, false);
        schedule(task, delay + 1, () -> mineOneStep(task));
    }

    private void mineOneStep(MiningTask task) {
        try {
            if (!isStructureValid(task.structure())) {
                stop(task, "machines.industrial-miner.structure-changed", "<red>Industrial Miner stopped: structure changed.</red>");
                return;
            }
            Inventory inventory = chestInventory(task);
            if (inventory == null) {
                stop(task, "machines.industrial-miner.structure-changed", "<red>Industrial Miner stopped: structure changed.</red>");
                return;
            }
            if (task.fuelRemaining() <= 0) {
                int gained = consumeFuel(inventory, task.structure().profile());
                if (gained <= 0) {
                    stop(task, "machines.industrial-miner.no-fuel", "<red>Industrial Miner stopped: no fuel.</red>");
                    return;
                }
                task.fuelRemaining(gained);
            }
            Block ore = findNextOre(task);
            if (ore == null) {
                active.remove(task.key());
                Player player = plugin.getServer().getPlayer(task.ownerId());
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.4F, 1F);
                    send(player, "machines.industrial-miner.finished", "<green>Industrial Miner finished. Mined {ores} ores.</green>", Map.of("ores", task.oresMined()));
                }
                return;
            }
            List<ItemStack> drops = dropsFor(ore, task.structure().profile());
            if (!canInsertAll(inventory, drops)) {
                stop(task, "machines.industrial-miner.chest-full", "<red>Industrial Miner stopped: chest is full.</red>");
                return;
            }
            for (ItemStack drop : drops) {
                inventory.addItem(drop);
            }
            Block furnace = task.structure().blastFurnace();
            furnace.getWorld().playEffect(furnace.getLocation(), Effect.STEP_SOUND, ore.getType());
            furnace.getWorld().playSound(furnace.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.BLOCKS, 0.2F, 1.0F);
            ore.setType(Material.AIR, true);
            task.fuelRemaining(task.fuelRemaining() - 1);
            task.oresMined(task.oresMined() + 1);
            runtime.executeAtLater(task.structure().blastFurnace().getLocation(), Math.max(1L, plugin.getConfig().getLong("legacy.industrial-miner.step-delay-ticks", 4L)), () -> runMiningCycle(task));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Industrial Miner task failed: " + exception.getMessage());
            active.remove(task.key());
        }
    }

    private Inventory chestInventory(MiningTask task) {
        Block chest = task.structure().chest();
        if (chest.getType() == Material.CHEST && chest.getState() instanceof Chest chestState) {
            return chestState.getBlockInventory();
        }
        return null;
    }

    private boolean isStructureValid(MinerStructure structure) {
        MinerStructure found = findStructure(structure.blastFurnace());
        if (found == null) {
            return false;
        }
        return found.profile().advanced() == structure.profile().advanced();
    }

    private Block findNextOre(MiningTask task) {
        MinerStructure structure = task.structure();
        int range = structure.profile().range();
        Block origin = structure.blastFurnace();
        World world = origin.getWorld();
        int minY = world.getMinHeight();
        int width = range * 2 + 1;
        int maxSteps = width * width * Math.max(1, origin.getY() - minY);
        for (int i = 0; i < maxSteps; i++) {
            int cursor = task.cursor();
            task.cursor(cursor + 1);
            int column = cursor % (width * width);
            int dy = cursor / (width * width);
            int dx = (column % width) - range;
            int dz = (column / width) - range;
            int y = origin.getY() - 1 - dy;
            if (y < minY) {
                task.cursor(0);
                continue;
            }
            Block candidate = world.getBlockAt(origin.getX() + dx, y, origin.getZ() + dz);
            if (ORES.contains(candidate.getType()) && blockData.findAnchor(candidate.getLocation()).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private List<ItemStack> dropsFor(Block ore, MinerProfile profile) {
        if (profile.advanced()) {
            return List.of(new ItemStack(ore.getType()));
        }
        Collection<ItemStack> drops = ore.getDrops(new ItemStack(Material.IRON_PICKAXE));
        return drops.isEmpty() ? List.of(new ItemStack(ore.getType())) : new ArrayList<>(drops);
    }

    private int consumeFuel(Inventory inventory, MinerProfile profile) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            for (Fuel fuel : profile.fuels()) {
                if (!fuel.matches(stack, items)) {
                    continue;
                }
                int next = stack.getAmount() - 1;
                inventory.setItem(slot, next <= 0 ? null : withAmount(stack, next));
                if (fuel.returnItem() != null) {
                    inventory.addItem(new ItemStack(fuel.returnItem()));
                }
                return fuel.ores();
            }
        }
        return 0;
    }

    private boolean canInsertAll(Inventory inventory, List<ItemStack> drops) {
        Inventory clone = Bukkit.createInventory(null, inventory.getSize());
        clone.setContents(inventory.getContents());
        for (ItemStack drop : drops) {
            if (!clone.addItem(drop.clone()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack withAmount(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    private void setPistonState(MiningTask task, Block block, boolean extended) {
        if (!isActive(task)) {
            return;
        }
        try {
            Location particleLoc = task.structure().blastFurnace().getLocation().clone();
            block.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 20, 0.7D, 0.7D, 0.7D, 0.0D);
            if (block.getType() == Material.MOVING_PISTON) {
                block.getRelative(BlockFace.UP).setType(Material.AIR, false);
                return;
            }
            if (block.getType() != Material.PISTON || !(block.getBlockData() instanceof Piston piston)) {
                stop(task, "machines.industrial-miner.structure-changed", "<red>Industrial Miner stopped: structure changed.</red>");
                return;
            }
            Block above = block.getRelative(BlockFace.UP);
            if (!above.isEmpty() && above.getType() != Material.PISTON_HEAD) {
                stop(task, "machines.industrial-miner.piston-no-space", "<red>Industrial Miner stopped: piston has no space above.</red>");
                return;
            }
            if (piston.getFacing() != BlockFace.UP) {
                stop(task, "machines.industrial-miner.piston-wrong-direction", "<red>Industrial Miner stopped: pistons must face upwards.</red>");
                return;
            }
            piston.setExtended(extended);
            block.setBlockData(piston, false);
            if (extended) {
                PistonHead head = (PistonHead) Material.PISTON_HEAD.createBlockData();
                head.setFacing(BlockFace.UP);
                above.setBlockData(head, false);
            } else if (above.getType() == Material.PISTON_HEAD) {
                above.setType(Material.AIR, false);
            }
            block.getWorld().playSound(block.getLocation(), extended ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.1F, 1.0F);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Industrial Miner piston animation failed: " + exception.getMessage());
            stop(task, "machines.industrial-miner.structure-changed", "<red>Industrial Miner stopped: structure changed.</red>");
        }
    }

    private void stop(MiningTask task, String key, String fallback) {
        active.remove(task.key());
        Player player = plugin.getServer().getPlayer(task.ownerId());
        if (player != null && player.isOnline()) {
            send(player, key, fallback);
        }
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback)));
    }

    private void send(Player player, String key, String fallback, Map<String, ?> placeholders) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback, placeholders)));
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record MinerStructure(Block blastFurnace, Block chest, Block[] pistons, MinerProfile profile) {
    }

    private record Fuel(int ores, Material material, String itemId, Material returnItem) {
        static Fuel material(int ores, Material material) {
            return new Fuel(ores, material, null, null);
        }

        static Fuel material(int ores, Material material, Material returnItem) {
            return new Fuel(ores, material, null, returnItem);
        }

        static Fuel itemId(int ores, String itemId, Material returnItem) {
            return new Fuel(ores, null, itemId, returnItem);
        }

        boolean matches(ItemStack stack, SfxItems items) {
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
            if (material != null && stack.getType() == material) {
                return true;
            }
            if (itemId == null) {
                return false;
            }
            return items.readMarker(stack).map(SfxItemMarker::itemId).map(id -> id.equals(itemId)).orElse(false);
        }
    }

    private record MinerProfile(boolean advanced, int range, List<Fuel> fuels) {
        static MinerProfile normal() {
            List<Fuel> fuels = new ArrayList<>();
            fuels.add(Fuel.material(4, Material.COAL));
            fuels.add(Fuel.material(4, Material.CHARCOAL));
            fuels.add(Fuel.material(40, Material.COAL_BLOCK));
            fuels.add(Fuel.material(10, Material.DRIED_KELP_BLOCK));
            fuels.add(Fuel.material(4, Material.BLAZE_ROD));
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) {
                    fuels.add(Fuel.material(1, material));
                }
            }
            return new MinerProfile(false, 3, List.copyOf(fuels));
        }

        static MinerProfile advancedProfile() {
            return new MinerProfile(true, 5, List.of(
                    Fuel.material(48, Material.LAVA_BUCKET, Material.BUCKET),
                    Fuel.itemId(64, "sf:bucket_of_oil", Material.BUCKET),
                    Fuel.itemId(64, "sf:oil_bucket", Material.BUCKET),
                    Fuel.itemId(128, "sf:bucket_of_fuel", Material.BUCKET),
                    Fuel.itemId(128, "sf:fuel_bucket", Material.BUCKET)
            ));
        }
    }

    private static final class MiningTask {
        private final String key;
        private final MinerStructure structure;
        private final UUID ownerId;
        private int fuelRemaining;
        private int cursor;
        private int oresMined;

        private MiningTask(String key, MinerStructure structure, UUID ownerId) {
            this.key = key;
            this.structure = structure;
            this.ownerId = ownerId;
        }

        String key() { return key; }
        MinerStructure structure() { return structure; }
        UUID ownerId() { return ownerId; }
        int fuelRemaining() { return fuelRemaining; }
        void fuelRemaining(int fuelRemaining) { this.fuelRemaining = fuelRemaining; }
        int cursor() { return cursor; }
        void cursor(int cursor) { this.cursor = cursor; }
        int oresMined() { return oresMined; }
        void oresMined(int oresMined) { this.oresMined = oresMined; }
    }
}
