package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SfxTalismanListener implements Listener {
    private static final Set<String> CONSUMABLE = Set.of("anvil", "lava", "water", "fire", "warrior", "knight");
    private static final Set<Material> FARMER_BLOCKS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
            Material.COCOA, Material.MELON, Material.PUMPKIN
    );

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;

    public SfxTalismanListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        switch (event.getCause()) {
            case LAVA -> {
                if (activate(player, "lava", event)) {
                    addPotion(player, "FIRE_RESISTANCE", 20 * 30, 0);
                    event.setCancelled(true);
                }
            }
            case FIRE, FIRE_TICK -> {
                if (activate(player, "fire", event)) {
                    addPotion(player, "FIRE_RESISTANCE", 20 * 30, 0);
                    event.setCancelled(true);
                }
            }
            case DROWNING -> {
                if (activate(player, "water", event)) {
                    addPotion(player, "WATER_BREATHING", 20 * 30, 0);
                    event.setCancelled(true);
                }
            }
            case FALL -> {
                if (activate(player, "angel", event)) {
                    event.setCancelled(true);
                }
            }
            case ENTITY_ATTACK -> {
                if (activate(player, "warrior", event)) {
                    addPotion(player, "INCREASE_DAMAGE", 20 * 15, 2);
                }
                if (activate(player, "knight", event)) {
                    addPotion(player, "REGENERATION", 20 * 5, 0);
                }
            }
            case PROJECTILE -> {
                if (event instanceof EntityDamageByEntityEvent projectileDamage && activate(player, "whirlwind", event)) {
                    projectileDamage.getDamager().remove();
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
            addPotion(event.getPlayer(), "SPEED", 20 * 10, 1);
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
            addPotion(event.getPlayer(), "FAST_DIGGING", 20 * 8, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        Material type = event.getBlockState().getType();
        String talismanType = isOre(type) ? "miner" : (FARMER_BLOCKS.contains(type) ? "farmer" : null);
        if (talismanType == null || !activate(event.getPlayer(), talismanType, event)) {
            return;
        }
        event.getItems().forEach(item -> {
            ItemStack stack = item.getItemStack();
            if (!stack.getType().isBlock()) {
                item.getWorld().dropItemNaturally(item.getLocation(), stack.clone());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player || !activate(killer, "hunter", event)) {
            return;
        }
        for (ItemStack drop : event.getDrops().toArray(ItemStack[]::new)) {
            if (drop != null && !drop.getType().isAir()) {
                event.getDrops().add(drop.clone());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Map<Enchantment, Integer> additions = event.getEnchantsToAdd();
        if (activate(event.getEnchanter(), "magician", event)) {
            Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (unbreaking != null) {
                additions.put(unbreaking, Math.max(additions.getOrDefault(unbreaking, 0), 1));
            }
        }
        if (activate(event.getEnchanter(), "wizard", event)) {
            Enchantment fortune = Enchantment.getByKey(NamespacedKey.minecraft("fortune"));
            if (fortune != null && fortune.canEnchantItem(event.getItem())) {
                additions.put(fortune, ThreadLocalRandom.current().nextInt(3, 6));
            }
        }
    }

    private boolean activate(Player player, String type, Event event) {
        if (ThreadLocalRandom.current().nextInt(100) >= chance(type)) {
            return false;
        }
        InventoryMatch match = findTalisman(player.getInventory(), type, false);
        if (match == null) {
            match = findTalisman(player.getEnderChest(), type, true);
        }
        if (match == null) {
            return false;
        }
        if (CONSUMABLE.contains(type)) {
            ItemStack stack = match.inventory().getItem(match.slot());
            if (stack != null) {
                int amount = stack.getAmount() - 1;
                match.inventory().setItem(match.slot(), amount <= 0 ? null : withAmount(stack, amount));
            }
        }
        return true;
    }

    private InventoryMatch findTalisman(Inventory inventory, String type, boolean requireEnderVariant) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            Optional<SfxItemMarker> marker = items.readMarker(stack);
            if (marker.isEmpty()) {
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

    private int chance(String type) {
        return switch (type) {
            case "miner", "farmer", "hunter", "wise" -> 20;
            case "knight" -> 30;
            case "caveman" -> 50;
            case "traveller", "whirlwind" -> 60;
            case "angel" -> 75;
            case "magician" -> 80;
            default -> 100;
        };
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private record InventoryMatch(Inventory inventory, int slot) {
    }
}
