package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.config.SfxTalismanBehaviorConfig;
import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.util.SfxEnchantmentRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SfxTalismanListener implements Listener {
    private static final Set<Material> FARMER_BLOCKS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
            Material.COCOA, Material.MELON, Material.PUMPKIN
    );

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxResearchService researches;
    private final SfxTalismanBehaviorConfig config;

    public SfxTalismanListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxResearchService researches, SfxTalismanBehaviorConfig config) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.items = items;
        this.researches = researches;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        switch (event.getCause()) {
            case LAVA -> {
                if (activate(player, "lava", event)) {
                    addPotion(player, "FIRE_RESISTANCE", config.durationTicks("lava"), config.amplifier("lava"));
                    event.setCancelled(true);
                }
            }
            case FIRE, FIRE_TICK -> {
                if (activate(player, "fire", event)) {
                    addPotion(player, "FIRE_RESISTANCE", config.durationTicks("fire"), config.amplifier("fire"));
                    event.setCancelled(true);
                }
            }
            case DROWNING -> {
                if (activate(player, "water", event)) {
                    addPotion(player, "WATER_BREATHING", config.durationTicks("water"), config.amplifier("water"));
                    event.setCancelled(true);
                }
            }
            case FALL -> {
                if (activate(player, "angel", event)) {
                    event.setCancelled(true);
                }
            }
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> {
                if (activate(player, "warrior", event)) {
                    addPotion(player, "INCREASE_DAMAGE", config.durationTicks("warrior"), config.amplifier("warrior"));
                }
                if (activate(player, "knight", event)) {
                    addPotion(player, "REGENERATION", config.durationTicks("knight"), config.amplifier("knight"));
                }
            }
            case PROJECTILE -> {
                if (event instanceof EntityDamageByEntityEvent projectileDamage && projectileDamage.getDamager() instanceof Projectile projectile && activate(player, "whirlwind", event)) {
                    reflectProjectile(player, projectile);
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (!activate(event.getPlayer(), "anvil", event)) {
            return;
        }
        PlayerInventory inventory = event.getPlayer().getInventory();
        ItemStack restored = event.getBrokenItem().clone();
        ItemMeta meta = restored.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
            restored.setItemMeta(meta);
        }
        runtime.executeForPlayerLater(event.getPlayer(), 1L, () -> {
            ItemStack main = inventory.getItemInMainHand();
            if (main.getType().isAir()) {
                inventory.setItemInMainHand(restored);
            } else {
                event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), restored);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && activate(event.getPlayer(), "traveller", event)) {
            addPotion(event.getPlayer(), "SPEED", config.durationTicks("traveller"), config.amplifier("traveller"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0 && activate(event.getPlayer(), "wise", event)) {
            event.setAmount(event.getAmount() * 2);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (isOre(type) && activate(event.getPlayer(), "caveman", event)) {
            addPotion(event.getPlayer(), "FAST_DIGGING", config.durationTicks("caveman"), config.amplifier("caveman"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        Material type = event.getBlockState().getType();
        String talismanType = isOre(type) ? "miner" : (isFarmerTarget(type, event) ? "farmer" : null);
        if (talismanType == null || !activate(event.getPlayer(), talismanType, event)) {
            return;
        }
        boolean allowBlockDrops = "miner".equals(talismanType) ? config.minerDuplicateBlockDrops() : config.farmerDuplicateBlockDrops();
        event.getItems().forEach(item -> {
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            if (!allowBlockDrops && stack.getType().isBlock()) {
                return;
            }
            item.getWorld().dropItemNaturally(item.getLocation(), stack.clone());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player || event.getEntity() instanceof ArmorStand || !activate(killer, "hunter", event)) {
            return;
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            if (!config.hunterCopyEquipmentDrops() && isEquipmentDrop(event.getEntity(), drop)) {
                continue;
            }
            copies.add(drop.clone());
        }
        event.getDrops().addAll(copies);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Map<Enchantment, Integer> additions = event.getEnchantsToAdd();
        ItemStack item = event.getItem();
        if (activate(event.getEnchanter(), "magician", event)) {
            randomLegalEnchant(item, additions).ifPresent(enchantment -> additions.put(enchantment, randomLevel(enchantment)));
        }
        if (activate(event.getEnchanter(), "wizard", event)) {
            Enchantment silkTouch = enchantment("silk_touch");
            Enchantment fortune = enchantment("fortune");
            if (fortune != null && fortune.canEnchantItem(item)
                    && (silkTouch == null || (!item.containsEnchantment(silkTouch) && !additions.containsKey(silkTouch)))) {
                degradeWizardEnchantments(event.getEnchanter(), additions, fortune);
                int min = config.wizardFortuneMin();
                int max = config.wizardFortuneMax();
                int level = ThreadLocalRandom.current().nextInt(min, max + 1);
                additions.put(fortune, level);
                SfxValidationDiagnostics.log(plugin, "talisman", "wizard fortune=" + level + " player=" + event.getEnchanter().getName());
            }
        }
    }

    private void degradeWizardEnchantments(Player player, Map<Enchantment, Integer> enchantments, Enchantment fortune) {
        if (!config.wizardDegradeExistingEnchantments() || enchantments.isEmpty()) {
            return;
        }
        int chance = config.wizardDegradeChance();
        if (chance <= 0) {
            return;
        }
        for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(enchantments.entrySet())) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (enchantment == null || enchantment.equals(fortune) || level <= 1 || ThreadLocalRandom.current().nextInt(100) >= chance) {
                continue;
            }
            enchantments.put(enchantment, level - 1);
            SfxValidationDiagnostics.log(plugin, "talisman", "wizard degraded=" + enchantment.getKey() + " " + level + "->" + (level - 1) + " player=" + player.getName());
        }
    }

    private boolean activate(Player player, String type, Event event) {
        if (ThreadLocalRandom.current().nextInt(100) >= config.chance(type)) {
            return false;
        }
        InventoryMatch match = findTalisman(player, player.getInventory(), type, false);
        if (match == null) {
            match = findTalisman(player, player.getEnderChest(), type, true);
        }
        if (match == null) {
            return false;
        }
        if (config.consume(type)) {
            ItemStack stack = match.inventory().getItem(match.slot());
            if (stack != null) {
                int amount = stack.getAmount() - 1;
                match.inventory().setItem(match.slot(), amount <= 0 ? null : withAmount(stack, amount));
            }
        }
        return true;
    }

    private InventoryMatch findTalisman(Player player, Inventory inventory, String type, boolean requireEnderVariant) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            Optional<SfxItemMarker> marker = items.readMarker(stack);
            if (marker.isEmpty()) {
                continue;
            }
            if (!researches.canUse(player, marker.get().itemId())) {
                continue;
            }
            var flags = marker.get().flags();
            if (flags.contains("talisman-" + type) && (!requireEnderVariant || flags.contains("ender-talisman"))) {
                return new InventoryMatch(inventory, i);
            }
        }
        return null;
    }

    private ItemStack withAmount(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    private void addPotion(Player player, String typeName, int durationTicks, int amplifier) {
        PotionEffectType type = resolvePotion(typeName);
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
        }
    }

    private PotionEffectType resolvePotion(String typeName) {
        PotionEffectType type = PotionEffectType.getByName(typeName);
        if (type != null) {
            return type;
        }
        String key = switch (typeName) {
            case "INCREASE_DAMAGE" -> "strength";
            case "FAST_DIGGING" -> "haste";
            case "JUMP" -> "jump_boost";
            default -> typeName.toLowerCase(java.util.Locale.ROOT);
        };
        return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private boolean isFarmerTarget(Material material, BlockDropItemEvent event) {
        if (!FARMER_BLOCKS.contains(material)) {
            return false;
        }
        if (event.getBlockState().getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void reflectProjectile(Player player, Projectile projectile) {
        try {
            Class<? extends Projectile> projectileClass = (Class<? extends Projectile>) projectile.getClass();
            Projectile reflected = player.launchProjectile(projectileClass, projectile.getVelocity().multiply(-1.0D));
            reflected.setShooter(player);
        } catch (IllegalArgumentException ignored) {
            // Some plugin-specific projectile classes cannot be launched. Cancellation still protects the player.
        }
        projectile.remove();
    }

    private boolean isEquipmentDrop(LivingEntity entity, ItemStack drop) {
        if (entity instanceof AbstractHorse) {
            return true;
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || drop == null) {
            return false;
        }
        ItemStack[] equipmentItems = {
                equipment.getItemInMainHand(), equipment.getItemInOffHand(),
                equipment.getHelmet(), equipment.getChestplate(), equipment.getLeggings(), equipment.getBoots()
        };
        for (ItemStack equipmentItem : equipmentItems) {
            if (equipmentItem != null && !equipmentItem.getType().isAir() && equipmentItem.isSimilar(drop)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Enchantment> randomLegalEnchant(ItemStack item, Map<Enchantment, Integer> additions) {
        Map<Enchantment, Integer> existing = new java.util.HashMap<>(item.getEnchantments());
        existing.putAll(additions);
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment == null || !enchantment.canEnchantItem(item)) {
                continue;
            }
            if (existing.containsKey(enchantment) || SfxEnchantmentRules.conflictsWithExisting(existing, enchantment)) {
                continue;
            }
            candidates.add(enchantment);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    private int randomLevel(Enchantment enchantment) {
        int max = Math.max(1, enchantment.getMaxLevel());
        int start = Math.max(1, enchantment.getStartLevel());
        return ThreadLocalRandom.current().nextInt(start, max + 1);
    }

    private Enchantment enchantment(String key) {
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    private record InventoryMatch(Inventory inventory, int slot) {
    }
}
