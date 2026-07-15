package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.SfxEnchantmentRules;
import cc.theends6.sfx.api.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxAncientRuneEffectListener implements Listener {
    private static final double RADIUS = 1.5D;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final NamespacedKey soulboundKey;

    public SfxAncientRuneEffectListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.soulboundKey = new NamespacedKey(plugin, "soulbound");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item rune = event.getItemDrop();
        String itemId = itemId(rune.getItemStack());
        if (!"sf:ancient_rune_enchantment".equals(itemId) && !"sf:ancient_rune_soulbound".equals(itemId)) {
            return;
        }
        Location location = rune.getLocation();
        runtime.executeAtLater(location, 20L, () -> {
            if (!rune.isValid() || rune.getItemStack().getAmount() <= 0) {
                return;
            }
            if ("sf:ancient_rune_enchantment".equals(itemId)) {
                applyEnchantmentRune(rune);
            } else {
                applySoulboundRune(rune);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!"sf:ancient_rune_villagers".equals(itemId(hand))) {
            return;
        }
        if (villager.getProfession() == Villager.Profession.NONE || villager.getProfession() == Villager.Profession.NITWIT) {
            return;
        }
        event.setCancelled(true);
        consumeOne(hand, player);
        villager.setProfession(Villager.Profession.NONE);
        villager.setVillagerLevel(1);
        villager.setVillagerExperience(0);
        villager.getWorld().spawnParticle(Particle.ENCHANT, villager.getLocation().add(0.0D, 1.0D, 0.0D), 32, 0.5D, 0.7D, 0.5D, 0.0D);
        villager.getWorld().playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_WORK_LIBRARIAN, 1.0F, 0.65F);
    }

    private void applyEnchantmentRune(Item rune) {
        Item target = findTargetItem(rune).orElse(null);
        if (target == null) {
            return;
        }
        ItemStack targetStack = target.getItemStack();
        Optional<Enchantment> enchantment = randomLegalEnchant(targetStack);
        if (enchantment.isEmpty()) {
            return;
        }
        Enchantment selected = enchantment.get();
        ItemStack result = targetStack.clone();
        result.addUnsafeEnchantment(selected, randomLevel(selected));
        consumeRuneEntity(rune);
        target.remove();
        dropResult(target.getLocation(), result);
        playRuneEffect(target.getLocation());
    }

    private void applySoulboundRune(Item rune) {
        Item target = findTargetItem(rune).orElse(null);
        if (target == null) {
            return;
        }
        ItemStack result = target.getItemStack().clone();
        if (isSoulbound(result)) {
            return;
        }
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.legacy("&6Soulbound"));
        meta.lore(lore);
        result.setItemMeta(meta);
        consumeRuneEntity(rune);
        target.remove();
        dropResult(target.getLocation(), result);
        playRuneEffect(target.getLocation());
    }

    private Optional<Item> findTargetItem(Item rune) {
        Location location = rune.getLocation();
        Item best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : location.getWorld().getNearbyEntities(location, RADIUS, RADIUS, RADIUS)) {
            if (!(entity instanceof Item item) || item.getUniqueId().equals(rune.getUniqueId()) || !item.isValid()) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir() || stack.getAmount() != 1) {
                continue;
            }
            String targetId = itemId(stack);
            if (targetId != null && (targetId.startsWith("sf:ancient_rune") || "sf:blank_rune".equals(targetId))) {
                continue;
            }
            double distance = item.getLocation().distanceSquared(location);
            if (distance < bestDistance) {
                best = item;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private void consumeRuneEntity(Item rune) {
        ItemStack stack = rune.getItemStack();
        int next = stack.getAmount() - 1;
        if (next <= 0) {
            rune.remove();
        } else {
            stack.setAmount(next);
            rune.setItemStack(stack);
        }
    }

    private void dropResult(Location location, ItemStack result) {
        Item dropped = location.getWorld().dropItemNaturally(location, result);
        dropped.setPickupDelay(0);
    }

    private void playRuneEffect(Location location) {
        location.getWorld().spawnParticle(Particle.ENCHANT, location, 48, 0.7D, 0.7D, 0.7D, 0.0D);
        location.getWorld().strikeLightningEffect(location);
        location.getWorld().playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
    }

    private Optional<Enchantment> randomLegalEnchant(ItemStack item) {
        Map<Enchantment, Integer> existing = item.getEnchantments();
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment == null || !enchantment.canEnchantItem(item) || existing.containsKey(enchantment)) {
                continue;
            }
            if (SfxEnchantmentRules.conflictsWithExisting(existing, enchantment)) {
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
        int start = Math.max(1, enchantment.getStartLevel());
        int max = Math.max(start, enchantment.getMaxLevel());
        return ThreadLocalRandom.current().nextInt(start, max + 1);
    }

    private boolean isSoulbound(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(soulboundKey, PersistentDataType.BYTE);
    }

    private String itemId(ItemStack item) {
        return items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
    }

    private void consumeOne(ItemStack stack, Player player) {
        if (stack == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        int next = stack.getAmount() - 1;
        if (next <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(next);
        }
    }
}
