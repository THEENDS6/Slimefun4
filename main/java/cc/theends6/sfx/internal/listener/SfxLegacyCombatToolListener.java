package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.config.SfxLegacyItemBehaviorConfig;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Orientable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class SfxLegacyCombatToolListener implements Listener {
    private static final String SEISMIC_METADATA = "sfx_seismic_axe";
    private static final int TREE_LIMIT = 100;
    private static final int STRIP_LIMIT = 20;
    private static final double CLIMB_MAX_DISTANCE_SQUARED = 4.4D * 4.4D;
    private static final double CLIMB_STRONG_POWER = 1.0D;
    private static final double CLIMB_WEAK_POWER = 0.6D;
    private static final double CLIMB_EFFICIENCY_MODIFIER = 0.125D;
    private static final Set<Material> ORE_BLOCKS = Set.of(
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
    private static final Set<Material> SHOVEL_BLOCKS = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.MUD, Material.CLAY, Material.GRAVEL, Material.SAND, Material.RED_SAND,
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW_BLOCK, Material.SNOW, Material.MOSS_BLOCK
    );
    private static final Set<Material> UNBREAKABLE = Set.of(
            Material.BEDROCK, Material.BARRIER, Material.END_PORTAL_FRAME, Material.END_PORTAL,
            Material.END_GATEWAY, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW,
            Material.LIGHT, Material.REINFORCED_DEEPSLATE
    );
    private static final Set<Material> CLIMB_STRONG = Set.of(
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            Material.STONE, Material.GRANITE, Material.POLISHED_GRANITE,
            Material.DIORITE, Material.POLISHED_DIORITE,
            Material.ANDESITE, Material.POLISHED_ANDESITE,
            Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
            Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
            Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK,
            Material.BLACKSTONE, Material.POLISHED_BLACKSTONE,
            Material.BASALT, Material.SMOOTH_BASALT, Material.NETHERRACK
    );
    private static final Set<Material> CLIMB_WEAK = Set.of(
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.WHITE_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE_POWDER, Material.GRAY_CONCRETE_POWDER,
            Material.BLACK_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER, Material.RED_CONCRETE_POWDER,
            Material.ORANGE_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER, Material.LIME_CONCRETE_POWDER,
            Material.GREEN_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE_POWDER,
            Material.BLUE_CONCRETE_POWDER, Material.PURPLE_CONCRETE_POWDER, Material.MAGENTA_CONCRETE_POWDER,
            Material.PINK_CONCRETE_POWDER
    );

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxLegacyItemBehaviorConfig behaviorConfig;
    private final SfxBlockDataService blockData;
    private final NamespacedKey spawnerTypeKey;
    private final NamespacedKey bowEffectKey;
    private final Set<UUID> climbingCooldown = new HashSet<>();

    public SfxLegacyCombatToolListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxLegacyItemBehaviorConfig behaviorConfig, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.behaviorConfig = Objects.requireNonNull(behaviorConfig, "behaviorConfig");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.spawnerTypeKey = new NamespacedKey(plugin, "spawner_type");
        this.bowEffectKey = new NamespacedKey(plugin, "bow_effect");
    }

    boolean handleItemUse(PlayerInteractEvent event, String itemId) {
        switch (itemId) {
            case "sf:seismic_axe" -> useSeismicAxe(event);
            case "sf:pickaxe_of_the_seeker" -> usePickaxeOfTheSeeker(event);
            case "sf:climbing_pick" -> useClimbingPick(event);
            case "sf:lumber_axe" -> useLumberAxe(event);
            default -> {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        String bowId = itemId(event.getBow());
        if ("sf:explosive_bow".equals(bowId)) {
            arrow.getPersistentDataContainer().set(bowEffectKey, PersistentDataType.STRING, BowEffect.EXPLOSIVE.name());
        } else if ("sf:icy_bow".equals(bowId)) {
            arrow.getPersistentDataContainer().set(bowEffectKey, PersistentDataType.STRING, BowEffect.ICY.name());
        }
        if (bowId != null) {
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            BowEffect effect = bowEffect(arrow);
            if (effect == BowEffect.EXPLOSIVE) {
                applyExplosiveBow(event, player, target);
            } else if (effect == BowEffect.ICY) {
                applyIcyBow(event, target);
            }
            return;
        }

        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        String itemId = itemId(player.getInventory().getItemInMainHand());
        if ("sf:blade_of_vampires".equals(itemId)) {
            applyVampireBlade(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (!"sf:sword_of_beheading".equals(itemId(killer.getInventory().getItemInMainHand()))) {
            return;
        }

        ItemStack head = resolveHeadDrop(event.getEntityType(), event.getEntity());
        if (head == null) {
            return;
        }

        int chance = behaviorConfig.beheadingChance(event.getEntityType());
        addChanceDrop(event, ThreadLocalRandom.current(), chance, head);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String itemId = itemId(tool);
        if (itemId == null) {
            return;
        }
        if (isSfxAnchored(event.getBlock())) {
            return;
        }

        switch (itemId) {
            case "sf:smelters_pickaxe" -> handleSmeltersPickaxe(event, tool);
            case "sf:lumber_axe" -> handleLumberAxeBreak(event, tool);
            case "sf:pickaxe_of_containment" -> handleContainmentPickaxe(event, tool);
            case "sf:explosive_pickaxe" -> handleExplosiveTool(event, tool, false, behaviorConfig.explosivePickaxeAllowFortune());
            case "sf:explosive_shovel" -> handleExplosiveTool(event, tool, true, behaviorConfig.explosiveShovelAllowFortune());
            case "sf:pickaxe_of_vein_mining" -> handleVeinMining(event, tool);
            default -> {
            }
        }
    }

    @EventHandler
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock fallingBlock && fallingBlock.hasMetadata(SEISMIC_METADATA)) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        climbingCooldown.remove(event.getPlayer().getUniqueId());
    }

    private void useSeismicAxe(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        denyItemUse(event);
        Player player = event.getPlayer();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Set<UUID> pushed = new HashSet<>();

        for (int i = 2; i <= 10; i++) {
            Block block = player.getEyeLocation().clone().add(direction.clone().multiply(i)).getBlock();
            Block ground = findGround(block);
            if (ground.getType().isAir()) {
                continue;
            }

            ground.getWorld().playEffect(ground.getLocation(), Effect.STEP_SOUND, ground.getType());
            Block above = ground.getRelative(BlockFace.UP);
            if (above.getType().isAir()) {
                FallingBlock falling = ground.getWorld().spawnFallingBlock(above.getLocation().add(0.5, 0.0, 0.5), ground.getBlockData());
                falling.setDropItem(false);
                falling.setVelocity(new Vector(0, 0.4 + i * 0.01, 0));
                falling.setMetadata(SEISMIC_METADATA, new FixedMetadataValue(plugin, Boolean.TRUE));
            }

            Collection<Entity> nearby = ground.getWorld().getNearbyEntities(ground.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5);
            for (Entity entity : nearby) {
                if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                    continue;
                }
                if (!pushed.add(entity.getUniqueId())) {
                    continue;
                }
                if (entity instanceof Player && !player.getWorld().getPVP()) {
                    continue;
                }
                Vector knockback = entity.getLocation().toVector().subtract(player.getLocation().toVector());
                if (knockback.lengthSquared() < 0.04D) {
                    continue;
                }
                knockback.normalize().multiply(1.2D).setY(0.9D);
                entity.setVelocity(knockback);
                living.damage(6.0D, player);
            }
        }

        damageItem(player, event.getItem(), 4);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.75f);
    }

    private void usePickaxeOfTheSeeker(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        denyItemUse(event);
        Player player = event.getPlayer();
        int range = behaviorConfig.seekerRange();
        Block origin = player.getLocation().getBlock();
        Block closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Block block = origin.getRelative(x, y, z);
                    if (!ORE_BLOCKS.contains(block.getType())) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(origin.getLocation());
                    if (closest == null || distance < closestDistance) {
                        closest = block;
                        closestDistance = distance;
                    }
                }
            }
        }

        if (closest == null) {
            send(player, "messages.pickaxe-of-the-seeker.no-ores", "&cCannot find any nearby ores!");
            return;
        }

        double dx = closest.getX() + 0.5D - player.getLocation().getX();
        double dz = closest.getZ() + 0.5D - player.getLocation().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 0.0001D) {
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan((closest.getY() - 0.5D - player.getLocation().getY()) / horizontal));
        var location = player.getLocation().clone();
        location.setYaw(yaw);
        location.setPitch(pitch);
        player.teleport(location);
        damageItem(player, event.getItem(), behaviorConfig.seekerDurabilityCost());
    }

    private void useClimbingPick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            send(event.getPlayer(), "messages.climbing-pick.no-surface", "&cAim at a wall to use your Climbing Picks.");
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        BlockFace face = event.getBlockFace();
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            denyItemUse(event);
            send(player, "messages.climbing-pick.wrong-face", "&cYou need to click the side of a wall to climb.");
            return;
        }

        denyItemUse(event);
        Block block = event.getClickedBlock();
        if (player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) > CLIMB_MAX_DISTANCE_SQUARED) {
            return;
        }

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean needsDualWield = behaviorConfig.climbingPickDualWielding();
        if (needsDualWield && !(matchesId(main, "sf:climbing_pick") && matchesId(off, "sf:climbing_pick"))) {
            send(player, "messages.climbing-pick.dual-wielding", "&4You need to hold Climbing Picks in both hands to use them!");
            return;
        }

        double power = climbPower(event.getItem(), block.getType());
        if (power <= 0.05D) {
            send(player, "messages.climbing-pick.wrong-material", "&cYou cannot climb this surface. Check your Slimefun Guide for more info!");
            return;
        }

        if (!climbingCooldown.add(player.getUniqueId())) {
            return;
        }
        runtime.executeForPlayerLater(player, 4L, () -> climbingCooldown.remove(player.getUniqueId()));

        player.setVelocity(new Vector(0, power, 0));
        player.setFallDistance(0.0f);
        player.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());

        if (needsDualWield && ThreadLocalRandom.current().nextBoolean()) {
            damageItem(player, off, 1);
            player.swingOffHand();
        } else {
            damageItem(player, main, 1);
            player.swingMainHand();
        }
    }

    private void useLumberAxe(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getPlayer().isSneaking()) {
            return;
        }

        Block root = event.getClickedBlock();
        if (!isUnstrippedLog(root)) {
            return;
        }

        event.setCancelled(true);
        List<Block> logs = collectConnected(root, STRIP_LIMIT, this::isUnstrippedLog);
        for (Block log : logs) {
            stripLog(log);
        }
        damageItem(event.getPlayer(), event.getItem(), 1);
    }

    private void applyExplosiveBow(EntityDamageByEntityEvent event, Player shooter, LivingEntity target) {
        target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);

        double radius = 3.0D;
        Collection<Entity> nearby = target.getWorld().getNearbyEntities(target.getLocation(), radius, radius, radius);
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living) || entity.equals(target)) {
                continue;
            }
            if (entity instanceof Player && !shooter.getWorld().getPVP()) {
                continue;
            }

            Vector distanceVector = living.getLocation().toVector().subtract(target.getLocation().toVector()).add(new Vector(0, 0.75, 0));
            double distanceSquared = Math.max(0.01D, distanceVector.lengthSquared());
            double damage = event.getDamage() * (1.0D - (distanceSquared / (2.0D * radius * radius)));
            if (damage <= 0.0D) {
                continue;
            }

            distanceVector.setY(0.75D);
            living.setVelocity(living.getVelocity().add(distanceVector.normalize().multiply(2.0D)));
            living.damage(damage, shooter);
        }
    }

    private void applyIcyBow(EntityDamageByEntityEvent event, LivingEntity target) {
        target.getWorld().playEffect(target.getLocation(), Effect.STEP_SOUND, Material.ICE);
        target.getWorld().playEffect(target.getEyeLocation(), Effect.STEP_SOUND, Material.ICE);
        if (target instanceof Player player) {
            player.setFreezeTicks(Math.max(player.getFreezeTicks(), 60));
        }
        target.addPotionEffect(new PotionEffect(resolvePotion("SLOW"), 40, 10));
        if (target instanceof Player player && player.isBlocking() && event.getFinalDamage() <= 0.0D) {
            return;
        }
    }

    private void applyVampireBlade(Player player) {
        if (ThreadLocalRandom.current().nextInt(100) >= 45) {
            return;
        }
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attribute == null ? 20.0D : attribute.getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + 4.0D));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
    }

    private void handleSmeltersPickaxe(BlockBreakEvent event, ItemStack tool) {
        Block block = event.getBlock();
        ItemStack dropTool = toolForDrops(tool, behaviorConfig.smeltersPickaxeAllowFortune());
        List<ItemStack> drops = new ArrayList<>(block.getDrops(dropTool));
        if (drops.isEmpty()) {
            return;
        }

        boolean changed = false;
        List<ItemStack> outputs = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            ItemStack output = resolveSmelterOutput(drop).orElse(drop).clone();
            output.setAmount(drop.getAmount());
            outputs.add(output);
            if (output.getType() != drop.getType()) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        event.setCancelled(true);
        block.getWorld().playEffect(block.getLocation(), Effect.MOBSPAWNER_FLAMES, 1);
        org.bukkit.Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack output : outputs) {
            org.bukkit.entity.Item dropped = block.getWorld().dropItem(dropLocation, output);
            dropped.setVelocity(new Vector(0.0, 0.0, 0.0));
        }
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, "sf:legacy_tool", block, Material.AIR, true, "legacy-combat", "smelters-pickaxe");
        damageItem(event.getPlayer(), tool, 1);
    }

    private void handleLumberAxeBreak(BlockBreakEvent event, ItemStack tool) {
        Block root = event.getBlock();
        if (event.getPlayer().isSneaking() || !isLog(root)) {
            return;
        }

        event.setCancelled(true);
        List<Block> logs = collectConnected(root, TREE_LIMIT, this::isLog);
        for (Block log : logs) {
            breakBlock(log, tool, true);
        }
        damageItem(event.getPlayer(), tool, 1);
    }

    private void handleContainmentPickaxe(BlockBreakEvent event, ItemStack tool) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        event.setCancelled(true);
        ItemStack drop = createSpawnerDrop(block);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, "sf:legacy_tool", block, Material.AIR, true, "legacy-combat", "containment-pickaxe");
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
        damageItem(event.getPlayer(), tool, 1);
    }

    private void handleExplosiveTool(BlockBreakEvent event, ItemStack tool, boolean shovelOnly, boolean allowFortune) {
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }

        Block center = event.getBlock();
        if (!canExplosiveBreak(center, shovelOnly)) {
            return;
        }

        center.getWorld().createExplosion(center.getLocation(), 0.0F);
        center.getWorld().playSound(center.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);

        int broken = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    Block block = center.getRelative(x, y, z);
                    if (canExplosiveBreak(block, shovelOnly) && breakBlock(block, tool, allowFortune)) {
                        broken++;
                    }
                }
            }
        }

        if (broken > 0) {
            damageItem(player, tool, broken);
        }
    }

    private void handleVeinMining(BlockBreakEvent event, ItemStack tool) {
        Block origin = event.getBlock();
        if (!ORE_BLOCKS.contains(origin.getType())) {
            return;
        }

        event.setCancelled(true);
        List<Block> vein = collectConnected(origin, behaviorConfig.veinMiningMaxBlocks(), block -> ORE_BLOCKS.contains(block.getType()));
        for (Block block : vein) {
            breakBlock(block, tool, behaviorConfig.veinMiningAllowFortune());
        }
        damageItem(event.getPlayer(), tool, 1);
    }

    private boolean canExplosiveBreak(Block block, boolean shovelOnly) {
        if (block.getType().isAir() || block.isLiquid() || UNBREAKABLE.contains(block.getType())) {
            return false;
        }
        return !shovelOnly || SHOVEL_BLOCKS.contains(block.getType());
    }

    private Optional<ItemStack> resolveFurnaceOutput(ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return Optional.empty();
        }

        for (java.util.Iterator<Recipe> iterator = plugin.getServer().recipeIterator(); iterator.hasNext(); ) {
            Recipe recipe = iterator.next();
            if (recipe instanceof CookingRecipe<?> cookingRecipe
                    && cookingRecipe.getInputChoice() != null
                    && cookingRecipe.getInputChoice().test(input)) {
                ItemStack result = cookingRecipe.getResult();
                if (result != null && !result.getType().isAir()) {
                    return Optional.of(result);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<ItemStack> resolveSmelterOutput(ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return Optional.empty();
        }

        Material customOutput = behaviorConfig.smeltersPickaxeCustomOutput(input.getType());
        if (customOutput != null && !customOutput.isAir()) {
            ItemStack output = input.clone();
            output.setType(customOutput);
            return Optional.of(output);
        }

        return resolveFurnaceOutput(input);
    }

    private ItemStack createSpawnerDrop(Block block) {
        ItemStack stack = items.create("sf:broken_spawner");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String entityName = "Unknown";
        if (block.getState() instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
            entityName = prettyEnumName(spawner.getSpawnedType().name());
            meta.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, spawner.getSpawnedType().name());
        }

        meta.lore(List.of(
                Text.renderFlexible(localization.text("items.sf.broken_spawner.type-line", "&7Type: &b{type}", Map.of("type", entityName))),
                Component.empty(),
                Text.renderFlexible(localization.text("items.sf.broken_spawner.fractured-line", "&cFractured, must be repaired in an Ancient Altar"))
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private List<Block> collectConnected(Block origin, int limit, Predicate<Block> predicate) {
        List<Block> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty() && result.size() < limit) {
            Block block = queue.removeFirst();
            String key = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!visited.add(key) || !predicate.test(block)) {
                continue;
            }
            result.add(block);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        queue.add(block.getRelative(x, y, z));
                    }
                }
            }
        }

        return result;
    }

    private void stripLog(Block block) {
        if (isSfxAnchored(block)) {
            return;
        }
        Material stripped = Material.matchMaterial("STRIPPED_" + block.getType().name());
        if (stripped == null) {
            return;
        }
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_AXE_STRIP, 1.0f, 1.0f);
        if (block.getBlockData() instanceof Orientable orientable) {
            var axis = orientable.getAxis();
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, "sf:legacy_tool", block, stripped, true, "legacy-combat", "strip-log");
            if (block.getBlockData() instanceof Orientable updated) {
                updated.setAxis(axis);
                cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, "sf:legacy_tool", block, updated, true, "legacy-combat", "strip-log:axis");
            }
            return;
        }
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, "sf:legacy_tool", block, stripped, true, "legacy-combat", "strip-log");
    }

    private boolean breakBlock(Block block, ItemStack tool, boolean allowFortune) {
        if (block.getType().isAir() || isSfxAnchored(block)) {
            return false;
        }
        Material type = block.getType();
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, type);
        ItemStack dropTool = toolForDrops(tool, allowFortune);
        boolean broken = cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.breakNaturally(null, "sf:legacy_tool", block, dropTool, "legacy-combat", "tool-break");
        if (!broken && block.getType() == type) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, "sf:legacy_tool", block, Material.AIR, true, "legacy-combat", "force-break");
            broken = true;
        }
        return broken;
    }

    private boolean isSfxAnchored(Block block) {
        return block != null && blockData.findAnchor(block.getLocation()).isPresent();
    }

    private Block findGround(Block block) {
        if (!block.getType().isAir()) {
            return block;
        }
        int minHeight = block.getWorld().getMinHeight();
        for (int y = block.getY(); y >= minHeight; y--) {
            Block current = block.getWorld().getBlockAt(block.getX(), y, block.getZ());
            if (!current.getType().isAir()) {
                return current;
            }
        }
        return block;
    }

    private double climbPower(ItemStack item, Material type) {
        double base;
        if (CLIMB_STRONG.contains(type) || type.name().endsWith("_TERRACOTTA")) {
            base = CLIMB_STRONG_POWER;
        } else if (CLIMB_WEAK.contains(type)) {
            base = CLIMB_WEAK_POWER;
        } else {
            base = 0.0D;
        }

        if (base <= 0.0D || item == null) {
            return base;
        }

        int efficiency = item.getEnchantmentLevel(resolveEnchantment("EFFICIENCY"));
        return base + (efficiency * CLIMB_EFFICIENCY_MODIFIER);
    }

    private void damageItem(Player player, ItemStack item, int amount) {
        if (player == null || item == null || item.getType().isAir() || player.getGameMode().name().equals("CREATIVE") || amount <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return;
        }

        int nextDamage = damageable.getDamage() + amount;
        if (nextDamage >= item.getType().getMaxDurability()) {
            item.setAmount(0);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            return;
        }

        damageable.setDamage(nextDamage);
        item.setItemMeta(meta);
    }

    private boolean isLog(Block block) {
        return block.getType().name().endsWith("_LOG") || block.getType().name().endsWith("_STEM") || block.getType().name().endsWith("_HYPHAE");
    }

    private boolean isUnstrippedLog(Block block) {
        return isLog(block) && !block.getType().name().startsWith("STRIPPED_");
    }

    private boolean matchesId(ItemStack item, String itemId) {
        return itemId.equals(itemId(item));
    }

    private String itemId(ItemStack item) {
        return items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
    }

    private BowEffect bowEffect(Arrow arrow) {
        String raw = arrow.getPersistentDataContainer().get(bowEffectKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return BowEffect.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ItemStack resolveHeadDrop(EntityType entityType, Entity entity) {
        if (entityType == EntityType.PLAYER && entity instanceof Player target) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = skull.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(target);
                skull.setItemMeta(skullMeta);
            }
            return skull;
        }

        if (entityType == EntityType.ZOMBIFIED_PIGLIN) {
            return materialDrop("PIGLIN_HEAD");
        }
        if (entityType == EntityType.ENDER_DRAGON) {
            return materialDrop("DRAGON_HEAD");
        }

        String[] candidates = {
                entityType.name() + "_HEAD",
                entityType.name() + "_SKULL"
        };
        for (String candidate : candidates) {
            ItemStack drop = materialDrop(candidate);
            if (drop != null) {
                return drop;
            }
        }
        return null;
    }

    private ItemStack materialDrop(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material == null ? null : new ItemStack(material);
    }

    private ItemStack toolForDrops(ItemStack tool, boolean allowFortune) {
        if (tool == null || allowFortune) {
            return tool;
        }

        ItemStack clone = tool.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            Enchantment fortune = resolveEnchantment("FORTUNE");
            if (fortune != null && meta.hasEnchant(fortune)) {
                meta.removeEnchant(fortune);
                clone.setItemMeta(meta);
            }
        }
        return clone;
    }

    private void addChanceDrop(EntityDeathEvent event, ThreadLocalRandom random, int chance, ItemStack item) {
        if (chance > 0 && random.nextInt(100) < chance) {
            event.getDrops().add(item);
        }
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback)));
    }

    private ItemStack itemInHand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null) {
            return item;
        }
        return event.getPlayer().getInventory().getItemInMainHand();
    }

    private void denyItemUse(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    private PotionEffectType resolvePotion(String rawName) {
        PotionEffectType type = PotionEffectType.getByName(rawName);
        if (type != null) {
            return type;
        }
        String key = switch (rawName.toUpperCase(Locale.ROOT)) {
            case "SLOW" -> "slowness";
            case "JUMP" -> "jump_boost";
            default -> rawName.toLowerCase(Locale.ROOT);
        };
        return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
    }

    private Enchantment resolveEnchantment(String rawName) {
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(rawName.toLowerCase(Locale.ROOT)));
        if (enchantment != null) {
            return enchantment;
        }
        return Enchantment.getByName(switch (rawName.toUpperCase(Locale.ROOT)) {
            case "EFFICIENCY" -> "DIG_SPEED";
            case "FORTUNE" -> "LOOT_BONUS_BLOCKS";
            default -> rawName.toUpperCase(Locale.ROOT);
        });
    }

    private String prettyEnumName(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }


}
